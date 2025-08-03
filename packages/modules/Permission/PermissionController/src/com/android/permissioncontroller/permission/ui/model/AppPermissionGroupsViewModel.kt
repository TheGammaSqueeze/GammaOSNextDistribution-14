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

package com.android.permissioncontroller.permission.ui.model

import android.Manifest
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_IGNORED
import android.app.AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED
import android.apphibernation.AppHibernationManager
import android.content.Context
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__SWITCH_DISABLED
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__SWITCH_ENABLED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.hibernation.isHibernationEnabled
import com.android.permissioncontroller.permission.data.AppPermGroupUiInfoLiveData
import com.android.permissioncontroller.permission.data.FullStoragePermissionAppsLiveData
import com.android.permissioncontroller.permission.data.HibernationSettingStateLiveData
import com.android.permissioncontroller.permission.data.LightPackageInfoLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData.Companion.NON_RUNTIME_NORMAL_PERMS
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage
import com.android.permissioncontroller.permission.ui.Category
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.AppPermsLastAccessType
import com.android.permissioncontroller.permission.utils.navigateSafe
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * ViewModel for the AppPermissionGroupsFragment. Has a liveData with the UI information for all
 * permission groups that this package requests runtime permissions from
 *
 * @param packageName The name of the package this viewModel is representing
 * @param user The user of the package this viewModel is representing
 */
class AppPermissionGroupsViewModel(
    private val packageName: String,
    private val user: UserHandle,
    private val sessionId: Long
) : ViewModel() {

    companion object {
        const val AGGREGATE_DATA_FILTER_BEGIN_DAYS_1 = 1
        const val AGGREGATE_DATA_FILTER_BEGIN_DAYS_7 = 7
        val LOG_TAG: String = AppPermissionGroupsViewModel::class.java.simpleName
    }

    val app = PermissionControllerApplication.get()!!

    enum class PermSubtitle(val value: Int) {
        NONE(0),
        MEDIA_ONLY(1),
        ALL_FILES(2),
        FOREGROUND_ONLY(3),
        BACKGROUND(4),
    }

    data class GroupUiInfo(
        val groupName: String,
        val isSystem: Boolean = false,
        val subtitle: PermSubtitle
    ) {
        constructor(
            groupName: String,
            isSystem: Boolean
        ) : this(groupName, isSystem, PermSubtitle.NONE)
    }

    // Auto-revoke and hibernation share the same settings
    val autoRevokeLiveData = HibernationSettingStateLiveData[packageName, user]

    private val packagePermsLiveData = PackagePermissionsLiveData[packageName, user]
    private val appPermGroupUiInfoLiveDatas = mutableMapOf<String, AppPermGroupUiInfoLiveData>()
    private val fullStoragePermsLiveData = FullStoragePermissionAppsLiveData

    /**
     * LiveData whose data is a map of grant category (either allowed or denied) to a list of
     * permission group names that match the key, and two booleans representing if this is a system
     * group, and a subtitle resource ID, if applicable.
     */
    val packagePermGroupsLiveData =
        object :
            SmartUpdateMediatorLiveData<@JvmSuppressWildcards Map<Category, List<GroupUiInfo>>>() {

            init {
                addSource(packagePermsLiveData) { update() }
                addSource(fullStoragePermsLiveData) { update() }
                addSource(autoRevokeLiveData) {
                    removeSource(autoRevokeLiveData)
                    update()
                }
                update()
            }

            override fun onUpdate() {
                val groups =
                    packagePermsLiveData.value?.keys?.filter { it != NON_RUNTIME_NORMAL_PERMS }
                if (groups == null && packagePermsLiveData.isInitialized) {
                    value = null
                    return
                } else if (
                    groups == null ||
                        (Manifest.permission_group.STORAGE in groups &&
                            !fullStoragePermsLiveData.isInitialized) ||
                        !autoRevokeLiveData.isInitialized
                ) {
                    return
                }

                val getLiveData = { groupName: String ->
                    AppPermGroupUiInfoLiveData[packageName, groupName, user]
                }
                setSourcesToDifference(groups, appPermGroupUiInfoLiveDatas, getLiveData)

                if (!appPermGroupUiInfoLiveDatas.all { it.value.isInitialized }) {
                    return
                }

                val groupGrantStates = mutableMapOf<Category, MutableList<GroupUiInfo>>()
                groupGrantStates[Category.ALLOWED] = mutableListOf()
                groupGrantStates[Category.ASK] = mutableListOf()
                groupGrantStates[Category.DENIED] = mutableListOf()

                val fullStorageState =
                    fullStoragePermsLiveData.value?.find { pkg ->
                        pkg.packageName == packageName && pkg.user == user
                    }

                for (groupName in groups) {
                    val isSystem =
                        PermissionMapping.getPlatformPermissionGroups().contains(groupName)
                    appPermGroupUiInfoLiveDatas[groupName]?.value?.let { uiInfo ->
                        if (SdkLevel.isAtLeastT() && !uiInfo.shouldShow) {
                            return@let
                        }
                        if (
                            groupName == Manifest.permission_group.STORAGE &&
                                (fullStorageState?.isGranted == true && !fullStorageState.isLegacy)
                        ) {
                            groupGrantStates[Category.ALLOWED]!!.add(
                                GroupUiInfo(groupName, isSystem, PermSubtitle.ALL_FILES)
                            )
                            return@let
                        }
                        when (uiInfo.permGrantState) {
                            PermGrantState.PERMS_ALLOWED -> {
                                val subtitle =
                                    if (groupName == Manifest.permission_group.STORAGE) {
                                        if (SdkLevel.isAtLeastT()) {
                                            PermSubtitle.NONE
                                        } else {
                                            if (fullStorageState?.isLegacy == true) {
                                                PermSubtitle.ALL_FILES
                                            } else {
                                                PermSubtitle.MEDIA_ONLY
                                            }
                                        }
                                    } else {
                                        PermSubtitle.NONE
                                    }
                                groupGrantStates[Category.ALLOWED]!!.add(
                                    GroupUiInfo(groupName, isSystem, subtitle)
                                )
                            }
                            PermGrantState.PERMS_ALLOWED_ALWAYS ->
                                groupGrantStates[Category.ALLOWED]!!.add(
                                    GroupUiInfo(groupName, isSystem, PermSubtitle.BACKGROUND)
                                )
                            PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY ->
                                groupGrantStates[Category.ALLOWED]!!.add(
                                    GroupUiInfo(groupName, isSystem, PermSubtitle.FOREGROUND_ONLY)
                                )
                            PermGrantState.PERMS_DENIED ->
                                groupGrantStates[Category.DENIED]!!.add(
                                    GroupUiInfo(groupName, isSystem)
                                )
                            PermGrantState.PERMS_ASK ->
                                groupGrantStates[Category.ASK]!!.add(
                                    GroupUiInfo(groupName, isSystem)
                                )
                        }
                    }
                }

                value = groupGrantStates
            }
        }

    // TODO 206455664: remove once issue is identified
    fun logLiveDataState() {
        Log.i(
            LOG_TAG,
            "Overall liveData isStale: ${packagePermGroupsLiveData.isStale}, " +
                "isInitialized: ${packagePermGroupsLiveData.isInitialized}, " +
                "value: ${packagePermGroupsLiveData.value}"
        )
        Log.i(
            LOG_TAG,
            "AutoRevoke liveData isStale: ${autoRevokeLiveData.isStale}, " +
                "isInitialized: ${autoRevokeLiveData.isInitialized}, " +
                "value: ${autoRevokeLiveData.value}"
        )
        Log.i(
            LOG_TAG,
            "PackagePerms liveData isStale: ${packagePermsLiveData.isStale}, " +
                "isInitialized: ${packagePermsLiveData.isInitialized}, " +
                "value: ${packagePermsLiveData.value}"
        )
        Log.i(
            LOG_TAG,
            "FullStorage liveData isStale: ${fullStoragePermsLiveData.isStale}, " +
                "isInitialized: ${fullStoragePermsLiveData.isInitialized}, " +
                "value size: ${fullStoragePermsLiveData.value?.size}"
        )
        for ((group, liveData) in appPermGroupUiInfoLiveDatas) {
            Log.i(
                LOG_TAG,
                "$group ui liveData isStale: ${liveData.isStale}, " +
                    "isInitialized: ${liveData.isInitialized}, " +
                    "value size: ${liveData.value}"
            )
        }
    }

    fun setAutoRevoke(enabled: Boolean) {
        GlobalScope.launch(IPC) {
            val aom = app.getSystemService(AppOpsManager::class.java)!!
            val lightPackageInfo = LightPackageInfoLiveData[packageName, user].getInitializedValue()

            if (lightPackageInfo != null) {
                Log.i(
                    LOG_TAG,
                    "sessionId $sessionId setting auto revoke enabled to $enabled for" +
                        "$packageName $user"
                )
                val tag =
                    if (enabled) {
                        APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__SWITCH_ENABLED
                    } else {
                        APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__SWITCH_DISABLED
                    }
                PermissionControllerStatsLog.write(
                    APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION,
                    sessionId,
                    lightPackageInfo.uid,
                    packageName,
                    tag
                )

                val mode =
                    if (enabled) {
                        MODE_ALLOWED
                    } else {
                        MODE_IGNORED
                    }
                aom.setUidMode(OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, lightPackageInfo.uid, mode)
                if (isHibernationEnabled() && SdkLevel.isAtLeastSv2() && !enabled) {
                    // Only unhibernate on S_V2+ to have consistent toggle behavior w/ Settings
                    val ahm = app.getSystemService(AppHibernationManager::class.java)!!
                    ahm.setHibernatingForUser(packageName, false)
                    ahm.setHibernatingGlobally(packageName, false)
                }
            }
        }
    }

    fun showExtraPerms(fragment: Fragment, args: Bundle) {
        fragment.findNavController().navigateSafe(R.id.perm_groups_to_custom, args)
    }

    fun showAllPermissions(fragment: Fragment, args: Bundle) {
        fragment.findNavController().navigateSafe(R.id.perm_groups_to_all_perms, args)
    }

    // This method should be consolidated with
    // PermissionAppsViewModel#extractGroupUsageLastAccessTime
    fun extractGroupUsageLastAccessTime(
        accessTime: MutableMap<String, Long>,
        appPermissionUsages: List<AppPermissionUsage>,
        packageName: String
    ) {
        if (!SdkLevel.isAtLeastS()) {
            return
        }

        val aggregateDataFilterBeginDays =
            if (KotlinUtils.is7DayToggleEnabled()) AGGREGATE_DATA_FILTER_BEGIN_DAYS_7
            else AGGREGATE_DATA_FILTER_BEGIN_DAYS_1

        accessTime.clear()
        val filterTimeBeginMillis =
            max(
                System.currentTimeMillis() -
                    TimeUnit.DAYS.toMillis(aggregateDataFilterBeginDays.toLong()),
                Instant.EPOCH.toEpochMilli()
            )
        val numApps: Int = appPermissionUsages.size
        for (appIndex in 0 until numApps) {
            val appUsage: AppPermissionUsage = appPermissionUsages[appIndex]
            if (appUsage.packageName != packageName) {
                continue
            }
            val appGroups = appUsage.groupUsages
            val numGroups = appGroups.size
            for (groupIndex in 0 until numGroups) {
                val groupUsage = appGroups[groupIndex]
                var lastAccessTime = groupUsage.lastAccessTime
                val groupName = groupUsage.group.name
                if (lastAccessTime == 0L || lastAccessTime < filterTimeBeginMillis) {
                    continue
                }

                // We might have another AppPermissionUsage entry that's of the same packageName
                // but with a different uid. In that case, we want to grab the max lastAccessTime
                // as the last usage to show.
                lastAccessTime =
                    Math.max(
                        accessTime.getOrDefault(groupName, Instant.EPOCH.toEpochMilli()),
                        lastAccessTime
                    )
                accessTime[groupName] = lastAccessTime
            }
        }
    }

    fun getPreferenceSummary(
        groupInfo: GroupUiInfo,
        context: Context,
        lastAccessTime: Long?
    ): String {
        val summaryTimestamp =
            Utils.getPermissionLastAccessSummaryTimestamp(
                lastAccessTime,
                context,
                groupInfo.groupName
            )
        @AppPermsLastAccessType val lastAccessType: Int = summaryTimestamp.second

        return when (groupInfo.subtitle) {
            PermSubtitle.BACKGROUND ->
                when (lastAccessType) {
                    Utils.LAST_24H_CONTENT_PROVIDER ->
                        context.getString(R.string.app_perms_content_provider_24h_background)
                    Utils.LAST_7D_CONTENT_PROVIDER ->
                        context.getString(R.string.app_perms_content_provider_7d_background)
                    Utils.LAST_24H_SENSOR_TODAY ->
                        context.getString(
                            R.string.app_perms_24h_access_background,
                            summaryTimestamp.first
                        )
                    Utils.LAST_24H_SENSOR_YESTERDAY ->
                        context.getString(
                            R.string.app_perms_24h_access_yest_background,
                            summaryTimestamp.first
                        )
                    Utils.LAST_7D_SENSOR ->
                        context.getString(
                            R.string.app_perms_7d_access_background,
                            summaryTimestamp.third,
                            summaryTimestamp.first
                        )
                    Utils.NOT_IN_LAST_7D ->
                        context.getString(R.string.permission_subtitle_background)
                    else -> context.getString(R.string.permission_subtitle_background)
                }
            PermSubtitle.MEDIA_ONLY ->
                when (lastAccessType) {
                    Utils.LAST_24H_CONTENT_PROVIDER ->
                        context.getString(R.string.app_perms_content_provider_24h_media_only)
                    Utils.LAST_7D_CONTENT_PROVIDER ->
                        context.getString(R.string.app_perms_content_provider_7d_media_only)
                    Utils.LAST_24H_SENSOR_TODAY ->
                        context.getString(
                            R.string.app_perms_24h_access_media_only,
                            summaryTimestamp.first
                        )
                    Utils.LAST_24H_SENSOR_YESTERDAY ->
                        context.getString(
                            R.string.app_perms_24h_access_yest_media_only,
                            summaryTimestamp.first
                        )
                    Utils.LAST_7D_SENSOR ->
                        context.getString(
                            R.string.app_perms_7d_access_media_only,
                            summaryTimestamp.third,
                            summaryTimestamp.first
                        )
                    Utils.NOT_IN_LAST_7D ->
                        context.getString(R.string.permission_subtitle_media_only)
                    else -> context.getString(R.string.permission_subtitle_media_only)
                }
            PermSubtitle.ALL_FILES ->
                when (lastAccessType) {
                    Utils.LAST_24H_CONTENT_PROVIDER ->
                        context.getString(R.string.app_perms_content_provider_24h_all_files)
                    Utils.LAST_7D_CONTENT_PROVIDER ->
                        context.getString(R.string.app_perms_content_provider_7d_all_files)
                    Utils.LAST_24H_SENSOR_TODAY ->
                        context.getString(
                            R.string.app_perms_24h_access_all_files,
                            summaryTimestamp.first
                        )
                    Utils.LAST_24H_SENSOR_YESTERDAY ->
                        context.getString(
                            R.string.app_perms_24h_access_yest_all_files,
                            summaryTimestamp.first
                        )
                    Utils.LAST_7D_SENSOR ->
                        context.getString(
                            R.string.app_perms_7d_access_all_files,
                            summaryTimestamp.third,
                            summaryTimestamp.first
                        )
                    Utils.NOT_IN_LAST_7D ->
                        context.getString(R.string.permission_subtitle_all_files)
                    else -> context.getString(R.string.permission_subtitle_all_files)
                }
            else ->
                // PermSubtitle.FOREGROUND_ONLY should fall into this as well
                when (lastAccessType) {
                    Utils.LAST_24H_CONTENT_PROVIDER ->
                        context.getString(R.string.app_perms_content_provider_24h)
                    Utils.LAST_7D_CONTENT_PROVIDER ->
                        context.getString(R.string.app_perms_content_provider_7d)
                    Utils.LAST_24H_SENSOR_TODAY ->
                        context.getString(R.string.app_perms_24h_access, summaryTimestamp.first)
                    Utils.LAST_24H_SENSOR_YESTERDAY ->
                        context.getString(
                            R.string.app_perms_24h_access_yest,
                            summaryTimestamp.first
                        )
                    Utils.LAST_7D_SENSOR ->
                        context.getString(
                            R.string.app_perms_7d_access,
                            summaryTimestamp.third,
                            summaryTimestamp.first
                        )
                    Utils.NOT_IN_LAST_7D -> ""
                    else -> ""
                }
        }
    }
}

/**
 * Factory for an AppPermissionGroupsViewModel
 *
 * @param packageName The name of the package this viewModel is representing
 * @param user The user of the package this viewModel is representing
 */
class AppPermissionGroupsViewModelFactory(
    private val packageName: String,
    private val user: UserHandle,
    private val sessionId: Long
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppPermissionGroupsViewModel(packageName, user, sessionId) as T
    }
}
