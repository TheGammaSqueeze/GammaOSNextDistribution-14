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

package com.android.permissioncontroller.privacysources

import android.content.Context
import android.content.Intent

interface PrivacySource {

    /**
     * It is {@code true} for privacy source that wants to receive broadcasts signals for profiles
     */
    val shouldProcessProfileRequest: Boolean

    /**
     * Indicates that permission controller has received the safety center enabled changed broadcast
     *
     * <p> Invoked when {@link SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED} received
     *
     * @param context: Context of the broadcast
     * @param enabled: {@code true} if Safety Center now enabled
     */
    fun safetyCenterEnabledChanged(context: Context, enabled: Boolean)

    /**
     * Indicates that permission controller has received the safety center rescan broadcast.
     * context: Context of the broadcast intent: Intent of the broadcast refreshEvent: Enum
     * explaining why this rescan was triggered. If the value is EVENT_REFRESH_REQUESTED, get the
     * broadcast id using code below, val refreshBroadcastId =
     * intent.getStringExtra(SafetyCenterManager .EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID) and add
     * it to the safety event, when sending SafetyCenterManager#setSafetyCenterUpdate val
     * safetyEvent = SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
     * .setRefreshBroadcastId(refreshBroadcastId).build()
     */
    fun rescanAndPushSafetyCenterData(
        context: Context,
        intent: Intent,
        refreshEvent: SafetyCenterReceiver.RefreshEvent
    )
}
