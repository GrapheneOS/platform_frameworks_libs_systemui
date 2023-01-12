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
import com.android.app.motiontool.DdmHandleMotionTool.Companion.CHUNK_MOTO
import com.android.app.motiontool.nano.BeginTraceRequest
import com.android.app.motiontool.nano.EndTraceRequest
import com.android.app.motiontool.nano.ErrorResponse
import com.android.app.motiontool.nano.HandshakeRequest
import com.android.app.motiontool.nano.HandshakeResponse
import com.android.app.motiontool.nano.MotionToolsRequest
import com.android.app.motiontool.nano.MotionToolsResponse
import com.android.app.motiontool.nano.PollTraceRequest
import com.android.app.motiontool.nano.WindowIdentifier
import com.android.app.motiontool.util.TestActivity
import com.android.app.viewcapture.ViewCapture
import com.google.protobuf.nano.MessageNano
import junit.framework.Assert
import junit.framework.Assert.assertEquals
import org.apache.harmony.dalvik.ddmc.Chunk
import org.apache.harmony.dalvik.ddmc.ChunkHandler.wrapChunk
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@SmallTest
@RunWith(AndroidTestingRunner::class)
class DdmHandleMotionToolTest {

    private val viewCaptureMemorySize = 100
    private val viewCaptureInitPoolSize = 15
    private val viewCapture =
        ViewCapture.getInstance(false, viewCaptureMemorySize, viewCaptureInitPoolSize)
    private val windowManagerGlobal = WindowManagerGlobal.getInstance()
    private val motionToolManager = MotionToolManager.getInstance(viewCapture, windowManagerGlobal)
    private val ddmHandleMotionTool = DdmHandleMotionTool.getInstance(motionToolManager)
    private val CLIENT_VERSION = 1

    private val activityIntent =
        Intent(InstrumentationRegistry.getInstrumentation().context, TestActivity::class.java)

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule<TestActivity>(activityIntent)

    @Before
    fun setup() {
        ddmHandleMotionTool.register()
    }

    @After
    fun cleanup() {
        ddmHandleMotionTool.unregister()
        motionToolManager.reset()
    }

    @Test
    fun testHandshakeErrorWithInvalidWindowId() {
        val handshakeResponse = performHandshakeRequest("InvalidWindowId")
        assertEquals(HandshakeResponse.WINDOW_NOT_FOUND, handshakeResponse.handshake.status)
    }

    @Test
    fun testHandshakeOkWithValidWindowId() {
        val handshakeResponse = performHandshakeRequest(getActivityViewRootId())
        assertEquals(HandshakeResponse.OK, handshakeResponse.handshake.status)
    }

    @Test
    fun testBeginFailsWithInvalidWindowId() {
        val errorResponse = performBeginTraceRequest("InvalidWindowId")
        assertEquals(ErrorResponse.WINDOW_NOT_FOUND, errorResponse.error.code)
    }

    @Test
    fun testEndTraceFailsWithoutPrecedingBeginTrace() {
        val errorResponse = performEndTraceRequest(0)
        assertEquals(ErrorResponse.UNKNOWN_TRACE_ID, errorResponse.error.code)
    }

    @Test
    fun testPollTraceFailsWithoutPrecedingBeginTrace() {
        val errorResponse = performPollTraceRequest(0)
        assertEquals(ErrorResponse.UNKNOWN_TRACE_ID, errorResponse.error.code)
    }

    @Test
    fun testEndTraceFailsWithInvalidTraceId() {
        val beginTraceResponse = performBeginTraceRequest(getActivityViewRootId())
        val endTraceResponse = performEndTraceRequest(beginTraceResponse.beginTrace.traceId + 1)
        assertEquals(ErrorResponse.UNKNOWN_TRACE_ID, endTraceResponse.error.code)
    }

    @Test
    fun testPollTraceFailsWithInvalidTraceId() {
        val beginTraceResponse = performBeginTraceRequest(getActivityViewRootId())
        val endTraceResponse = performPollTraceRequest(beginTraceResponse.beginTrace.traceId + 1)
        assertEquals(ErrorResponse.UNKNOWN_TRACE_ID, endTraceResponse.error.code)
    }

    @Test
    fun testMalformedRequestFails() {
        val requestBytes = ByteArray(9)
        val requestChunk = Chunk(CHUNK_MOTO, requestBytes, 0, requestBytes.size)
        val responseChunk = ddmHandleMotionTool.handleChunk(requestChunk)
        val response = MotionToolsResponse.parseFrom(wrapChunk(responseChunk).array()).error
        assertEquals(ErrorResponse.INVALID_REQUEST, response.code)
    }

    @Test
    fun testNoOnDrawCallReturnsEmptyTrace() {
        activityScenarioRule.scenario.onActivity {
            val beginTraceResponse = performBeginTraceRequest(getActivityViewRootId())
            val endTraceResponse = performEndTraceRequest(beginTraceResponse.beginTrace.traceId)
            Assert.assertTrue(endTraceResponse.endTrace.exportedData.frameData.isEmpty())
        }
    }

    @Test
    fun testOneOnDrawCallReturnsOneFrameResponse() {
        var traceId = 0
        activityScenarioRule.scenario.onActivity {
            val beginTraceResponse = performBeginTraceRequest(getActivityViewRootId())
            traceId = beginTraceResponse.beginTrace.traceId
            val rootView = it.findViewById<View>(android.R.id.content)
            rootView.invalidate()
        }

        // waits until main looper has no remaining tasks and is idle
        activityScenarioRule.scenario.onActivity {
            val pollTraceResponse = performPollTraceRequest(traceId)
            assertEquals(1, pollTraceResponse.pollTrace.exportedData.frameData.size)

            // Verify that frameData is only included once and is not returned again
            val endTraceResponse = performEndTraceRequest(traceId)
            assertEquals(0, endTraceResponse.endTrace.exportedData.frameData.size)
        }

    }

    private fun performPollTraceRequest(requestTraceId: Int): MotionToolsResponse {
        val pollTraceRequest = MotionToolsRequest().apply {
            pollTrace = PollTraceRequest().apply {
                traceId = requestTraceId
            }
        }
        return performRequest(pollTraceRequest)
    }

    private fun performEndTraceRequest(requestTraceId: Int): MotionToolsResponse {
        val endTraceRequest = MotionToolsRequest().apply {
            endTrace = EndTraceRequest().apply {
                traceId = requestTraceId
            }
        }
        return performRequest(endTraceRequest)
    }

    private fun performBeginTraceRequest(windowId: String): MotionToolsResponse {
        val beginTraceRequest = MotionToolsRequest().apply {
            beginTrace = BeginTraceRequest().apply {
                window = WindowIdentifier().apply {
                    rootWindow = windowId
                }
            }
        }
        return performRequest(beginTraceRequest)
    }

    private fun performHandshakeRequest(windowId: String): MotionToolsResponse {
        val handshakeRequest = MotionToolsRequest().apply {
            handshake = HandshakeRequest().apply {
                window = WindowIdentifier().apply {
                    rootWindow = windowId
                }
                clientVersion = CLIENT_VERSION
            }
        }
        return performRequest(handshakeRequest)
    }

    private fun performRequest(motionToolsRequest: MotionToolsRequest): MotionToolsResponse {
        val requestBytes = MessageNano.toByteArray(motionToolsRequest)
        val requestChunk = Chunk(CHUNK_MOTO, requestBytes, 0, requestBytes.size)
        val responseChunk = ddmHandleMotionTool.handleChunk(requestChunk)
        return MotionToolsResponse.parseFrom(wrapChunk(responseChunk).array())
    }

    private fun getActivityViewRootId(): String {
        var activityViewRootId = ""
        activityScenarioRule.scenario.onActivity {
            activityViewRootId = WindowManagerGlobal.getInstance().viewRootNames.first()
        }
        return activityViewRootId
    }

}
