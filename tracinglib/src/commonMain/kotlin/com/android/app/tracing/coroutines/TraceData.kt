/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.app.tracing.beginSlice
import com.android.app.tracing.endSlice
import java.util.ArrayDeque

/**
 * Represents a section of code executing in a coroutine. This may be split up into multiple slices
 * on different threads as the coroutine is suspended and resumed.
 *
 * @see traceCoroutine
 */
typealias TraceSection = String

/**
 * Used for storing trace sections so that they can be added and removed from the currently running
 * thread when the coroutine is suspended and resumed.
 *
 * @see traceCoroutine
 */
@PublishedApi
internal class TraceData(private val slices: ArrayDeque<TraceSection> = ArrayDeque()) : Cloneable {
    /** Adds current trace slices back to the current thread. Called when coroutine is resumed. */
    internal fun beginAllOnThread() {
        slices.descendingIterator().forEach { beginSlice(it) }
    }

    /**
     * Removes all current trace slices from the current thread. Called when coroutine is suspended.
     */
    internal fun endAllOnThread() {
        repeat(slices.size) { endSlice() }
    }

    /**
     * Creates a new trace section with a unique ID and adds it to the current trace data. The slice
     * will also be added to the current thread immediately. This slice will not propagate to parent
     * coroutines, or to child coroutines that have already started. The unique ID is used to verify
     * that the [endSpan] is corresponds to a [beginSpan].
     */
    @PublishedApi
    internal fun beginSpan(name: String) {
        slices.push(name)
        beginSlice(name)
    }

    /**
     * Used by [TraceContextElement] when launching a child coroutine so that the child coroutine's
     * state is isolated from the parent.
     */
    public override fun clone(): TraceData {
        return TraceData(slices.clone())
    }

    /**
     * Ends the trace section and validates it corresponds with an earlier call to [beginSpan]. The
     * trace slice will immediately be removed from the current thread. This information will not
     * propagate to parent coroutines, or to child coroutines that have already started.
     */
    @PublishedApi
    internal fun endSpan() {
        slices.pop()
        endSlice()
    }
}
