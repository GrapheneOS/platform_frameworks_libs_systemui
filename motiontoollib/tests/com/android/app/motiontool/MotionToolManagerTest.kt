/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.app.motiontool

import android.content.Intent
import android.testing.AndroidTestingRunner
import android.view.View
import android.view.WindowManagerGlobal
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.app.motiontool.util.TestActivity
import com.android.app.viewcapture.ViewCapture
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@SmallTest
@RunWith(AndroidTestingRunner::class)
class MotionToolManagerTest {

    private val windowManagerGlobal = WindowManagerGlobal.getInstance()
    private val viewCaptureMemorySize = 100
    private val viewCaptureInitPoolSize = 15
    private val viewCapture =
        ViewCapture.getInstance(false, viewCaptureMemorySize, viewCaptureInitPoolSize)
    private val motionToolManager = MotionToolManager.getInstance(viewCapture, windowManagerGlobal)

    private val activityIntent =
        Intent(InstrumentationRegistry.getInstrumentation().context, TestActivity::class.java)

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule<TestActivity>(activityIntent)

    @After
    fun cleanup() {
        motionToolManager.reset()
    }

    @Test(expected = UnknownTraceIdException::class)
    fun testEndTraceThrowsWithoutPrecedingBeginTrace() {
        motionToolManager.endTrace(0)
    }

    @Test(expected = UnknownTraceIdException::class)
    fun testPollTraceThrowsWithoutPrecedingBeginTrace() {
        motionToolManager.pollTrace(0)
    }

    @Test(expected = UnknownTraceIdException::class)
    fun testEndTraceThrowsWithInvalidTraceId() {
        val traceId = motionToolManager.beginTrace(getActivityViewRootId())
        motionToolManager.endTrace(traceId + 1)
    }

    @Test(expected = UnknownTraceIdException::class)
    fun testPollTraceThrowsWithInvalidTraceId() {
        val traceId = motionToolManager.beginTrace(getActivityViewRootId())
        motionToolManager.pollTrace(traceId + 1)
    }

    @Test(expected = WindowNotFoundException::class)
    fun testBeginTraceThrowsWithInvalidWindowId() {
        motionToolManager.beginTrace("InvalidWindowId")
    }

    @Test
    fun testNoOnDrawCallReturnsEmptyResponse() {
        activityScenarioRule.scenario.onActivity {
            val traceId = motionToolManager.beginTrace(getActivityViewRootId())
            val result = motionToolManager.endTrace(traceId)
            assertTrue(result.frameData.isEmpty())
        }
    }

    @Test
    fun testOneOnDrawCallReturnsOneFrameResponse() {
        var traceId = 0
        activityScenarioRule.scenario.onActivity {
            traceId = motionToolManager.beginTrace(getActivityViewRootId())
            val rootView = it.findViewById<View>(android.R.id.content)
            rootView.invalidate()
        }

        // waits until main looper has no remaining tasks and is idle
        activityScenarioRule.scenario.onActivity {
            val polledExportedData = motionToolManager.pollTrace(traceId)
            assertEquals(1, polledExportedData.frameData.size)

            // Verify that frameData is only included once and is not returned again
            val endExportedData = motionToolManager.endTrace(traceId)
            assertEquals(0, endExportedData.frameData.size)
        }

    }

    private fun getActivityViewRootId(): String {
        var activityViewRootId = ""
        activityScenarioRule.scenario.onActivity {
            activityViewRootId = WindowManagerGlobal.getInstance().viewRootNames.first()
        }
        return activityViewRootId
    }
}
