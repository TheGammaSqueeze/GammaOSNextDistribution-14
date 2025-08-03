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

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import android.content.pm.PackageManager
import android.os.Build
import android.provider.DeviceConfig
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.ACTION_SAFETY_CENTER_ENABLED_CHANGED
import android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_SAFETY_SOURCE_IDS
import androidx.annotation.RequiresApi
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants.UNUSED_APPS_SAFETY_CENTER_SOURCE_ID
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.service.LocationAccessCheck.BG_LOCATION_SOURCE_ID
import com.android.permissioncontroller.permission.service.v33.SafetyCenterQsTileService
import com.android.permissioncontroller.permission.service.v33.SafetyCenterQsTileService.Companion.QS_TILE_COMPONENT_SETTING_FLAGS
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.WorkPolicyInfo.Companion.WORK_POLICY_INFO_SOURCE_ID
import com.android.permissioncontroller.privacysources.v34.AppDataSharingUpdatesPrivacySource
import com.android.permissioncontroller.privacysources.v34.AppDataSharingUpdatesPrivacySource.Companion.APP_DATA_SHARING_UPDATES_SOURCE_ID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.launch

private fun createMapOfSourceIdsToSources(context: Context): Map<String, PrivacySource> {
    val sourceMap: MutableMap<String, PrivacySource> = mutableMapOf()

    if (SdkLevel.isAtLeastT()) {
        sourceMap[SC_NLS_SOURCE_ID] = NotificationListenerPrivacySource()
        sourceMap[WORK_POLICY_INFO_SOURCE_ID] = WorkPolicyInfo.create(context)
        sourceMap[SC_ACCESSIBILITY_SOURCE_ID] = AccessibilitySourceService(context)
        sourceMap[BG_LOCATION_SOURCE_ID] = LocationAccessPrivacySource()
        sourceMap[UNUSED_APPS_SAFETY_CENTER_SOURCE_ID] = AutoRevokePrivacySource()
    }

    if (SdkLevel.isAtLeastU()) {
        sourceMap[APP_DATA_SHARING_UPDATES_SOURCE_ID] = AppDataSharingUpdatesPrivacySource()
    }

    return sourceMap
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class SafetyCenterReceiver(
    private val getMapOfSourceIdsToSources: (Context) -> Map<String, PrivacySource> =
        ::createMapOfSourceIdsToSources,
    private val dispatcher: CoroutineDispatcher = Default
) : BroadcastReceiver() {

    enum class RefreshEvent {
        UNKNOWN,
        EVENT_DEVICE_REBOOTED,
        EVENT_REFRESH_REQUESTED
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!SdkLevel.isAtLeastT()) {
            return
        }
        val safetyCenterManager: SafetyCenterManager =
            Utils.getSystemServiceSafe(
                PermissionControllerApplication.get().applicationContext,
                SafetyCenterManager::class.java
            )

        val mapOfSourceIdsToSources = getMapOfSourceIdsToSources(context)

        when (intent.action) {
            ACTION_SAFETY_CENTER_ENABLED_CHANGED -> {
                safetyCenterEnabledChanged(
                    context,
                    safetyCenterManager.isSafetyCenterEnabled,
                    mapOfSourceIdsToSources.values
                )
            }
            ACTION_REFRESH_SAFETY_SOURCES -> {
                if (safetyCenterManager.isSafetyCenterEnabled) {
                    val sourceIdsExtra = intent.getStringArrayExtra(EXTRA_REFRESH_SAFETY_SOURCE_IDS)
                    if (sourceIdsExtra != null && sourceIdsExtra.isNotEmpty()) {
                        refreshSafetySources(
                            context,
                            intent,
                            RefreshEvent.EVENT_REFRESH_REQUESTED,
                            mapOfSourceIdsToSources,
                            sourceIdsExtra.toList()
                        )
                    }
                }
            }
            ACTION_BOOT_COMPLETED -> {
                updateTileVisibility(context, safetyCenterManager.isSafetyCenterEnabled)
                if (safetyCenterManager.isSafetyCenterEnabled) {
                    refreshSafetySources(
                        context,
                        intent,
                        RefreshEvent.EVENT_DEVICE_REBOOTED,
                        mapOfSourceIdsToSources,
                        mapOfSourceIdsToSources.keys.toList()
                    )
                }
            }
        }
    }

    private fun safetyCenterEnabledChanged(
        context: Context,
        enabled: Boolean,
        privacySources: Collection<PrivacySource>
    ) {
        privacySources.forEach { source ->
            CoroutineScope(dispatcher).launch {
                if (source.shouldProcessRequest(context)) {
                    source.safetyCenterEnabledChanged(context, enabled)
                }
            }
        }
        updateTileVisibility(context, enabled)
    }

    private fun updateTileVisibility(context: Context, enabled: Boolean) {
        val tileComponent = ComponentName(context, SafetyCenterQsTileService::class.java)
        val wasEnabled =
            context.packageManager?.getComponentEnabledSetting(tileComponent) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val qsTileComponentSettingFlags =
            DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_PRIVACY,
                QS_TILE_COMPONENT_SETTING_FLAGS,
                PackageManager.DONT_KILL_APP
            )
        if (enabled && !wasEnabled) {
            context.packageManager.setComponentEnabledSetting(
                tileComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                qsTileComponentSettingFlags
            )
        } else if (!enabled && wasEnabled) {
            context.packageManager.setComponentEnabledSetting(
                tileComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                qsTileComponentSettingFlags
            )
        }
    }

    private fun refreshSafetySources(
        context: Context,
        intent: Intent,
        refreshEvent: RefreshEvent,
        mapOfSourceIdsToSources: Map<String, PrivacySource>,
        sourceIdsToRefresh: List<String>
    ) {
        for (sourceId in sourceIdsToRefresh) {
            CoroutineScope(dispatcher).launch {
                val privacySource = mapOfSourceIdsToSources[sourceId] ?: return@launch
                if (privacySource.shouldProcessRequest(context)) {
                    privacySource.rescanAndPushSafetyCenterData(context, intent, refreshEvent)
                }
            }
        }
    }

    private fun PrivacySource.shouldProcessRequest(context: Context): Boolean {
        if (!isProfile(context)) {
            return true
        }
        return shouldProcessProfileRequest
    }
}
