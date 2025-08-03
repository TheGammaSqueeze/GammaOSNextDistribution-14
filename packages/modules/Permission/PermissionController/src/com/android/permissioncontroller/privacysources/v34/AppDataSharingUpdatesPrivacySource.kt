/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.privacysources.v34

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_REVIEW_APP_DATA_SHARING_UPDATES
import android.os.Build
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceData.SEVERITY_LEVEL_INFORMATION
import android.safetycenter.SafetySourceStatus
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.PrivacySource
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_DEVICE_REBOOTED
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.EVENT_REFRESH_REQUESTED
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent.UNKNOWN

/**
 * Privacy source providing the App Data Sharing Updates page entry to Safety Center.
 *
 * The content of the App Data Sharing Updates page is static, however the entry should only be
 * displayed if the Safety Label Change Notification feature is enabled.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AppDataSharingUpdatesPrivacySource : PrivacySource {

    override val shouldProcessProfileRequest: Boolean = false

    override fun safetyCenterEnabledChanged(context: Context, enabled: Boolean) {
        // Do nothing.
    }

    override fun rescanAndPushSafetyCenterData(
        context: Context,
        intent: Intent,
        refreshEvent: RefreshEvent
    ) {
        val safetyCenterManager: SafetyCenterManager =
            Utils.getSystemServiceSafe(context, SafetyCenterManager::class.java)

        val safetySourceData =
            if (KotlinUtils.isSafetyLabelChangeNotificationsEnabled(context)) {
                SafetySourceData.Builder()
                    .setStatus(
                        SafetySourceStatus.Builder(
                                context.getString(R.string.data_sharing_updates_title),
                                context.getString(R.string.data_sharing_updates_summary),
                                SEVERITY_LEVEL_INFORMATION
                            )
                            .setPendingIntent(
                                PendingIntent.getActivity(
                                    context,
                                    /* requestCode= */ 0,
                                    Intent(ACTION_REVIEW_APP_DATA_SHARING_UPDATES),
                                    FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
                                )
                            )
                            .build(),
                    )
                    .build()
            } else {
                null
            }

        safetyCenterManager.setSafetySourceData(
            APP_DATA_SHARING_UPDATES_SOURCE_ID,
            safetySourceData,
            createSafetyEventForDataSharingUpdates(refreshEvent, intent)
        )
    }

    private fun createSafetyEventForDataSharingUpdates(
        refreshEvent: RefreshEvent,
        intent: Intent
    ): SafetyEvent {
        return when (refreshEvent) {
            EVENT_REFRESH_REQUESTED -> {
                val refreshBroadcastId =
                    intent.getStringExtra(
                        SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID
                    )
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                    .setRefreshBroadcastId(refreshBroadcastId)
                    .build()
            }
            EVENT_DEVICE_REBOOTED -> {
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build()
            }
            UNKNOWN -> {
                SafetyEvent.Builder(SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
            }
        }
    }

    /** Companion object for [AppDataSharingUpdatesPrivacySource]. */
    companion object {
        /** Source id for safety center source for app data sharing updates. */
        const val APP_DATA_SHARING_UPDATES_SOURCE_ID = "AndroidPrivacyAppDataSharingUpdates"
    }
}
