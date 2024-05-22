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
import com.android.app.tracing.demo.IO
import com.android.app.tracing.demo.Unconfined
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.coroutineScope

@Singleton
class UnconfinedThreadSwitch
@Inject
constructor(
    @IO private var ioContext: CoroutineContext,
    @Unconfined private var unconfinedContext: CoroutineContext,
) : Experiment {
    override fun getDescription(): String = "launch with Dispatchers.Unconfined"

    override suspend fun run(): Unit = coroutineScope {
        launch("launch(Dispatchers.Unconfined)", unconfinedContext) { doWork() }
        launch("launch(EmptyCoroutineContext)") { doWork() }
        launch("launch(Dispatchers.IO)", ioContext) { doWork() }
    }
}
