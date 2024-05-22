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
package com.android.app.tracing.demo.experiments

import com.android.app.tracing.coroutines.launch
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.demo.FixedThread1
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Singleton
class CollectFlow
@Inject
constructor(
    @FixedThread1 private var fixedThreadContext1: CoroutineContext,
    @FixedThread1 private var fixedThreadContext2: CoroutineContext,
) : Experiment {

    override fun getDescription(): String = "Collect flow and delay after getting result"

    private val countTo100 =
        flow {
                for (n in 0..100) {
                    traceCoroutine("$tag: flow producer - delay(20)") { delay(20) }
                    traceCoroutine("$tag: flow producer - emit($n)") { emit(n) }
                }
            }
            .flowOn(fixedThreadContext2)

    override suspend fun run(): Unit = coroutineScope {
        launch("$tag: launch and collect", fixedThreadContext1) {
            traceCoroutine("$tag: flow consumer - collect") {
                countTo100.collect { value ->
                    traceCoroutine("$tag: flow consumer - got $value") { delay(1) }
                }
            }
        }
    }
}
