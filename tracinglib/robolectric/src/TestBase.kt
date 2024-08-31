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

import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.app.tracing.coroutines.util.FakeTraceState
import com.android.app.tracing.coroutines.util.FakeTraceState.getOpenTraceSectionsOnCurrentThread
import com.android.app.tracing.coroutines.util.ShadowTrace
import com.android.systemui.util.Compile
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowTrace::class])
open class TestBase {

    @get:Rule val setFlagsRule = SetFlagsRule()

    @Before
    fun setup() {
        assumeTrue(
            "Coroutine tracing tests are only applicable on debuggable builds",
            Compile.IS_DEBUG
        )
        TraceData.strictModeForTesting = true
        FakeTraceState.isTracingEnabled = true
        eventCounter.set(0)
    }

    @After
    fun tearDown() {
        val lastEvent = eventCounter.get()
        assertTrue(
            "Expected `finish(${lastEvent + 1})` to be called, but the test finished",
            lastEvent == FINAL_EVENT || lastEvent == 0,
        )
    }

    protected fun runTestWithTraceContext(block: suspend CoroutineScope.() -> Unit) {
        runTest(TraceContextElement(), block)
    }

    protected fun runTest(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        runBlocking(context, block)
    }

    internal fun expect(vararg expectedOpenTraceSections: String) {
        expect(null, *expectedOpenTraceSections)
    }

    protected fun expectEndsWith(vararg expectedOpenTraceSections: String) {
        // Inspect trace output to the fake used for recording android.os.Trace API calls:
        val actualSections = getOpenTraceSectionsOnCurrentThread()
        assertTrue(expectedOpenTraceSections.size <= actualSections.size)
        val lastSections = actualSections.takeLast(expectedOpenTraceSections.size).toTypedArray()
        assertTraceSectionsEquals(expectedOpenTraceSections, lastSections)
    }

    /**
     * Checks the currently active trace sections on the current thread, and optionally checks the
     * order of operations if [expectedEvent] is not null.
     */
    protected fun expect(expectedEvent: Int? = null, vararg expectedOpenTraceSections: String) {
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
        assertTraceSectionsEquals(expectedOpenTraceSections, getOpenTraceSectionsOnCurrentThread())
    }

    private fun assertTraceSectionsEquals(
        expectedOpenTraceSections: Array<out String>,
        actualOpenSections: Array<String>
    ) {
        assertArrayEquals(
            """
            Expected:{${expectedOpenTraceSections.prettyPrintList()}}
            Actual:{${actualOpenSections.prettyPrintList()}}
        """
                .trimIndent(),
            expectedOpenTraceSections,
            actualOpenSections
        )
    }

    /** Same as [expect], except that no more [expect] statements can be called after it. */
    protected fun finish(expectedEvent: Int, vararg expectedOpenTraceSections: String) {
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
        assertTraceSectionsEquals(expectedOpenTraceSections, getOpenTraceSectionsOnCurrentThread())
    }

    private val eventCounter = AtomicInteger(0)

    private companion object {
        const val FINAL_EVENT = Int.MIN_VALUE
    }
}

private fun <T> Array<T>.prettyPrintList(): String {
    return toList().joinToString(separator = "\", \"", prefix = "\"", postfix = "\"")
}
