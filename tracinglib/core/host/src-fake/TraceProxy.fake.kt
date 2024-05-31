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

const val DEBUG = false

/** Log a message with a tag indicating the current thread ID */
private fun debug(message: String) {
    if (DEBUG) println("Thread #${Thread.currentThread().id}: $message")
}

@PublishedApi
internal actual fun isEnabled(): Boolean {
    return true
}

val traceCounters = mutableMapOf<String, Int>()

internal actual fun traceCounter(counterName: String, counterValue: Int) {
    traceCounters[counterName] = counterValue
}

object FakeTraceState {

    private val allThreadStates = hashMapOf<Long, MutableList<String>>()

    fun begin(sectionName: String) {
        val threadId = Thread.currentThread().id
        synchronized(allThreadStates) {
            if (allThreadStates.containsKey(threadId)) {
                allThreadStates[threadId]!!.add(sectionName)
            } else {
                allThreadStates[threadId] = mutableListOf(sectionName)
            }
        }
    }

    fun end() {
        val threadId = Thread.currentThread().id
        synchronized(allThreadStates) {
            assertFalse(
                "Attempting to close trace section on thread=$threadId, " +
                    "but there are no open sections",
                allThreadStates[threadId].isNullOrEmpty()
            )
            // TODO: Replace with .removeLast() once available
            allThreadStates[threadId]!!.removeAt(allThreadStates[threadId]!!.lastIndex)
        }
    }

    fun getOpenTraceSectionsOnCurrentThread(): Array<String> {
        val threadId = Thread.currentThread().id
        synchronized(allThreadStates) {
            return allThreadStates[threadId]?.toTypedArray() ?: emptyArray()
        }
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
    debug("traceBegin: name=$methodName")
    FakeTraceState.begin(methodName)
}

internal actual fun traceEnd() {
    debug("traceEnd")
    FakeTraceState.end()
}

internal actual fun asyncTraceBegin(methodName: String, cookie: Int) {
    debug("asyncTraceBegin: name=$methodName cookie=${cookie.toHexString()}")
}

internal actual fun asyncTraceEnd(methodName: String, cookie: Int) {
    debug("asyncTraceEnd: name=$methodName cookie=${cookie.toHexString()}")
}

@PublishedApi
internal actual fun asyncTraceForTrackBegin(trackName: String, methodName: String, cookie: Int) {
    debug(
        "asyncTraceForTrackBegin: track=$trackName name=$methodName cookie=${cookie.toHexString()}"
    )
}

@PublishedApi
internal actual fun asyncTraceForTrackEnd(trackName: String, methodName: String, cookie: Int) {
    debug("asyncTraceForTrackEnd: track=$trackName name=$methodName cookie=${cookie.toHexString()}")
}

internal actual fun instant(eventName: String) {
    debug("instant: name=$eventName")
}

internal actual fun instantForTrack(trackName: String, eventName: String) {
    debug("instantForTrack: track=$trackName name=$eventName")
}
