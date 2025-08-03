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

package com.android.permissioncontroller.permission.util

import android.Manifest.permission.READ_CONTACTS
import android.Manifest.permission_group.ACTIVITY_RECOGNITION
import android.Manifest.permission_group.CALENDAR
import android.Manifest.permission_group.CALL_LOG
import android.Manifest.permission_group.CAMERA
import android.Manifest.permission_group.CONTACTS
import android.Manifest.permission_group.LOCATION
import android.Manifest.permission_group.MICROPHONE
import android.Manifest.permission_group.NEARBY_DEVICES
import android.Manifest.permission_group.PHONE
import android.Manifest.permission_group.READ_MEDIA_AURAL
import android.Manifest.permission_group.READ_MEDIA_VISUAL
import android.Manifest.permission_group.SENSORS
import android.Manifest.permission_group.SMS
import android.Manifest.permission_group.STORAGE
import android.Manifest.permission_group.UNDEFINED
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Resources
import androidx.test.platform.app.InstrumentationRegistry
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.privacysources.WorkPolicyInfo
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Ignore
import org.junit.Test

class UtilsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext as Context

    @Test
    fun getAbsoluteTimeString_zero_returnsNull() {
        assertThat(Utils.getAbsoluteTimeString(context, 0)).isNull()
    }

    @Test
    fun getAbsoluteTimeString_currentTime_returnsTimeFormatString() {
        val time = Utils.getAbsoluteTimeString(context, System.currentTimeMillis())
        assertThat(time).isNotNull()
        if (time != null) {
            if (time.contains(":")) {
                val times = time.split(":")
                assertThat(times.size).isEqualTo(2)
                val isTime = times[1].contains("am", true) || times[1].contains("pm", true)
                assertThat(isTime).isTrue()
            } else {
                assertThat(time.contains(".")).isTrue()
                val times = time.split(".")
                assertThat(times.size).isEqualTo(3)
            }
        }
    }

    @Test
    fun getAbsoluteTimeString_previousDateTime_returnsDateFormatString() {
        val lastAccessTime = 1680739840723L
        val time = Utils.getAbsoluteTimeString(context, lastAccessTime)
        assertThat(time == "Apr 5, 2023" || time == "Apr 6, 2023").isTrue()
    }

    @Test
    fun getBlockedIcon_invalidGroupName_returnsMinusOne() {
        assertThat(Utils.getBlockedIcon(INVALID_GROUP_NAME)).isEqualTo(-1)
    }

    @Test
    fun getBlockedIcon_validGroupName() {
        assertThat(Utils.getBlockedIcon(CAMERA)).isEqualTo(R.drawable.ic_camera_blocked)
    }

    @Test
    fun getBlockedTitle_invalidGroupName_returnsMinusOne() {
        assertThat(Utils.getBlockedTitle(INVALID_GROUP_NAME)).isEqualTo(-1)
    }
    @Test
    fun getBlockedTitle_validGroupName() {
        assertThat(Utils.getBlockedTitle(CAMERA)).isEqualTo(R.string.blocked_camera_title)
    }

    @Test
    fun getDeviceProtectedSharedPreferences() {
        assertThat(Utils.getDeviceProtectedSharedPreferences(context))
            .isInstanceOf(SharedPreferences::class.java)
    }

    @Test
    @Ignore("b/277782895")
    fun getEnterpriseString() {
        assertThat(
                Utils.getEnterpriseString(
                    context,
                    WorkPolicyInfo.WORK_POLICY_TITLE,
                    R.string.work_policy_title
                )
            )
            .isInstanceOf(String::class.java)
    }

    @Test
    fun getOneTimePermissionsTimeout_returnsNonNegativeTimeout() {
        assertThat(Utils.getOneTimePermissionsTimeout()).isGreaterThan(0L)
    }

    @Test
    fun getOneTimePermissionsKilledDelaySelfRevoked() {
        assertThat(Utils.getOneTimePermissionsKilledDelay(true)).isEqualTo(0)
    }

    @Test
    fun getOneTimePermissionsKilledDelayNonSelfRevoked() {
        assertThat(Utils.getOneTimePermissionsKilledDelay(false)).isAtLeast(0L)
    }

    @Test
    fun getPackageInfoForComponentName_NonExistComponent_throwsNameNotFoundException() {
        val testComponent = ComponentName("com.test.package", "TestClass")
        assertFailsWith<NameNotFoundException> {
            Utils.getPackageInfoForComponentName(context, testComponent)
        }
    }

    @Test
    fun getPermissionGroupDescriptionString_undefinedPermissionGroup() {
        val description = "test permission group description"
        val resultString =
            context.getString(R.string.permission_description_summary_generic, description)
        assertThat(Utils.getPermissionGroupDescriptionString(context, UNDEFINED, description))
            .isEqualTo(resultString)
    }

    @Test
    fun getPermissionGroupDescriptionString_validPermissionGroup() {
        val permissionGroupNames =
            listOf(
                ACTIVITY_RECOGNITION,
                CALENDAR,
                CALL_LOG,
                CAMERA,
                CONTACTS,
                LOCATION,
                MICROPHONE,
                NEARBY_DEVICES,
                PHONE,
                READ_MEDIA_AURAL,
                READ_MEDIA_VISUAL,
                SENSORS,
                SMS,
                STORAGE
            )
        for (permissionGroupName in permissionGroupNames) {
            assertThat(Utils.getPermissionGroupDescriptionString(context, permissionGroupName, ""))
                .isNotNull()
        }
    }

    @Test
    fun getPermissionLastAccessSummaryTimestamp_lastAccessSummaryTimestampIsNull() {
        val result = Utils.getPermissionLastAccessSummaryTimestamp(null, context, LOCATION)
        assertThat(result.first).isEqualTo("")
        assertThat(result.second).isEqualTo(Utils.NOT_IN_LAST_7D)
        assertThat(result.third).isEqualTo("")
    }

    @Test
    fun getPermissionLastAccessSummaryTimestamp_sensorDataPermission_lastAccessSummaryTimestampIsToday() {
        val result =
            Utils.getPermissionLastAccessSummaryTimestamp(
                System.currentTimeMillis(),
                context,
                LOCATION
            )
        assertThat(result.first).isNotEmpty()
        assertThat(result.second).isEqualTo(Utils.LAST_24H_SENSOR_TODAY)
        assertThat(result.third).isNotEmpty()
    }

    @Test
    fun getPermissionLastAccessSummaryTimestamp_sensorDataPermission_lastAccessSummaryTimestampIsYesterday() {
        val result =
            Utils.getPermissionLastAccessSummaryTimestamp(
                System.currentTimeMillis() - 24 * 60 * 60 * 1000,
                context,
                LOCATION
            )
        assertThat(result.first).isNotEmpty()
        assertThat(result.second).isEqualTo(Utils.LAST_24H_SENSOR_YESTERDAY)
        assertThat(result.third).isNotEmpty()
    }

    @Test
    fun getPermissionLastAccessSummaryTimestamp_sensorDataPermission_lastAccessSummaryTimestampIsLast7Days() {
        val result =
            Utils.getPermissionLastAccessSummaryTimestamp(
                System.currentTimeMillis() - 5 * 24 * 60 * 60 * 1000,
                context,
                LOCATION
            )
        assertThat(result.first).isNotEmpty()
        assertThat(result.second).isEqualTo(Utils.LAST_7D_SENSOR)
        assertThat(result.third).isNotEmpty()
    }

    @Test
    fun getPermissionLastAccessSummaryTimestamp_nonSensorDataPermission_lastAccessSummaryTimestampIsLast24Hrs() {
        val result =
            Utils.getPermissionLastAccessSummaryTimestamp(
                System.currentTimeMillis(),
                context,
                STORAGE
            )
        assertThat(result.first).isNotEmpty()
        assertThat(result.second).isEqualTo(Utils.LAST_24H_CONTENT_PROVIDER)
        assertThat(result.third).isNotEmpty()
    }

    @Test
    fun getPermissionLastAccessSummaryTimestamp_nonSensorDataPermission_lastAccessSummaryTimestampIs7Days() {
        val result =
            Utils.getPermissionLastAccessSummaryTimestamp(
                System.currentTimeMillis() - 5 * 60 * 60 * 24 * 1000,
                context,
                STORAGE
            )
        assertThat(result.first).isNotEmpty()
        assertThat(result.second).isEqualTo(Utils.LAST_7D_CONTENT_PROVIDER)
        assertThat(result.third).isNotEmpty()
    }

    @Test
    fun getOrGenerateSessionId_validSessionId() {
        val intent = Intent()
        intent.putExtra(EXTRA_SESSION_ID, VALID_SESSION_ID)
        val sessionId = Utils.getOrGenerateSessionId(intent)
        assertThat(sessionId).isEqualTo(VALID_SESSION_ID)
    }

    @Test
    fun getOrGenerateSessionId_invalidSessionId() {
        val intent = Intent()
        val sessionId = Utils.getOrGenerateSessionId(intent)
        assertThat(sessionId).isNotEqualTo(INVALID_SESSION_ID)
    }

    @Test
    fun getGroupPermissionInfos_validGroupName_returnsGroupPermissions() {
        val permissionInfos = Utils.getGroupPermissionInfos(CONTACTS, context)
        assertThat(permissionInfos).isNotNull()
        val permissions = mutableListOf<String>()
        for (permissionInfo in permissionInfos!!) {
            permissions.add(permissionInfo.name)
        }
        assertThat(permissions.contains(READ_CONTACTS)).isTrue()
    }

    @Test
    fun getGroupPermissionInfos_inValidGroup_returnsNull() {
        assertThat(Utils.getGroupPermissionInfos(INVALID_GROUP_NAME, context)).isNull()
    }

    @Test
    fun getGroupPermissionInfos_undefinedGroup_returnsAllSystemPermissions() {
        val permissionInfos = Utils.getGroupPermissionInfos(UNDEFINED, context)
        assertThat(permissionInfos).isNotNull()
    }

    @Test
    fun getGroupPermissionInfo_permissionName_returnsSamePermission() {
        val permissionInfos = Utils.getGroupPermissionInfos(READ_CONTACTS, context)
        assertThat(permissionInfos).isNotNull()
        assertThat(permissionInfos!!.size).isEqualTo(1)
        assertThat(permissionInfos[0].name).isEqualTo(READ_CONTACTS)
    }

    @Test
    fun getColorResId_validId_returnsNonZero() {
        assertThat(Utils.getColorResId(context, android.R.attr.colorPrimary))
            .isNotEqualTo(Resources.ID_NULL)
    }

    @Test
    fun getColorResId_inValidId_returnsZero() {
        assertThat(Utils.getColorResId(context, INVALID_ATTR_ID)).isEqualTo(Resources.ID_NULL)
    }

    companion object {
        private const val INVALID_ATTR_ID = 1000
        private const val VALID_SESSION_ID = 10000L
        private const val INVALID_GROUP_NAME = "invalid group name"
    }
}
