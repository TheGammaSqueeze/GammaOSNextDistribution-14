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
@file:Suppress("DEPRECATION")

package com.android.permissioncontroller.permission.ui.legacy

import android.Manifest
import android.app.LoaderManager
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.permission.model.AppPermissionGroup
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.PermissionApp
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage
import com.android.permissioncontroller.permission.model.v31.PermissionUsages
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupLabel
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.Utils
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** [ViewModel] for Permission Usage fragments. */
@RequiresApi(Build.VERSION_CODES.S)
class PermissionUsageViewModelLegacy(val roleManager: RoleManager) : ViewModel() {

    private val exemptedPackages: Set<String> = Utils.getExemptedPackages(roleManager)

    /** Companion object for [PermissionUsageViewModelLegacy]. */
    companion object {
        /** TODO(ewol): Use the config setting to determine amount of time to show. */
        private val TIME_FILTER_MILLIS = TimeUnit.DAYS.toMillis(7)
        private val TIME_7_DAYS_DURATION = TimeUnit.DAYS.toMillis(7)
        private val TIME_24_HOURS_DURATION = TimeUnit.DAYS.toMillis(1)
        /** Permission groups that should be hidden from the permissions usage UI. */
        private val EXEMPTED_PERMISSION_GROUPS = setOf(Manifest.permission_group.NOTIFICATIONS)
        @JvmStatic
        /** Map to represent ordering for permission groups in the permissions usage UI. */
        val PERMISSION_GROUP_ORDER: Map<String, Int> =
            mapOf(
                Manifest.permission_group.LOCATION to 0,
                Manifest.permission_group.CAMERA to 1,
                Manifest.permission_group.MICROPHONE to 2
            )
        private const val DEFAULT_ORDER = 3
    }

    /** Loads data from [PermissionUsages] using the [LoaderManager] pattern. */
    fun loadPermissionUsages(
        loaderManager: LoaderManager,
        permissionUsages: PermissionUsages,
        callback: PermissionUsages.PermissionsUsagesChangeCallback
    ) {
        val filterTimeBeginMillis =
            max(System.currentTimeMillis() - TIME_FILTER_MILLIS, Instant.EPOCH.toEpochMilli())
        permissionUsages.load(
            null /*filterPackageName*/,
            null /*filterPermissionGroups*/,
            filterTimeBeginMillis,
            Long.MAX_VALUE,
            PermissionUsages.USAGE_FLAG_LAST or PermissionUsages.USAGE_FLAG_HISTORICAL,
            loaderManager,
            false /*getUiInfo*/,
            false /*getNonPlatformPermissions*/,
            callback /*callback*/,
            false /*sync*/
        )
    }

    /**
     * Parses the provided list of [AppPermissionUsage] instances to build data for the UI to
     * display.
     */
    fun buildPermissionUsagesUiData(
        appPermissionUsages: List<AppPermissionUsage>,
        show7Days: Boolean,
        showSystem: Boolean,
        context: Context,
    ): PermissionUsagesUiData {
        val curTime = System.currentTimeMillis()
        val showPermissionUsagesDuration =
            if (KotlinUtils.is7DayToggleEnabled() && show7Days) {
                TIME_7_DAYS_DURATION
            } else {
                TIME_24_HOURS_DURATION
            }
        val startTime = max(curTime - showPermissionUsagesDuration, Instant.EPOCH.toEpochMilli())

        val filteredAppPermissionUsages =
            appPermissionUsages.filter { !exemptedPackages.contains(it.packageName) }
        val displayShowSystemToggle: Boolean =
            filteredAppPermissionUsages.displayShowSystemToggle(startTime)
        val permissionApps = filteredAppPermissionUsages.getRecentPermissionApps(startTime)
        val orderedPermissionGroupsWithUsage =
            filteredAppPermissionUsages.buildOrderedPermissionGroupsWithUsageCount(
                context,
                startTime,
                showSystem
            )

        return PermissionUsagesUiData(
            permissionApps,
            displayShowSystemToggle,
            orderedPermissionGroupsWithUsage
        )
    }

    /**
     * Creates an ordered list of [PermissionGroupWithUsageCount] instances to show in the UI,
     * representing a mapping of permission groups to the number of apps that recently accessed
     * them.
     *
     * The list is ordered as follows:
     * 1. Location
     * 2. Camera
     * 3. Microphone
     * 4. Remaining permission groups, ordered alphabetically
     */
    private fun List<AppPermissionUsage>.buildOrderedPermissionGroupsWithUsageCount(
        context: Context,
        startTime: Long,
        showSystem: Boolean
    ): List<PermissionGroupWithUsageCount> {
        val permissionGroupsUsageCountMap: MutableMap<String, Int> = HashMap()
        extractPlatformAppPermissionGroupsToDisplay().forEach {
            permissionGroupsUsageCountMap[it] = 0
        }

        for (appUsage in this) {
            appUsage.groupUsages
                .filter { showSystem || !it.group.isSystem() }
                .filter { !EXEMPTED_PERMISSION_GROUPS.contains(it.group.name) }
                .filter { it.lastAccessTime >= startTime }
                .forEach {
                    permissionGroupsUsageCountMap[it.group.name] =
                        permissionGroupsUsageCountMap.getOrDefault(it.group.name, 0) + 1
                }
        }
        return permissionGroupsUsageCountMap.entries
            .map { PermissionGroupWithUsageCount(it.key, it.value) }
            .sortedWith(
                compareBy(
                    { PERMISSION_GROUP_ORDER.getOrDefault(it.permGroup, DEFAULT_ORDER) },
                    { getPermGroupLabel(context, it.permGroup).toString() }
                )
            )
    }

    /** Extracts [PermissionApp] where there has been recent permission usage. */
    private fun List<AppPermissionUsage>.getRecentPermissionApps(
        startTime: Long,
    ): java.util.ArrayList<PermissionApp> {
        return ArrayList(
            filter { appPermissionUsage ->
                    appPermissionUsage.groupUsages
                        .filter { !EXEMPTED_PERMISSION_GROUPS.contains(it.group.name) }
                        .any { it.lastAccessTime >= startTime || it.lastAccessTime == 0L }
                }
                .map { it.app }
        )
    }

    /**
     * Returns whether there are any user-sensitive app permission groups with recent usage, and
     * therefore if the "show/hide system" toggle needs to be displayed in the UI
     */
    private fun List<AppPermissionUsage>.displayShowSystemToggle(
        startTime: Long,
    ): Boolean {
        return flatMap { it.groupUsages }
            .filter { !EXEMPTED_PERMISSION_GROUPS.contains(it.group.name) }
            .filter { it.lastAccessTime > startTime && it.lastAccessTime > 0L }
            .any { it.group.isSystem() }
    }

    /**
     * Extracts to a set all the permission groups declared by the platform that should be displayed
     * in the UI.
     */
    private fun List<AppPermissionUsage>.extractPlatformAppPermissionGroupsToDisplay():
        Set<String> =
        this.flatMap { it.groupUsages }
            .map { it.group.name }
            .filter { PermissionMapping.isPlatformPermissionGroup(it) }
            .filter { !EXEMPTED_PERMISSION_GROUPS.contains(it) }
            .toSet()

    /**
     * Returns whether the [AppPermissionGroup] is considered a system group.
     *
     * For the purpose of Permissions Hub UI, non user-sensitive [AppPermissionGroup]s are
     * considered "system" and should be hidden from the main page unless requested by the user
     * through the "show/hide system" toggle.
     */
    private fun AppPermissionGroup.isSystem() = !Utils.isGroupOrBgGroupUserSensitive(this)

    /** Data class to hold all the information required to configure the UI. */
    data class PermissionUsagesUiData(
        /** List of [PermissionApp] instances */
        // Note that these are used only to cache app data for the permission usage details
        // fragment, and have no bearing on the UI on the main permission usage page.
        val permissionApps: ArrayList<PermissionApp>,
        /** Whether to show the "show/hide system" toggle. */
        val displayShowSystemToggle: Boolean,
        // TODO(b/243970988): Consider moving ordering logic to fragment.
        /** [PermissionGroupWithUsageCount] instances ordered for display in UI */
        val orderedPermissionGroupsWithUsageCount: List<PermissionGroupWithUsageCount>,
    )

    /**
     * Data class to associate permission groups with the number of apps that recently accessed
     * them.
     */
    data class PermissionGroupWithUsageCount(val permGroup: String, val appCount: Int)
}

/** Factory for [PermissionUsageViewModelLegacy]. */
@RequiresApi(Build.VERSION_CODES.S)
class PermissionUsageViewModelFactoryLegacy(private val roleManager: RoleManager) :
    ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return PermissionUsageViewModelLegacy(roleManager) as T
    }
}
