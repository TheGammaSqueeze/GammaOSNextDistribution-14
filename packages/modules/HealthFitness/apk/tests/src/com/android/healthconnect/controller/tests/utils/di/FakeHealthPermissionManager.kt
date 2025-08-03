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

package com.android.healthconnect.controller.tests.utils.di

import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.tests.utils.NOW
import java.time.Instant

class FakeHealthPermissionManager : HealthPermissionManager {

    private val grantedPermissions = mutableMapOf<String, MutableList<String>>()

    fun setGrantedPermissionsForTest(packageName: String, permissions: List<String>) {
        grantedPermissions[packageName] = permissions.toMutableList()
    }

    override fun getGrantedHealthPermissions(packageName: String): List<String> {
        return grantedPermissions.getOrDefault(packageName, emptyList())
    }

    override fun getHealthPermissionsFlags(
        packageName: String,
        permissions: List<String>
    ): Map<String, Int> {
        return emptyMap()
    }

    override fun setHealthPermissionsUserFixedFlagValue(
        packageName: String,
        permissions: List<String>,
        value: Boolean
    ) {
        // do nothing
    }

    override fun grantHealthPermission(packageName: String, permissionName: String) {
        val permissions = grantedPermissions.getOrDefault(packageName, mutableListOf())
        permissions.add(permissionName)
        grantedPermissions[packageName] = permissions
    }

    override fun revokeHealthPermission(packageName: String, permissionName: String) {
        val permissions = grantedPermissions.getOrDefault(packageName, mutableListOf())
        permissions.remove(permissionName)
        grantedPermissions[packageName] = permissions
    }

    override fun revokeAllHealthPermissions(packageName: String) {
        grantedPermissions[packageName] = mutableListOf()
    }

    override fun loadStartAccessDate(packageName: String?): Instant? {
        return NOW
    }
}
