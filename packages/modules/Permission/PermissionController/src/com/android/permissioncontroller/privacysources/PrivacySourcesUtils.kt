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

@file:JvmName("PrivacySourcesUtils")

package com.android.permissioncontroller.privacysources

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun getSafetyCenterEvent(refreshEvent: RefreshEvent, intent: Intent): SafetyEvent {
    return when (refreshEvent) {
        RefreshEvent.UNKNOWN ->
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
        RefreshEvent.EVENT_DEVICE_REBOOTED ->
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build()
        RefreshEvent.EVENT_REFRESH_REQUESTED -> {
            val refreshBroadcastId =
                intent.getStringExtra(SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID)
            SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                .setRefreshBroadcastId(refreshBroadcastId)
                .build()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun isProfile(context: Context): Boolean {
    val userManager = Utils.getSystemServiceSafe(context, UserManager::class.java)
    return userManager.isProfile
}
