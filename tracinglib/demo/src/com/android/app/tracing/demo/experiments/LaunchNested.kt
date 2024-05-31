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
import com.android.app.tracing.demo.Default
import com.android.app.tracing.demo.FixedThread1
import com.android.app.tracing.demo.FixedThread2
import com.android.app.tracing.demo.IO
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.coroutineScope

@Singleton
class LaunchNested
@Inject
constructor(
    @FixedThread1 private var fixedThreadContext1: CoroutineContext,
    @FixedThread2 private var fixedThreadContext2: CoroutineContext,
    @Default private var defaultContext: CoroutineContext,
    @IO private var ioContext: CoroutineContext,
) : Experiment {
    override fun getDescription(): String = "launch{launch{launch{launch{}}}}"

    override suspend fun run(): Unit = coroutineScope {
        launch("launch(fixedThreadContext1)", fixedThreadContext1) {
            doWork()
            launch("launch(fixedThreadContext2)", fixedThreadContext2) {
                doWork()
                launch("launch(Dispatchers.IO)", ioContext) {
                    doWork()
                    launch("launch(Dispatchers.Default)", defaultContext) { doWork() }
                }
            }
        }
    }
}
