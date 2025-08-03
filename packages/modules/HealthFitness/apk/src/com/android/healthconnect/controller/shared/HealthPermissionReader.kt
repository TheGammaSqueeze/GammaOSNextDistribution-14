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
package com.android.healthconnect.controller.shared

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.PackageManager.ResolveInfoFlags
import android.health.connect.HealthConnectManager
import android.health.connect.HealthPermissions
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.utils.FeatureUtils
import com.google.common.annotations.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Class that reads permissions declared by Health Connect clients as a string array in their XML
 * resources. See android.health.connect.HealthPermissions
 */
@Singleton
class HealthPermissionReader
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val featureUtils: FeatureUtils
) {

    companion object {
        private const val RESOLVE_INFO_FLAG: Long = PackageManager.MATCH_ALL.toLong()
        private const val PACKAGE_INFO_PERMISSIONS_FLAG: Long =
            PackageManager.GET_PERMISSIONS.toLong()
        private val sessionTypePermissions =
            listOf(
                HealthPermissions.READ_EXERCISE,
                HealthPermissions.WRITE_EXERCISE,
                HealthPermissions.READ_SLEEP,
                HealthPermissions.WRITE_SLEEP,
            )

        private val backgroundReadPermissions =
            listOf(
                // TODO (b/300270771) use the permission reference.
                "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
            )

        /** Special health permissions that don't represent health data types. */
        private val additionalPermissions =
            setOf(
                HealthPermissions.READ_EXERCISE_ROUTES,
                // TODO (b/300270771) use the permission reference.
                "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
            )
    }

    fun getAppsWithHealthPermissions(): List<String> {
        return try {
            val appsWithDeclaredIntent =
                context.packageManager
                    .queryIntentActivities(
                        getRationaleIntent(), ResolveInfoFlags.of(RESOLVE_INFO_FLAG))
                    .map { it.activityInfo.packageName }
                    .distinct()

            appsWithDeclaredIntent.filter { getDeclaredHealthPermissions(it).isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Identifies apps that have the old permissions declared - they need to update before
     * continuing to sync with Health Connect.
     */
    fun getAppsWithOldHealthPermissions(): List<String> {
        return try {
            val oldPermissionsRationale = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"
            val oldPermissionsMetaDataKey = "health_permissions"
            val intent = Intent(oldPermissionsRationale)
            val resolveInfoList =
                context.packageManager
                    .queryIntentActivities(intent, PackageManager.GET_META_DATA)
                    .filter { resolveInfo -> resolveInfo.activityInfo != null }
                    .filter { resolveInfo -> resolveInfo.activityInfo.metaData != null }
                    .filter { resolveInfo ->
                        resolveInfo.activityInfo.metaData.getInt(oldPermissionsMetaDataKey) != -1
                    }

            resolveInfoList.map { it.activityInfo.packageName }.distinct()
        } catch (e: NameNotFoundException) {
            emptyList()
        }
    }

    /** Returns a list of health permissions that can be rendered in permission list in our UI. */
    fun getDeclaredHealthPermissions(packageName: String): List<HealthPermission> {
        return try {
            val permissions = getHealthPermissions(packageName)
            permissions.mapNotNull { permission -> parsePermission(permission) }
        } catch (e: NameNotFoundException) {
            emptyList()
        }
    }

    /** Returns a list of health permissions that are declared by an app. */
    fun getHealthPermissions(packageName: String): List<String> {
        return try {
            val appInfo =
                context.packageManager.getPackageInfo(
                    packageName, PackageInfoFlags.of(PACKAGE_INFO_PERMISSIONS_FLAG))
            val healthPermissions = getHealthPermissions()
            appInfo.requestedPermissions?.filter { it in healthPermissions }.orEmpty()
        } catch (e: NameNotFoundException) {
            emptyList()
        }
    }

    fun getAdditionalPermissions(packageName: String): List<String> {
        return getHealthPermissions(packageName).filter { perm -> isAdditionalPermission(perm) }
    }

    fun isRationalIntentDeclared(packageName: String): Boolean {
        val intent = getRationaleIntent(packageName)
        val resolvedInfo =
            context.packageManager.queryIntentActivities(
                intent, ResolveInfoFlags.of(RESOLVE_INFO_FLAG))
        return resolvedInfo.any { info -> info.activityInfo.packageName == packageName }
    }

    fun getApplicationRationaleIntent(packageName: String): Intent {
        val intent = getRationaleIntent(packageName)
        val resolvedInfo =
            context.packageManager.queryIntentActivities(
                intent, ResolveInfoFlags.of(RESOLVE_INFO_FLAG))
        resolvedInfo.forEach { info -> intent.setClassName(packageName, info.activityInfo.name) }
        return intent
    }

    private fun parsePermission(permission: String): HealthPermission? {
        return try {
            HealthPermission.fromPermissionString(permission)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    @VisibleForTesting
    fun getHealthPermissions(): List<String> {
        val permissions =
            context.packageManager
                .queryPermissionsByGroup("android.permission-group.HEALTH", 0)
                .map { permissionInfo -> permissionInfo.name }
        return permissions.filterNot { permission -> shouldHidePermission(permission) }
    }

    fun isAdditionalPermission(permission: String): Boolean {
        return additionalPermissions.contains(permission)
    }

    private fun shouldHidePermission(permission: String): Boolean {
        return shouldHideSessionTypes(permission) ||
            shouldHideBackgroundReadPermission(permission) ||
            shouldHideSkinTemperaturePermissions(permission) ||
            shouldHidePlannedExercisePermissions(permission)
    }

    private fun shouldHideSkinTemperaturePermissions(permission: String): Boolean {
        return permission == HealthPermissions.READ_SKIN_TEMPERATURE ||
            permission == HealthPermissions.WRITE_SKIN_TEMPERATURE
    }

    private fun shouldHidePlannedExercisePermissions(permission: String): Boolean {
        return permission == HealthPermissions.READ_PLANNED_EXERCISE ||
            permission == HealthPermissions.WRITE_PLANNED_EXERCISE
    }

    private fun shouldHideSessionTypes(permission: String): Boolean {
        return permission in sessionTypePermissions && !featureUtils.isSessionTypesEnabled()
    }

    private fun shouldHideBackgroundReadPermission(permission: String): Boolean {
        return permission in backgroundReadPermissions && !featureUtils.isBackgroundReadEnabled()
    }

    private fun getRationaleIntent(packageName: String? = null): Intent {
        val intent =
            Intent(Intent.ACTION_VIEW_PERMISSION_USAGE).apply {
                addCategory(HealthConnectManager.CATEGORY_HEALTH_PERMISSIONS)
                if (packageName != null) {
                    setPackage(packageName)
                }
            }
        return intent
    }
}
