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

package com.android.permissioncontroller.permission.ui.model.v31

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__CAMERA_ACCESS_TIMELINE_VIEWED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__LOCATION_ACCESS_TIMELINE_VIEWED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__MICROPHONE_ACCESS_TIMELINE_VIEWED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.StringUtils

@RequiresApi(Build.VERSION_CODES.S)
object PermissionUsageControlPreferenceUtils {

    private val SENSOR_DATA_PERMISSIONS: List<String> =
        listOf(
            Manifest.permission_group.LOCATION,
            Manifest.permission_group.CAMERA,
            Manifest.permission_group.MICROPHONE,
            Manifest.permission_group.SENSORS,
            Manifest.permission_group.CALENDAR,
            Manifest.permission_group.CALL_LOG,
            Manifest.permission_group.CONTACTS,
            Manifest.permission_group.STORAGE,
            Manifest.permission_group.NEARBY_DEVICES,
            Manifest.permission_group.PHONE,
            Manifest.permission_group.ACTIVITY_RECOGNITION,
            Manifest.permission_group.SMS
        )

    @JvmStatic
    fun initPreference(
        preference: Preference,
        context: Context,
        groupName: String,
        count: Int,
        showSystem: Boolean,
        sessionId: Long,
        show7Days: Boolean
    ): Preference {
        val permGroupLabel = KotlinUtils.getPermGroupLabel(context, groupName)
        return preference.apply {
            title = permGroupLabel
            icon = KotlinUtils.getPermGroupIcon(context, groupName)
            summary =
                StringUtils.getIcuPluralsString(
                    context,
                    R.string.permission_usage_preference_label,
                    count
                )
            if (count == 0) {
                isEnabled = false
                val permissionUsageSummaryNotUsed =
                    if (show7Days) {
                        StringUtils.getIcuPluralsString(
                            context,
                            R.string.permission_usage_preference_summary_not_used_in_past_n_days,
                            7
                        )
                    } else {
                        StringUtils.getIcuPluralsString(
                            context,
                            R.string.permission_usage_preference_summary_not_used_in_past_n_hours,
                            24
                        )
                    }
                setSummary(permissionUsageSummaryNotUsed)
            } else if (SENSOR_DATA_PERMISSIONS.contains(groupName)) {
                onPreferenceClickListener = OnPreferenceClickListener {
                    val intent = Intent(Intent.ACTION_REVIEW_PERMISSION_HISTORY)
                    intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName)
                    intent.putExtra(ManagePermissionsActivity.EXTRA_SHOW_SYSTEM, showSystem)
                    intent.putExtra(ManagePermissionsActivity.EXTRA_SHOW_7_DAYS, show7Days)
                    logSensorDataTimelineViewed(groupName, sessionId)
                    context.startActivity(intent)
                    true
                }
            } else {
                onPreferenceClickListener = OnPreferenceClickListener {
                    val intent = Intent(Intent.ACTION_MANAGE_PERMISSION_APPS)
                    intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName)
                    context.startActivity(intent)
                    true
                }
            }
        }
    }

    private fun logSensorDataTimelineViewed(groupName: String, sessionId: Long) {
        val act =
            when (groupName) {
                Manifest.permission_group.LOCATION -> {
                    PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__LOCATION_ACCESS_TIMELINE_VIEWED
                }
                Manifest.permission_group.CAMERA -> {
                    PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__CAMERA_ACCESS_TIMELINE_VIEWED
                }
                Manifest.permission_group.MICROPHONE -> {
                    PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__MICROPHONE_ACCESS_TIMELINE_VIEWED
                }
                else -> 0
            }
        if (act == 0) return
        PermissionControllerStatsLog.write(PERMISSION_USAGE_FRAGMENT_INTERACTION, sessionId, act)
    }
}
