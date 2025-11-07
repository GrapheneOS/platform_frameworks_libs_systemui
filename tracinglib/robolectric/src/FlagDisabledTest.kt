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

package com.android.test.tracing.coroutines

import android.platform.test.annotations.DisableFlags
import com.android.app.tracing.coroutines.DebugSysProps.coroutineTracingEnabled
import com.android.app.tracing.coroutines.createCoroutineTracingContext
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.app.tracing.coroutines.traceThreadLocal
import com.android.systemui.Flags.FLAG_COROUTINE_TRACING
import com.android.systemui.util.Compile
import com.android.test.tracing.coroutines.util.FakeTraceState
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@DisableFlags(FLAG_COROUTINE_TRACING)
class FlagDisabledTest : TestBase() {
    override val extraContext: CoroutineContext by lazy { EmptyCoroutineContext }

    @Test
    fun tracingDisabledWhenFlagIsOff() = runTest {
        assertFalse(com.android.systemui.Flags.coroutineTracing())
        assertTrue(Compile.IS_DEBUG)
        assertTrue(coroutineTracingEnabled) // sysprop enabled, but aconfig flag is off
        assertNull(traceThreadLocal.get())
        withContext(createCoroutineTracingContext(testMode = true)) {
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
}
