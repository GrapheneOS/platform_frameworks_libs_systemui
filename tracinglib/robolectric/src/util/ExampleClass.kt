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

package com.android.app.tracing.coroutines.util

import com.android.app.tracing.coroutines.TestBase

class ExampleClass(
    private val testBase: TestBase,
    private val incrementCounter: suspend () -> Unit,
) {
    suspend fun classMethod(value: Int) {
        value.inc() // <-- suppress warning that parameter 'value' is unused
        testBase.expect(
            "main:1^:1^launch-for-collect:3^",
            "com.android.app.tracing.coroutines.FlowTracingTest\$stateFlowCollection$1\$collectJob$1$3:collect",
            "com.android.app.tracing.coroutines.FlowTracingTest\$stateFlowCollection$1\$collectJob$1$3:emit",
        )
        incrementCounter()
    }
}
