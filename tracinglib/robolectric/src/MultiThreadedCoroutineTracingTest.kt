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
import kotlin.coroutines.EmptyCoroutineContext
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
class MultiThreadedCoroutineTracingTest : TracingTestBase() {
    @Test
    fun nestedUpdateAndRestoreOnSingleThread_unconfinedDispatcher() = runTestTraced {
        traceCoroutine("parent-span") {
            expect(1, "main:1^", "parent-span")
            launch(Dispatchers.Unconfined) {
                // This may appear unusual, but it is expected behavior:
                //   1) The parent has an open trace section called "parent-span".
                //   2) The child launches, derives a new scope name from its parent, and resumes
                //      immediately due to its use of the unconfined dispatcher.
                //   3) The child emits all the trace sections known to its scope. The parent
                //      does not have an opportunity to restore its context yet.
                //   4) After the suspension point, the parent restores its context, and the
                //      child
                //
                // [parent's active trace sections]
                //               /           \      [new trace section for child scope]
                //              /             \                \
                expect(2, "main:1^", "parent-span", "main:1^:1^")
                traceCoroutine("child-span") {
                    expect(3, "main:1^", "parent-span", "main:1^:1^", "child-span")
                    delay(1) // <-- delay will give parent a chance to restore its context
                    // After a delay, the parent resumes, finishing its trace section, so we are
                    // left with only those in the child's scope
                    finish(5, "main:1^:1^", "child-span")
                }
            }
        }
        expect(4, "main:1^") // <-- because of the delay above, this is not the last event
    }

    /** @see nestedUpdateAndRestoreOnSingleThread_unconfinedDispatcher */
    @Test
    fun nestedUpdateAndRestoreOnSingleThread_undispatchedLaunch() = runTestTraced {
        traceCoroutine("parent-span") {
            launch(start = CoroutineStart.UNDISPATCHED) {
                traceCoroutine("child-span") {
                    expect(1, "main:1^", "parent-span", "main:1^:1^", "child-span")
                    delay(1) // <-- delay will give parent a chance to restore its context
                    finish(3, "main:1^:1^", "child-span")
                }
            }
        }
        expect(2, "main:1^")
    }

    @Test
    fun launchOnSeparateThread_defaultDispatcher() = runTestTraced {
        val channel = Channel<Int>()
        val thread1 = newSingleThreadContext("thread-#1")
        expect("main:1^")
        traceCoroutine("hello") {
            expect(1, "main:1^", "hello")
            launch(thread1) {
                expect(2, "main:1^:1^")
                traceCoroutine("world") {
                    expect("main:1^:1^", "world")
                    channel.send(1)
                    expect(3, "main:1^:1^", "world")
                }
            }
            expect("main:1^", "hello")
        }
        expect("main:1^")
        assertEquals(1, channel.receive())
        finish(4, "main:1^")
    }

    @Test
    fun testTraceStorage() = runTestTraced {
        val thread1 = newSingleThreadContext("thread-#1")
        val thread2 = newSingleThreadContext("thread-#2")
        val thread3 = newSingleThreadContext("thread-#3")
        val thread4 = newSingleThreadContext("thread-#4")
        val channel = Channel<Int>()
        val threadContexts = listOf(thread1, thread2, thread3, thread4)
        val finishedLaunches = Channel<Int>()
        // Start 1000 coroutines waiting on [channel]
        val job = launch {
            repeat(1000) {
                launch("span-for-launch", threadContexts[it % threadContexts.size]) {
                    assertNotNull(traceThreadLocal.get())
                    traceCoroutine("span-for-fetchData") {
                        channel.receive()
                        expectEndsWith("span-for-fetchData")
                    }
                    assertNotNull(traceThreadLocal.get())
                    finishedLaunches.send(it)
                }
                expect("main:1^:1^")
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

    @Test
    fun nestedTraceSectionsMultiThreaded() = runTestTraced {
        val context1 = newSingleThreadContext("thread-#1") + nameCoroutine("coroutineA")
        val context2 = newSingleThreadContext("thread-#2") + nameCoroutine("coroutineB")
        val context3 = context1 + nameCoroutine("coroutineC")

        launch("launch#1", context1) {
            expect("main:1^:1^coroutineA")
            delay(1L)
            traceCoroutine("span-1") { expect("main:1^:1^coroutineA", "span-1") }
            expect("main:1^:1^coroutineA")
            expect("main:1^:1^coroutineA")
            launch("launch#2", context2) {
                expect("main:1^:1^coroutineA:1^coroutineB")
                delay(1L)
                traceCoroutine("span-2") { expect("main:1^:1^coroutineA:1^coroutineB", "span-2") }
                expect("main:1^:1^coroutineA:1^coroutineB")
                expect("main:1^:1^coroutineA:1^coroutineB")
                launch("launch#3", context3) {
                    // "launch#3" is dropped because context has a TraceContextElement.
                    // The CoroutineScope (i.e. `this` in `this.launch {}`) should have a
                    // TraceContextElement, but using TraceContextElement in the passed context is
                    // incorrect.
                    expect("main:1^:1^coroutineA:1^coroutineB:1^coroutineC")
                    launch("launch#4", context1) {
                        expect("main:1^:1^coroutineA:1^coroutineB:1^coroutineC:1^coroutineA")
                    }
                }
            }
            expect("main:1^:1^coroutineA")
        }
        expect("main:1^")

        // Launching without the trace extension won't result in traces
        launch(context1) { expect("main:1^:2^coroutineA") }
        launch(context2) { expect("main:1^:3^coroutineB") }
    }

    @Test
    fun missingTraceContextObjects() = runTest {
        val channel = Channel<Int>()
        val context1 = newSingleThreadContext("thread-#1")
        val context2 = newSingleThreadContext("thread-#2") + mainTraceContext

        launch("launch#1", context1) {
            expect()
            channel.receive()
            traceCoroutine("span-1") { expect() }
            expect()
            launch("launch#2", context2) {
                // "launch#2" is not traced because TraceContextElement was installed too
                // late; it is not part of the scope that was launched (i.e., the `this` in
                // `this.launch {}`)
                expect("main:1^")
                channel.receive()
                traceCoroutine("span-2") { expect("main:1^", "span-2") }
                expect("main:1^")
                launch {
                    // ...it won't appear in the child scope either because in launch("string"), it
                    // adds: `CoroutineTraceName` + `TraceContextElement`. This demonstrates why
                    // it is important to only use `TraceContextElement` in the root scope. In this
                    // case, the `TraceContextElement`  overwrites the name, so the name is dropped.
                    // Tracing still works with a default, empty name, however.
                    expect("main:1^:1^")
                }
            }
            expect()
        }
        expect()

        channel.send(1)
        channel.send(2)

        launch(context1) { expect() }
        launch(context2) { expect("main:2^") }
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

        val thread1ResumptionPoint = CyclicBarrier(2)
        val thread1SuspensionPoint = CyclicBarrier(2)

        val thread1 = Executors.newSingleThreadExecutor()
        val thread2 = Executors.newSingleThreadExecutor()
        val slicesForThread1 = listOf("a", "c", "e", "g")
        val slicesForThread2 = listOf("b", "d", "f", "h")
        var failureOnThread1: Error? = null
        var failureOnThread2: Error? = null

        val expectedTraceForThread1 = arrayOf("1:a", "2:b", "1:c", "2:d", "1:e", "2:f", "1:g")

        val traceContext = mainTraceContext as TraceContextElement
        thread1.execute {
            try {
                slicesForThread1.forEachIndexed { index, sliceName ->
                    assertNull(traceThreadLocal.get())
                    val oldTrace = traceContext.updateThreadContext(EmptyCoroutineContext)
                    // await() AFTER updateThreadContext, thus thread #1 always resumes the
                    // coroutine before thread #2
                    assertSame(traceThreadLocal.get(), traceContext.contextTraceData)

                    // coroutine body start {
                    (traceThreadLocal.get() as TraceData).beginSpan("1:$sliceName")

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

                    val oldTrace = traceContext.updateThreadContext(EmptyCoroutineContext)

                    // coroutine body start {
                    (traceThreadLocal.get() as TraceData).beginSpan("2:$n")

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
    fun scopeReentry_withContextFastPath() = runTestTraced {
        val thread1 = newSingleThreadContext("thread-#1")
        val channel = Channel<Int>()
        val job =
            launch("#1", thread1) {
                expect("main:1^:1^#1")
                var i = 0
                while (true) {
                    expect("main:1^:1^#1")
                    channel.send(i++)
                    expect("main:1^:1^#1")
                    // when withContext is passed the same scope, it takes a fast path, dispatching
                    // immediately. This means that in subsequent loops, if we do not handle reentry
                    // correctly in TraceContextElement, the trace may become deeply nested:
                    // "#1", "#1", "#1", ... "#2"
                    withContext(thread1) {
                        expect("main:1^:1^#1")
                        traceCoroutine("#2") {
                            expect("main:1^:1^#1", "#2")
                            channel.send(i++)
                            expect("main:1^:1^#1", "#2")
                        }
                        expect("main:1^:1^#1")
                    }
                }
            }
        repeat(1000) {
            expect("main:1^")
            traceCoroutine("receive") {
                expect("main:1^", "receive")
                val receivedVal = channel.receive()
                assertEquals(it, receivedVal)
                expect("main:1^", "receive")
            }
            expect("main:1^")
        }
        job.cancel()
    }

    @Test
    fun traceContextIsCopied() = runTest {
        expect()
        val traceContext = mainTraceContext as TraceContextElement
        withContext(traceContext) {
            // Not the same object because it should be copied into the current context
            assertNotSame(traceThreadLocal.get(), traceContext.contextTraceData)
            // slices is lazily created, so it should be null:
            assertNull((traceThreadLocal.get() as TraceData).slices)
            assertNull(traceContext.contextTraceData?.slices)
            expect("main:1^")
            traceCoroutine("hello") {
                assertNotSame(traceThreadLocal.get(), traceContext.contextTraceData)
                assertArrayEquals(
                    arrayOf("hello"),
                    (traceThreadLocal.get() as TraceData).slices?.toArray(),
                )
                assertNull(traceContext.contextTraceData?.slices)
            }
            assertNotSame(traceThreadLocal.get(), traceContext.contextTraceData)
            // Because slices is lazily created, it will no longer be null after it was used to
            // trace "hello", but this time it will be empty
            assertArrayEquals(arrayOf(), (traceThreadLocal.get() as TraceData).slices?.toArray())
            assertNull(traceContext.contextTraceData?.slices)
            expect("main:1^")
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
