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

import android.ddm.DdmHandle
import com.android.app.motiontool.nano.*
import com.google.protobuf.nano.InvalidProtocolBufferNanoException
import com.google.protobuf.nano.MessageNano
import org.apache.harmony.dalvik.ddmc.Chunk
import org.apache.harmony.dalvik.ddmc.ChunkHandler
import org.apache.harmony.dalvik.ddmc.DdmServer

/**
 * This class handles the 'MOTO' type DDM requests (defined in [motion_tool.proto]).
 *
 * It executes some validity checks and forwards valid requests to the [MotionToolManager]. It
 * requires a [MotionToolsRequest] as parameter and returns a [MotionToolsResponse]. Failures will
 * return a [MotionToolsResponse] with the [error][MotionToolsResponse.error] field set instead of
 * the respective return value.
 *
 * To activate this server, call [register]. This will register the DdmHandleMotionTool with the
 * [DdmServer]. The DdmHandleMotionTool can be registered once per process. To unregister from the
 * DdmServer, call [unregister].
 */
class DdmHandleMotionTool private constructor(
    private val motionToolManager: MotionToolManager
) : DdmHandle() {

    companion object {
        val CHUNK_MOTO = ChunkHandler.type("MOTO")
        private const val SERVER_VERSION = 1

        private var INSTANCE: DdmHandleMotionTool? = null

        @Synchronized
        fun getInstance(motionToolManager: MotionToolManager): DdmHandleMotionTool {
            return INSTANCE ?: DdmHandleMotionTool(motionToolManager).also {
                INSTANCE = it
            }
        }
    }

    fun register() {
        DdmServer.registerHandler(CHUNK_MOTO, this)
    }

    fun unregister() {
        DdmServer.unregisterHandler(CHUNK_MOTO)
    }

    override fun handleChunk(request: Chunk): Chunk {
        val requestDataBuffer = wrapChunk(request)
        val protoRequest =
            try {
                MotionToolsRequest.parseFrom(requestDataBuffer.array())
            } catch (e: InvalidProtocolBufferNanoException) {
                val parseErrorResponse =
                    ErrorResponse().apply {
                        code = ErrorResponse.INVALID_REQUEST
                        message = "Invalid request format (Protobuf parse exception)"
                    }
                val wrappedResponse = MotionToolsResponse().apply { error = parseErrorResponse }
                val responseData = MessageNano.toByteArray(wrappedResponse)
                return Chunk(CHUNK_MOTO, responseData, 0, responseData.size)
            }

        val response =
            when (protoRequest.typeCase) {
                MotionToolsRequest.HANDSHAKE_FIELD_NUMBER ->
                    handleHandshakeRequest(protoRequest.handshake)
                MotionToolsRequest.BEGIN_TRACE_FIELD_NUMBER ->
                    handleBeginTraceRequest(protoRequest.beginTrace)
                MotionToolsRequest.POLL_TRACE_FIELD_NUMBER ->
                    handlePollTraceRequest(protoRequest.pollTrace)
                MotionToolsRequest.END_TRACE_FIELD_NUMBER ->
                    handleEndTraceRequest(protoRequest.endTrace)
                else ->
                    MotionToolsResponse().apply {
                        error =
                            ErrorResponse().apply {
                                code = ErrorResponse.INVALID_REQUEST
                                message = "Unknown request type"
                            }
                    }
            }

        val responseData = MessageNano.toByteArray(response)
        return Chunk(CHUNK_MOTO, responseData, 0, responseData.size)
    }

    private fun handleBeginTraceRequest(beginTraceRequest: BeginTraceRequest) =
        MotionToolsResponse().apply {
            tryCatchingMotionToolManagerExceptions {
                beginTrace =
                    BeginTraceResponse().apply {
                        traceId = motionToolManager.beginTrace(beginTraceRequest.window.rootWindow)
                    }
            }
        }

    private fun handlePollTraceRequest(pollTraceRequest: PollTraceRequest) =
        MotionToolsResponse().apply {
            tryCatchingMotionToolManagerExceptions {
                pollTrace =
                    PollTraceResponse().apply {
                        exportedData = motionToolManager.pollTrace(pollTraceRequest.traceId)
                    }
            }
        }

    private fun handleEndTraceRequest(endTraceRequest: EndTraceRequest) =
        MotionToolsResponse().apply {
            tryCatchingMotionToolManagerExceptions {
                endTrace =
                    EndTraceResponse().apply {
                        exportedData = motionToolManager.endTrace(endTraceRequest.traceId)
                    }
            }
        }

    private fun handleHandshakeRequest(handshakeRequest: HandshakeRequest) =
        MotionToolsResponse().apply {
            handshake =
                HandshakeResponse().apply {
                    serverVersion = SERVER_VERSION
                    status =
                        if (motionToolManager.hasWindow(handshakeRequest.window)) {
                            HandshakeResponse.OK
                        } else {
                            HandshakeResponse.WINDOW_NOT_FOUND
                        }
                }
        }

    /**
     * Executes the [block] and catches all Exceptions thrown by [MotionToolManager]. In case of an
     * exception being caught, the error response field of the [MotionToolsResponse] is being set
     * with the according [ErrorResponse].
     */
    private fun MotionToolsResponse.tryCatchingMotionToolManagerExceptions(block: () -> Unit) {
        try {
            block()
        } catch (e: UnknownTraceIdException) {
            error = createUnknownTraceIdResponse(e.traceId)
        } catch (e: WindowNotFoundException) {
            error = createWindowNotFoundResponse(e.windowId)
        }
    }

    private fun createUnknownTraceIdResponse(traceId: Int) =
        ErrorResponse().apply {
            this.code = ErrorResponse.UNKNOWN_TRACE_ID
            this.message = "No running Trace found with traceId $traceId"
        }

    private fun createWindowNotFoundResponse(windowId: String) =
        ErrorResponse().apply {
            this.code = ErrorResponse.WINDOW_NOT_FOUND
            this.message = "No window found with windowId $windowId"
        }

    override fun onConnected() {}

    override fun onDisconnected() {}
}
