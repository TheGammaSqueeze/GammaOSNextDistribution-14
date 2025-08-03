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

package com.android.healthconnect.controller.permissions.api

import android.health.connect.HealthConnectManager
import java.time.Instant
import javax.inject.Inject

class HealthPermissionManagerImpl @Inject constructor(private val manager: HealthConnectManager) :
    HealthPermissionManager {
    override fun getGrantedHealthPermissions(packageName: String): List<String> {
        return manager.getGrantedHealthPermissions(packageName)
    }

    override fun getHealthPermissionsFlags(
        packageName: String,
        permissions: List<String>
    ): Map<String, Int> {
        return manager.getHealthPermissionsFlags(packageName, permissions)
    }

    override fun setHealthPermissionsUserFixedFlagValue(
        packageName: String,
        permissions: List<String>,
        value: Boolean
    ) {
        manager.setHealthPermissionsUserFixedFlagValue(packageName, permissions, value)
    }

    override fun grantHealthPermission(packageName: String, permissionName: String) {
        manager.grantHealthPermission(packageName, permissionName)
    }

    override fun revokeHealthPermission(packageName: String, permissionName: String) {
        manager.revokeHealthPermission(packageName, permissionName, /* reason= */ "")
    }

    override fun revokeAllHealthPermissions(packageName: String) {
        manager.revokeAllHealthPermissions(packageName, /* reason= */ "")
    }

    override fun loadStartAccessDate(packageName: String?): Instant? {
        return packageName?.let { manager.getHealthDataHistoricalAccessStartDate(it) }
    }
}
