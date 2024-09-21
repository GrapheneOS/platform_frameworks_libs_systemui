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

package com.android.app.tracing.coroutines

import android.platform.test.annotations.EnableFlags
import com.android.systemui.Flags.FLAG_COROUTINE_TRACING
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test

@EnableFlags(FLAG_COROUTINE_TRACING)
class CoroutineTracingTest : TracingTestBase() {

    @Test
    fun simpleTraceSection() = runTestTraced {
        expectD(1, "main:1^")
        traceCoroutine("hello") { expectD(2, "main:1^", "hello") }
        finish(3, "main:1^")
    }

    @Test
    fun simpleNestedTraceSection() = runTestTraced {
        expectD(1, "main:1^")
        traceCoroutine("hello") {
            expectD(2, "main:1^", "hello")
            traceCoroutine("world") { expectD(3, "main:1^", "hello", "world") }
            expectD(4, "main:1^", "hello")
        }
        finish(5, "main:1^")
    }

    @Test
    fun simpleLaunch() = runTestTraced {
        expectD(1, "main:1^")
        traceCoroutine("hello") {
            expectD(2, "main:1^", "hello")
            launch {
                // "hello" is not passed to child scope
                finish(4, "main:1^:1^")
            }
        }
        expect(3, "main:1^")
    }

    @Test
    fun launchWithSuspendingLambda() = runTestTraced {
        val fetchData: suspend () -> String = {
            expect(3, "main:1^:1^span-for-launch")
            delay(1L)
            traceCoroutine("span-for-fetchData") {
                expect(4, "main:1^:1^span-for-launch", "span-for-fetchData")
            }
            "stuff"
        }
        expect(1, "main:1^")
        launch("span-for-launch") {
            assertEquals("stuff", fetchData())
            finish(5, "main:1^:1^span-for-launch")
        }
        expect(2, "main:1^")
    }

    @Test
    fun launchInCoroutineScope() = runTestTraced {
        launch("launch#0") {
            expect("main:1^:1^launch#0")
            delay(1)
            expect("main:1^:1^launch#0")
        }
        coroutineScope("span-for-coroutineScope-1") {
            launch("launch#1") {
                expect("main:1^:2^launch#1")
                delay(1)
                expect("main:1^:2^launch#1")
            }
            launch("launch#2") {
                expect("main:1^:3^launch#2")
                delay(1)
                expect("main:1^:3^launch#2")
            }
            coroutineScope("span-for-coroutineScope-2") {
                launch("launch#3") {
                    expect("main:1^:4^launch#3")
                    delay(1)
                    expect("main:1^:4^launch#3")
                }
                launch("launch#4") {
                    expect("main:1^:5^launch#4")
                    delay(1)
                    expect("main:1^:5^launch#4")
                }
            }
        }
        launch("launch#5") {
            expect("main:1^:6^launch#5")
            delay(1)
            expect("main:1^:6^launch#5")
        }
    }

    @Test
    fun namedScopeMerging() = runTestTraced {
        // to avoid race conditions in the test leading to flakes, avoid calling expectD() or
        // delaying before launching (e.g. only call expectD() in leaf blocks)
        expect("main:1^")
        launch("A") {
            expect("main:1^:1^A")
            traceCoroutine("span") { expectD("main:1^:1^A", "span") }
            launch("B") { expectD("main:1^:1^A:1^B") }
            launch("C") {
                expect("main:1^:1^A:2^C")
                launch { expectD("main:1^:1^A:2^C:1^") }
                launch("D") { expectD("main:1^:1^A:2^C:2^D") }
                launch("E") {
                    expect("main:1^:1^A:2^C:3^E")
                    launch("F") { expectD("main:1^:1^A:2^C:3^E:1^F") }
                    expect("main:1^:1^A:2^C:3^E")
                }
            }
            launch("G") { expectD("main:1^:1^A:3^G") }
        }
        launch { launch { launch { expectD("main:1^:2^:1^:1^") } } }
        delay(2)
        launch("H") { launch { launch { expectD("main:1^:3^H:1^:1^") } } }
        delay(2)
        launch {
            launch {
                launch {
                    launch { launch { launch("I") { expectD("main:1^:4^:1^:1^:1^:1^:1^I") } } }
                }
            }
        }
        delay(2)
        launch("J") { launch("K") { launch { launch { expectD("main:1^:5^J:1^K:1^:1^") } } } }
        delay(2)
        launch("L") { launch("M") { launch { launch { expectD("main:1^:6^L:1^M:1^:1^") } } } }
        delay(2)
        launch("N") { launch("O") { launch { launch("D") { expectD("main:1^:7^N:1^O:1^:1^D") } } } }
        delay(2)
        launch("P") { launch("Q") { launch { launch("R") { expectD("main:1^:8^P:1^Q:1^:1^R") } } } }
        delay(2)
        launch("S") { launch("T") { launch { expectD("main:1^:9^S:1^T:1^") } } }
        delay(2)
        launch("U") { launch("V") { launch { expectD("main:1^:10^U:1^V:1^") } } }
        delay(2)
        expectD("main:1^")
    }

    @Test
    fun launchIntoSelf() = runTestTraced {
        expectD("main:1^")
        val reusedNameContext = nameCoroutine("my-coroutine")
        launch(reusedNameContext) {
            expectD("main:1^:1^my-coroutine")
            launch(reusedNameContext) { expectD("main:1^:1^my-coroutine:1^my-coroutine") }
            expectD("main:1^:1^my-coroutine")
            launch(reusedNameContext) { expectD("main:1^:1^my-coroutine:2^my-coroutine") }
            expectD("main:1^:1^my-coroutine")
        }
        launch(reusedNameContext) { expectD("main:1^:2^my-coroutine") }
        expectD("main:1^")
    }
}
