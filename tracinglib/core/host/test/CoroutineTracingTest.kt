/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.app.tracing.coroutines

import com.android.app.tracing.FakeTraceState.getOpenTraceSectionsOnCurrentThread
import com.android.app.tracing.setAndroidSystemTracingEnabled
import com.android.systemui.Flags
import com.android.systemui.util.Compile
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@RunWith(BlockJUnit4ClassRunner::class)
class CoroutineTracingTest {
    @Before
    fun setup() {
        TraceData.strictModeForTesting = true
        Compile.setIsDebug(true)
        Flags.setCoroutineTracingEnabled(true)
        setAndroidSystemTracingEnabled(true)
    }

    @After
    fun checkFinished() {
        val lastEvent = eventCounter.get()
        assertTrue(
            "Expected `finish(${lastEvent + 1})` to be called, but the test finished",
            lastEvent == FINAL_EVENT || lastEvent == 0,
        )
    }

    @Test
    fun simpleTraceSection() = runTestWithTraceContext {
        expect(1)
        traceCoroutine("hello") { expect(2, "hello") }
        finish(3)
    }

    @Test
    fun simpleNestedTraceSection() = runTestWithTraceContext {
        expect(1)
        traceCoroutine("hello") {
            expect(2, "hello")
            traceCoroutine("world") { expect(3, "hello", "world") }
            expect(4, "hello")
        }
        finish(5)
    }

    @Test
    fun simpleLaunch() = runTestWithTraceContext {
        expect(1)
        traceCoroutine("hello") {
            expect(2, "hello")
            launch { finish(4, "hello") }
        }
        expect(3)
    }

    @Test
    fun launchWithSuspendingLambda() = runTestWithTraceContext {
        val fetchData: suspend () -> String = {
            expect(3, "span-for-launch")
            delay(1L)
            traceCoroutine("span-for-fetchData") {
                expect(4, "span-for-launch", "span-for-fetchData")
            }
            "stuff"
        }
        expect(1)
        launch("span-for-launch") {
            assertEquals("stuff", fetchData())
            finish(5, "span-for-launch")
        }
        expect(2)
    }

    @Test
    fun nestedUpdateAndRestoreOnSingleThread_unconfinedDispatcher() = runTestWithTraceContext {
        traceCoroutine("parent-span") {
            expect(1, "parent-span")
            launch(UnconfinedTestDispatcher(scheduler = testScheduler)) {
                // While this may appear unusual, it is actually expected behavior:
                //   1) The parent has an open trace section called "parent-span".
                //   2) The child launches, it inherits from its parent, and it is resumed
                //      immediately due to its use of the unconfined dispatcher.
                //   3) The child emits all the trace sections known to its scope. The parent
                //      does not have an opportunity to restore its context yet.
                traceCoroutine("child-span") {
                    // [parent's active trace]
                    //           \  [trace section inherited from parent]
                    //            \                 |    [new trace section in child scope]
                    //             \                |             /
                    expect(2, "parent-span", "parent-span", "child-span")
                    delay(1) // <-- delay will give parent a chance to restore its context
                    // After a delay, the parent resumes, finishing its trace section, so we are
                    // left with only those in the child's scope
                    finish(4, "parent-span", "child-span")
                }
            }
        }
        expect(3)
    }

    /** @see nestedUpdateAndRestoreOnSingleThread_unconfinedDispatcher */
    @Test
    fun nestedUpdateAndRestoreOnSingleThread_undispatchedLaunch() = runTestWithTraceContext {
        traceCoroutine("parent-span") {
            launch(start = CoroutineStart.UNDISPATCHED) {
                traceCoroutine("child-span") {
                    expect(1, "parent-span", "parent-span", "child-span")
                    delay(1) // <-- delay will give parent a chance to restore its context
                    finish(3, "parent-span", "child-span")
                }
            }
        }
        expect(2)
    }

    @Test
    fun launchOnSeparateThread_defaultDispatcher() = runTestWithTraceContext {
        val channel = Channel<Int>()
        val bgThread = newSingleThreadContext("thread-#1")
        expect()
        traceCoroutine("hello") {
            expect(1, "hello")
            launch(bgThread) {
                expect(2, "hello")
                traceCoroutine("world") {
                    expect("hello", "world")
                    channel.send(1)
                    expect(3, "hello", "world")
                }
            }
            expect("hello")
        }
        expect()
        assertEquals(1, channel.receive())
        finish(4)
    }

    @Test
    fun testTraceStorage() = runTestWithTraceContext {
        val channel = Channel<Int>()
        val fetchData: suspend () -> String = {
            traceCoroutine("span-for-fetchData") {
                channel.receive()
                expect("span-for-launch", "span-for-fetchData")
            }
            "stuff"
        }
        val threadContexts =
            listOf(
                newSingleThreadContext("thread-#1"),
                newSingleThreadContext("thread-#2"),
                newSingleThreadContext("thread-#3"),
                newSingleThreadContext("thread-#4"),
            )

        val finishedLaunches = Channel<Int>()

        // Start 1000 coroutines waiting on [channel]
        val job = launch {
            repeat(1000) {
                launch("span-for-launch", threadContexts[it % threadContexts.size]) {
                    assertNotNull(traceThreadLocal.get())
                    assertEquals("stuff", fetchData())
                    expect("span-for-launch")
                    assertNotNull(traceThreadLocal.get())
                    expect("span-for-launch")
                    finishedLaunches.send(it)
                }
                expect()
            }
        }
        // Resume half the coroutines that are waiting on this channel
        repeat(500) { channel.send(1) }
        var receivedClosures = 0
        repeat(500) {
            finishedLaunches.receive()
            receivedClosures++
        }
        // ...and cancel the rest
        job.cancel()
    }

    private fun CoroutineScope.testTraceSectionsMultiThreaded(
        thread1Context: CoroutineContext,
        thread2Context: CoroutineContext
    ) {
        val fetchData1: suspend () -> String = {
            expect("span-for-launch-1")
            delay(1L)
            traceCoroutine("span-for-fetchData-1") {
                expect("span-for-launch-1", "span-for-fetchData-1")
            }
            expect("span-for-launch-1")
            "stuff-1"
        }

        val fetchData2: suspend () -> String = {
            expect(
                "span-for-launch-1",
                "span-for-launch-2",
            )
            delay(1L)
            traceCoroutine("span-for-fetchData-2") {
                expect("span-for-launch-1", "span-for-launch-2", "span-for-fetchData-2")
            }
            expect(
                "span-for-launch-1",
                "span-for-launch-2",
            )
            "stuff-2"
        }

        val thread1 = newSingleThreadContext("thread-#1") + thread1Context
        val thread2 = newSingleThreadContext("thread-#2") + thread2Context

        launch("span-for-launch-1", thread1) {
            assertEquals("stuff-1", fetchData1())
            expect("span-for-launch-1")
            launch("span-for-launch-2", thread2) {
                assertEquals("stuff-2", fetchData2())
                expect("span-for-launch-1", "span-for-launch-2")
            }
            expect("span-for-launch-1")
        }
        expect()

        // Launching without the trace extension won't result in traces
        launch(thread1) { expect() }
        launch(thread2) { expect() }
    }

    @Test
    fun nestedTraceSectionsMultiThreaded1() = runTestWithTraceContext {
        // Thread-#1 and Thread-#2 inherit TraceContextElement from the test's CoroutineContext.
        testTraceSectionsMultiThreaded(
            thread1Context = EmptyCoroutineContext,
            thread2Context = EmptyCoroutineContext
        )
    }

    @Test
    fun nestedTraceSectionsMultiThreaded2() = runTest {
        // Thread-#2 inherits the TraceContextElement from Thread-#1. The test's CoroutineContext
        // does not need a TraceContextElement because it does not do any tracing.
        testTraceSectionsMultiThreaded(
            thread1Context = TraceContextElement(),
            thread2Context = EmptyCoroutineContext
        )
    }

    @Test
    fun nestedTraceSectionsMultiThreaded3() = runTest {
        // Thread-#2 overrides the TraceContextElement from Thread-#1, but the merging context
        // should be fine; it is essentially a no-op. The test's CoroutineContext does not need the
        // trace context because it does not do any tracing.
        testTraceSectionsMultiThreaded(
            thread1Context = TraceContextElement(),
            thread2Context = TraceContextElement()
        )
    }

    @Test
    fun nestedTraceSectionsMultiThreaded4() = runTestWithTraceContext {
        // TraceContextElement is merged on each context switch, which should have no effect on the
        // trace results.
        testTraceSectionsMultiThreaded(
            thread1Context = TraceContextElement(),
            thread2Context = TraceContextElement()
        )
    }

    @Test
    fun missingTraceContextObjects() = runTest {
        val channel = Channel<Int>()
        // Thread-#1 is missing a TraceContextElement, so some of the trace sections get dropped.
        // The resulting trace sections will be different than the 4 tests above.
        val fetchData1: suspend () -> String = {
            expect()
            channel.receive()
            traceCoroutine("span-for-fetchData-1") { expect() }
            expect()
            "stuff-1"
        }

        val fetchData2: suspend () -> String = {
            expect(
                "span-for-launch-2",
            )
            channel.receive()
            traceCoroutine("span-for-fetchData-2") {
                expect("span-for-launch-2", "span-for-fetchData-2")
            }
            expect(
                "span-for-launch-2",
            )
            "stuff-2"
        }

        val thread1 = newSingleThreadContext("thread-#1")
        val thread2 = newSingleThreadContext("thread-#2") + TraceContextElement()

        launch("span-for-launch-1", thread1) {
            assertEquals("stuff-1", fetchData1())
            expect()
            launch("span-for-launch-2", thread2) {
                assertEquals("stuff-2", fetchData2())
                expect("span-for-launch-2")
            }
            expect()
        }
        expect()

        channel.send(1)
        channel.send(2)

        // Launching without the trace extension won't result in traces
        launch(thread1) { expect() }
        launch(thread2) { expect() }
    }

    /**
     * Tests interleaving:
     * ```
     * Thread #1 | [updateThreadContext]....^              [restoreThreadContext]
     * --------------------------------------------------------------------------------------------
     * Thread #2 |                           [updateThreadContext]...........^[restoreThreadContext]
     * ```
     *
     * This test checks for issues with concurrent modification of the trace state. For example, the
     * test should fail if [TraceData.endAllOnThread] uses the size of the slices array as follows
     * instead of using the ThreadLocal count:
     * ```
     * class TraceData {
     *   ...
     *   fun endAllOnThread() {
     *     repeat(slices.size) {
     *       // THIS WOULD BE AN ERROR. If the thread is slow, the TraceData object could have been
     *       // modified by another thread
     *       endSlice()
     *     }
     *   ...
     *   }
     * }
     * ```
     */
    @Test
    fun coroutineMachinery() {
        assertNull(traceThreadLocal.get())
        val traceContext = TraceContextElement()
        assertNull(traceThreadLocal.get())

        val thread1ResumptionPoint = CyclicBarrier(2)
        val thread1SuspensionPoint = CyclicBarrier(2)

        val thread1 = Executors.newSingleThreadExecutor()
        val thread2 = Executors.newSingleThreadExecutor()
        val slicesForThread1 = listOf("a", "c", "e", "g")
        val slicesForThread2 = listOf("b", "d", "f", "h")
        var failureOnThread1: Error? = null
        var failureOnThread2: Error? = null

        val expectedTraceForThread1 = arrayOf("1:a", "2:b", "1:c", "2:d", "1:e", "2:f", "1:g")
        thread1.execute {
            try {
                slicesForThread1.forEachIndexed { index, sliceName ->
                    assertNull(traceThreadLocal.get())
                    val oldTrace = traceContext.updateThreadContext(EmptyCoroutineContext)
                    // await() AFTER updateThreadContext, thus thread #1 always resumes the
                    // coroutine before thread #2
                    assertSame(traceThreadLocal.get(), traceContext.traceData)

                    // coroutine body start {
                    traceThreadLocal.get()?.beginSpan("1:$sliceName")

                    // At the end, verify the interleaved trace sections look correct:
                    if (index == slicesForThread1.size - 1) {
                        expect(*expectedTraceForThread1)
                    }

                    // simulate a slow thread, wait to call restoreThreadContext until after thread
                    // A
                    // has resumed
                    thread1SuspensionPoint.await(3, TimeUnit.SECONDS)
                    Thread.sleep(500)
                    // } coroutine body end

                    traceContext.restoreThreadContext(EmptyCoroutineContext, oldTrace)
                    thread1ResumptionPoint.await(3, TimeUnit.SECONDS)
                    assertNull(traceThreadLocal.get())
                }
            } catch (e: Error) {
                failureOnThread1 = e
            }
        }

        val expectedTraceForThread2 =
            arrayOf("1:a", "2:b", "1:c", "2:d", "1:e", "2:f", "1:g", "2:h")
        thread2.execute {
            try {
                slicesForThread2.forEachIndexed { i, n ->
                    assertNull(traceThreadLocal.get())
                    thread1SuspensionPoint.await(3, TimeUnit.SECONDS)

                    val oldTrace: TraceData? =
                        traceContext.updateThreadContext(EmptyCoroutineContext)

                    // coroutine body start {
                    traceThreadLocal.get()?.beginSpan("2:$n")

                    // At the end, verify the interleaved trace sections look correct:
                    if (i == slicesForThread2.size - 1) {
                        expect(*expectedTraceForThread2)
                    }
                    // } coroutine body end

                    traceContext.restoreThreadContext(EmptyCoroutineContext, oldTrace)
                    thread1ResumptionPoint.await(3, TimeUnit.SECONDS)
                    assertNull(traceThreadLocal.get())
                }
            } catch (e: Error) {
                failureOnThread2 = e
            }
        }

        thread1.shutdown()
        thread1.awaitTermination(5, TimeUnit.SECONDS)
        thread2.shutdown()
        thread2.awaitTermination(5, TimeUnit.SECONDS)

        assertNull("Failure executing coroutine on thread-#1.", failureOnThread1)
        assertNull("Failure executing coroutine on thread-#2.", failureOnThread2)
    }

    @Test
    fun scopeReentry_withContextFastPath() = runTestWithTraceContext {
        val channel = Channel<Int>()
        val bgThread = newSingleThreadContext("bg-thread #1")
        val job =
            launch("#1", bgThread) {
                expect("#1")
                var i = 0
                while (true) {
                    expect("#1")
                    channel.send(i++)
                    expect("#1")
                    // when withContext is passed the same scope, it takes a fast path, dispatching
                    // immediately. This means that in subsequent loops, if we do not handle reentry
                    // correctly in TraceContextElement, the trace may become deeply nested:
                    // "#1", "#1", "#1", ... "#2"
                    withContext(bgThread) {
                        expect("#1")
                        traceCoroutine("#2") {
                            expect("#1", "#2")
                            channel.send(i++)
                            expect("#1", "#2")
                        }
                        expect("#1")
                    }
                }
            }
        repeat(1000) {
            expect()
            traceCoroutine("receive") {
                expect("receive")
                val receivedVal = channel.receive()
                assertEquals(it, receivedVal)
                expect("receive")
            }
            expect()
        }
        job.cancel()
    }

    @Test
    fun traceContextIsCopied() = runTest {
        expect()
        val traceContext = TraceContextElement()
        expect()
        withContext(traceContext) {
            // Not the same object because it should be copied into the current context
            assertNotSame(traceThreadLocal.get(), traceContext.traceData)
            assertNotSame(traceThreadLocal.get()?.slices, traceContext.traceData?.slices)
            expect()
            traceCoroutine("hello") {
                assertNotSame(traceThreadLocal.get(), traceContext.traceData)
                assertNotSame(traceThreadLocal.get()?.slices, traceContext.traceData?.slices)
                assertArrayEquals(arrayOf("hello"), traceThreadLocal.get()?.slices?.toArray())
            }
            assertNotSame(traceThreadLocal.get(), traceContext.traceData)
            assertNotSame(traceThreadLocal.get()?.slices, traceContext.traceData?.slices)
            expect()
        }
        expect()
    }

    @Test
    fun tracingDisabledWhenFlagIsOff() = runTest {
        Flags.setCoroutineTracingEnabled(false)
        assertNull(traceThreadLocal.get())
        withContext(createCoroutineTracingContext()) {
            assertNull(traceThreadLocal.get())
            traceCoroutine("hello") { assertNull(traceThreadLocal.get()) }
        }
    }

    @Test
    fun lazyStringIsAlwaysCalledOnDebugBuilds() = runTest {
        setAndroidSystemTracingEnabled(false)
        assertNull(traceThreadLocal.get())
        withContext(createCoroutineTracingContext()) {
            assertNotNull(traceThreadLocal.get())

            // When Compile.IS_DEBUG=true, it is expected that the lazy-String is called even when
            // tracing is disabled, because otherwise the coroutine resumption points would be
            // missing their names.
            var lazyStringCalled = false
            traceCoroutine({
                lazyStringCalled = true
                "hello"
            }) {
                assertTrue(
                    "Lazy string should have been called when Compile.IS_DEBUG=true, " +
                        "even when Trace.isEnabled()=false",
                    lazyStringCalled
                )
                val traceData = traceThreadLocal.get()
                assertNotNull(traceData)
                assertEquals(traceData?.slices?.size, 1)
            }
        }
    }

    @Test
    fun tracingDisabledForNonDebugBuild() = runTest {
        Compile.setIsDebug(false)
        assertNull(traceThreadLocal.get())
        withContext(createCoroutineTracingContext()) {
            assertNull(traceThreadLocal.get())
            traceCoroutine("hello") { // should not crash
                assertNull(traceThreadLocal.get())
            }
        }
        withContext(TraceContextElement()) {
            assertNull(traceThreadLocal.get())

            // Change Trace.isEnabled() to false so that the lazy-String is not called for async
            // tracing, which would be expected even when coroutine tracing is disabled.
            setAndroidSystemTracingEnabled(false)

            // Verify that the lazy-String is not called when tracing is disabled and
            // Compile.IS_DEBUG=false.
            traceCoroutine({
                fail("Lazy string should not be called when Compile.IS_DEBUG=false")
                "error"
            }) {
                // This should edge-case should never happen because TraceContextElement is internal
                // and can only be created through createCoroutineTracingContext(), which checks for
                // Compile.IS_DEBUG=true. However, we want to be certain that even if a
                // TraceContextElement is somehow used, it is unused when IS_DEBUG=false.
                assertNull(traceThreadLocal.get())
            }
        }
    }

    private fun expect(vararg expectedOpenTraceSections: String) {
        expect(null, *expectedOpenTraceSections)
    }

    /**
     * Checks the currently active trace sections on the current thread, and optionally checks the
     * order of operations if [expectedEvent] is not null.
     */
    private fun expect(expectedEvent: Int? = null, vararg expectedOpenTraceSections: String) {
        if (expectedEvent != null) {
            val previousEvent = eventCounter.getAndAdd(1)
            val currentEvent = previousEvent + 1
            check(expectedEvent == currentEvent) {
                if (previousEvent == FINAL_EVENT) {
                    "Expected event=$expectedEvent, but finish() was already called"
                } else {
                    "Expected event=$expectedEvent," +
                        " but the event counter is currently at $currentEvent"
                }
            }
        }

        // Inspect trace output to the fake used for recording android.os.Trace API calls:
        assertArrayEquals(expectedOpenTraceSections, getOpenTraceSectionsOnCurrentThread())
    }

    /** Same as [expect], except that no more [expect] statements can be called after it. */
    private fun finish(expectedEvent: Int, vararg expectedOpenTraceSections: String) {
        val previousEvent = eventCounter.getAndSet(FINAL_EVENT)
        val currentEvent = previousEvent + 1
        check(expectedEvent == currentEvent) {
            if (previousEvent == FINAL_EVENT) {
                "finish() was called more than once"
            } else {
                "Finished with event=$expectedEvent," +
                    " but the event counter is currently $currentEvent"
            }
        }

        // Inspect trace output to the fake used for recording android.os.Trace API calls:
        assertArrayEquals(expectedOpenTraceSections, getOpenTraceSectionsOnCurrentThread())
    }

    private val eventCounter = AtomicInteger(0)

    companion object {
        const val FINAL_EVENT = Int.MIN_VALUE
    }
}

/**
 * Helper util for calling [runTest] with a [TraceContextElement]. This is useful for formatting
 * purposes. Passing an arg to `runTest {}` directly, as in `fun testStuff() =
 * runTestWithTraceContext {}` would require more indentations according to our style guide.
 */
private fun runTestWithTraceContext(testBody: suspend TestScope.() -> Unit) =
    runTest(context = TraceContextElement(), testBody = testBody)
