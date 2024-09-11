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

import android.os.HandlerThread
import android.platform.test.annotations.EnableFlags
import com.android.systemui.Flags.FLAG_COROUTINE_TRACING
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Ignore
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@EnableFlags(FLAG_COROUTINE_TRACING)
class CoroutineTracingTest : TestBase() {
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
    fun launchInCoroutineScope() = runTestWithTraceContext {
        launch("span-for-launch-0") {
            expect("span-for-launch-0")
            delay(1)
            expect("span-for-launch-0")
        }
        coroutineScope("span-for-coroutineScope-1") {
            launch("span-for-launch-1") {
                expect("span-for-coroutineScope-1", "span-for-launch-1")
                delay(1)
                expect("span-for-coroutineScope-1", "span-for-launch-1")
            }
            launch("span-for-launch-2") {
                expect("span-for-coroutineScope-1", "span-for-launch-2")
                delay(1)
                expect("span-for-coroutineScope-1", "span-for-launch-2")
            }
            coroutineScope("span-for-coroutineScope-2") {
                launch("span-for-launch-3") {
                    expect(
                        "span-for-coroutineScope-1",
                        "span-for-coroutineScope-2",
                        "span-for-launch-3",
                    )
                    delay(1)
                    expect(
                        "span-for-coroutineScope-1",
                        "span-for-coroutineScope-2",
                        "span-for-launch-3",
                    )
                }
                launch("span-for-launch-4") {
                    expect(
                        "span-for-coroutineScope-1",
                        "span-for-coroutineScope-2",
                        "span-for-launch-4",
                    )
                    delay(1)
                    expect(
                        "span-for-coroutineScope-1",
                        "span-for-coroutineScope-2",
                        "span-for-launch-4",
                    )
                }
            }
        }
        launch("span-for-launch-5") {
            expect("span-for-launch-5")
            delay(1)
            expect("span-for-launch-5")
        }
    }

    @Test
    fun nestedUpdateAndRestoreOnSingleThread_unconfinedDispatcher() = runTestWithTraceContext {
        traceCoroutine("parent-span") {
            expect(1, "parent-span")
            launch(Dispatchers.Unconfined) {
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
        val thread1 = newSingleThreadContext("thread-#1")
        expect()
        traceCoroutine("hello") {
            expect(1, "hello")
            launch(thread1) {
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
        val thread1 = newSingleThreadContext("thread-#1")
        val thread2 = newSingleThreadContext("thread-#2")
        val thread3 = newSingleThreadContext("thread-#3")
        val thread4 = newSingleThreadContext("thread-#4")
        val channel = Channel<Int>()
        val fetchData: suspend () -> String = {
            traceCoroutine("span-for-fetchData") {
                channel.receive()
                expect("span-for-launch", "span-for-fetchData")
            }
            "stuff"
        }

        val threadContexts = listOf(thread1, thread2, thread3, thread4)

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
        thread2Context: CoroutineContext,
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
            expect("span-for-launch-1", "span-for-launch-2")
            delay(1L)
            traceCoroutine("span-for-fetchData-2") {
                expect("span-for-launch-1", "span-for-launch-2", "span-for-fetchData-2")
            }
            expect("span-for-launch-1", "span-for-launch-2")
            "stuff-2"
        }

        val context1 = newSingleThreadContext("thread-#1") + thread1Context
        val context2 = newSingleThreadContext("thread-#2") + thread2Context

        launch("span-for-launch-1", context1) {
            assertEquals("stuff-1", fetchData1())
            expect("span-for-launch-1")
            launch("span-for-launch-2", context2) {
                assertEquals("stuff-2", fetchData2())
                expect("span-for-launch-1", "span-for-launch-2")
            }
            expect("span-for-launch-1")
        }
        expect()

        // Launching without the trace extension won't result in traces
        launch(context1) { expect() }
        launch(context2) { expect() }
    }

    @Test
    fun nestedTraceSectionsMultiThreaded1() = runTestWithTraceContext {
        // Thread-#1 and Thread-#2 inherit TraceContextElement from the test's CoroutineContext.
        testTraceSectionsMultiThreaded(
            thread1Context = EmptyCoroutineContext,
            thread2Context = EmptyCoroutineContext,
        )
    }

    @Test
    fun nestedTraceSectionsMultiThreaded2() = runTest {
        // Thread-#2 inherits the TraceContextElement from Thread-#1. The test's CoroutineContext
        // does not need a TraceContextElement because it does not do any tracing.
        testTraceSectionsMultiThreaded(
            thread1Context = TraceContextElement(),
            thread2Context = EmptyCoroutineContext,
        )
    }

    @Test
    fun nestedTraceSectionsMultiThreaded3() = runTest {
        // Thread-#2 overrides the TraceContextElement from Thread-#1, but the merging context
        // should be fine; it is essentially a no-op. The test's CoroutineContext does not need the
        // trace context because it does not do any tracing.
        testTraceSectionsMultiThreaded(
            thread1Context = TraceContextElement(),
            thread2Context = TraceContextElement(),
        )
    }

    @Test
    fun nestedTraceSectionsMultiThreaded4() = runTestWithTraceContext {
        // TraceContextElement is merged on each context switch, which should have no effect on the
        // trace results.
        testTraceSectionsMultiThreaded(
            thread1Context = TraceContextElement(),
            thread2Context = TraceContextElement(),
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
            expect("span-for-launch-2")
            channel.receive()
            traceCoroutine("span-for-fetchData-2") {
                expect("span-for-launch-2", "span-for-fetchData-2")
            }
            expect("span-for-launch-2")
            "stuff-2"
        }

        val context1 = newSingleThreadContext("thread-#1")
        val context2 = newSingleThreadContext("thread-#2") + TraceContextElement()

        launch("span-for-launch-1", context1) {
            assertEquals("stuff-1", fetchData1())
            expect()
            launch("span-for-launch-2", context2) {
                assertEquals("stuff-2", fetchData2())
                expect("span-for-launch-2")
            }
            expect()
        }
        expect()

        channel.send(1)
        channel.send(2)

        // Launching without the trace extension won't result in traces
        launch(context1) { expect() }
        launch(context2) { expect() }
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
        val thread1 = newSingleThreadContext("thread-#1")
        val channel = Channel<Int>()
        val job =
            launch("#1", thread1) {
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
                    withContext(thread1) {
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

    @Ignore("Fails with java.net.SocketTimeoutException: Read timed out")
    @Test
    fun testHandlerDispatcher() = runTest {
        val handlerThread = HandlerThread("test-handler-thread")
        handlerThread.start()
        val dispatcher = handlerThread.threadHandler.asCoroutineDispatcher()
        val previousThread = Thread.currentThread().id
        launch(dispatcher) {
            val currentThreadBeforeDelay = Thread.currentThread().id
            delay(1)
            assertEquals(currentThreadBeforeDelay, Thread.currentThread().id)
            assertNotEquals(previousThread, currentThreadBeforeDelay)
            delay(1)
            assertEquals(currentThreadBeforeDelay, Thread.currentThread().id)
        }
    }
}
