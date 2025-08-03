/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.permissions.data

import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.ACTIVE_CALORIES_BURNED
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.BLOOD_GLUCOSE
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.tests.permissions.HealthPermissionConstants
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HealthPermissionTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun fromPermission_returnsCorrectReadHealthPermission() {
        assertThat(fromPermissionString("android.permission.health.READ_ACTIVE_CALORIES_BURNED"))
            .isEqualTo(HealthPermission(ACTIVE_CALORIES_BURNED, PermissionsAccessType.READ))
    }

    @Test
    fun fromPermission_returnsCorrectWriteHealthPermission() {
        assertThat(fromPermissionString("android.permission.health.WRITE_BLOOD_GLUCOSE"))
            .isEqualTo(HealthPermission(BLOOD_GLUCOSE, PermissionsAccessType.WRITE))
    }

    @Test
    fun fromPermissionString_canParseAllHealthPermissions() {
        val allPermissions =
            healthPermissionReader.getHealthPermissions().filterNot { perm ->
                healthPermissionReader.isAdditionalPermission(perm)
            }
        for (permissionString in allPermissions) {
            if (permissionString == HealthPermissionConstants.READ_HEALTH_DATA_HISTORY) {
                // TODO(b/325434006): Remove this exception case when we have strings properly
                //  defined for the Background Read permission
                continue
            }

            assertThat(fromPermissionString(permissionString).toString())
                .isEqualTo(permissionString)
        }
    }

    @Test
    fun fromPermissionString_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            fromPermissionString("Unsupported_permission")
        }
    }
}
