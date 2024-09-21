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

package com.android.app.tracing.coroutines.flow

import android.os.Trace
import com.android.app.tracing.coroutines.CoroutineTraceName
import com.android.app.tracing.coroutines.traceCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.ExperimentalTypeInference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collectLatest as kx_collectLatest
import kotlinx.coroutines.flow.filter as kx_filter
import kotlinx.coroutines.flow.filterIsInstance as kx_filterIsInstance
import kotlinx.coroutines.flow.flowOn as kx_flowOn
import kotlinx.coroutines.flow.map as kx_map

fun <T> Flow<T>.withTraceName(name: String?): Flow<T> {
    return object : Flow<T> {
        override suspend fun collect(collector: FlowCollector<T>) {
            this@withTraceName.collect(name ?: walkStackForClassName(), collector)
        }
    }
}

/**
 * NOTE: We cannot use a default value for the String name because [Flow.collect] is a member
 * function. When an extension function has the same receiver type, name, and applicable arguments
 * as a class member function, the member takes precedence.
 */
@OptIn(ExperimentalTypeInference::class)
suspend inline fun <T> Flow<T>.collect(
    name: String, /* cannot have a default parameter or else Flow#collect() override this call */
    @BuilderInference block: FlowCollector<T>,
) {
    val (collectSlice, emitSlice) = getFlowSliceNames(name)
    traceCoroutine(collectSlice) {
        collect { value -> traceCoroutine(emitSlice) { block.emit(value) } }
    }
}

@OptIn(ExperimentalTypeInference::class)
suspend inline fun <T> Flow<T>.collectTraced(@BuilderInference block: FlowCollector<T>) {
    collect(walkStackForClassName(), block)
}

suspend fun <T> Flow<T>.collectLatest(name: String? = null, action: suspend (T) -> Unit) {
    val (collectSlice, emitSlice) = getFlowSliceNames(name)
    traceCoroutine(collectSlice) {
        kx_collectLatest { value -> traceCoroutine(emitSlice) { action(value) } }
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun <T> Flow<T>.flowOn(context: CoroutineContext): Flow<T> {
    val contextName =
        context[CoroutineTraceName]?.name
            ?: context[CoroutineName]?.name
            ?: context[CoroutineDispatcher]?.javaClass?.simpleName
            ?: context.javaClass.simpleName
    return kx_flowOn(context).withTraceName("flowOn($contextName)")
}

inline fun <T> Flow<T>.filter(
    name: String? = null,
    crossinline predicate: suspend (T) -> Boolean,
): Flow<T> {
    val flowName = name ?: walkStackForClassName()
    return withTraceName(flowName).kx_filter {
        return@kx_filter traceCoroutine("$flowName:predicate") { predicate(it) }
    }
}

inline fun <reified R> Flow<*>.filterIsInstance(): Flow<R> {
    return kx_filterIsInstance<R>().withTraceName("${walkStackForClassName()}#filterIsInstance")
}

inline fun <T, R> Flow<T>.map(
    name: String? = null,
    crossinline transform: suspend (T) -> R,
): Flow<R> {
    val flowName = name ?: walkStackForClassName()
    return withTraceName(flowName).kx_map {
        return@kx_map traceCoroutine("$flowName:transform") { transform(it) }
    }
}

fun getFlowSliceNames(name: String?): Pair<String, String> {
    val flowName = name ?: walkStackForClassName()
    return Pair("$flowName:collect", "$flowName:emit")
}

object FlowExt {
    val currentFileName: String =
        StackWalker.getInstance().walk { stream -> stream.limit(1).findFirst() }.get().fileName
}

private fun isFrameInteresting(frame: StackWalker.StackFrame): Boolean {
    return frame.fileName != FlowExt.currentFileName
}

/** Get a name for the trace section include the name of the call site. */
fun walkStackForClassName(): String {
    Trace.traceBegin(Trace.TRACE_TAG_APP, "FlowExt#walkStackForClassName")
    try {
        val interestingFrame =
            StackWalker.getInstance().walk { stream ->
                stream.filter(::isFrameInteresting).limit(5).findFirst()
            }
        return if (interestingFrame.isPresent) {
            val frame = interestingFrame.get()
            return frame.className
        } else {
            "<unknown>"
        }
    } finally {
        Trace.traceEnd(Trace.TRACE_TAG_APP)
    }
}
