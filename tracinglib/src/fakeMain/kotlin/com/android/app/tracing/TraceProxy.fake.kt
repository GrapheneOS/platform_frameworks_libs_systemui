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

object TraceState {
    private val traceSections = mutableMapOf<Long, MutableList<String>>()

    fun begin(sectionName: String) {
        synchronized(this) {
            traceSections.getOrPut(Thread.currentThread().id) { mutableListOf() }.add(sectionName)
        }
    }

    fun end() {
        synchronized(this) {
            val openSectionsOnThread = traceSections[Thread.currentThread().id]
            assertFalse(
                "Attempting to close trace section on thread=${Thread.currentThread().id}, " +
                    "but there are no open sections",
                openSectionsOnThread.isNullOrEmpty()
            )
            // TODO: Replace with .removeLast() once available
            openSectionsOnThread!!.removeAt(openSectionsOnThread!!.lastIndex)
        }
    }

    fun openSectionsOnCurrentThread(): Array<String> {
        return synchronized(this) {
            traceSections.getOrPut(Thread.currentThread().id) { mutableListOf() }.toTypedArray()
        }
    }

    override fun toString(): String {
        return traceSections.toString()
    }
}

internal actual fun traceBegin(methodName: String) {
    TraceState.begin(methodName)
}

internal actual fun traceEnd() {
    TraceState.end()
}

internal actual fun asyncTraceBegin(methodName: String, cookie: Int) {}

internal actual fun asyncTraceEnd(methodName: String, cookie: Int) {}

@PublishedApi
internal actual fun asyncTraceForTrackBegin(trackName: String, methodName: String, cookie: Int) {}

@PublishedApi
internal actual fun asyncTraceForTrackEnd(trackName: String, methodName: String, cookie: Int) {}

internal actual fun instant(eventName: String) {}

internal actual fun instantForTrack(trackName: String, eventName: String) {}
