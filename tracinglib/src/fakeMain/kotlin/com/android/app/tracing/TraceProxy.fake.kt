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

package com.android.app.tracing

import org.junit.Assert.assertFalse

@PublishedApi
internal actual fun isEnabled(): Boolean {
    return true
}

val traceCounters = mutableMapOf<String, Int>()

internal actual fun traceCounter(counterName: String, counterValue: Int) {
    traceCounters[counterName] = counterValue
}

private val allThreadStates = HashMap<Long, MutableList<String>>()

private class FakeThreadStateLocal : ThreadLocal<MutableList<String>>() {
    override fun initialValue(): MutableList<String> {
        val openTraceSections = mutableListOf<String>()
        val threadId = Thread.currentThread().id
        synchronized(allThreadStates) { allThreadStates.put(threadId, openTraceSections) }
        return openTraceSections
    }
}

private val threadLocalTraceState = FakeThreadStateLocal()

object FakeTraceState {

    fun begin(sectionName: String) {
        threadLocalTraceState.get().add(sectionName)
    }

    fun end() {
        threadLocalTraceState.get().let {
            assertFalse(
                "Attempting to close trace section on thread=${Thread.currentThread().id}, " +
                    "but there are no open sections",
                it.isNullOrEmpty()
            )
            // TODO: Replace with .removeLast() once available
            it.removeAt(it.lastIndex)
        }
    }

    fun getOpenTraceSectionsOnCurrentThread(): Array<String> {
        return threadLocalTraceState.get().toTypedArray()
    }

    /**
     * Helper function for debugging; use as follows:
     * ```
     * println(FakeThreadStateLocal)
     * ```
     */
    override fun toString(): String {
        val sb = StringBuilder()
        synchronized(allThreadStates) {
            allThreadStates.entries.forEach { sb.appendLine("${it.key} -> ${it.value}") }
        }
        return sb.toString()
    }
}

internal actual fun traceBegin(methodName: String) {
    FakeTraceState.begin(methodName)
}

internal actual fun traceEnd() {
    FakeTraceState.end()
}

internal actual fun asyncTraceBegin(methodName: String, cookie: Int) {}

internal actual fun asyncTraceEnd(methodName: String, cookie: Int) {}

@PublishedApi
internal actual fun asyncTraceForTrackBegin(trackName: String, methodName: String, cookie: Int) {}

@PublishedApi
internal actual fun asyncTraceForTrackEnd(trackName: String, methodName: String, cookie: Int) {}

internal actual fun instant(eventName: String) {}

internal actual fun instantForTrack(trackName: String, eventName: String) {}
