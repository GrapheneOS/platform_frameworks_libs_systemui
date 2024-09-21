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

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import com.android.app.tracing.coroutines.util.FakeTraceState
import com.android.systemui.Flags.FLAG_COROUTINE_TRACING
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CoroutineTracingFlagsTest : TestBase() {

    @DisableFlags(FLAG_COROUTINE_TRACING)
    @Test
    fun tracingDisabledWhenFlagIsOff() = runTest {
        assertFalse(com.android.systemui.Flags.coroutineTracing())
        assertNull(traceThreadLocal.get())
        withContext(createCoroutineTracingContext()) {
            assertNull(traceThreadLocal.get())
            traceCoroutine("hello") { // should not crash
                assertNull(traceThreadLocal.get())
            }

            // Change Trace.isEnabled() to false so that the lazy-String is not called for async
            // tracing, which would be expected even when coroutine tracing is disabled.
            FakeTraceState.isTracingEnabled = false

            // Verify that the lazy-String is not called when tracing is disabled and feature flag
            // is off
            traceCoroutine({
                fail("Lazy string should not be called when FLAG_COROUTINE_TRACING is disabled")
                "error"
            }) {
                assertNull(traceThreadLocal.get())
            }
        }
    }

    @EnableFlags(FLAG_COROUTINE_TRACING)
    @Test
    fun lazyStringIsAlwaysCalledOnDebugBuilds() = runTest {
        FakeTraceState.isTracingEnabled = false
        assertNull(traceThreadLocal.get())
        withContext(createCoroutineTracingContext()) {
            assertNotNull(traceThreadLocal.get())

            // It is expected that the lazy-String is called even when tracing is disabled because
            // otherwise the coroutine resumption points would be missing names.
            var lazyStringCalled = false
            traceCoroutine({
                lazyStringCalled = true
                "hello"
            }) {
                assertTrue(
                    "Lazy string should be been called when FLAG_COROUTINE_TRACING is enabled, " +
                        "even when Trace.isEnabled()=false",
                    lazyStringCalled,
                )
                val traceData = traceThreadLocal.get() as TraceData
                assertEquals(traceData.slices?.size, 1)
            }
        }
    }
}
