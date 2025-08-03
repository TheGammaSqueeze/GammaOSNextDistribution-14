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
package com.android.healthconnect.controller.permissions.data

/** Pair of {@link HealthPermission} and {@link PermissionsAccessType}. */
data class HealthPermission(
    val healthPermissionType: HealthPermissionType,
    val permissionsAccessType: PermissionsAccessType
) {
    companion object {
        private const val READ_PERMISSION_PREFIX = "android.permission.health.READ_"
        private const val WRITE_PERMISSION_PREFIX = "android.permission.health.WRITE_"

        fun fromPermissionString(permission: String): HealthPermission {
            return if (permission.startsWith(READ_PERMISSION_PREFIX)) {
                val type =
                    getHealthPermissionType(permission.substring(READ_PERMISSION_PREFIX.length))
                HealthPermission(type, PermissionsAccessType.READ)
            } else if (permission.startsWith(WRITE_PERMISSION_PREFIX)) {
                val type =
                    getHealthPermissionType(permission.substring(WRITE_PERMISSION_PREFIX.length))
                HealthPermission(type, PermissionsAccessType.WRITE)
            } else {
                throw IllegalArgumentException("Permission not supported! $permission")
            }
        }

        private fun getHealthPermissionType(value: String): HealthPermissionType {
            return HealthPermissionType.valueOf(value)
        }
    }

    override fun toString(): String {
        return if (permissionsAccessType == PermissionsAccessType.READ) {
            "$READ_PERMISSION_PREFIX${healthPermissionType.name}"
        } else {
            "$WRITE_PERMISSION_PREFIX${healthPermissionType.name}"
        }
    }
}
