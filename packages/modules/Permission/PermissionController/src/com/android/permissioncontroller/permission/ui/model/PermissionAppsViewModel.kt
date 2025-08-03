/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.model

import android.Manifest
import android.Manifest.permission_group.LOCATION
import android.app.Application
import android.content.Intent
import android.content.res.Resources
import android.hardware.SensorPrivacyManager
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.savedstate.SavedStateRegistryOwner
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__ALLOWED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__ALLOWED_FOREGROUND
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__DENIED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__UNDEFINED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.FullStoragePermissionAppsLiveData
import com.android.permissioncontroller.permission.data.FullStoragePermissionAppsLiveData.FullStoragePackageState
import com.android.permissioncontroller.permission.data.SinglePermGroupPackagesUiInfoLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage
import com.android.permissioncontroller.permission.ui.Category
import com.android.permissioncontroller.permission.ui.LocationProviderInterceptDialog
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModel.Companion.CREATION_LOGGED_KEY
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModel.Companion.HAS_SYSTEM_APPS_KEY
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModel.Companion.SHOULD_SHOW_SYSTEM_KEY
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModel.Companion.SHOW_ALWAYS_ALLOWED
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPackageUid
import com.android.permissioncontroller.permission.utils.KotlinUtils.is7DayToggleEnabled
import com.android.permissioncontroller.permission.utils.LocationUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.navigateSafe
import java.text.Collator
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * ViewModel for the PermissionAppsFragment. Has a liveData with all of the UI info for each package
 * which requests permissions in this permission group, a liveData which tracks whether or not to
 * show system apps, and a liveData tracking whether there are any system apps which request
 * permissions in this group.
 *
 * @param app The current application
 * @param groupName The name of the permission group this viewModel is representing
 */
class PermissionAppsViewModel(
    private val state: SavedStateHandle,
    private val app: Application,
    private val groupName: String
) : ViewModel() {

    companion object {
        const val AGGREGATE_DATA_FILTER_BEGIN_DAYS_1 = 1
        const val AGGREGATE_DATA_FILTER_BEGIN_DAYS_7 = 7
        internal const val SHOULD_SHOW_SYSTEM_KEY = "showSystem"
        internal const val HAS_SYSTEM_APPS_KEY = "hasSystem"
        internal const val SHOW_ALWAYS_ALLOWED = "showAlways"
        internal const val CREATION_LOGGED_KEY = "creationLogged"
    }

    val shouldShowSystemLiveData = state.getLiveData(SHOULD_SHOW_SYSTEM_KEY, false)
    val hasSystemAppsLiveData = state.getLiveData(HAS_SYSTEM_APPS_KEY, true)
    val showAllowAlwaysStringLiveData = state.getLiveData(SHOW_ALWAYS_ALLOWED, false)
    val categorizedAppsLiveData = CategorizedAppsLiveData(groupName)

    @get:RequiresApi(Build.VERSION_CODES.S)
    val sensorStatusLiveData: SensorStatusLiveData by
        lazy(LazyThreadSafetyMode.NONE) { SensorStatusLiveData() }

    fun updateShowSystem(showSystem: Boolean) {
        if (showSystem != state.get(SHOULD_SHOW_SYSTEM_KEY)) {
            state.set(SHOULD_SHOW_SYSTEM_KEY, showSystem)
        }
    }

    var creationLogged
        get() = state.get(CREATION_LOGGED_KEY) ?: false
        set(value) = state.set(CREATION_LOGGED_KEY, value)

    /** A LiveData that tracks the status (blocked or available) of a sensor */
    @RequiresApi(Build.VERSION_CODES.S)
    inner class SensorStatusLiveData() : SmartUpdateMediatorLiveData<Boolean>() {
        val sensorPrivacyManager = app.getSystemService(SensorPrivacyManager::class.java)!!
        val sensor = Utils.getSensorCode(groupName)
        val isLocation = LOCATION.equals(groupName)

        init {
            checkAndUpdateStatus()
        }

        fun checkAndUpdateStatus() {
            var blocked: Boolean

            if (isLocation) {
                blocked = !LocationUtils.isLocationEnabled(app.getApplicationContext())
            } else {
                blocked = sensorPrivacyManager.isSensorPrivacyEnabled(sensor)
            }

            if (blocked) {
                value = blocked
            }
        }

        override fun onActive() {
            super.onActive()
            checkAndUpdateStatus()
            if (isLocation) {
                LocationUtils.addLocationListener(locListener)
            } else {
                sensorPrivacyManager.addSensorPrivacyListener(sensor, listener)
            }
        }

        override fun onInactive() {
            super.onInactive()
            if (isLocation) {
                LocationUtils.removeLocationListener(locListener)
            } else {
                sensorPrivacyManager.removeSensorPrivacyListener(sensor, listener)
            }
        }

        private val listener = { _: Int, status: Boolean -> value = status }

        private val locListener = { status: Boolean -> value = !status }

        override fun onUpdate() {
            // Do nothing
        }
    }

    inner class CategorizedAppsLiveData(groupName: String) :
        MediatorLiveData<
            @kotlin.jvm.JvmSuppressWildcards Map<Category, List<Pair<String, UserHandle>>>
        >() {
        private val packagesUiInfoLiveData = SinglePermGroupPackagesUiInfoLiveData[groupName]

        init {
            var fullStorageLiveData: FullStoragePermissionAppsLiveData? = null

            // If this is the Storage group, observe a FullStoragePermissionAppsLiveData, update
            // the packagesWithFullFileAccess list, and call update to populate the subtitles.
            if (groupName == Manifest.permission_group.STORAGE) {
                fullStorageLiveData = FullStoragePermissionAppsLiveData
                addSource(FullStoragePermissionAppsLiveData) { fullAccessPackages ->
                    if (fullAccessPackages != packagesWithFullFileAccess) {
                        packagesWithFullFileAccess = fullAccessPackages.filter { it.isGranted }
                        if (packagesUiInfoLiveData.isInitialized) {
                            update()
                        }
                    }
                }
            }

            addSource(packagesUiInfoLiveData) {
                if (fullStorageLiveData == null || fullStorageLiveData.isInitialized) update()
            }
            addSource(shouldShowSystemLiveData) {
                if (fullStorageLiveData == null || fullStorageLiveData.isInitialized) update()
            }

            if (
                (fullStorageLiveData == null || fullStorageLiveData.isInitialized) &&
                    packagesUiInfoLiveData.isInitialized
            ) {
                packagesWithFullFileAccess =
                    fullStorageLiveData?.value?.filter { it.isGranted } ?: emptyList()
                update()
            }
        }

        fun update() {
            val categoryMap = mutableMapOf<Category, MutableList<Pair<String, UserHandle>>>()
            val showSystem: Boolean = state.get(SHOULD_SHOW_SYSTEM_KEY) ?: false

            categoryMap[Category.ALLOWED] = mutableListOf()
            categoryMap[Category.ALLOWED_FOREGROUND] = mutableListOf()
            categoryMap[Category.ASK] = mutableListOf()
            categoryMap[Category.DENIED] = mutableListOf()

            val packageMap =
                packagesUiInfoLiveData.value
                    ?: run {
                        if (packagesUiInfoLiveData.isInitialized) {
                            value = categoryMap
                        }
                        return
                    }

            val hasSystem = packageMap.any { it.value.isSystem && it.value.shouldShow }
            if (hasSystem != state.get(HAS_SYSTEM_APPS_KEY)) {
                state.set(HAS_SYSTEM_APPS_KEY, hasSystem)
            }

            var showAlwaysAllowedString = false

            for ((packageUserPair, uiInfo) in packageMap) {
                if (!uiInfo.shouldShow) {
                    continue
                }

                if (uiInfo.isSystem && !showSystem) {
                    continue
                }

                if (
                    uiInfo.permGrantState == PermGrantState.PERMS_ALLOWED_ALWAYS ||
                        uiInfo.permGrantState == PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY
                ) {
                    showAlwaysAllowedString = true
                }

                var category =
                    when (uiInfo.permGrantState) {
                        PermGrantState.PERMS_ALLOWED -> Category.ALLOWED
                        PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY -> Category.ALLOWED_FOREGROUND
                        PermGrantState.PERMS_ALLOWED_ALWAYS -> Category.ALLOWED
                        PermGrantState.PERMS_DENIED -> Category.DENIED
                        PermGrantState.PERMS_ASK -> Category.ASK
                    }

                if (
                    !SdkLevel.isAtLeastT() &&
                        groupName == Manifest.permission_group.STORAGE &&
                        packagesWithFullFileAccess.any {
                            !it.isLegacy &&
                                it.isGranted &&
                                it.packageName to it.user == packageUserPair
                        }
                ) {
                    category = Category.ALLOWED
                }
                categoryMap[category]!!.add(packageUserPair)
            }
            showAllowAlwaysStringLiveData.value = showAlwaysAllowedString
            value = categoryMap
        }
    }

    /**
     * If this is the storage permission group, some apps have full access to storage, while others
     * just have access to media files. This list contains the packages with full access. To listen
     * for changes, create and observe a FullStoragePermissionAppsLiveData
     */
    private var packagesWithFullFileAccess = listOf<FullStoragePackageState>()

    /**
     * Whether or not to show the "Files and Media" subtitle label for a package, vs. the normal
     * "Media". Requires packagesWithFullFileAccess to be updated in order to work. To do this,
     * create and observe a FullStoragePermissionAppsLiveData.
     *
     * @param packageName The name of the package we want to check
     * @param user The name of the user whose package we want to check
     * @return true if the package and user has full file access
     */
    fun packageHasFullStorage(packageName: String, user: UserHandle): Boolean {
        return packagesWithFullFileAccess.any { it.packageName == packageName && it.user == user }
    }

    /**
     * Whether or not packages have been loaded from the system. To update, need to observe the
     * allPackageInfosLiveData.
     *
     * @return Whether or not all packages have been loaded
     */
    fun arePackagesLoaded(): Boolean {
        return AllPackageInfosLiveData.isInitialized
    }

    /**
     * Navigate to an AppPermissionFragment, unless this is a special location package
     *
     * @param fragment The fragment attached to this ViewModel
     * @param packageName The package name we want to navigate to
     * @param user The user we want to navigate to the package of
     * @param args The arguments to pass onto the fragment
     */
    fun navigateToAppPermission(
        fragment: Fragment,
        packageName: String,
        user: UserHandle,
        args: Bundle
    ) {
        val activity = fragment.activity!!
        if (LocationUtils.isLocationGroupAndProvider(activity, groupName, packageName)) {
            val intent = Intent(activity, LocationProviderInterceptDialog::class.java)
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            activity.startActivityAsUser(intent, user)
            return
        }

        if (
            LocationUtils.isLocationGroupAndControllerExtraPackage(activity, groupName, packageName)
        ) {
            // Redirect to location controller extra package settings.
            LocationUtils.startLocationControllerExtraPackageSettings(activity, user)
            return
        }

        fragment.findNavController().navigateSafe(R.id.perm_apps_to_app, args)
    }

    fun getFilterTimeBeginMillis(): Long {
        val aggregateDataFilterBeginDays =
            if (is7DayToggleEnabled()) AGGREGATE_DATA_FILTER_BEGIN_DAYS_7
            else AGGREGATE_DATA_FILTER_BEGIN_DAYS_1

        return max(
            System.currentTimeMillis() -
                TimeUnit.DAYS.toMillis(aggregateDataFilterBeginDays.toLong()),
            Instant.EPOCH.toEpochMilli()
        )
    }

    /**
     * Return a mapping of user + packageName to their last access timestamps for the permission
     * group.
     */
    fun extractGroupUsageLastAccessTime(
        appPermissionUsages: List<AppPermissionUsage>
    ): MutableMap<String, Long> {
        val accessTime: MutableMap<String, Long> = HashMap()
        if (!SdkLevel.isAtLeastS()) {
            return accessTime
        }

        val aggregateDataFilterBeginDays =
            if (is7DayToggleEnabled()) AGGREGATE_DATA_FILTER_BEGIN_DAYS_7
            else AGGREGATE_DATA_FILTER_BEGIN_DAYS_1
        val now = System.currentTimeMillis()
        val filterTimeBeginMillis =
            max(
                now - TimeUnit.DAYS.toMillis(aggregateDataFilterBeginDays.toLong()),
                Instant.EPOCH.toEpochMilli()
            )
        val numApps: Int = appPermissionUsages.size
        for (appIndex in 0 until numApps) {
            val appUsage: AppPermissionUsage = appPermissionUsages.get(appIndex)
            val packageName = appUsage.packageName
            val appGroups = appUsage.groupUsages
            val numGroups = appGroups.size
            for (groupIndex in 0 until numGroups) {
                val groupUsage = appGroups[groupIndex]
                val groupUsageGroupName = groupUsage.group.name
                if (groupName != groupUsageGroupName) {
                    continue
                }
                val lastAccessTime = groupUsage.lastAccessTime
                if (lastAccessTime == 0L || lastAccessTime < filterTimeBeginMillis) {
                    continue
                }
                val key = groupUsage.group.user.toString() + packageName
                accessTime[key] = lastAccessTime
            }
        }
        return accessTime
    }

    /** Return the String preference summary based on the last access time. */
    fun getPreferenceSummary(
        res: Resources,
        summaryTimestamp: Triple<String, Int, String>
    ): String {
        return when (summaryTimestamp.second) {
            Utils.LAST_24H_CONTENT_PROVIDER ->
                res.getString(R.string.app_perms_content_provider_24h)
            Utils.LAST_7D_CONTENT_PROVIDER -> res.getString(R.string.app_perms_content_provider_7d)
            Utils.LAST_24H_SENSOR_TODAY ->
                res.getString(R.string.app_perms_24h_access, summaryTimestamp.first)
            Utils.LAST_24H_SENSOR_YESTERDAY ->
                res.getString(R.string.app_perms_24h_access_yest, summaryTimestamp.first)
            Utils.LAST_7D_SENSOR ->
                res.getString(
                    R.string.app_perms_7d_access,
                    summaryTimestamp.third,
                    summaryTimestamp.first
                )
            else -> ""
        }
    }

    /** Return two preferences to determine their ordering. */
    fun comparePreference(collator: Collator, lhs: Preference, rhs: Preference): Int {
        var result: Int = collator.compare(lhs.title.toString(), rhs.title.toString())
        if (result == 0) {
            result = lhs.key.compareTo(rhs.key)
        }
        return result
    }

    /** Log that the fragment was created. */
    fun logPermissionAppsFragmentCreated(
        packageName: String,
        user: UserHandle,
        viewId: Long,
        isAllowed: Boolean,
        isAllowedForeground: Boolean,
        isDenied: Boolean,
        sessionId: Long,
        application: Application,
        permGroupName: String,
        tag: String
    ) {
        var category = PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__UNDEFINED
        when {
            isAllowed -> {
                category = PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__ALLOWED
            }
            isAllowedForeground -> {
                category = PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__ALLOWED_FOREGROUND
            }
            isDenied -> {
                category = PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__DENIED
            }
        }
        val uid = getPackageUid(application, packageName, user) ?: return
        PermissionControllerStatsLog.write(
            PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED,
            sessionId,
            viewId,
            permGroupName,
            uid,
            packageName,
            category
        )
        Log.i(
            tag,
            tag +
                " created with sessionId=" +
                sessionId +
                " permissionGroupName=" +
                permGroupName +
                " appUid=" +
                uid +
                " packageName=" +
                packageName +
                " category=" +
                category
        )
    }
}

/**
 * Factory for a PermissionAppsViewModel
 *
 * @param app The current application of the fragment
 * @param groupName The name of the permission group this viewModel is representing
 * @param owner The owner of this saved state
 * @param defaultArgs The default args to pass
 */
class PermissionAppsViewModelFactory(
    private val app: Application,
    private val groupName: String,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        handle.set(SHOULD_SHOW_SYSTEM_KEY, handle.get<Boolean>(SHOULD_SHOW_SYSTEM_KEY) ?: false)
        handle.set(HAS_SYSTEM_APPS_KEY, handle.get<Boolean>(HAS_SYSTEM_APPS_KEY) ?: true)
        handle.set(SHOW_ALWAYS_ALLOWED, handle.get<Boolean>(SHOW_ALWAYS_ALLOWED) ?: false)
        handle.set(CREATION_LOGGED_KEY, handle.get<Boolean>(CREATION_LOGGED_KEY) ?: false)
        @Suppress("UNCHECKED_CAST") return PermissionAppsViewModel(handle, app, groupName) as T
    }
}
