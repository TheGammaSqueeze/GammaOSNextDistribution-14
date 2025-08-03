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

package com.android.permissioncontroller.permission.ui.model.v31

import android.Manifest
import android.app.AppOpsManager
import android.app.AppOpsManager.OPSTR_PHONE_CALL_CAMERA
import android.app.AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE
import android.app.Application
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.compat.IntentCompat
import com.android.permissioncontroller.permission.data.AppPermGroupUiInfoLiveData
import com.android.permissioncontroller.permission.data.LightPackageInfoLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.v31.AllLightHistoricalPackageOpsLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.model.livedatatypes.v31.AppPermissionId
import com.android.permissioncontroller.permission.model.livedatatypes.v31.LightHistoricalPackageOps
import com.android.permissioncontroller.permission.model.livedatatypes.v31.LightHistoricalPackageOps.AppPermissionDiscreteAccesses
import com.android.permissioncontroller.permission.model.livedatatypes.v31.LightHistoricalPackageOps.AttributedAppPermissionDiscreteAccesses
import com.android.permissioncontroller.permission.model.livedatatypes.v31.LightHistoricalPackageOps.Companion.NO_ATTRIBUTION_TAG
import com.android.permissioncontroller.permission.model.livedatatypes.v31.LightHistoricalPackageOps.DiscreteAccess
import com.android.permissioncontroller.permission.ui.handheld.v31.getDurationUsedStr
import com.android.permissioncontroller.permission.ui.handheld.v31.shouldShowSubattributionInPermissionsDashboard
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.v31.SubattributionUtils
import java.time.Instant
import java.util.Objects
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.DAYS

/** [ViewModel] for the Permission Usage Details page. */
@RequiresApi(Build.VERSION_CODES.S)
class PermissionUsageDetailsViewModel(
    val application: Application,
    private val state: SavedStateHandle,
    private val permissionGroup: String,
) : ViewModel() {

    val allLightHistoricalPackageOpsLiveData =
        AllLightHistoricalPackageOpsLiveData(application, opNames)
    private val appPermGroupUiInfoLiveDataList =
        mutableMapOf<AppPermissionId, AppPermGroupUiInfoLiveData>()
    private val lightPackageInfoLiveDataMap =
        mutableMapOf<Pair<String, UserHandle>, LightPackageInfoLiveData>()
    val showSystemLiveData = state.getLiveData(SHOULD_SHOW_SYSTEM_KEY, false)
    val show7DaysLiveData = state.getLiveData(SHOULD_SHOW_7_DAYS_KEY, false)

    private val packageIconCache: MutableMap<Pair<String, UserHandle>, Drawable> = mutableMapOf()
    private val packageLabelCache: MutableMap<String, String> = mutableMapOf()

    private val roleManager =
        Utils.getSystemServiceSafe(application.applicationContext, RoleManager::class.java)
    private val userManager =
        Utils.getSystemServiceSafe(application.applicationContext, UserManager::class.java)

    /** Updates whether system app permissions usage should be displayed in the UI. */
    fun updateShowSystemAppsToggle(showSystem: Boolean) {
        if (showSystem != state[SHOULD_SHOW_SYSTEM_KEY]) {
            state[SHOULD_SHOW_SYSTEM_KEY] = showSystem
        }
    }

    /** Updates whether 7 days usage or 1 day usage should be displayed in the UI. */
    fun updateShow7DaysToggle(show7Days: Boolean) {
        if (show7Days != state[SHOULD_SHOW_7_DAYS_KEY]) {
            state[SHOULD_SHOW_7_DAYS_KEY] = show7Days
        }
    }

    /** Creates a [PermissionUsageDetailsUiInfo] containing all information to render the UI. */
    fun buildPermissionUsageDetailsUiInfo(): PermissionUsageDetailsUiInfo {
        val showSystem: Boolean = state[SHOULD_SHOW_SYSTEM_KEY] ?: false
        val show7Days: Boolean = state[SHOULD_SHOW_7_DAYS_KEY] ?: false
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

        return PermissionUsageDetailsUiInfo(
            show7Days,
            showSystem,
            buildAppPermissionAccessUiInfoList(
                allLightHistoricalPackageOpsLiveData,
                startTime,
                showSystem
            ),
            containsSystemAppUsages(allLightHistoricalPackageOpsLiveData, startTime)
        )
    }

    /**
     * Returns whether the "show/hide system" toggle should be displayed in the UI for the provided
     * [AllLightHistoricalPackageOpsLiveData].
     */
    private fun containsSystemAppUsages(
        allLightHistoricalPackageOpsLiveData: AllLightHistoricalPackageOpsLiveData,
        startTime: Long
    ): Boolean {
        return allLightHistoricalPackageOpsLiveData
            .getLightHistoricalPackageOps()
            ?.flatMap {
                it.appPermissionDiscreteAccesses
                    .map { it.withLabel() }
                    .filterOutExemptAppPermissions(true)
                    .filterAccessesLaterThan(startTime)
            }
            ?.any { isAppPermissionSystem(it.appPermissionId) }
            ?: false
    }

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

    /**
     * Extracts access data from [AllLightHistoricalPackageOpsLiveData] and composes
     * [AppPermissionAccessUiInfo]s to be displayed in the UI.
     */
    private fun buildAppPermissionAccessUiInfoList(
        allLightHistoricalPackageOpsLiveData: AllLightHistoricalPackageOpsLiveData,
        startTime: Long,
        showSystem: Boolean
    ): List<AppPermissionAccessUiInfo> {
        return allLightHistoricalPackageOpsLiveData
            .getLightHistoricalPackageOps()
            ?.filter { Utils.shouldShowInSettings(it.userHandle, userManager) }
            ?.flatMap { it.clusterAccesses(startTime, showSystem) }
            ?.sortedBy { -1 * it.discreteAccesses.first().accessTimeMs }
            ?.map { it.buildAppPermissionAccessUiInfo() }
            ?: listOf()
    }

    private fun LightHistoricalPackageOps.clusterAccesses(
        startTime: Long,
        showSystem: Boolean
    ): List<AppPermissionDiscreteAccessCluster> {
        return if (!shouldShowSubAttributionForApp(getLightPackageInfo(packageName, userHandle)))
            this.clusterAccessesWithoutAttribution(startTime, showSystem)
        else {
            this.clusterAccessesWithAttribution(startTime, showSystem)
        }
    }

    /**
     * Clusters accesses that are close enough together in time such that they can be displayed as a
     * single access to the user.
     *
     * Accesses are clustered taking into account any app subattribution, so each cluster will
     * pertain a particular attribution label.
     */
    private fun LightHistoricalPackageOps.clusterAccessesWithAttribution(
        startTime: Long,
        showSystem: Boolean
    ): List<AppPermissionDiscreteAccessCluster> =
        this.attributedAppPermissionDiscreteAccesses
            .flatMap { it.groupAccessesByLabel(getLightPackageInfo(packageName, userHandle)) }
            .filterOutExemptAppPermissions(showSystem)
            .filterAccessesLaterThan(startTime)
            .flatMap { createAccessClusters(it) }

    /**
     * Clusters accesses that are close enough together in time such that they can be displayed as a
     * single access to the user.
     *
     * Accesses are clustered disregarding any app subattribution.
     */
    private fun LightHistoricalPackageOps.clusterAccessesWithoutAttribution(
        startTime: Long,
        showSystem: Boolean
    ): List<AppPermissionDiscreteAccessCluster> =
        this.appPermissionDiscreteAccesses
            .map { it.withLabel() }
            .filterOutExemptAppPermissions(showSystem)
            .filterAccessesLaterThan(startTime)
            .flatMap { createAccessClusters(it) }

    /** Filters out accesses earlier than the provided start time. */
    private fun List<AppPermissionDiscreteAccessesWithLabel>.filterAccessesLaterThan(
        startTime: Long,
    ): List<AppPermissionDiscreteAccessesWithLabel> =
        this.mapNotNull {
            val updatedDiscreteAccesses =
                it.discreteAccesses.filter { access -> access.accessTimeMs > startTime }
            if (updatedDiscreteAccesses.isEmpty()) null
            else
                AppPermissionDiscreteAccessesWithLabel(
                    it.appPermissionId,
                    it.attributionLabel,
                    it.attributionTags,
                    updatedDiscreteAccesses
                )
        }

    /** Filters out data for apps and permissions that don't need to be displayed in the UI. */
    private fun List<AppPermissionDiscreteAccessesWithLabel>.filterOutExemptAppPermissions(
        showSystem: Boolean
    ): List<AppPermissionDiscreteAccessesWithLabel> {
        return this.filter {
                !Utils.getExemptedPackages(roleManager).contains(it.appPermissionId.packageName)
            }
            .filter { it.appPermissionId.permissionGroup == permissionGroup }
            .filter { isPermissionRequestedByApp(it.appPermissionId) }
            .filter { showSystem || !isAppPermissionSystem(it.appPermissionId) }
    }

    /**
     * Converts the provided [AppPermissionDiscreteAccesses] to a
     * [AppPermissionDiscreteAccessesWithLabel] by adding a label.
     */
    private fun AppPermissionDiscreteAccesses.withLabel(): AppPermissionDiscreteAccessesWithLabel =
        AppPermissionDiscreteAccessesWithLabel(
            this.appPermissionId,
            Resources.ID_NULL,
            attributionTags = emptyList(),
            this.discreteAccesses
        )

    /** Groups tag-attributed accesses for the provided app and permission by attribution label. */
    private fun AttributedAppPermissionDiscreteAccesses.groupAccessesByLabel(
        lightPackageInfo: LightPackageInfo?
    ): List<AppPermissionDiscreteAccessesWithLabel> {
        if (lightPackageInfo == null) return emptyList()

        val appPermissionId = this.appPermissionId
        val labelsToDiscreteAccesses = mutableMapOf<Int, MutableList<DiscreteAccess>>()
        val labelsToTags = mutableMapOf<Int, MutableList<String>>()

        val appPermissionDiscreteAccessWithLabels =
            mutableListOf<AppPermissionDiscreteAccessesWithLabel>()

        for ((tag, discreteAccesses) in this.attributedDiscreteAccesses) {
            val label: Int =
                if (tag == NO_ATTRIBUTION_TAG) Resources.ID_NULL
                else lightPackageInfo.attributionTagsToLabels[tag] ?: Resources.ID_NULL

            if (!labelsToDiscreteAccesses.containsKey(label)) {
                labelsToDiscreteAccesses[label] = mutableListOf()
            }
            labelsToDiscreteAccesses[label]?.addAll(discreteAccesses)

            if (!labelsToTags.containsKey(label)) {
                labelsToTags[label] = mutableListOf()
            }
            labelsToTags[label]?.add(tag)
        }

        for ((label, discreteAccesses) in labelsToDiscreteAccesses.entries) {
            val tags = labelsToTags[label]?.toList() ?: listOf()

            appPermissionDiscreteAccessWithLabels.add(
                AppPermissionDiscreteAccessesWithLabel(
                    appPermissionId,
                    label,
                    tags,
                    discreteAccesses.sortedBy { -1 * it.accessTimeMs }
                )
            )
        }

        return appPermissionDiscreteAccessWithLabels
    }

    /**
     * Clusters [DiscreteAccess]es represented by a [AppPermissionDiscreteAccessesWithLabel] into
     * smaller groups to form a list of [AppPermissionDiscreteAccessCluster] instances.
     *
     * [DiscreteAccess]es which have accesses sufficiently close together in time will be places in
     * the same cluster.
     */
    private fun createAccessClusters(
        appPermAccesses: AppPermissionDiscreteAccessesWithLabel,
    ): List<AppPermissionDiscreteAccessCluster> {
        val clusters = mutableListOf<AppPermissionDiscreteAccessCluster>()
        val currentDiscreteAccesses = mutableListOf<DiscreteAccess>()
        for (discreteAccess in appPermAccesses.discreteAccesses) {
            if (currentDiscreteAccesses.isEmpty()) {
                currentDiscreteAccesses.add(discreteAccess)
            } else if (!canAccessBeAddedToCluster(discreteAccess, currentDiscreteAccesses)) {
                clusters.add(
                    AppPermissionDiscreteAccessCluster(
                        appPermAccesses.appPermissionId,
                        appPermAccesses.attributionLabel,
                        appPermAccesses.attributionTags,
                        currentDiscreteAccesses.toMutableList()
                    )
                )
                currentDiscreteAccesses.clear()
                currentDiscreteAccesses.add(discreteAccess)
            } else {
                currentDiscreteAccesses.add(discreteAccess)
            }
        }

        if (currentDiscreteAccesses.isNotEmpty()) {
            clusters.add(
                AppPermissionDiscreteAccessCluster(
                    appPermAccesses.appPermissionId,
                    appPermAccesses.attributionLabel,
                    appPermAccesses.attributionTags,
                    currentDiscreteAccesses.toMutableList()
                )
            )
        }
        return clusters
    }

    /**
     * Returns whether the provided [DiscreteAccess] occurred close enough to those in the clustered
     * list that it can be added to the cluster.
     */
    private fun canAccessBeAddedToCluster(
        discreteAccess: DiscreteAccess,
        clusteredAccesses: List<DiscreteAccess>
    ): Boolean =
        discreteAccess.accessTimeMs / ONE_HOUR_MS ==
            clusteredAccesses.first().accessTimeMs / ONE_HOUR_MS &&
            clusteredAccesses.last().accessTimeMs / ONE_MINUTE_MS -
                discreteAccess.accessTimeMs / ONE_MINUTE_MS <= CLUSTER_SPACING_MINUTES

    /**
     * Composes all UI information from a [AppPermissionDiscreteAccessCluster] into a
     * [AppPermissionAccessUiInfo].
     */
    private fun AppPermissionDiscreteAccessCluster.buildAppPermissionAccessUiInfo():
        AppPermissionAccessUiInfo {
        val context = application
        val accessTimeList = this.discreteAccesses.map { it.accessTimeMs }
        val durationSummaryLabel = getDurationSummary(context, this, accessTimeList)
        val proxyLabel = getProxyPackageLabel(this)
        val subAttributionLabel = getSubAttributionLabel(this)
        val showingSubAttribution = subAttributionLabel != null && subAttributionLabel.isNotEmpty()
        val summary =
            buildUsageSummary(context, subAttributionLabel, proxyLabel, durationSummaryLabel)

        return AppPermissionAccessUiInfo(
            this.appPermissionId.userHandle,
            this.appPermissionId.packageName,
            getPackageLabel(this.appPermissionId.packageName, this.appPermissionId.userHandle),
            permissionGroup,
            this.discreteAccesses.last().accessTimeMs,
            this.discreteAccesses.first().accessTimeMs,
            summary,
            showingSubAttribution,
            ArrayList(this.attributionTags),
            getBadgedPackageIcon(this.appPermissionId.packageName, this.appPermissionId.userHandle)
        )
    }

    /** Builds a summary of the permission access. */
    private fun buildUsageSummary(
        context: Context,
        subAttributionLabel: String?,
        proxyPackageLabel: String?,
        durationSummary: String?
    ): String? {
        val subTextStrings: MutableList<String> = mutableListOf()

        subAttributionLabel?.let { subTextStrings.add(subAttributionLabel) }
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

    /** Returns whether app subattribution should be shown. */
    private fun shouldShowSubAttributionForApp(lightPackageInfo: LightPackageInfo?): Boolean {
        return lightPackageInfo != null &&
            shouldShowSubattributionInPermissionsDashboard() &&
            SubattributionUtils.isSubattributionSupported(lightPackageInfo)
    }

    /** Returns a summary of the duration the permission was accessed for. */
    private fun getDurationSummary(
        context: Context,
        accessCluster: AppPermissionDiscreteAccessCluster,
        accessTimeList: List<Long>,
    ): String? {
        if (accessTimeList.isEmpty()) {
            return null
        }
        // Since Location accesses are atomic, we manually calculate the access duration by
        // comparing the first and last access within the cluster.
        val durationMs: Long =
            if (permissionGroup == Manifest.permission_group.LOCATION) {
                accessTimeList[0] - accessTimeList[accessTimeList.size - 1]
            } else {
                accessCluster.discreteAccesses
                    .filter { it.accessDurationMs > 0 }
                    .sumOf { it.accessDurationMs }
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
    private fun getProxyPackageLabel(accessCluster: AppPermissionDiscreteAccessCluster): String? =
        accessCluster.discreteAccesses
            .firstOrNull { it.proxy?.packageName != null }
            ?.let {
                getPackageLabel(
                    it.proxy!!.packageName!!,
                    UserHandle.getUserHandleForUid(it.proxy.uid)
                )
            }

    /** Returns the attribution label for the permission access, if any. */
    private fun getSubAttributionLabel(accessCluster: AppPermissionDiscreteAccessCluster): String? =
        if (accessCluster.attributionLabel == Resources.ID_NULL) null
        else {
            val lightPackageInfo = getLightPackageInfo(accessCluster.appPermissionId)
            getSubAttributionLabels(lightPackageInfo)?.get(accessCluster.attributionLabel)
        }

    private fun getSubAttributionLabels(lightPackageInfo: LightPackageInfo?): Map<Int, String>? =
        if (lightPackageInfo == null) null
        else SubattributionUtils.getAttributionLabels(application, lightPackageInfo)

    private fun getLightPackageInfo(appPermissionId: AppPermissionId) =
        lightPackageInfoLiveDataMap[Pair(appPermissionId.packageName, appPermissionId.userHandle)]
            ?.value

    private fun getLightPackageInfo(packageName: String, userHandle: UserHandle) =
        lightPackageInfoLiveDataMap[Pair(packageName, userHandle)]?.value

    private fun AllLightHistoricalPackageOpsLiveData.getLightHistoricalPackageOps() =
        this.value?.values

    /** Data used to create a preference for an app's permission usage. */
    data class AppPermissionAccessUiInfo(
        val userHandle: UserHandle,
        val packageName: String,
        val packageLabel: String,
        val permissionGroup: String,
        val accessStartTime: Long,
        val accessEndTime: Long,
        val summaryText: CharSequence?,
        val showingAttribution: Boolean,
        val attributionTags: ArrayList<String>,
        val badgedPackageIcon: Drawable?,
    )

    /**
     * Class containing all the information needed by the permission usage details fragments to
     * render UI.
     */
    data class PermissionUsageDetailsUiInfo(
        /**
         * Whether to show data over the last 7 days.
         *
         * While this information is available from the [SHOULD_SHOW_7_DAYS_KEY] state, we include
         * it in the UI info so that it triggers a UI update when changed.
         */
        private val show7Days: Boolean,
        /**
         * Whether to show system apps' data.
         *
         * While this information is available from the [SHOULD_SHOW_SYSTEM_KEY] state, we include
         * it in the UI info so that it triggers a UI update when changed.
         */
        private val showSystem: Boolean,
        /** List of [AppPermissionAccessUiInfo]s to be displayed in the UI. */
        val appPermissionAccessUiInfoList: List<AppPermissionAccessUiInfo>,
        /** Whether to show the "show/hide system" toggle. */
        val containsSystemAppAccesses: Boolean,
    )

    /**
     * Data class representing a cluster of permission accesses close enough together to be
     * displayed as a single access in the UI.
     */
    private data class AppPermissionDiscreteAccessCluster(
        val appPermissionId: AppPermissionId,
        val attributionLabel: Int,
        val attributionTags: List<String>,
        val discreteAccesses: List<DiscreteAccess>,
    )

    /**
     * Data class representing all permission accesses for a particular package, user, permission
     * and attribution label.
     */
    private data class AppPermissionDiscreteAccessesWithLabel(
        val appPermissionId: AppPermissionId,
        val attributionLabel: Int,
        val attributionTags: List<String>,
        val discreteAccesses: List<DiscreteAccess>
    )

    /** [LiveData] object for [PermissionUsageDetailsUiInfo]. */
    val permissionUsagesDetailsInfoUiLiveData =
        object : SmartUpdateMediatorLiveData<@JvmSuppressWildcards PermissionUsageDetailsUiInfo>() {
            private val getAppPermGroupUiInfoLiveData = { appPermissionId: AppPermissionId ->
                AppPermGroupUiInfoLiveData[
                    Triple(
                        appPermissionId.packageName,
                        appPermissionId.permissionGroup,
                        appPermissionId.userHandle,
                    )]
            }
            private val getLightPackageInfoLiveData =
                { packageWithUserHandle: Pair<String, UserHandle> ->
                    LightPackageInfoLiveData[packageWithUserHandle]
                }

            init {
                addSource(allLightHistoricalPackageOpsLiveData) { update() }
                addSource(showSystemLiveData) { update() }
                addSource(show7DaysLiveData) { update() }
            }

            override fun onUpdate() {
                if (!allLightHistoricalPackageOpsLiveData.isInitialized) {
                    return
                }

                val appPermissionIds = mutableSetOf<AppPermissionId>()
                val allPackages: Set<Pair<String, UserHandle>> =
                    allLightHistoricalPackageOpsLiveData.value?.keys ?: setOf()
                for (packageWithUserHandle: Pair<String, UserHandle> in allPackages) {
                    val appPermGroupIds =
                        allLightHistoricalPackageOpsLiveData.value
                            ?.get(packageWithUserHandle)
                            ?.appPermissionDiscreteAccesses
                            ?.map { it.appPermissionId }
                            ?.toSet()
                            ?: setOf()

                    appPermissionIds.addAll(appPermGroupIds)
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

                if (appPermGroupUiInfoLiveDataList.any { it.value.isStale }) {
                    return
                }

                if (lightPackageInfoLiveDataMap.any { it.value.isStale }) {
                    return
                }

                value = buildPermissionUsageDetailsUiInfo()
            }
        }

    /**
     * Returns the icon for the provided package name and user, by first searching the cache
     * otherwise retrieving it from the app's [android.content.pm.ApplicationInfo].
     */
    private fun getBadgedPackageIcon(packageName: String, userHandle: UserHandle): Drawable? {
        val packageNameWithUser: Pair<String, UserHandle> = Pair(packageName, userHandle)
        if (packageIconCache.containsKey(packageNameWithUser)) {
            return requireNotNull(packageIconCache[packageNameWithUser])
        }
        val packageIcon = KotlinUtils.getBadgedPackageIcon(application, packageName, userHandle)
        if (packageIcon != null) packageIconCache[packageNameWithUser] = packageIcon

        return packageIcon
    }

    /**
     * Returns the label for the provided package name, by first searching the cache otherwise
     * retrieving it from the app's [android.content.pm.ApplicationInfo].
     */
    private fun getPackageLabel(packageName: String, user: UserHandle): String {
        if (packageLabelCache.containsKey(packageName)) {
            return requireNotNull(packageLabelCache[packageName])
        }

        val packageLabel = KotlinUtils.getPackageLabel(application, packageName, user)
        packageLabelCache[packageName] = packageLabel

        return packageLabel
    }

    /** Companion object for [PermissionUsageDetailsViewModel]. */
    companion object {
        private const val ONE_HOUR_MS = 3_600_000
        private const val ONE_MINUTE_MS = 60_000
        private const val CLUSTER_SPACING_MINUTES: Long = 1L
        private const val TELECOM_PACKAGE = "com.android.server.telecom"
        private val TIME_7_DAYS_DURATION: Long = DAYS.toMillis(7)
        private val TIME_24_HOURS_DURATION: Long = DAYS.toMillis(1)
        internal const val SHOULD_SHOW_SYSTEM_KEY = "showSystem"
        internal const val SHOULD_SHOW_7_DAYS_KEY = "show7Days"

        /** Returns all op names for all permissions in a list of permission groups. */
        val opNames =
            listOf(
                    Manifest.permission_group.CAMERA,
                    Manifest.permission_group.LOCATION,
                    Manifest.permission_group.MICROPHONE
                )
                .flatMap { group -> PermissionMapping.getPlatformPermissionNamesOfGroup(group) }
                .mapNotNull { permName -> AppOpsManager.permissionToOp(permName) }
                .toMutableSet()
                .apply {
                    add(OPSTR_PHONE_CALL_MICROPHONE)
                    add(OPSTR_PHONE_CALL_CAMERA)
                    if (SdkLevel.isAtLeastT()) {
                        add(AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO)
                    }
                }

        /** Creates the [Intent] for the click action of a privacy dashboard app usage event. */
        fun createHistoryPreferenceClickIntent(
            context: Context,
            userHandle: UserHandle,
            packageName: String,
            permissionGroup: String,
            accessStartTime: Long,
            accessEndTime: Long,
            showingAttribution: Boolean,
            attributionTags: List<String>
        ): Intent {
            return getManagePermissionUsageIntent(
                context,
                packageName,
                permissionGroup,
                accessStartTime,
                accessEndTime,
                showingAttribution,
                attributionTags
            )
                ?: getDefaultManageAppPermissionsIntent(packageName, userHandle)
        }

        /**
         * Gets an [Intent.ACTION_MANAGE_PERMISSION_USAGE] intent, or null if attribution shouldn't
         * be shown or the intent can't be handled.
         */
        private fun getManagePermissionUsageIntent(
            context: Context,
            packageName: String,
            permissionGroup: String,
            accessStartTime: Long,
            accessEndTime: Long,
            showingAttribution: Boolean,
            attributionTags: List<String>
        ): Intent? {
            // TODO(b/255992934) only location provider apps should be able to provide this intent
            if (!showingAttribution || !SdkLevel.isAtLeastT()) {
                return null
            }
            val intent =
                Intent(Intent.ACTION_MANAGE_PERMISSION_USAGE).apply {
                    setPackage(packageName)
                    putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, permissionGroup)
                    putExtra(Intent.EXTRA_ATTRIBUTION_TAGS, attributionTags.toTypedArray())
                    putExtra(Intent.EXTRA_START_TIME, accessStartTime)
                    putExtra(Intent.EXTRA_END_TIME, accessEndTime)
                    putExtra(IntentCompat.EXTRA_SHOWING_ATTRIBUTION, showingAttribution)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val resolveInfo =
                context.packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            if (
                resolveInfo?.activityInfo == null ||
                    !Objects.equals(
                        resolveInfo.activityInfo.permission,
                        Manifest.permission.START_VIEW_PERMISSION_USAGE
                    )
            ) {
                return null
            }
            intent.component = ComponentName(packageName, resolveInfo.activityInfo.name)
            return intent
        }

        private fun getDefaultManageAppPermissionsIntent(
            packageName: String,
            userHandle: UserHandle
        ): Intent {
            return Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS).apply {
                putExtra(Intent.EXTRA_USER, userHandle)
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            }
        }
    }

    /** Factory for [PermissionUsageDetailsViewModel]. */
    @RequiresApi(Build.VERSION_CODES.S)
    class PermissionUsageDetailsViewModelFactory(
        val app: Application,
        owner: SavedStateRegistryOwner,
        private val permissionGroup: String,
    ) : AbstractSavedStateViewModelFactory(owner, Bundle()) {
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle,
        ): T {
            @Suppress("UNCHECKED_CAST")
            return PermissionUsageDetailsViewModel(app, handle, permissionGroup) as T
        }
    }
}
