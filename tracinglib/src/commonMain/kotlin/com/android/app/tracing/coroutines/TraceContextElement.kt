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
import com.android.systemui.Flags.coroutineTracing
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName

/**
 * Thread-local storage for giving each thread a unique [TraceData]. It can only be used when paired
 * with a [TraceContextElement].
 *
 * [CURRENT_TRACE] will be `null` if either 1) we aren't in a coroutine, or 2) the current coroutine
 * context does not have [TraceContextElement]. In both cases, writing to this thread-local would be
 * undefined behavior if it were not null, which is why we use null as the default value rather than
 * an empty TraceData.
 *
 * @see traceCoroutine
 */
internal val CURRENT_TRACE = ThreadLocal<TraceData?>()

/**
 * If `true`, the CoroutineDispatcher and CoroutineName will be included in the trace each time the
 * coroutine context changes. This makes the trace extremely noisy, so it is off by default.
 */
private const val DEBUG_COROUTINE_CONTEXT_UPDATES = false

/**
 * Returns a new [CoroutineContext] used for tracing. Used to hide internal implementation details.
 */
fun createCoroutineTracingContext(): CoroutineContext {
    return if (coroutineTracing()) TraceContextElement() else EmptyCoroutineContext
}

private fun CoroutineContext.nameForTrace(): String {
    val dispatcherStr = "${this[CoroutineDispatcher]}"
    val nameStr = "${this[CoroutineName]?.name}"
    return "CoroutineDispatcher: $dispatcherStr; CoroutineName: $nameStr"
}

/**
 * Used for safely persisting [TraceData] state when coroutines are suspended and resumed.
 *
 * This is internal machinery for [traceCoroutine]. It cannot be made `internal` or `private`
 * because [traceCoroutine] is a Public-API inline function.
 *
 * @see traceCoroutine
 */
@PublishedApi
internal class TraceContextElement(@PublishedApi internal val traceData: TraceData = TraceData()) :
    CopyableThreadContextElement<TraceData?> {

    @PublishedApi internal companion object Key : CoroutineContext.Key<TraceContextElement>

    override val key: CoroutineContext.Key<*>
        get() = Key

    override fun updateThreadContext(context: CoroutineContext): TraceData? {
        val oldState = CURRENT_TRACE.get()
        // oldState should never be null because we always initialize the thread-local with a
        // non-null instance,
        oldState?.endAllOnThread()
        CURRENT_TRACE.set(traceData)
        if (DEBUG_COROUTINE_CONTEXT_UPDATES) beginSlice(context.nameForTrace())
        traceData.beginAllOnThread()
        return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: TraceData?) {
        if (DEBUG_COROUTINE_CONTEXT_UPDATES) endSlice()
        traceData.endAllOnThread()
        CURRENT_TRACE.set(oldState)
        oldState?.beginAllOnThread()
    }

    override fun copyForChild(): CopyableThreadContextElement<TraceData?> {
        return TraceContextElement(traceData.clone())
    }

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
        return TraceContextElement(traceData.clone())
    }
}
