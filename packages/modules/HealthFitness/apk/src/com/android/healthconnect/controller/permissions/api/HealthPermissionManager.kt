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

import java.time.Instant

/** Wrapper for HealthConnectManager permission apis. */
interface HealthPermissionManager {
    fun getGrantedHealthPermissions(packageName: String): List<String>

    fun getHealthPermissionsFlags(packageName: String, permissions: List<String>): Map<String, Int>

    fun setHealthPermissionsUserFixedFlagValue(
        packageName: String,
        permissions: List<String>,
        value: Boolean
    )

    fun grantHealthPermission(packageName: String, permissionName: String)

    fun revokeHealthPermission(packageName: String, permissionName: String)

    fun revokeAllHealthPermissions(packageName: String)

    fun loadStartAccessDate(packageName: String?): Instant?
}
