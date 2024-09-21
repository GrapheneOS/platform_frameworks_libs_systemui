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

import android.annotation.SuppressLint
import android.os.Trace
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.systemui.Flags
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextKey
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.getPolymorphicElement
import kotlin.coroutines.minusPolymorphicKey
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
fun createCoroutineTracingContext(name: String = "UnnamedScope"): CoroutineContext =
    if (Flags.coroutineTracing()) TraceContextElement(name) else EmptyCoroutineContext

fun nameCoroutine(name: String): CoroutineContext =
    if (Flags.coroutineTracing()) CoroutineTraceName(name) else EmptyCoroutineContext

inline fun nameCoroutine(name: () -> String): CoroutineContext =
    if (Flags.coroutineTracing()) CoroutineTraceName(name()) else EmptyCoroutineContext

open class BaseTraceElement : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<BaseTraceElement>

    override val key: CoroutineContext.Key<*>
        get() = Key

    // It is important to use getPolymorphicKey and minusPolymorphicKey
    @OptIn(ExperimentalStdlibApi::class)
    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? =
        getPolymorphicElement(key)

    @OptIn(ExperimentalStdlibApi::class)
    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext = minusPolymorphicKey(key)

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(
        message =
            "Operator `+` on two BaseTraceElement objects is meaningless. " +
                "If used, the context element to the right of `+` would simply replace the " +
                "element to the left. To properly use `BaseTraceElement`, `CoroutineTraceName` " +
                "should be used when creating a top-level `CoroutineScope`, " +
                "and `TraceContextElement` should be passed to the child context " +
                "that is under construction.",
        level = DeprecationLevel.ERROR,
    )
    operator fun plus(other: BaseTraceElement): BaseTraceElement = other
}

class CoroutineTraceName(val name: String) : BaseTraceElement() {
    @OptIn(ExperimentalStdlibApi::class)
    companion object Key :
        AbstractCoroutineContextKey<BaseTraceElement, CoroutineTraceName>(
            BaseTraceElement,
            { it as? CoroutineTraceName },
        )
}

const val ROOT_SCOPE = 0

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
class TraceContextElement
private constructor(
    coroutineTraceName: String,
    inheritedTracePrefix: String,
    @get:VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    val contextTraceData: TraceData?,
    private val coroutineDepth: Int, // depth relative to first TraceContextElement
    parentId: Int,
) : CopyableThreadContextElement<TraceData?>, BaseTraceElement() {

    @OptIn(ExperimentalStdlibApi::class)
    companion object Key :
        AbstractCoroutineContextKey<BaseTraceElement, TraceContextElement>(
            BaseTraceElement,
            { it as? TraceContextElement },
        )

    /**
     * Minor perf optimization: no need to create TraceData() for root scopes since all launches
     * require creation of child via [copyForChild] or [mergeForChild].
     */
    constructor(scopeName: String) : this(scopeName, "", null, 0, ROOT_SCOPE)

    private var childCoroutineCount = AtomicInteger(0)
    private val currentId = hashCode()

    private val fullCoroutineTraceName = "$inheritedTracePrefix$coroutineTraceName"
    private val continuationTraceMessage =
        "$fullCoroutineTraceName;$coroutineTraceName;d=$coroutineDepth;c=$currentId;p=$parentId"

    init {
        debug { "#init" }
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
    @SuppressLint("UnclosedTrace")
    override fun updateThreadContext(context: CoroutineContext): TraceData? {
        val oldState = traceThreadLocal.get()
        debug { "#updateThreadContext oldState=$oldState" }
        if (oldState !== contextTraceData) {
            Trace.traceBegin(Trace.TRACE_TAG_APP, continuationTraceMessage)
            traceThreadLocal.set(contextTraceData)
            // Calls to `updateThreadContext` will not happen in parallel on the same context, and
            // they cannot happen before the prior suspension point. Additionally,
            // `restoreThreadContext` does not modify `traceData`, so it is safe to iterate over the
            // collection here:
            contextTraceData?.beginAllOnThread()
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
        debug { "#restoreThreadContext restoring=$oldState" }
        // We not use the `TraceData` object here because it may have been modified on another
        // thread after the last suspension point. This is why we use a [TraceStateHolder]:
        // so we can end the correct number of trace sections, restoring the thread to its state
        // prior to the last call to [updateThreadContext].
        if (oldState !== traceThreadLocal.get()) {
            contextTraceData?.endAllOnThread()
            traceThreadLocal.set(oldState)
            Trace.traceEnd(Trace.TRACE_TAG_APP) // end: currentScopeTraceMessage
        }
    }

    override fun copyForChild(): CopyableThreadContextElement<TraceData?> {
        debug { "#copyForChild" }
        return createChildContext()
    }

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
        debug { "#mergeForChild" }
        val otherTraceContext = overwritingElement[TraceContextElement]
        if (DEBUG && otherTraceContext != null) {
            Log.e(
                TAG,
                UNEXPECTED_TRACE_DATA_ERROR_MESSAGE +
                    "Current CoroutineContext.Element=$fullCoroutineTraceName, other CoroutineContext.Element=${otherTraceContext.fullCoroutineTraceName}",
            )
        }
        return createChildContext(overwritingElement[CoroutineTraceName]?.name ?: "")
    }

    private fun createChildContext(coroutineTraceName: String = ""): TraceContextElement {
        val childCount = childCoroutineCount.incrementAndGet()
        return TraceContextElement(
            coroutineTraceName,
            "$fullCoroutineTraceName:$childCount^",
            TraceData(),
            coroutineDepth + 1,
            currentId,
        )
    }

    private inline fun debug(message: () -> String) {
        if (DEBUG) Log.d(TAG, "@$currentId ${message()} $contextTraceData")
    }
}

private const val UNEXPECTED_TRACE_DATA_ERROR_MESSAGE =
    "Overwriting context element with non-empty trace data. There should only be one " +
        "TraceContextElement per coroutine, and it should be installed in the root scope. "
private const val TAG = "TraceContextElement"
internal const val DEBUG = false
