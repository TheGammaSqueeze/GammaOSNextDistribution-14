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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserHandle
import android.provider.Settings
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceStatus
import androidx.annotation.RequiresApi
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.SafetyCenterReceiver.RefreshEvent
import com.android.settingslib.utils.WorkPolicyUtils

/**
 * Work Policy Info for managed devices to show the settings managed by their Organisation's IT
 * Admin. It is a Privacy Source, and it receives broadcasts from SafetyCenter using
 * SafetyCenterReceiver.kt
 *
 * safetyCenterEnabledChanged and rescanAndPushSafetyCenterData methods checks if the device is
 * managed and shows the Work Policy Info by pushing the data in SafetyCenter
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WorkPolicyInfo(private val workPolicyUtils: WorkPolicyUtils) : PrivacySource {

    companion object {
        const val WORK_POLICY_INFO_SOURCE_ID = "AndroidWorkPolicyInfo"
        const val WORK_POLICY_TITLE = "SafetyCenter.WORK_POLICY_TITLE"
        const val WORK_POLICY_SUMMARY = "SafetyCenter.WORK_POLICY_SUMMARY"
        fun create(context: Context): WorkPolicyInfo {
            val workPolicyUtils = WorkPolicyUtils(context)
            return WorkPolicyInfo(workPolicyUtils)
        }
    }

    override val shouldProcessProfileRequest: Boolean = false

    override fun safetyCenterEnabledChanged(context: Context, enabled: Boolean) {
        val intent = Intent(Settings.ACTION_SHOW_WORK_POLICY_INFO)
        val refreshEvent: RefreshEvent = RefreshEvent.UNKNOWN
        if (enabled) {
            rescanAndPushSafetyCenterData(context, intent, refreshEvent)
        }
    }

    override fun rescanAndPushSafetyCenterData(
        context: Context,
        intent: Intent,
        refreshEvent: RefreshEvent
    ) {
        val safetyCenterManager: SafetyCenterManager =
            Utils.getSystemServiceSafe(context, SafetyCenterManager::class.java)
        val safetyEvent: SafetyEvent = createSafetyEventForWorkPolicy(refreshEvent, intent)
        val safetySourceData: SafetySourceData? = createSafetySourceDataForWorkPolicy(context)

        safetyCenterManager.setSafetySourceData(
            WORK_POLICY_INFO_SOURCE_ID,
            safetySourceData,
            safetyEvent
        )
    }

    private fun createSafetySourceDataForWorkPolicy(context: Context): SafetySourceData? {
        val deviceOwnerIntent = workPolicyUtils.workPolicyInfoIntentDO
        val profileOwnerIntent = workPolicyUtils.workPolicyInfoIntentPO
        val pendingIntent =
            when {
                deviceOwnerIntent != null -> {
                    PendingIntent.getActivity(
                        context,
                        0,
                        deviceOwnerIntent,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                }
                profileOwnerIntent != null -> {
                    val managedProfileContext =
                        context.createPackageContextAsUser(
                            context.packageName,
                            0,
                            UserHandle.of(workPolicyUtils.managedProfileUserId)
                        )
                    PendingIntent.getActivity(
                        managedProfileContext,
                        0,
                        profileOwnerIntent,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                }
                else -> null
            }
                ?: return null

        val safetySourceStatus: SafetySourceStatus =
            SafetySourceStatus.Builder(
                    Utils.getEnterpriseString(
                        context,
                        WORK_POLICY_TITLE,
                        R.string.work_policy_title
                    ),
                    Utils.getEnterpriseString(
                        context,
                        WORK_POLICY_SUMMARY,
                        R.string.work_policy_summary
                    ),
                    SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED
                )
                .setPendingIntent(pendingIntent)
                .build()

        return SafetySourceData.Builder().setStatus(safetySourceStatus).build()
    }

    private fun createSafetyEventForWorkPolicy(
        refreshEvent: RefreshEvent,
        intent: Intent
    ): SafetyEvent {
        return when (refreshEvent) {
            RefreshEvent.EVENT_REFRESH_REQUESTED -> {
                val refreshBroadcastId =
                    intent.getStringExtra(
                        SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID
                    )
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                    .setRefreshBroadcastId(refreshBroadcastId)
                    .build()
            }
            RefreshEvent.EVENT_DEVICE_REBOOTED -> {
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build()
            }
            RefreshEvent.UNKNOWN -> {
                SafetyEvent.Builder(SafetyEvent.SAFETY_EVENT_TYPE_SOURCE_STATE_CHANGED).build()
            }
        }
    }
}
