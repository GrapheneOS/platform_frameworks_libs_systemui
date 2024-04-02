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

import com.android.app.tracing.asyncTraceForTrackBegin
import com.android.app.tracing.asyncTraceForTrackEnd
import com.android.app.tracing.isEnabled
import java.util.concurrent.ThreadLocalRandom
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@PublishedApi internal const val TAG = "CoroutineTracing"

@PublishedApi internal const val DEFAULT_TRACK_NAME = "Coroutines"

/**
 * Convenience function for calling [CoroutineScope.launch] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
inline fun CoroutineScope.launch(
    crossinline spanName: () -> String,
    context: CoroutineContext = EmptyCoroutineContext,
    // TODO(b/306457056): DO NOT pass CoroutineStart; doing so will regress .odex size
    crossinline block: suspend CoroutineScope.() -> Unit
): Job = launch(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [CoroutineScope.launch] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
inline fun CoroutineScope.launch(
    spanName: String,
    context: CoroutineContext = EmptyCoroutineContext,
    // TODO(b/306457056): DO NOT pass CoroutineStart; doing so will regress .odex size
    crossinline block: suspend CoroutineScope.() -> Unit
): Job = launch(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [CoroutineScope.async] with [traceCoroutine] enable tracing
 *
 * @see traceCoroutine
 */
inline fun <T> CoroutineScope.async(
    crossinline spanName: () -> String,
    context: CoroutineContext = EmptyCoroutineContext,
    // TODO(b/306457056): DO NOT pass CoroutineStart; doing so will regress .odex size
    crossinline block: suspend CoroutineScope.() -> T
): Deferred<T> = async(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [CoroutineScope.async] with [traceCoroutine] enable tracing.
 *
 * @see traceCoroutine
 */
inline fun <T> CoroutineScope.async(
    spanName: String,
    context: CoroutineContext = EmptyCoroutineContext,
    // TODO(b/306457056): DO NOT pass CoroutineStart; doing so will regress .odex size
    crossinline block: suspend CoroutineScope.() -> T
): Deferred<T> = async(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [runBlocking] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
inline fun <T> runBlocking(
    crossinline spanName: () -> String,
    context: CoroutineContext,
    crossinline block: suspend () -> T
): T = runBlocking(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [runBlocking] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
inline fun <T> runBlocking(
    spanName: String,
    context: CoroutineContext,
    crossinline block: suspend CoroutineScope.() -> T
): T = runBlocking(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [withContext] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
suspend inline fun <T> withContext(
    spanName: String,
    context: CoroutineContext,
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(context) { traceCoroutine(spanName) { block() } }

/**
 * Convenience function for calling [withContext] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
suspend inline fun <T> withContext(
    crossinline spanName: () -> String,
    context: CoroutineContext,
    crossinline block: suspend CoroutineScope.() -> T
): T = withContext(context) { traceCoroutine(spanName) { block() } }

/**
 * Traces a section of work of a `suspend` [block]. The trace sections will appear on the thread
 * that is currently executing the [block] of work. If the [block] is suspended, all trace sections
 * added using this API will end until the [block] is resumed, which could happen either on this
 * thread or on another thread. If a child coroutine is started, it will inherit the trace sections
 * of its parent. The child will continue to print these trace sections whether or not the parent
 * coroutine is still running them.
 *
 * The current [CoroutineContext] must have a [TraceContextElement] for this API to work. Otherwise,
 * the trace sections will be dropped.
 *
 * For example, in the following trace, Thread #1 ran some work, suspended, then continued working
 * on Thread #2. Meanwhile, Thread #2 created a new child coroutine which inherited its trace
 * sections. Then, the original coroutine resumed on Thread #1 before ending. Meanwhile Thread #3 is
 * still printing trace sections from its parent because they were copied when it was created. There
 * is no way for the parent to communicate to the child that it marked these slices as completed.
 * While this might seem counterintuitive, it allows us to pinpoint the origin of the child
 * coroutine's work.
 *
 * ```
 * Thread #1 | [==== Slice A ====]                        [==== Slice A ====]
 *           |       [==== B ====]                        [=== B ===]
 * --------------------------------------------------------------------------------------
 * Thread #2 |                    [====== Slice A ======]
 *           |                    [========= B =========]
 *           |                        [===== C ======]
 * --------------------------------------------------------------------------------------
 * Thread #3 |                            [== Slice A ==]                [== Slice A ==]
 *           |                            [===== B =====]                [===== B =====]
 *           |                            [===== C =====]                [===== C =====]
 *           |                                                               [=== D ===]
 * ```
 *
 * @param name The name of the code section to appear in the trace
 * @see endSlice
 * @see traceCoroutine
 */
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> traceCoroutine(spanName: () -> String, block: () -> T): T {
    contract {
        callsInPlace(spanName, InvocationKind.AT_MOST_ONCE)
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    // For coroutine tracing to work, trace spans must be added and removed even when
    // tracing is not active (i.e. when TRACE_TAG_APP is disabled). Otherwise, when the
    // coroutine resumes when tracing is active, we won't know its name.
    val tracer = currentCoroutineContext()[TraceContextElement]?.traceData

    val asyncTracingEnabled = isEnabled()
    val spanString = if (tracer != null || asyncTracingEnabled) spanName() else "<none>"

    tracer?.beginSpan(spanString)

    // Also trace to the "Coroutines" async track. This makes it easy to see the duration of
    // coroutine spans. When the coroutine_tracing flag is enabled, those same names will
    // appear in small slices on each thread as the coroutines are suspended and resumed.
    val cookie = if (asyncTracingEnabled) ThreadLocalRandom.current().nextInt() else 0
    if (asyncTracingEnabled) asyncTraceForTrackBegin(DEFAULT_TRACK_NAME, spanString, cookie)
    try {
        return block()
    } finally {
        if (asyncTracingEnabled) asyncTraceForTrackEnd(DEFAULT_TRACK_NAME, spanString, cookie)
        tracer?.endSpan()
    }
}

/** @see traceCoroutine */
suspend inline fun <T> traceCoroutine(spanName: String, block: () -> T): T =
    traceCoroutine({ spanName }, block)
