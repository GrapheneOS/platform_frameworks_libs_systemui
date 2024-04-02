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

import com.android.app.tracing.TraceState.openSectionsOnCurrentThread
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

/**
 * Helper util for asserting that the open trace sections on the current thread equal the passed
 * list of strings.
 */
private fun assertTraceEquals(vararg openTraceSections: String) {
    assertArrayEquals(openTraceSections, openSectionsOnCurrentThread())
}

/** Helper util for asserting that there are no open trace sections on the current thread. */
private fun assertTraceIsEmpty() = assertEquals(0, openSectionsOnCurrentThread().size)

/**
 * Helper util for calling [runTest] with a [TraceContextElement]. This is useful for formatting
 * purposes. Passing an arg to `runTest {}` directly, as in `fun testStuff() =
 * runTest(TraceContextElement()) {}` would require more indentations according to our style guide.
 */
private fun runTestWithTraceContext(testBody: suspend TestScope.() -> Unit) =
    runTest(context = TraceContextElement(), testBody = testBody)

@RunWith(BlockJUnit4ClassRunner::class)
class CoroutineTracingTest {

    @Test
    fun testTraceStorage() = runTest {
        val fetchData: suspend () -> String = {
            delay(ThreadLocalRandom.current().nextLong(0, 10))
            traceCoroutine("span-for-fetchData") {
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
        kotlinx.coroutines.withContext(TraceContextElement()) {
            assertNotNull(CURRENT_TRACE.get())
            assertTrue(
                CURRENT_TRACE.get() === currentCoroutineContext()[TraceContextElement]?.traceData
            )
            val job = launch {
                repeat(1000) {
                    launch("span-for-launch", threadContexts[it % threadContexts.size]) {
                        assertTrue(
                            CURRENT_TRACE.get() ===
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
            assertTrue(
                CURRENT_TRACE.get() === currentCoroutineContext()[TraceContextElement]?.traceData
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
        val thread2 = newSingleThreadContext("thread-#2") + TraceContextElement()

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
}
