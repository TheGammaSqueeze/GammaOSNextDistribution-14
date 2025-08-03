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
import android.app.Application
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.android.permissioncontroller.permission.data.AppPermGroupUiInfoLiveData
import com.android.permissioncontroller.permission.data.LightPackageInfoLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.StandardPermGroupNamesLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.v31.AllLightPackageOpsLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.v31.AppPermissionId
import com.android.permissioncontroller.permission.model.livedatatypes.v31.LightPackageOps
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.Companion.SHOULD_SHOW_SYSTEM_KEY
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageViewModel.Companion.SHOULD_SHOW_7_DAYS_KEY
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.Utils
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * [ViewModel] for handheld Permissions Usage UI.
 *
 * Note that this class replaces [PermissionUsageViewModelLegacy] to rely on [LiveData] instead of
 * [PermissionUsages] loader.
 */
class PermissionUsageViewModel(
    private val state: SavedStateHandle,
    app: Application,
) : AndroidViewModel(app) {
    private val userManager =
        Utils.getSystemServiceSafe(app.applicationContext, UserManager::class.java)
    private val roleManager =
        Utils.getSystemServiceSafe(app.applicationContext, RoleManager::class.java)
    private val exemptedPackages: Set<String> = Utils.getExemptedPackages(roleManager)

    private val mAllLightPackageOpsLiveData = AllLightPackageOpsLiveData(app)
    private val appPermGroupUiInfoLiveDataList =
        mutableMapOf<AppPermissionId, AppPermGroupUiInfoLiveData>()
    private val lightPackageInfoLiveDataMap =
        mutableMapOf<Pair<String, UserHandle>, LightPackageInfoLiveData>()
    private val standardPermGroupNamesLiveData = StandardPermGroupNamesLiveData

    val showSystemAppsLiveData = state.getLiveData(SHOULD_SHOW_SYSTEM_KEY, false)
    val show7DaysLiveData = state.getLiveData(SHOULD_SHOW_7_DAYS_KEY, false)

    /** Updates whether system app permissions usage should be displayed in the UI. */
    fun updateShowSystem(showSystem: Boolean) {
        if (showSystem != state[SHOULD_SHOW_SYSTEM_KEY]) {
            state[SHOULD_SHOW_SYSTEM_KEY] = showSystem
        }
    }

    /** Updates whether 7 days usage or 1 day usage should be displayed in the UI. */
    fun updateShow7Days(show7Days: Boolean) {
        if (show7Days != state[SHOULD_SHOW_7_DAYS_KEY]) {
            state[SHOULD_SHOW_7_DAYS_KEY] = show7Days
        }
    }

    /** Builds a [PermissionUsagesUiData] containing all the data necessary to render the UI. */
    private fun buildPermissionUsagesUiData(): PermissionUsagesUiData {
        val curTime = System.currentTimeMillis()
        val showSystem: Boolean = state[SHOULD_SHOW_SYSTEM_KEY] ?: false
        val show7Days: Boolean = state[SHOULD_SHOW_7_DAYS_KEY] ?: false
        val showPermissionUsagesDuration =
            if (KotlinUtils.is7DayToggleEnabled() && show7Days) {
                TIME_7_DAYS_DURATION
            } else {
                TIME_24_HOURS_DURATION
            }
        val startTime = max(curTime - showPermissionUsagesDuration, Instant.EPOCH.toEpochMilli())
        return PermissionUsagesUiData(
            showSystem,
            show7Days,
            mAllLightPackageOpsLiveData.containsSystemAppUsages(startTime),
            mAllLightPackageOpsLiveData.buildPermissionGroupsWithUsageCounts(startTime, showSystem)
        )
    }

    /** Builds a map of permission groups to the number of apps that recently accessed them. */
    private fun AllLightPackageOpsLiveData.buildPermissionGroupsWithUsageCounts(
        startTime: Long,
        showSystem: Boolean,
    ): Map<String, Int> {
        val permissionUsageCountMap: MutableMap<String, Int> = HashMap()
        for (permissionGroup: String in getAllEligiblePermissionGroups()) {
            permissionUsageCountMap[permissionGroup] = 0
        }

        val eligibleLightPackageOpsList: List<LightPackageOps> =
            getAllLightPackageOps()?.filterOutExemptedApps()?.filter {
                Utils.shouldShowInSettings(it.userHandle, userManager)
            }
                ?: listOf()

        for (lightPackageOps: LightPackageOps in eligibleLightPackageOpsList) {
            val permGroupsToLastAccess: List<Map.Entry<String, Long>> =
                lightPackageOps.lastPermissionGroupAccessTimesMs.entries
                    .filterOutExemptedPermissionGroupsFromKeys()
                    .filterOutPermissionsNotRequestedByApp(
                        lightPackageOps.packageName,
                        lightPackageOps.userHandle
                    )
                    .filterOutSystemAppPermissionsIfNecessary(
                        showSystem,
                        lightPackageOps.packageName,
                        lightPackageOps.userHandle
                    )
                    .filterAccessTimeLaterThan(startTime)
            val recentlyUsedPermissions: List<String> = permGroupsToLastAccess.map { it.key }

            for (permissionGroup: String in recentlyUsedPermissions) {
                permissionUsageCountMap[permissionGroup] =
                    permissionUsageCountMap.getOrDefault(permissionGroup, 0) + 1
            }
        }
        return permissionUsageCountMap
    }

    /**
     * Determines whether there are any system app permissions with recent usage, in which case the
     * "show/hide system" toggle should be displayed in the UI.
     */
    private fun AllLightPackageOpsLiveData.containsSystemAppUsages(startTime: Long): Boolean {
        val eligibleLightPackageOpsList: List<LightPackageOps> =
            getAllLightPackageOps()?.filterOutExemptedApps() ?: listOf()

        for (lightPackageOps: LightPackageOps in eligibleLightPackageOpsList) {
            val recentlyUsedPermissions: Set<String> =
                lightPackageOps.lastPermissionGroupAccessTimesMs.entries
                    .filterAccessTimeLaterThan(startTime)
                    .map { it.key }
                    .toSet()
            if (
                recentlyUsedPermissions
                    .filterOutExemptedPermissionGroups()
                    .containsSystemAppPermission(
                        lightPackageOps.packageName,
                        lightPackageOps.userHandle
                    )
            ) {
                return true
            }
        }

        return false
    }

    /** Returns all permission groups eligible for display in the UI. */
    private fun getAllEligiblePermissionGroups(): Set<String> =
        standardPermGroupNamesLiveData.value?.filterOutExemptedPermissionGroups()?.toSet()
            ?: setOf()

    private fun isPermissionRequestedByApp(appPermissionId: AppPermissionId): Boolean {
        val appRequestedPermissions =
            lightPackageInfoLiveDataMap[
                    Pair(appPermissionId.packageName, appPermissionId.userHandle)]
                ?.value
                ?.requestedPermissions
                ?: listOf()
        return appRequestedPermissions.any {
            PermissionMapping.getGroupOfPlatformPermission(it) == appPermissionId.permissionGroup
        }
    }

    private fun isAppPermissionSystem(appPermissionId: AppPermissionId): Boolean {
        val appPermGroupUiInfo = appPermGroupUiInfoLiveDataList[appPermissionId]?.value

        if (appPermGroupUiInfo != null) {
            return appPermGroupUiInfo.isSystem
        } else
        // The AppPermGroupUiInfo may be null if it has either not loaded yet or if the app has not
        // requested any permissions from the permission group in question.
        // The Telecom doesn't request microphone or camera permissions. However, telecom app may
        // use these permissions and they are considered system app permissions, so we return true
        // even if the AppPermGroupUiInfo is unavailable.
        if (
            appPermissionId.packageName == TELECOM_PACKAGE &&
                (appPermissionId.permissionGroup == Manifest.permission_group.CAMERA ||
                    appPermissionId.permissionGroup == Manifest.permission_group.MICROPHONE)
        ) {
            return true
        }
        return false
    }

    private fun AllLightPackageOpsLiveData.getAllLightPackageOps() = value?.values

    /**
     * Filters out accesses earlier than the provided start time from a map of permission last
     * accesses.
     */
    private fun Collection<Map.Entry<String, Long>>.filterAccessTimeLaterThan(startTime: Long) =
        filter {
            it.value > startTime
        }

    /** Filters out app permissions when the permission has not been requested by the app. */
    private fun Collection<Map.Entry<String, Long>>.filterOutPermissionsNotRequestedByApp(
        packageName: String,
        userHandle: UserHandle
    ) = filter { isPermissionRequestedByApp(AppPermissionId(packageName, userHandle, it.key)) }

    /**
     * Filters out system app permissions from a map of permission last accesses, if showSystem is
     * false.
     */
    private fun Collection<Map.Entry<String, Long>>.filterOutSystemAppPermissionsIfNecessary(
        showSystem: Boolean,
        packageName: String,
        userHandle: UserHandle
    ) = filter {
        showSystem || !isAppPermissionSystem(AppPermissionId(packageName, userHandle, it.key))
    }

    /**
     * Filters out permission groups that are exempt from permission usage tracking from a map of
     * permission last accesses.
     */
    private fun Collection<Map.Entry<String, Long>>.filterOutExemptedPermissionGroupsFromKeys() =
        filter {
            !EXEMPTED_PERMISSION_GROUPS.contains(it.key)
        }

    /**
     * Filters out permission groups that are exempt from permission usage tracking from a map of
     * permission last accesses.
     */
    private fun Collection<String>.filterOutExemptedPermissionGroups() = filter {
        !EXEMPTED_PERMISSION_GROUPS.contains(it)
    }

    /** Filters out [LightPackageOps] for apps that are exempt from permission usage tracking. */
    private fun Collection<LightPackageOps>.filterOutExemptedApps() = filter {
        !exemptedPackages.contains(it.packageName)
    }

    /**
     * Returns from a list of permissions whether any permission along with the provided package
     * name and user handle are considered a system app permission.
     */
    private fun Collection<String>.containsSystemAppPermission(
        packageName: String,
        userHandle: UserHandle
    ) = any { isAppPermissionSystem(AppPermissionId(packageName, userHandle, it)) }

    /** Data class to hold all the information required to configure the UI. */
    data class PermissionUsagesUiData(
        /**
         * Whether to show data over the last 7 days.
         *
         * While this information is available from the [SHOULD_SHOW_7_DAYS_KEY] state, we include
         * it in the UI info so that it triggers a UI update when changed.
         */
        private val show7DaysUsage: Boolean,
        /**
         * Whether to show system apps' data.
         *
         * While this information is available from the [SHOULD_SHOW_SYSTEM_KEY] state, we include
         * it in the UI info so that it triggers a UI update when changed.
         */
        private val showSystem: Boolean,
        /** Whether to show the "show/hide system" toggle. */
        val containsSystemAppUsages: Boolean,
        /** Map instances for display in UI */
        val permissionGroupsWithUsageCount: Map<String, Int>,
    )

    /** LiveData object for [PermissionUsagesUiData]. */
    val permissionUsagesUiLiveData =
        object : SmartUpdateMediatorLiveData<@JvmSuppressWildcards PermissionUsagesUiData>() {
            private val getAppPermGroupUiInfoLiveData = { appPermissionId: AppPermissionId ->
                AppPermGroupUiInfoLiveData[
                    Triple(
                        appPermissionId.packageName,
                        appPermissionId.permissionGroup,
                        appPermissionId.userHandle,
                    )]
            }
            private val getLightPackageInfoLiveData = { packageUser: Pair<String, UserHandle> ->
                LightPackageInfoLiveData[packageUser]
            }

            init {
                addSource(mAllLightPackageOpsLiveData) { update() }
                addSource(showSystemAppsLiveData) { update() }
                addSource(show7DaysLiveData) { update() }
                addSource(standardPermGroupNamesLiveData) { update() }
            }

            override fun onUpdate() {
                if (mAllLightPackageOpsLiveData.isStale) {
                    return
                }
                if (
                    appPermGroupUiInfoLiveDataList.any {
                        !it.value.isInitialized || it.value.isStale
                    }
                ) {
                    return
                }
                if (
                    lightPackageInfoLiveDataMap.any { !it.value.isInitialized || it.value.isStale }
                ) {
                    return
                }

                val packageOps: Map<Pair<String, UserHandle>, LightPackageOps> =
                    mAllLightPackageOpsLiveData.value ?: emptyMap()
                val appPermissionIds = mutableListOf<AppPermissionId>()
                val allPackages = packageOps.keys

                packageOps.forEach { (packageWithUserHandle, pkgOps) ->
                    pkgOps.lastPermissionGroupAccessTimesMs.keys.forEach { permissionGroup ->
                        appPermissionIds.add(
                            AppPermissionId(
                                packageWithUserHandle.first,
                                packageWithUserHandle.second,
                                permissionGroup,
                            )
                        )
                    }
                }

                setSourcesToDifference(
                    appPermissionIds,
                    appPermGroupUiInfoLiveDataList,
                    getAppPermGroupUiInfoLiveData
                ) {
                    update()
                }

                setSourcesToDifference(
                    allPackages,
                    lightPackageInfoLiveDataMap,
                    getLightPackageInfoLiveData
                ) {
                    update()
                }

                if (lightPackageInfoLiveDataMap.any { it.value.isStale }) {
                    return
                }

                if (appPermGroupUiInfoLiveDataList.any { it.value.isStale }) {
                    return
                }

                val uiData = buildPermissionUsagesUiData()
                // We include this check as we don't want UX updates unless the data to be displayed
                // has changed. SmartUpdateMediatorLiveData sends updates if the data has changed OR
                // if the data has changed from stale to fresh.
                if (value != uiData) {
                    value = uiData
                }
            }
        }

    /** Companion class for [PermissionUsageViewModel]. */
    companion object {
        private val TIME_7_DAYS_DURATION = TimeUnit.DAYS.toMillis(7)
        private val TIME_24_HOURS_DURATION = TimeUnit.DAYS.toMillis(1)
        internal const val SHOULD_SHOW_SYSTEM_KEY = "showSystem"
        internal const val SHOULD_SHOW_7_DAYS_KEY = "show7Days"
        private const val TELECOM_PACKAGE = "com.android.server.telecom"

        /** Permission groups that should be hidden from the permissions usage UI. */
        private val EXEMPTED_PERMISSION_GROUPS = setOf(Manifest.permission_group.NOTIFICATIONS)
    }

    /** Factory for [PermissionUsageViewModel]. */
    @RequiresApi(Build.VERSION_CODES.S)
    class PermissionUsageViewModelFactory(
        val app: Application,
        owner: SavedStateRegistryOwner,
        defaultArgs: Bundle
    ) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ): T {
            @Suppress("UNCHECKED_CAST") return PermissionUsageViewModel(handle, app) as T
        }
    }
}
