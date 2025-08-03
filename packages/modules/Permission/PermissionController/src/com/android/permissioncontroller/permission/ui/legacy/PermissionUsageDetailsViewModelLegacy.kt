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
import android.app.AppOpsManager
import android.app.Application
import android.app.LoaderManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.model.AppPermissionGroup
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.PermissionApp
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage.TimelineUsage
import com.android.permissioncontroller.permission.model.v31.PermissionUsages
import com.android.permissioncontroller.permission.ui.handheld.v31.getDurationUsedStr
import com.android.permissioncontroller.permission.ui.handheld.v31.shouldShowSubattributionInPermissionsDashboard
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPackageLabel
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.StringUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.v31.SubattributionUtils
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.DAYS
import kotlin.math.max

/** View model for the permission details fragment. */
@RequiresApi(Build.VERSION_CODES.S)
class PermissionUsageDetailsViewModelLegacy(
    val application: Application,
    val roleManager: RoleManager,
    private val permissionGroup: String,
    val sessionId: Long
) : ViewModel() {

    companion object {
        private const val ONE_HOUR_MS = 3_600_000
        private const val ONE_MINUTE_MS = 60_000
        private const val CLUSTER_SPACING_MINUTES: Long = 1L
        private val TIME_7_DAYS_DURATION: Long = DAYS.toMillis(7)
        private val TIME_24_HOURS_DURATION: Long = DAYS.toMillis(1)
    }

    private val mTimeFilterItemMs = mutableListOf<TimeFilterItemMs>()

    init {
        initializeTimeFilterItems(application)
    }

    /** Loads permission usages using [PermissionUsages]. Response is returned to the [callback]. */
    fun loadPermissionUsages(
        loaderManager: LoaderManager,
        permissionUsages: PermissionUsages,
        callback: PermissionUsages.PermissionsUsagesChangeCallback,
        filterTimesIndex: Int
    ) {
        val timeFilterItemMs: TimeFilterItemMs = mTimeFilterItemMs[filterTimesIndex]
        val filterTimeBeginMillis = max(System.currentTimeMillis() - timeFilterItemMs.timeMs, 0)
        permissionUsages.load(
            /* filterPackageName= */ null,
            /* filterPermissionGroups= */ null,
            filterTimeBeginMillis,
            Long.MAX_VALUE,
            PermissionUsages.USAGE_FLAG_LAST or PermissionUsages.USAGE_FLAG_HISTORICAL,
            loaderManager,
            /* getUiInfo= */ false,
            /* getNonPlatformPermissions= */ false,
            /* callback= */ callback,
            /* sync= */ false
        )
    }

    /**
     * Create a [PermissionUsageDetailsUiData] based on the provided data.
     *
     * @param appPermissionUsages data about app permission usages
     * @param showSystem whether system apps should be shown
     * @param show7Days whether the last 7 days of history should be shown
     */
    fun buildPermissionUsageDetailsUiData(
        appPermissionUsages: List<AppPermissionUsage>,
        showSystem: Boolean,
        show7Days: Boolean
    ): PermissionUsageDetailsUiData {
        val showPermissionUsagesDuration =
            if (KotlinUtils.is7DayToggleEnabled() && show7Days) {
                TIME_7_DAYS_DURATION
            } else {
                TIME_24_HOURS_DURATION
            }
        val startTime =
            (System.currentTimeMillis() - showPermissionUsagesDuration).coerceAtLeast(
                Instant.EPOCH.toEpochMilli()
            )
        val appPermissionTimelineUsages: List<AppPermissionTimelineUsage> =
            extractAppPermissionTimelineUsagesForGroup(appPermissionUsages, permissionGroup)
        val shouldDisplayShowSystemToggle =
            shouldDisplayShowSystemToggle(appPermissionTimelineUsages)
        val permissionApps: List<PermissionApp> =
            getPermissionAppsWithRecentDiscreteUsage(
                appPermissionTimelineUsages,
                showSystem,
                startTime
            )
        val appPermissionUsageEntries =
            buildDiscreteAccessClusterData(appPermissionTimelineUsages, showSystem, startTime)

        return PermissionUsageDetailsUiData(
            permissionApps,
            shouldDisplayShowSystemToggle,
            appPermissionUsageEntries
        )
    }

    private fun getHistoryPreferenceData(
        discreteAccessClusterData: DiscreteAccessClusterData,
    ): HistoryPreferenceData {
        val context = application
        val accessTimeList =
            discreteAccessClusterData.discreteAccessDataList.map { p -> p.accessTimeMs }
        val durationSummaryLabel =
            getDurationSummary(discreteAccessClusterData, accessTimeList, context)
        val proxyLabel = getProxyPackageLabel(discreteAccessClusterData)
        val subattributionLabel = getSubattributionLabel(discreteAccessClusterData)
        val showingSubattribution = subattributionLabel != null && subattributionLabel.isNotEmpty()
        val summary =
            buildUsageSummary(durationSummaryLabel, proxyLabel, subattributionLabel, context)

        return HistoryPreferenceData(
            UserHandle.getUserHandleForUid(
                discreteAccessClusterData.appPermissionTimelineUsage.permissionApp.uid
            ),
            discreteAccessClusterData.appPermissionTimelineUsage.permissionApp.packageName,
            discreteAccessClusterData.appPermissionTimelineUsage.permissionApp.icon,
            discreteAccessClusterData.appPermissionTimelineUsage.permissionApp.label,
            permissionGroup,
            discreteAccessClusterData.discreteAccessDataList.last().accessTimeMs,
            discreteAccessClusterData.discreteAccessDataList.first().accessTimeMs,
            summary,
            showingSubattribution,
            discreteAccessClusterData.appPermissionTimelineUsage.attributionTags,
            sessionId
        )
    }

    /**
     * Returns whether the provided [AppPermissionUsage] instances contains the provided platform
     * permission group.
     */
    fun containsPlatformAppPermissionGroup(
        appPermissionUsages: List<AppPermissionUsage>,
        groupName: String,
    ) = appPermissionUsages.extractAllPlatformAppPermissionGroups().any { it.name == groupName }

    /** Extracts a list of [AppPermissionTimelineUsage] for a particular permission group. */
    private fun extractAppPermissionTimelineUsagesForGroup(
        appPermissionUsages: List<AppPermissionUsage>,
        group: String
    ): List<AppPermissionTimelineUsage> =
        appPermissionUsages
            .filter { !Utils.getExemptedPackages(roleManager).contains(it.packageName) }
            .map { appPermissionUsage ->
                getAppPermissionTimelineUsages(
                    appPermissionUsage.app,
                    appPermissionUsage.groupUsages.firstOrNull { it.group.name == group }
                )
            }
            .flatten()

    /** Returns whether the show/hide system toggle should be displayed in the UI. */
    private fun shouldDisplayShowSystemToggle(
        appPermissionTimelineUsages: List<AppPermissionTimelineUsage>,
    ): Boolean =
        appPermissionTimelineUsages
            .map { it.timelineUsage }
            .filter { it.hasDiscreteData() }
            .any { it.group.isSystem() }

    /**
     * Returns a list of [PermissionApp] instances which had recent discrete permission usage
     * (recent here refers to usages occurring after the provided start time).
     */
    private fun getPermissionAppsWithRecentDiscreteUsage(
        appPermissionTimelineUsageList: List<AppPermissionTimelineUsage>,
        showSystem: Boolean,
        startTime: Long,
    ): List<PermissionApp> =
        appPermissionTimelineUsageList
            .filter { it.timelineUsage.hasDiscreteData() }
            .filter { showSystem || !it.timelineUsage.group.isSystem() }
            .filter { it.timelineUsage.allDiscreteAccessTime.any { it.first >= startTime } }
            .map { it.permissionApp }

    /**
     * Builds a list of [DiscreteAccessClusterData] from the provided list of
     * [AppPermissionTimelineUsage].
     */
    private fun buildDiscreteAccessClusterData(
        appPermissionTimelineUsageList: List<AppPermissionTimelineUsage>,
        showSystem: Boolean,
        startTime: Long,
    ): List<DiscreteAccessClusterData> =
        appPermissionTimelineUsageList
            .map { appPermissionTimelineUsages ->
                val accessDataList =
                    extractRecentDiscreteAccessData(
                        appPermissionTimelineUsages.timelineUsage,
                        showSystem,
                        startTime
                    )

                if (accessDataList.size <= 1) {
                    return@map accessDataList.map {
                        DiscreteAccessClusterData(appPermissionTimelineUsages, listOf(it))
                    }
                }

                clusterDiscreteAccessData(appPermissionTimelineUsages, accessDataList)
            }
            .flatten()
            .sortedWith(
                compareBy(
                    { -it.discreteAccessDataList.first().accessTimeMs },
                    { it.appPermissionTimelineUsage.permissionApp.label }
                )
            )
            .toList()

    /**
     * Clusters a list of [DiscreteAccessData] into a list of [DiscreteAccessClusterData] instances.
     *
     * [DiscreteAccessData] which have accesses sufficiently close together in time will be places
     * in the same cluster.
     */
    private fun clusterDiscreteAccessData(
        appPermissionTimelineUsage: AppPermissionTimelineUsage,
        discreteAccessDataList: List<DiscreteAccessData>
    ): List<DiscreteAccessClusterData> {
        val clusterDataList = mutableListOf<DiscreteAccessClusterData>()
        val currentDiscreteAccessDataList: MutableList<DiscreteAccessData> = mutableListOf()
        for (discreteAccessData in discreteAccessDataList) {
            if (currentDiscreteAccessDataList.isEmpty()) {
                currentDiscreteAccessDataList.add(discreteAccessData)
            } else if (
                !canAccessBeAddedToCluster(discreteAccessData, currentDiscreteAccessDataList)
            ) {
                clusterDataList.add(
                    DiscreteAccessClusterData(
                        appPermissionTimelineUsage,
                        currentDiscreteAccessDataList.toMutableList()
                    )
                )
                currentDiscreteAccessDataList.clear()
                currentDiscreteAccessDataList.add(discreteAccessData)
            } else {
                currentDiscreteAccessDataList.add(discreteAccessData)
            }
        }
        if (currentDiscreteAccessDataList.isNotEmpty()) {
            clusterDataList.add(
                DiscreteAccessClusterData(appPermissionTimelineUsage, currentDiscreteAccessDataList)
            )
        }
        return clusterDataList
    }

    /**
     * Extract recent [DiscreteAccessData] from a list of [TimelineUsage] instances, and return them
     * ordered descending by access time (recent here refers to accesses occurring after the
     * provided start time).
     */
    private fun extractRecentDiscreteAccessData(
        timelineUsages: TimelineUsage,
        showSystem: Boolean,
        startTime: Long
    ): List<DiscreteAccessData> {
        return if (
            timelineUsages.hasDiscreteData() && (showSystem || !timelineUsages.group.isSystem())
        ) {
            getRecentDiscreteAccessData(timelineUsages, startTime)
                .sortedWith(compareBy { -it.accessTimeMs })
                .toList()
        } else {
            listOf()
        }
    }

    /**
     * Extract recent [DiscreteAccessData] from a [TimelineUsage]. (recent here refers to accesses
     * occurring after the provided start time).
     */
    private fun getRecentDiscreteAccessData(
        timelineUsage: TimelineUsage,
        startTime: Long
    ): List<DiscreteAccessData> {
        return timelineUsage.allDiscreteAccessTime
            .filter { it.first >= startTime }
            .map {
                DiscreteAccessData(
                    it.first,
                    it.second,
                    it.third,
                )
            }
    }

    /**
     * Returns whether the provided [DiscreteAccessData] occurred close enough to those in the
     * clustered list that it can be added to the cluster
     */
    private fun canAccessBeAddedToCluster(
        accessData: DiscreteAccessData,
        clusteredAccessDataList: List<DiscreteAccessData>
    ): Boolean =
        accessData.accessTimeMs / ONE_HOUR_MS ==
            clusteredAccessDataList.first().accessTimeMs / ONE_HOUR_MS &&
            clusteredAccessDataList.last().accessTimeMs / ONE_MINUTE_MS -
                accessData.accessTimeMs / ONE_MINUTE_MS > CLUSTER_SPACING_MINUTES

    /**
     * Returns whether the provided [AppPermissionGroup] is considered a system group.
     *
     * For the purpose of Permissions Hub UI, non user-sensitive [AppPermissionGroup]s are
     * considered "system" and should be hidden from the main page unless requested by the user
     * through the "show/hide system" toggle.
     */
    private fun AppPermissionGroup.isSystem() = !Utils.isGroupOrBgGroupUserSensitive(this)

    /** Returns whether app subattribution should be shown. */
    private fun shouldShowSubattributionForApp(appInfo: ApplicationInfo): Boolean {
        return shouldShowSubattributionInPermissionsDashboard() &&
            SubattributionUtils.isSubattributionSupported(application, appInfo)
    }

    /** Returns a summary of the duration the permission was accessed for. */
    private fun getDurationSummary(
        usage: DiscreteAccessClusterData,
        accessTimeList: List<Long>,
        context: Context
    ): String? {
        if (accessTimeList.isEmpty()) {
            return null
        }

        var durationMs: Long

        // Since Location accesses are atomic, we manually calculate the access duration
        // by comparing the first and last access within the cluster.
        if (permissionGroup == Manifest.permission_group.LOCATION) {
            durationMs = accessTimeList[0] - accessTimeList[accessTimeList.size - 1]
        } else {
            durationMs =
                usage.discreteAccessDataList.map { it.accessDurationMs }.filter { it > 0 }.sum()
        }
        // Only show the duration summary if it is at least (CLUSTER_SPACING_MINUTES + 1) minutes.
        // Displaying a time that is shorter than the cluster granularity
        // (CLUSTER_SPACING_MINUTES) will not convey useful information.
        if (durationMs >= TimeUnit.MINUTES.toMillis(CLUSTER_SPACING_MINUTES + 1)) {
            return getDurationUsedStr(context, durationMs)
        }

        return null
    }

    /** Returns the proxied package label if the permission access was proxied. */
    private fun getProxyPackageLabel(usage: DiscreteAccessClusterData): String? =
        usage.discreteAccessDataList
            .firstOrNull { it.proxy?.packageName != null }
            ?.let {
                getPackageLabel(
                    PermissionControllerApplication.get(),
                    it.proxy!!.packageName!!,
                    UserHandle.getUserHandleForUid(it.proxy.uid)
                )
            }

    /** Returns the attribution label for the permission access, if any. */
    private fun getSubattributionLabel(usage: DiscreteAccessClusterData): String? =
        if (usage.appPermissionTimelineUsage.label == Resources.ID_NULL) null
        else
            usage.appPermissionTimelineUsage.permissionApp.attributionLabels?.let {
                it[usage.appPermissionTimelineUsage.label]
            }

    /** Builds a summary of the permission access. */
    private fun buildUsageSummary(
        subattributionLabel: String?,
        proxyPackageLabel: String?,
        durationSummary: String?,
        context: Context
    ): String? {
        val subTextStrings: MutableList<String?> = mutableListOf()

        subattributionLabel?.let { subTextStrings.add(subattributionLabel) }
        proxyPackageLabel?.let { subTextStrings.add(it) }
        durationSummary?.let { subTextStrings.add(it) }
        return when (subTextStrings.size) {
            3 ->
                context.getString(
                    R.string.history_preference_subtext_3,
                    subTextStrings[0],
                    subTextStrings[1],
                    subTextStrings[2]
                )
            2 ->
                context.getString(
                    R.string.history_preference_subtext_2,
                    subTextStrings[0],
                    subTextStrings[1]
                )
            1 -> subTextStrings[0]
            else -> null
        }
    }

    /**
     * Builds a list of [AppPermissionTimelineUsage] from the provided
     * [AppPermissionUsage.GroupUsage].
     */
    private fun getAppPermissionTimelineUsages(
        app: PermissionApp,
        groupUsage: AppPermissionUsage.GroupUsage?
    ): List<AppPermissionTimelineUsage> {
        if (groupUsage == null) {
            return listOf()
        }

        if (shouldShowSubattributionForApp(app.appInfo)) {
            return groupUsage.attributionLabelledGroupUsages.map {
                AppPermissionTimelineUsage(permissionGroup, app, it, it.label)
            }
        }

        return listOf(
            AppPermissionTimelineUsage(permissionGroup, app, groupUsage, Resources.ID_NULL)
        )
    }

    /** Extracts to a set all the permission groups declared by the platform. */
    private fun List<AppPermissionUsage>.extractAllPlatformAppPermissionGroups():
        Set<AppPermissionGroup> =
        this.flatMap { it.groupUsages }
            .map { it.group }
            .filter { PermissionMapping.isPlatformPermissionGroup(it.name) }
            .toSet()

    /** Initialize all relevant [TimeFilterItemMs] values. */
    private fun initializeTimeFilterItems(context: Context) {
        mTimeFilterItemMs.add(
            TimeFilterItemMs(Long.MAX_VALUE, context.getString(R.string.permission_usage_any_time))
        )
        mTimeFilterItemMs.add(
            TimeFilterItemMs(
                DAYS.toMillis(7),
                StringUtils.getIcuPluralsString(context, R.string.permission_usage_last_n_days, 7)
            )
        )
        mTimeFilterItemMs.add(
            TimeFilterItemMs(
                DAYS.toMillis(1),
                StringUtils.getIcuPluralsString(context, R.string.permission_usage_last_n_days, 1)
            )
        )

        // TODO: theianchen add code for filtering by time here.
    }

    /** Data used to create a preference for an app's permission usage. */
    data class HistoryPreferenceData(
        val userHandle: UserHandle,
        val pkgName: String,
        val appIcon: Drawable?,
        val preferenceTitle: String,
        val permissionGroup: String,
        val accessStartTime: Long,
        val accessEndTime: Long,
        val summaryText: CharSequence?,
        val showingAttribution: Boolean,
        val attributionTags: ArrayList<String>,
        val sessionId: Long
    )

    /**
     * A class representing a given time, e.g., "in the last hour".
     *
     * @param timeMs the time represented by this object in milliseconds.
     * @param label the label to describe the timeframe
     */
    data class TimeFilterItemMs(val timeMs: Long, val label: String)

    /**
     * Class containing all the information needed by the permission usage details fragments to
     * render UI.
     */
    inner class PermissionUsageDetailsUiData(
        /** List of [PermissionApp] instances */
        // Note that these are used only to cache app data for the permission usage details
        // fragment, and have no bearing on the UI on the main permission usage page.
        val permissionApps: List<PermissionApp>,
        /** Whether to show the "show/hide system" toggle. */
        val shouldDisplayShowSystemToggle: Boolean,
        /** [DiscreteAccessClusterData] instances ordered for display in UI */
        private val discreteAccessClusterDataList: List<DiscreteAccessClusterData>,
    ) {
        // Note that the HistoryPreferenceData are not initialized within the
        // PermissionUsageDetailsUiData instance as the need to be constructed only after the
        // calling fragment loads the necessary PermissionApp instances. We will attempt to remove
        // this dependency in b/240978905.
        /** Builds a list of [HistoryPreferenceData] to be displayed in the UI. */
        fun getHistoryPreferenceDataList(): List<HistoryPreferenceData> {
            return discreteAccessClusterDataList.map {
                this@PermissionUsageDetailsViewModelLegacy.getHistoryPreferenceData(it)
            }
        }
    }

    /**
     * Data class representing a cluster of accesses, to be represented as a single entry in the UI.
     */
    data class DiscreteAccessClusterData(
        val appPermissionTimelineUsage: AppPermissionTimelineUsage,
        val discreteAccessDataList: List<DiscreteAccessData>
    )

    /** Data class representing a discrete permission access. */
    data class DiscreteAccessData(
        val accessTimeMs: Long,
        val accessDurationMs: Long,
        val proxy: AppOpsManager.OpEventProxyInfo?
    )

    /** Data class representing an app's permissions usages for a particular permission group. */
    data class AppPermissionTimelineUsage(
        /** Permission group whose usage is being tracked. */
        val permissionGroup: String,
        // we need a PermissionApp because the loader takes the PermissionApp
        // object and loads the icon and label information asynchronously
        /** App whose permissions are being tracked. */
        val permissionApp: PermissionApp,
        /** Timeline usage for the given app and permission. */
        val timelineUsage: TimelineUsage,
        val label: Int
    ) {
        val attributionTags: java.util.ArrayList<String>
            get() = ArrayList(timelineUsage.attributionTags)
    }
}

/** Factory for an [PermissionUsageDetailsViewModelLegacy] */
@RequiresApi(Build.VERSION_CODES.S)
class PermissionUsageDetailsViewModelFactoryLegacy(
    private val application: Application,
    private val roleManager: RoleManager,
    private val filterGroup: String,
    private val sessionId: Long
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PermissionUsageDetailsViewModelLegacy(
            application,
            roleManager,
            filterGroup,
            sessionId
        )
            as T
    }
}
