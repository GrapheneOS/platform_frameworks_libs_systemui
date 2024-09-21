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

import com.android.systemui.Flags
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

const val DEFAULT_TRACK_NAME = "Coroutines"

@OptIn(ExperimentalContracts::class)
suspend inline fun <R> coroutineScope(
    traceName: String,
    crossinline block: suspend CoroutineScope.() -> R,
): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return traceCoroutine(traceName) {
        return@traceCoroutine coroutineScope wrappedCoroutineScope@{
            return@wrappedCoroutineScope block()
        }
    }
}

/**
 * Convenience function for calling [CoroutineScope.launch] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
inline fun CoroutineScope.launch(
    crossinline spanName: () -> String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    noinline block: suspend CoroutineScope.() -> Unit,
): Job = launch(nameCoroutine(spanName) + context, start, block)

/**
 * Convenience function for calling [CoroutineScope.launch] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
fun CoroutineScope.launch(
    spanName: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(nameCoroutine(spanName) + context, start, block)

/**
 * Convenience function for calling [CoroutineScope.async] with [traceCoroutine] enable tracing
 *
 * @see traceCoroutine
 */
inline fun <T> CoroutineScope.async(
    spanName: () -> String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    noinline block: suspend CoroutineScope.() -> T,
): Deferred<T> = async(nameCoroutine(spanName) + context, start, block)

/**
 * Convenience function for calling [CoroutineScope.async] with [traceCoroutine] enable tracing.
 *
 * @see traceCoroutine
 */
fun <T> CoroutineScope.async(
    spanName: String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T,
): Deferred<T> = async(nameCoroutine(spanName) + context, start, block)

/**
 * Convenience function for calling [runBlocking] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
inline fun <T> runBlocking(
    spanName: () -> String,
    context: CoroutineContext,
    noinline block: suspend CoroutineScope.() -> T,
): T = runBlocking(nameCoroutine(spanName) + context, block)

/**
 * Convenience function for calling [runBlocking] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
fun <T> runBlocking(
    spanName: String,
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T = runBlocking(nameCoroutine(spanName) + context, block)

/**
 * Convenience function for calling [withContext] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
suspend fun <T> withContext(
    spanName: String,
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T = withContext(nameCoroutine(spanName) + context, block)

/**
 * Convenience function for calling [withContext] with [traceCoroutine] to enable tracing.
 *
 * @see traceCoroutine
 */
suspend inline fun <T> withContext(
    spanName: () -> String,
    context: CoroutineContext,
    noinline block: suspend CoroutineScope.() -> T,
): T = withContext(nameCoroutine(spanName) + context, block)

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
inline fun <T> traceCoroutine(spanName: () -> String, block: () -> T): T {
    contract {
        callsInPlace(spanName, InvocationKind.AT_MOST_ONCE)
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    // For coroutine tracing to work, trace spans must be added and removed even when
    // tracing is not active (i.e. when TRACE_TAG_APP is disabled). Otherwise, when the
    // coroutine resumes when tracing is active, we won't know its name.
    val traceData = if (Flags.coroutineTracing()) traceThreadLocal.get() else null
    traceData?.beginSpan(spanName())
    try {
        return block()
    } finally {
        traceData?.endSpan()
    }
}

/** @see traceCoroutine */
inline fun <T> traceCoroutine(spanName: String, block: () -> T): T =
    traceCoroutine({ spanName }, block)
