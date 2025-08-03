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

import android.Manifest
import android.app.AppOpsManager
import android.health.connect.HealthPermissions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionMappingTest {
    @Test
    fun testGetPlatformPermissionGroupForOp_healthPermissionGroup() {
        assertThat(
                PermissionMapping.getPlatformPermissionGroupForOp(
                    AppOpsManager.OPSTR_READ_WRITE_HEALTH_DATA
                )
            )
            .isEqualTo(HealthPermissions.HEALTH_PERMISSION_GROUP)
    }

    @Test
    fun testGetPlatformPermissionGroupForOp_microphone() {
        assertThat(
                PermissionMapping.getPlatformPermissionGroupForOp(
                    AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE
                )
            )
            .isEqualTo(Manifest.permission_group.MICROPHONE)
    }

    @Test
    fun testGetPlatformPermissionGroupForOp_camera() {
        assertThat(
                PermissionMapping.getPlatformPermissionGroupForOp(
                    AppOpsManager.OPSTR_PHONE_CALL_CAMERA
                )
            )
            .isEqualTo(Manifest.permission_group.CAMERA)
    }

    @Test
    fun testGetPlatformPermissionGroupForOp_readContacts() {
        assertThat(
                PermissionMapping.getPlatformPermissionGroupForOp(AppOpsManager.OPSTR_READ_CONTACTS)
            )
            .isEqualTo(
                PermissionMapping.getGroupOfPlatformPermission(Manifest.permission.READ_CONTACTS)
            )
    }
}
