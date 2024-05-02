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
import com.android.systemui.Flags
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

/**
 * Helper util for asserting that the open trace sections on the current thread equal the passed
 * list of strings.
 */
private fun assertTraceEquals(vararg expectedOpenTraceSections: String) {
    // Inspect trace output (e.g. fake for recording android.os.Trace API calls):
    assertArrayEquals(expectedOpenTraceSections, getOpenTraceSectionsOnCurrentThread())

    // Inspect thread-local coroutine machinery:
    val threadLocalTraceState = CURRENT_TRACE.get()
    if (expectedOpenTraceSections.isEmpty()) {
        assertTrue(threadLocalTraceState == null || threadLocalTraceState.slices.isEmpty())
        assertEquals(0, TraceData.openSliceCount.get())
    } else {
        assertNotNull(threadLocalTraceState)
        assertEquals(expectedOpenTraceSections.size, TraceData.openSliceCount.get())
        // CURRENT_TRACE is a stack, so the order is reversed.
        // It is okay to reverse() in place since vararg is passed by value.
        expectedOpenTraceSections.reverse()
        assertArrayEquals(expectedOpenTraceSections, threadLocalTraceState!!.slices.toArray())
    }
}

/** Helper util for asserting that there are no open trace sections on the current thread. */
private fun assertTraceIsEmpty() {
    assertTraceEquals()
}

/**
 * Helper util for calling [runTest] with a [TraceContextElement]. This is useful for formatting
 * purposes. Passing an arg to `runTest {}` directly, as in `fun testStuff() =
 * runTest(TraceContextElement()) {}` would require more indentations according to our style guide.
 */
private fun runTestWithTraceContext(testBody: suspend TestScope.() -> Unit) =
    runTest(context = createCoroutineTracingContext(), testBody = testBody)

@RunWith(BlockJUnit4ClassRunner::class)
class CoroutineTracingTest {

    @Before
    fun setup() {
        TraceData.strictModeForTesting = true
    }

    @Test
    fun testTraceStorage() = runTest {
        val fetchData: suspend () -> String = {
            delay(ThreadLocalRandom.current().nextLong(0, 4))
            traceCoroutine("span-for-fetchData") {
                assertSame(
                    CURRENT_TRACE.get(),
                    currentCoroutineContext()[TraceContextElement]?.traceData
                )
                yield()
                assertSame(
                    CURRENT_TRACE.get(),
                    currentCoroutineContext()[TraceContextElement]?.traceData
                )
                assertTraceEquals("span-for-launch", "span-for-fetchData")
            }
            "stuff"
        }
        assertNull(CURRENT_TRACE.get())
        val threadContexts =
            listOf(
                newSingleThreadContext("thread-#1"),
                newSingleThreadContext("thread-#2"),
                newSingleThreadContext("thread-#3"),
                newSingleThreadContext("thread-#4"),
            )
        withContext(createCoroutineTracingContext()) {
            assertNotNull(CURRENT_TRACE.get())
            assertSame(
                CURRENT_TRACE.get(),
                currentCoroutineContext()[TraceContextElement]?.traceData
            )
            val job = launch {
                repeat(1000) {
                    launch("span-for-launch", threadContexts[it % threadContexts.size]) {
                        assertSame(
                            CURRENT_TRACE.get(),
                            currentCoroutineContext()[TraceContextElement]?.traceData
                        )
                        assertNotNull(CURRENT_TRACE.get())
                        assertEquals("stuff", fetchData())
                        assertTraceEquals("span-for-launch")
                        assertNotNull(CURRENT_TRACE.get())
                    }
                }
            }
            // half the time of the max delay in fetchData(), therefore cancelling some of the
            // outstanding jobs
            delay(5L)
            job.cancel()
            assertNotNull(CURRENT_TRACE.get())
            assertSame(
                CURRENT_TRACE.get(),
                currentCoroutineContext()[TraceContextElement]?.traceData
            )
        }
        // Should be null again after the coroutine finished
        assertNull(CURRENT_TRACE.get())
    }

    @Test
    fun nestedTraceSectionsOnSingleThread() = runTestWithTraceContext {
        val fetchData: suspend () -> String = {
            delay(1L)
            traceCoroutine("span-for-fetchData") {
                assertTraceEquals("span-for-launch", "span-for-fetchData")
            }
            "stuff"
        }
        launch("span-for-launch") {
            assertEquals("stuff", fetchData())
            assertTraceEquals("span-for-launch")
        }
        assertTraceIsEmpty()
    }

    private fun CoroutineScope.testTraceSectionsMultiThreaded(
        thread1Context: CoroutineContext,
        thread2Context: CoroutineContext
    ) {
        val fetchData1: suspend () -> String = {
            assertTraceEquals("span-for-launch-1")
            delay(1L)
            traceCoroutine("span-for-fetchData-1") {
                assertTraceEquals("span-for-launch-1", "span-for-fetchData-1")
            }
            assertTraceEquals("span-for-launch-1")
            "stuff-1"
        }

        val fetchData2: suspend () -> String = {
            assertTraceEquals(
                "span-for-launch-1",
                "span-for-launch-2",
            )
            delay(1L)
            traceCoroutine("span-for-fetchData-2") {
                assertTraceEquals("span-for-launch-1", "span-for-launch-2", "span-for-fetchData-2")
            }
            assertTraceEquals(
                "span-for-launch-1",
                "span-for-launch-2",
            )
            "stuff-2"
        }

        val thread1 = newSingleThreadContext("thread-#1") + thread1Context
        val thread2 = newSingleThreadContext("thread-#2") + thread2Context

        launch("span-for-launch-1", thread1) {
            assertEquals("stuff-1", fetchData1())
            assertTraceEquals("span-for-launch-1")
            launch("span-for-launch-2", thread2) {
                assertEquals("stuff-2", fetchData2())
                assertTraceEquals("span-for-launch-1", "span-for-launch-2")
            }
            assertTraceEquals("span-for-launch-1")
        }
        assertTraceIsEmpty()

        // Launching without the trace extension won't result in traces
        launch(thread1) { assertTraceIsEmpty() }
        launch(thread2) { assertTraceIsEmpty() }
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
            thread1Context = createCoroutineTracingContext(),
            thread2Context = EmptyCoroutineContext
        )
    }

    @Test
    fun nestedTraceSectionsMultiThreaded3() = runTest {
        // Thread-#2 overrides the TraceContextElement from Thread-#1, but the merging context
        // should be fine; it is essentially a no-op. The test's CoroutineContext does not need the
        // trace context because it does not do any tracing.
        testTraceSectionsMultiThreaded(
            thread1Context = createCoroutineTracingContext(),
            thread2Context = createCoroutineTracingContext()
        )
    }

    @Test
    fun nestedTraceSectionsMultiThreaded4() = runTestWithTraceContext {
        // TraceContextElement is merged on each context switch, which should have no effect on the
        // trace results.
        testTraceSectionsMultiThreaded(
            thread1Context = createCoroutineTracingContext(),
            thread2Context = createCoroutineTracingContext()
        )
    }

    @Test
    fun missingTraceContextObjects() = runTest {
        // Thread-#1 is missing a TraceContextElement, so some of the trace sections get dropped.
        // The resulting trace sections will be different than the 4 tests above.
        val fetchData1: suspend () -> String = {
            assertTraceIsEmpty()
            delay(1L)
            traceCoroutine("span-for-fetchData-1") { assertTraceIsEmpty() }
            assertTraceIsEmpty()
            "stuff-1"
        }

        val fetchData2: suspend () -> String = {
            assertTraceEquals(
                "span-for-launch-2",
            )
            delay(1L)
            traceCoroutine("span-for-fetchData-2") {
                assertTraceEquals("span-for-launch-2", "span-for-fetchData-2")
            }
            assertTraceEquals(
                "span-for-launch-2",
            )
            "stuff-2"
        }

        val thread1 = newSingleThreadContext("thread-#1")
        val thread2 = newSingleThreadContext("thread-#2") + createCoroutineTracingContext()

        launch("span-for-launch-1", thread1) {
            assertEquals("stuff-1", fetchData1())
            assertTraceIsEmpty()
            launch("span-for-launch-2", thread2) {
                assertEquals("stuff-2", fetchData2())
                assertTraceEquals("span-for-launch-2")
            }
            assertTraceIsEmpty()
        }
        assertTraceIsEmpty()

        // Launching without the trace extension won't result in traces
        launch(thread1) { assertTraceIsEmpty() }
        launch(thread2) { assertTraceIsEmpty() }
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
        assertNull(CURRENT_TRACE.get())
        val traceContext = TraceContextElement()
        assertNull(CURRENT_TRACE.get())

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
                    assertNull(CURRENT_TRACE.get())
                    val oldTrace = traceContext.updateThreadContext(EmptyCoroutineContext)
                    // await() AFTER updateThreadContext, thus thread #1 always resumes the
                    // coroutine before thread #2
                    assertSame(CURRENT_TRACE.get(), traceContext.traceData)

                    // coroutine body start {
                    CURRENT_TRACE.get()?.beginSpan("1:$sliceName")

                    // At the end, verify the interleaved trace sections look correct:
                    if (index == slicesForThread1.size - 1) {
                        assertTraceEquals(*expectedTraceForThread1)
                    }

                    // simulate a slow thread, wait to call restoreThreadContext until after thread
                    // A
                    // has resumed
                    thread1SuspensionPoint.await(3, TimeUnit.SECONDS)
                    Thread.sleep(500)
                    // } coroutine body end

                    traceContext.restoreThreadContext(EmptyCoroutineContext, oldTrace)
                    thread1ResumptionPoint.await(3, TimeUnit.SECONDS)
                    assertNull(CURRENT_TRACE.get())
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
                    assertNull(CURRENT_TRACE.get())
                    thread1SuspensionPoint.await(3, TimeUnit.SECONDS)

                    val oldTrace: TraceData? =
                        traceContext.updateThreadContext(EmptyCoroutineContext)

                    // coroutine body start {
                    CURRENT_TRACE.get()?.beginSpan("2:$n")

                    // At the end, verify the interleaved trace sections look correct:
                    if (i == slicesForThread2.size - 1) {
                        assertTraceEquals(*expectedTraceForThread2)
                    }
                    // } coroutine body end

                    traceContext.restoreThreadContext(EmptyCoroutineContext, oldTrace)
                    thread1ResumptionPoint.await(3, TimeUnit.SECONDS)
                    assertNull(CURRENT_TRACE.get())
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
    fun simpleTrace() = runTest {
        assertTraceIsEmpty()
        val traceContext = TraceContextElement()
        assertTraceIsEmpty()
        withContext(traceContext) {
            // Not the same object because it should be copied into the current context
            assertNotSame(CURRENT_TRACE.get(), traceContext.traceData)
            assertNotSame(CURRENT_TRACE.get()?.slices, traceContext.traceData.slices)
            assertTraceIsEmpty()
            traceCoroutine("hello") {
                assertNotSame(CURRENT_TRACE.get(), traceContext.traceData)
                assertNotSame(CURRENT_TRACE.get()?.slices, traceContext.traceData.slices)
                assertArrayEquals(arrayOf("hello"), CURRENT_TRACE.get()?.slices?.toArray())
            }
            assertNotSame(CURRENT_TRACE.get(), traceContext.traceData)
            assertNotSame(CURRENT_TRACE.get()?.slices, traceContext.traceData.slices)
            assertTraceIsEmpty()
        }
        assertTraceIsEmpty()
        runBlocking(traceContext) {
            // Again, not the same object because it was copied
            assertNotSame(CURRENT_TRACE.get(), traceContext.traceData)
            assertNotSame(CURRENT_TRACE.get()?.slices, traceContext.traceData.slices)
            assertTraceIsEmpty()
        }
        assertTraceIsEmpty()
    }

    @Test
    fun tracingDisabled() = runTest {
        Flags.disableCoroutineTracing()
        assertNull(CURRENT_TRACE.get())
        withContext(createCoroutineTracingContext()) {
            assertNull(CURRENT_TRACE.get())
            traceCoroutine("hello") { // should not crash
                assertNull(CURRENT_TRACE.get())
            }
        }
    }
}
