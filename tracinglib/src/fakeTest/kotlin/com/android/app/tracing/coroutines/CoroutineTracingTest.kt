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

import com.android.app.tracing.TraceState.openSectionsOnCurrentThread
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@RunWith(BlockJUnit4ClassRunner::class)
class CoroutineTracingTest {

    @Test
    fun nestedTraceSectionsOnSingleThread() {
        runTest(TraceContextElement()) {
            val fetchData: suspend () -> String = {
                delay(1L)
                traceCoroutine("span-for-fetchData") {
                    assertArrayEquals(
                        arrayOf("span-for-launch", "span-for-fetchData"),
                        openSectionsOnCurrentThread()
                    )
                }
                "stuff"
            }
            launch("span-for-launch") {
                assertEquals("stuff", fetchData())
                assertArrayEquals(arrayOf("span-for-launch"), openSectionsOnCurrentThread())
            }
            assertEquals(0, openSectionsOnCurrentThread().size)
        }
    }
}
