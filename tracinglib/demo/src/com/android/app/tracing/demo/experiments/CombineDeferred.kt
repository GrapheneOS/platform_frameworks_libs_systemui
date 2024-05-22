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

import com.android.app.tracing.coroutines.async
import com.android.app.tracing.demo.Default
import com.android.app.tracing.demo.FixedThread1
import com.android.app.tracing.demo.FixedThread2
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Singleton
class CombineDeferred
@Inject
constructor(
    @FixedThread1 private var fixedThreadContext1: CoroutineContext,
    @FixedThread2 private var fixedThreadContext2: CoroutineContext,
    @Default private var defaultContext: CoroutineContext,
) : Experiment {
    override fun getDescription(): String = "async{} then await()"

    override suspend fun run(): Unit = coroutineScope {
        val results =
            listOf(
                async("$tag: async#1 - getNumber()", fixedThreadContext1) { getNumber() },
                async("$tag: async#2 - getNumber()", fixedThreadContext2) { getNumber() },
                async("$tag: async#3 - getNumber()", defaultContext) { getNumber() },
                async("$tag: async#4 - getNumber()") { getNumber(0, 50) },
                async("$tag: async#5 - getNumber()") { getNumber(50, 0) },
                async("$tag: async#5 - getNumber()") { getNumber(50, 50) },
            )
        launch(fixedThreadContext1) { results.forEach { it.await() } }
    }
}
