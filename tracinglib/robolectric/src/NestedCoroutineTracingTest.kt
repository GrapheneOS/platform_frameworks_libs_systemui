/*
 * Copyright (C) 2025 The Android Open Source Project
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

@file:OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package com.android.test.tracing.coroutines

import android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import com.android.app.tracing.coroutines.launchTraced
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.systemui.Flags.FLAG_COROUTINE_TRACING
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import org.junit.Test

@DisableFlags(FLAG_PERFETTO_SDK_TRACING_V2)
@EnableFlags(FLAG_COROUTINE_TRACING)
class NestedCoroutineTracingTest : TestBase() {

    override val extraContext: CoroutineContext by lazy { createCoroutineTracingContext("main") }

    @Test
    fun stressTestContextSwitches_depth() {
        fun CoroutineScope.recursivelyLaunch(n: Int) {
            if (n == 400) return
            launchTraced("launch#$n", start = CoroutineStart.UNDISPATCHED) {
                traceCoroutine("a") {
                    if (n == 350) {
                        val expectedBeforeDelay = mutableListOf("main")
                        repeat(n + 1) {
                            expectedBeforeDelay.add("launch#$it")
                            expectedBeforeDelay.add("a")
                        }
                        expect(*expectedBeforeDelay.toTypedArray())
                    }
                    recursivelyLaunch(n + 1)
                    delay(1)
                    expect("launch#$n", "a")
                }
            }
        }
        runTest(totalEvents = 401) { recursivelyLaunch(0) }
    }
}
