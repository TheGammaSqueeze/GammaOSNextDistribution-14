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

package com.android.safetycenter.testing

import android.content.Context
import android.content.Intent
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.UserHandle
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCE_IDS
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceErrorDetails
import androidx.annotation.RequiresApi
import com.android.safetycenter.testing.SafetySourceTestData.Companion.EVENT_SOURCE_STATE_CHANGED
import javax.annotation.concurrent.GuardedBy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A class that handles [Intent] sent by Safety Center and interacts with the [SafetyCenterManager]
 * as a response.
 *
 * This is meant to emulate how Safety Sources will typically react to Safety Center intents.
 */
@RequiresApi(TIRAMISU)
class SafetySourceIntentHandler {

    private val refreshSafetySourcesChannel = Channel<String>(UNLIMITED)
    private val safetyCenterEnabledChangedChannel = Channel<Boolean>(UNLIMITED)
    private val resolveActionChannel = Channel<Unit>(UNLIMITED)
    private val dismissIssueChannel = Channel<Unit>(UNLIMITED)
    private val mutex = Mutex()
    @GuardedBy("mutex") private val requestsToResponses = mutableMapOf<Request, Response>()

    /** Handles the given [Intent] sent to a Safety Source in the given [Context]. */
    suspend fun handle(context: Context, intent: Intent) {
        val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
        val userId = context.userId
        when (val action = intent.action) {
            ACTION_REFRESH_SAFETY_SOURCES ->
                safetyCenterManager.processRefreshSafetySources(intent, userId)
            ACTION_SAFETY_CENTER_ENABLED_CHANGED ->
                safetyCenterEnabledChangedChannel.send(safetyCenterManager.isSafetyCenterEnabled)
            ACTION_RESOLVE_ACTION -> safetyCenterManager.processResolveAction(intent, userId)
            ACTION_DISMISS_ISSUE -> safetyCenterManager.processDismissIssue(intent, userId)
            else -> throw IllegalArgumentException("Unexpected intent action: $action")
        }
    }

    /**
     * Sets the [response] to perform on the [SafetyCenterManager] on incoming [Intent]s matching
     * the given [request].
     */
    suspend fun setResponse(request: Request, response: Response) {
        mutex.withLock { requestsToResponses.put(request, response) }
    }

    /**
     * Suspends until an [ACTION_REFRESH_SAFETY_SOURCES] [Intent] is processed, and returns the
     * associated [EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID].
     */
    suspend fun receiveRefreshSafetySources(): String = refreshSafetySourcesChannel.receive()

    /**
     * Suspends until an [ACTION_SAFETY_CENTER_ENABLED_CHANGED] [Intent] is processed, and returns
     * whether the Safety Center is now enabled.
     */
    suspend fun receiveSafetyCenterEnabledChanged(): Boolean =
        safetyCenterEnabledChangedChannel.receive()

    /** Suspends until an [ACTION_RESOLVE_ACTION] [Intent] is processed. */
    suspend fun receiveResolveAction() {
        resolveActionChannel.receive()
    }

    /** Suspends until an [ACTION_DISMISS_ISSUE] [Intent] is processed. */
    suspend fun receiveDimissIssue() {
        dismissIssueChannel.receive()
    }

    /** Cancels any pending update on this [SafetySourceIntentHandler]. */
    fun cancel() {
        refreshSafetySourcesChannel.cancel()
        safetyCenterEnabledChangedChannel.cancel()
        resolveActionChannel.cancel()
        dismissIssueChannel.cancel()
    }

    private suspend fun SafetyCenterManager.processRefreshSafetySources(
        intent: Intent,
        userId: Int
    ) {
        val broadcastId = intent.getStringExtra(EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID)
        if (broadcastId.isNullOrEmpty()) {
            throw IllegalArgumentException("Received refresh intent with no broadcast id specified")
        }

        val sourceIds = intent.getStringArrayExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS)
        if (sourceIds.isNullOrEmpty()) {
            throw IllegalArgumentException("Received refresh intent with no source ids specified")
        }

        val requestType = intent.getIntExtra(EXTRA_REFRESH_SAFETY_SOURCES_REQUEST_TYPE, -1)

        for (sourceId in sourceIds) {
            processRequest(toRefreshSafetySourcesRequest(requestType, sourceId, userId)) {
                createRefreshEvent(
                    if (it is Response.SetData && it.overrideBroadcastId != null) {
                        it.overrideBroadcastId
                    } else {
                        broadcastId
                    }
                )
            }
        }

        refreshSafetySourcesChannel.send(broadcastId)
    }

    private fun toRefreshSafetySourcesRequest(requestType: Int, sourceId: String, userId: Int) =
        when (requestType) {
            EXTRA_REFRESH_REQUEST_TYPE_GET_DATA -> Request.Refresh(sourceId, userId)
            EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA -> Request.Rescan(sourceId, userId)
            else -> throw IllegalStateException("Unexpected request type: $requestType")
        }

    private fun createRefreshEvent(broadcastId: String) =
        SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
            .setRefreshBroadcastId(broadcastId)
            .build()

    private suspend fun SafetyCenterManager.processResolveAction(intent: Intent, userId: Int) {
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
        if (sourceId.isNullOrEmpty()) {
            throw IllegalArgumentException(
                "Received resolve action intent with no source id specified"
            )
        }

        val sourceIssueId = intent.getStringExtra(EXTRA_SOURCE_ISSUE_ID)
        if (sourceIssueId.isNullOrEmpty()) {
            throw IllegalArgumentException(
                "Received resolve action intent with no source issue id specified"
            )
        }

        val sourceIssueActionId = intent.getStringExtra(EXTRA_SOURCE_ISSUE_ACTION_ID)
        if (sourceIssueActionId.isNullOrEmpty()) {
            throw IllegalArgumentException(
                "Received resolve action intent with no source issue action id specified"
            )
        }

        processRequest(Request.ResolveAction(sourceId, userId)) {
            if (it is Response.Error) {
                createResolveActionErrorEvent(sourceIssueId, sourceIssueActionId)
            } else {
                createResolveActionSuccessEvent(sourceIssueId, sourceIssueActionId)
            }
        }

        resolveActionChannel.send(Unit)
    }

    private fun createResolveActionSuccessEvent(
        sourceIssueId: String,
        sourceIssueActionId: String
    ) =
        SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED)
            .setSafetySourceIssueId(sourceIssueId)
            .setSafetySourceIssueActionId(sourceIssueActionId)
            .build()

    private fun createResolveActionErrorEvent(sourceIssueId: String, sourceIssueActionId: String) =
        SafetyEvent.Builder(SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED)
            .setSafetySourceIssueId(sourceIssueId)
            .setSafetySourceIssueActionId(sourceIssueActionId)
            .build()

    private suspend fun SafetyCenterManager.processDismissIssue(intent: Intent, userId: Int) {
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
        if (sourceId.isNullOrEmpty()) {
            throw IllegalArgumentException(
                "Received dismiss issue intent with no source id specified"
            )
        }

        processRequest(Request.DismissIssue(sourceId, userId)) { EVENT_SOURCE_STATE_CHANGED }

        dismissIssueChannel.send(Unit)
    }

    private suspend fun SafetyCenterManager.processRequest(
        request: Request,
        safetyEventForResponse: (Response) -> SafetyEvent
    ) {
        val response = mutex.withLock { requestsToResponses[request] } ?: return
        val safetyEvent = response.overrideSafetyEvent ?: safetyEventForResponse(response)
        when (response) {
            is Response.Error ->
                reportSafetySourceError(request.sourceId, SafetySourceErrorDetails(safetyEvent))
            is Response.ClearData -> setSafetySourceData(request.sourceId, null, safetyEvent)
            is Response.SetData ->
                setSafetySourceData(request.sourceId, response.safetySourceData, safetyEvent)
        }
    }

    /** An interface that matches an [Intent] request (or a subset of it). */
    sealed interface Request {
        /** The safety source id this request applies to. */
        val sourceId: String

        /** The user id this request applies to. */
        val userId: Int

        /** Creates a refresh [Request] based on the given [sourceId] and [userId]. */
        data class Refresh(
            override val sourceId: String,
            override val userId: Int = UserHandle.myUserId()
        ) : Request

        /** Creates a rescan [Request] based on the given [sourceId] and [userId]. */
        data class Rescan(
            override val sourceId: String,
            override val userId: Int = UserHandle.myUserId()
        ) : Request

        /** Creates a resolve action [Request] based on the given [sourceId] and [userId]. */
        data class ResolveAction(
            override val sourceId: String,
            override val userId: Int = UserHandle.myUserId()
        ) : Request

        /** Creates an issue dismissal [Request] based on the given [sourceId] and [userId]. */
        data class DismissIssue(
            override val sourceId: String,
            override val userId: Int = UserHandle.myUserId()
        ) : Request
    }

    /**
     * An interface that specifies the appropriate action to take on the [SafetyCenterManager] as a
     * response to an incoming [Request].
     */
    sealed interface Response {

        /**
         * If non-null, the [SafetyEvent] to use when calling any applicable [SafetyCenterManager]
         * methods.
         */
        val overrideSafetyEvent: SafetyEvent?
            get() = null

        /** Creates an error [Response]. */
        object Error : Response

        /** Creates a [Response] to clear the data. */
        object ClearData : Response

        /**
         * Creates a [Response] to set the given [SafetySourceData].
         *
         * @param overrideBroadcastId an optional override of the broadcast id to use in the
         *   [SafetyEvent] sent to the [SafetyCenterManager], in case of [Request.Refresh] or
         *   [Request.Rescan]. This is used to simulate a misuse of the [SafetyCenterManager] APIs
         * @param overrideSafetyEvent like [overrideBroadcastId] but allows the whole [SafetyEvent]
         *   to be override to send different types of [SafetyEvent].
         */
        data class SetData(
            val safetySourceData: SafetySourceData,
            val overrideBroadcastId: String? = null,
            override val overrideSafetyEvent: SafetyEvent? = null
        ) : Response
    }

    companion object {
        /** An intent action to handle a resolving action. */
        const val ACTION_RESOLVE_ACTION = "com.android.safetycenter.testing.action.RESOLVE_ACTION"

        /** An intent action to handle an issue dismissed by Safety Center. */
        const val ACTION_DISMISS_ISSUE = "com.android.safetycenter.testing.action.DISMISS_ISSUE"

        /**
         * An extra to be used with [ACTION_RESOLVE_ACTION] or [ACTION_DISMISS_ISSUE] to specify the
         * target safety source id.
         */
        const val EXTRA_SOURCE_ID = "com.android.safetycenter.testing.extra.SOURCE_ID"

        /**
         * An extra to be used with [ACTION_RESOLVE_ACTION] to specify the target safety source
         * issue id of the resolving action.
         */
        const val EXTRA_SOURCE_ISSUE_ID = "com.android.safetycenter.testing.extra.SOURCE_ISSUE_ID"

        /**
         * An extra to be used with [ACTION_RESOLVE_ACTION] to specify the target safety source
         * issue action id of the resolving action.
         */
        const val EXTRA_SOURCE_ISSUE_ACTION_ID =
            "com.android.safetycenter.testing.extra.SOURCE_ISSUE_ACTION_ID"
    }
}
