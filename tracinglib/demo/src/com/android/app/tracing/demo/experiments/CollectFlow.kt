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

import com.android.app.tracing.coroutines.flow.withTraceName
import com.android.app.tracing.coroutines.launch
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.demo.FixedThread1
import com.android.app.tracing.demo.FixedThread2
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Util for introducing artificial delays to make the trace more readable for demo purposes. */
private fun blockCurrentThread(millis: Long) {
    Thread.sleep(millis)
}

@Singleton
class CollectFlow
@Inject
constructor(
    @FixedThread1 private var fixedThreadContext1: CoroutineContext,
    @FixedThread2 private var fixedThreadContext2: CoroutineContext,
) : Experiment {

    override fun getDescription(): String = "Collect a cold flow with intermediate operators"

    override suspend fun run(): Unit = coroutineScope {
        val numFlow =
            flow {
                    for (n in 0..4) {
                        traceCoroutine("delay-and-emit for $n") {
                            blockCurrentThread(5)
                            delay(1)
                            blockCurrentThread(6)
                            emit(n)
                            blockCurrentThread(7)
                            delay(1)
                            blockCurrentThread(8)
                        }
                    }
                }
                .withTraceName("flowOf numbers")
                .filter {
                    blockCurrentThread(9)
                    it % 2 == 0
                }
                .withTraceName("filter for even")
                .map {
                    blockCurrentThread(10)
                    it * 3
                }
                .withTraceName("map 3x")
                .flowOn(fixedThreadContext2)
                .withTraceName("flowOn thread #2")

        launch("launch on thread #1", fixedThreadContext1) {
            numFlow.collect {
                traceCoroutine("got: $it") {
                    blockCurrentThread(11)
                    delay(1)
                    blockCurrentThread(12)
                }
            }
        }
    }
}
