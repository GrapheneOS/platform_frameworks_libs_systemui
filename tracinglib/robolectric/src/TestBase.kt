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
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.app.tracing.coroutines.util.FakeTraceState
import com.android.app.tracing.coroutines.util.FakeTraceState.getOpenTraceSectionsOnCurrentThread
import com.android.app.tracing.coroutines.util.ShadowTrace
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowTrace::class])
open class TestBase {

    companion object {
        @JvmField
        @ClassRule
        val setFlagsClassRule: SetFlagsRule.ClassRule = SetFlagsRule.ClassRule()
    }

    @JvmField @Rule val setFlagsRule = SetFlagsRule()

    private var skipAfterCheck = false

    @Before
    fun setup() {
        STRICT_MODE_FOR_TESTING = true
        FakeTraceState.isTracingEnabled = true
        eventCounter.set(0)
        skipAfterCheck = false
    }

    @After
    fun tearDown() {
        if (skipAfterCheck) return
        val lastEvent = eventCounter.get()
        check(lastEvent == FINAL_EVENT || lastEvent == 0) {
            "Expected `finish(${lastEvent + 1})` to be called, but the test finished"
        }
    }

    protected fun runTest(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        runBlocking(context, block)
    }

    /**
     * Same as [expect], but also call [delay] for 1ms, calling [expect] before and after the
     * suspension point.
     */
    protected suspend fun expectD(vararg expectedOpenTraceSections: String) {
        expectD(null, *expectedOpenTraceSections)
    }

    /**
     * Same as [expect], but also call [delay] for 1ms, calling [expect] before and after the
     * suspension point.
     */
    protected suspend fun expectD(
        expectedEvent: Int? = null,
        vararg expectedOpenTraceSections: String,
    ) {
        expect(expectedEvent, *expectedOpenTraceSections)
        delay(1)
        expect(*expectedOpenTraceSections)
    }

    internal fun expect(vararg expectedOpenTraceSections: String) {
        expect(null, *expectedOpenTraceSections)
    }

    protected fun expectEndsWith(vararg expectedOpenTraceSections: String) {
        try {
            // Inspect trace output to the fake used for recording android.os.Trace API calls:
            val actualSections = getOpenTraceSectionsOnCurrentThread()
            check(expectedOpenTraceSections.size <= actualSections.size)
            val lastSections =
                actualSections.takeLast(expectedOpenTraceSections.size).toTypedArray()
            assertTraceSectionsEquals(expectedOpenTraceSections, lastSections)
        } catch (e: IllegalStateException) {
            skipAfterCheck = true
        }
    }

    /**
     * Checks the currently active trace sections on the current thread, and optionally checks the
     * order of operations if [expectedEvent] is not null.
     */
    protected fun expect(expectedEvent: Int? = null, vararg expectedOpenTraceSections: String) {
        try {
            expectInternal(expectedEvent, *expectedOpenTraceSections)
        } catch (e: IllegalStateException) {
            skipAfterCheck = true
            throw e
        }
    }

    private fun expectInternal(
        expectedEvent: Int? = null,
        vararg expectedOpenTraceSections: String,
    ) {
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
        actualOpenSections: Array<String>,
    ) {
        val expectedSize = expectedOpenTraceSections.size
        val actualSize = actualOpenSections.size
        check(expectedSize == actualSize) {
            createFailureMessage(
                expectedOpenTraceSections,
                actualOpenSections,
                "Size mismatch, expected size $expectedSize but was size $actualSize",
            )
        }
        expectedOpenTraceSections.forEachIndexed { n, expectedTrace ->
            val actualTrace = actualOpenSections[n]
            val expected = expectedTrace.substringBefore(";")
            val actual = actualTrace.substringBefore(";")
            check(expected == actual) {
                createFailureMessage(
                    expectedOpenTraceSections,
                    actualOpenSections,
                    "Differed at index #$n, expected \"$expected\" but was \"$actual\"",
                )
            }
        }
    }

    private fun createFailureMessage(
        expectedOpenTraceSections: Array<out String>,
        actualOpenSections: Array<String>,
        extraMessage: String,
    ): String =
        """
                Incorrect trace sections found on current thread:
                  Expected : {${expectedOpenTraceSections.prettyPrintList()}}
                  Actual   : {${actualOpenSections.prettyPrintList()}}
                  $extraMessage
                """
            .trimIndent()

    /** Same as [expect], except that no more [expect] statements can be called after it. */
    protected fun finish(expectedEvent: Int, vararg expectedOpenTraceSections: String) {
        try {
            finishInternal(expectedEvent, *expectedOpenTraceSections)
        } catch (e: IllegalStateException) {
            skipAfterCheck = true
            throw e
        }
    }

    private fun finishInternal(expectedEvent: Int, vararg expectedOpenTraceSections: String) {
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
}

private const val FINAL_EVENT = Int.MIN_VALUE

private fun Array<out String>.prettyPrintList(): String {
    return toList().joinToString(separator = "\", \"", prefix = "\"", postfix = "\"") {
        it.substringBefore(";")
    }
}

private fun check(value: Boolean, lazyMessage: () -> String) {
    if (DEBUG_TEST) {
        if (!value) {
            Log.e("TestBase", lazyMessage(), Throwable())
        }
    } else {
        kotlin.check(value, lazyMessage)
    }
}

private const val DEBUG_TEST = false
