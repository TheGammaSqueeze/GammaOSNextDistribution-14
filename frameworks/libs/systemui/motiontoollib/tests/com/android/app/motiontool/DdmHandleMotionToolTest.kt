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
import android.view.Choreographer
import android.view.View
import android.view.WindowManagerGlobal
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.app.motiontool.DdmHandleMotionTool.Companion.CHUNK_MOTO
import com.android.app.motiontool.util.TestActivity
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

    private val windowManagerGlobal = WindowManagerGlobal.getInstance()
    private val motionToolManager = MotionToolManager.getInstance(windowManagerGlobal)
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
    }

    @Test
    fun testHandshakeErrorWithInvalidWindowId() {
        val handshakeResponse = performHandshakeRequest("InvalidWindowId")
        assertEquals(HandshakeResponse.Status.WINDOW_NOT_FOUND, handshakeResponse.handshake.status)
    }

    @Test
    fun testHandshakeOkWithValidWindowId() {
        val handshakeResponse = performHandshakeRequest(getActivityViewRootId())
        assertEquals(HandshakeResponse.Status.OK, handshakeResponse.handshake.status)
    }

    @Test
    fun testBeginFailsWithInvalidWindowId() {
        val errorResponse = performBeginTraceRequest("InvalidWindowId")
        assertEquals(ErrorResponse.Code.WINDOW_NOT_FOUND, errorResponse.error.code)
    }

    @Test
    fun testEndTraceFailsWithoutPrecedingBeginTrace() {
        val errorResponse = performEndTraceRequest(0)
        assertEquals(ErrorResponse.Code.UNKNOWN_TRACE_ID, errorResponse.error.code)
    }

    @Test
    fun testPollTraceFailsWithoutPrecedingBeginTrace() {
        val errorResponse = performPollTraceRequest(0)
        assertEquals(ErrorResponse.Code.UNKNOWN_TRACE_ID, errorResponse.error.code)
    }

    @Test
    fun testEndTraceFailsWithInvalidTraceId() {
        val beginTraceResponse = performBeginTraceRequest(getActivityViewRootId())
        val endTraceResponse = performEndTraceRequest(beginTraceResponse.beginTrace.traceId + 1)
        assertEquals(ErrorResponse.Code.UNKNOWN_TRACE_ID, endTraceResponse.error.code)
    }

    @Test
    fun testPollTraceFailsWithInvalidTraceId() {
        val beginTraceResponse = performBeginTraceRequest(getActivityViewRootId())
        val endTraceResponse = performPollTraceRequest(beginTraceResponse.beginTrace.traceId + 1)
        assertEquals(ErrorResponse.Code.UNKNOWN_TRACE_ID, endTraceResponse.error.code)
    }

    @Test
    fun testMalformedRequestFails() {
        val requestBytes = ByteArray(9)
        val requestChunk = Chunk(CHUNK_MOTO, requestBytes, 0, requestBytes.size)
        val responseChunk = ddmHandleMotionTool.handleChunk(requestChunk)
        val response = MotionToolsResponse.parseFrom(wrapChunk(responseChunk).array()).error
        assertEquals(ErrorResponse.Code.INVALID_REQUEST, response.code)
    }

    @Test
    fun testNoOnDrawCallReturnsEmptyTrace() {
        activityScenarioRule.scenario.onActivity {
            val beginTraceResponse = performBeginTraceRequest(getActivityViewRootId())
            val endTraceResponse = performEndTraceRequest(beginTraceResponse.beginTrace.traceId)
            Assert.assertTrue(endTraceResponse.endTrace.data.frameDataList.isEmpty())
        }
    }

    @Test
    fun testOneOnDrawCallReturnsOneFrameResponse() {
        activityScenarioRule.scenario.onActivity { activity ->
            val beginTraceResponse = performBeginTraceRequest(getActivityViewRootId())
            val traceId = beginTraceResponse.beginTrace.traceId

            Choreographer.getInstance().postFrameCallback {
                activity
                    .requireViewById<View>(android.R.id.content)
                    .viewTreeObserver
                    .dispatchOnDraw()

                val pollTraceResponse = performPollTraceRequest(traceId)
                assertEquals(1, pollTraceResponse.pollTrace.data.frameDataList.size)

                // Verify that frameData is only included once and is not returned again
                val endTraceResponse = performEndTraceRequest(traceId)
                assertEquals(0, endTraceResponse.endTrace.data.frameDataList.size)
            }
        }
    }

    private fun performPollTraceRequest(requestTraceId: Int): MotionToolsResponse {
        val pollTraceRequest = MotionToolsRequest.newBuilder()
                .setPollTrace(PollTraceRequest.newBuilder()
                        .setTraceId(requestTraceId))
                .build()
        return performRequest(pollTraceRequest)
    }

    private fun performEndTraceRequest(requestTraceId: Int): MotionToolsResponse {
        val endTraceRequest = MotionToolsRequest.newBuilder()
                .setEndTrace(EndTraceRequest.newBuilder()
                        .setTraceId(requestTraceId))
                .build()
        return performRequest(endTraceRequest)
    }

    private fun performBeginTraceRequest(windowId: String): MotionToolsResponse {
        val beginTraceRequest = MotionToolsRequest.newBuilder()
                .setBeginTrace(BeginTraceRequest.newBuilder()
                        .setWindow(WindowIdentifier.newBuilder()
                                .setRootWindow(windowId)))
                .build()
        return performRequest(beginTraceRequest)
    }

    private fun performHandshakeRequest(windowId: String): MotionToolsResponse {
        val handshakeRequest = MotionToolsRequest.newBuilder()
                .setHandshake(HandshakeRequest.newBuilder()
                        .setWindow(WindowIdentifier.newBuilder()
                                .setRootWindow(windowId))
                        .setClientVersion(CLIENT_VERSION))
                .build()
        return performRequest(handshakeRequest)
    }

    private fun performRequest(motionToolsRequest: MotionToolsRequest): MotionToolsResponse {
        val requestBytes = motionToolsRequest.toByteArray()
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
