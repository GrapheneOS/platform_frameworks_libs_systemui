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

package com.android.app.tracing.coroutines.util

import org.junit.Assert.assertFalse

object FakeTraceState {

    var isTracingEnabled: Boolean = true

    private val allThreadStates = hashMapOf<Long, MutableList<String>>()

    fun begin(sectionName: String) {
        val threadId = currentThreadId()
        synchronized(allThreadStates) {
            if (allThreadStates.containsKey(threadId)) {
                allThreadStates[threadId]!!.add(sectionName)
            } else {
                allThreadStates[threadId] = mutableListOf(sectionName)
            }
        }
    }

    fun end() {
        val threadId = currentThreadId()
        synchronized(allThreadStates) {
            assertFalse(
                "Attempting to close trace section on thread=$threadId, " +
                    "but there are no open sections",
                allThreadStates[threadId].isNullOrEmpty(),
            )
            // TODO: Replace with .removeLast() once available
            allThreadStates[threadId]!!.removeAt(allThreadStates[threadId]!!.lastIndex)
        }
    }

    fun getOpenTraceSectionsOnCurrentThread(): Array<String> {
        val threadId = currentThreadId()
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
