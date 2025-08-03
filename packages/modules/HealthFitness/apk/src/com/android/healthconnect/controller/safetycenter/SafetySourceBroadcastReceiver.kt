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

package com.android.healthconnect.controller.safetycenter

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.pm.PackageManager
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCE_IDS
import android.safetycenter.SafetyEvent
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_DEVICE_REBOOTED
import android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_REFRESH_REQUESTED
import com.android.healthconnect.controller.safetycenter.HealthConnectSafetySource.Companion.HEALTH_CONNECT_SOURCE_ID
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(BroadcastReceiver::class)
class SafetySourceBroadcastReceiver : Hilt_SafetySourceBroadcastReceiver() {

    @Inject lateinit var safetySource: HealthConnectSafetySource
    @Inject lateinit var safetyCenterManagerWrapper: SafetyCenterManagerWrapper

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        tryEnableLegacySettingsEntryPoint(context)
        if (!safetyCenterManagerWrapper.isEnabled(context)) {
            return
        }
        when (intent.action) {
            ACTION_BOOT_COMPLETED -> refreshAllSafetySources(context)
            ACTION_REFRESH_SAFETY_SOURCES -> {
                val sourceIdsExtra = intent.getStringArrayExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS)
                val refreshBroadcastId =
                    intent.getStringExtra(
                        SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCES_BROADCAST_ID)
                if (sourceIdsExtra != null &&
                    sourceIdsExtra.isNotEmpty() &&
                    refreshBroadcastId != null) {
                    val safetyEvent =
                        SafetyEvent.Builder(SAFETY_EVENT_TYPE_REFRESH_REQUESTED)
                            .setRefreshBroadcastId(refreshBroadcastId)
                            .build()
                    refreshSafetySources(context, sourceIdsExtra.toList(), safetyEvent)
                }
                return
            }
            else -> return
        }
    }
    
    private fun tryEnableLegacySettingsEntryPoint(context: Context) {
        val legacySettingsEntryPointComponent =
            ComponentName(context.packageName, LEGACY_SETTINGS_ACTIVITY_ALIAS)

        val componentState =
            if (shouldEnableLegacySettingsEntryPoint(context)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

        context.packageManager.setComponentEnabledSetting(
            legacySettingsEntryPointComponent, componentState, 0)
    }

    private fun shouldEnableLegacySettingsEntryPoint(context: Context): Boolean {
        return !safetyCenterManagerWrapper.isEnabled(context) && isHardwareSupported(context)
    }

    private fun isHardwareSupported(context: Context): Boolean {
        // These UI tests are not optimised for Watches, TVs, Auto;
        // IoT devices do not have a UI to run these UI tests
        val pm: PackageManager = context.packageManager
        return (!pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED) &&
                !pm.hasSystemFeature(PackageManager.FEATURE_WATCH) &&
                !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
                !pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
    }


    private fun refreshSafetySources(
        context: Context,
        sourceIds: List<String>,
        safetyEvent: SafetyEvent
    ) {
        if (HEALTH_CONNECT_SOURCE_ID in sourceIds) {
            safetySource.setSafetySourceData(context, safetyEvent)
        }
    }

    private fun refreshAllSafetySources(context: Context) {
        safetySource.setSafetySourceData(
            context, SafetyEvent.Builder(SAFETY_EVENT_TYPE_DEVICE_REBOOTED).build())
    }

    companion object {
        private const val LEGACY_SETTINGS_ACTIVITY_ALIAS =
            "com.android.healthconnect.controller.LegacySettingsEntryPoint"
    }
}
