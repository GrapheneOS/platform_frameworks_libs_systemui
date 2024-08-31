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

import androidx.annotation.VisibleForTesting
import com.android.systemui.Flags
import com.android.systemui.util.Compile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi

private const val DEBUG = false

/** Log a message with a tag indicating the current thread ID */
private inline fun debug(message: () -> String) {
    if (DEBUG) println("Thread #${Thread.currentThread().id}: ${message()}")
}

/** Use a final subclass to avoid virtual calls (b/316642146). */
class TraceDataThreadLocal : ThreadLocal<TraceData?>()

/**
 * Thread-local storage for giving each thread a unique [TraceData]. It can only be used when paired
 * with a [TraceContextElement].
 *
 * [traceThreadLocal] will be `null` if either 1) we aren't in a coroutine, or 2) the current
 * coroutine context does not have [TraceContextElement]. In both cases, writing to this
 * thread-local would be undefined behavior if it were not null, which is why we use null as the
 * default value rather than an empty TraceData.
 *
 * @see traceCoroutine
 */
val traceThreadLocal = TraceDataThreadLocal()

/**
 * Returns a new [CoroutineContext] used for tracing. Used to hide internal implementation details.
 */
fun createCoroutineTracingContext(): CoroutineContext =
    if (Compile.IS_DEBUG && Flags.coroutineTracing()) {
        TraceContextElement()
    } else {
        EmptyCoroutineContext
    }

/**
 * Used for safely persisting [TraceData] state when coroutines are suspended and resumed.
 *
 * This is internal machinery for [traceCoroutine]. It cannot be made `internal` or `private`
 * because [traceCoroutine] is a Public-API inline function.
 *
 * @see traceCoroutine
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class TraceContextElement private constructor(val traceData: TraceData?) :
    CopyableThreadContextElement<TraceData?> {

    companion object Key : CoroutineContext.Key<TraceContextElement>

    constructor() : this(if (Compile.IS_DEBUG) TraceData() else null)

    override val key: CoroutineContext.Key<*>
        get() = Key

    init {
        debug { "$this #init" }
    }

    /**
     * This function is invoked before the coroutine is resumed on the current thread. When a
     * multi-threaded dispatcher is used, calls to `updateThreadContext` may happen in parallel to
     * the prior `restoreThreadContext` in the same context. However, calls to `updateThreadContext`
     * will not run in parallel on the same context.
     *
     * ```
     * Thread #1 | [updateThreadContext]....^              [restoreThreadContext]
     * --------------------------------------------------------------------------------------------
     * Thread #2 |                           [updateThreadContext]...........^[restoreThreadContext]
     * ```
     *
     * (`...` indicate coroutine body is running; whitespace indicates the thread is not scheduled;
     * `^` is a suspension point)
     */
    override fun updateThreadContext(context: CoroutineContext): TraceData? {
        if (!Compile.IS_DEBUG) return null
        val oldState = traceThreadLocal.get()
        debug { "$this #updateThreadContext oldState=$oldState" }
        if (oldState !== traceData) {
            traceThreadLocal.set(traceData)
            // Calls to `updateThreadContext` will not happen in parallel on the same context, and
            // they cannot happen before the prior suspension point. Additionally,
            // `restoreThreadContext` does not modify `traceData`, so it is safe to iterate over the
            // collection here:
            traceData?.beginAllOnThread()
        }
        return oldState
    }

    /**
     * This function is invoked after the coroutine has suspended on the current thread. When a
     * multi-threaded dispatcher is used, calls to `restoreThreadContext` may happen in parallel to
     * the subsequent `updateThreadContext` and `restoreThreadContext` operations. The coroutine
     * body itself will not run in parallel, but `TraceData` could be modified by a coroutine body
     * after the suspension point in parallel to `restoreThreadContext` associated with the
     * coroutine body _prior_ to the suspension point.
     *
     * ```
     * Thread #1 | [updateThreadContext].x..^              [restoreThreadContext]
     * --------------------------------------------------------------------------------------------
     * Thread #2 |                           [updateThreadContext]..x..x.....^[restoreThreadContext]
     * ```
     *
     * OR
     *
     * ```
     * Thread #1 |                                 [restoreThreadContext]
     * --------------------------------------------------------------------------------------------
     * Thread #2 |     [updateThreadContext]...x....x..^[restoreThreadContext]
     * ```
     *
     * (`...` indicate coroutine body is running; whitespace indicates the thread is not scheduled;
     * `^` is a suspension point; `x` are calls to modify the thread-local trace data)
     *
     * ```
     */
    override fun restoreThreadContext(context: CoroutineContext, oldState: TraceData?) {
        if (!Compile.IS_DEBUG) return
        debug { "$this#restoreThreadContext restoring=$oldState" }
        // We not use the `TraceData` object here because it may have been modified on another
        // thread after the last suspension point. This is why we use a [TraceStateHolder]:
        // so we can end the correct number of trace sections, restoring the thread to its state
        // prior to the last call to [updateThreadContext].
        if (oldState !== traceThreadLocal.get()) {
            traceData?.endAllOnThread()
            traceThreadLocal.set(oldState)
        }
    }

    override fun copyForChild(): CopyableThreadContextElement<TraceData?> {
        if (!Compile.IS_DEBUG) return TraceContextElement(null)
        debug { "$this #copyForChild" }
        return TraceContextElement(traceData?.clone())
    }

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
        if (!Compile.IS_DEBUG) return EmptyCoroutineContext
        debug { "$this #mergeForChild" }
        // For our use-case, we always give precedence to the parent trace context, and the
        // child context (overwritingElement) is ignored
        return TraceContextElement(traceData?.clone())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return "TraceContextElement@${hashCode().toHexString()}[$traceData]"
    }
}
