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

package com.android.permissioncontroller.permission.ui.model

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.Manifest.permission_group.LOCATION
import android.Manifest.permission_group.NOTIFICATIONS
import android.Manifest.permission_group.READ_MEDIA_AURAL
import android.Manifest.permission_group.READ_MEDIA_VISUAL
import android.Manifest.permission_group.STORAGE
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_POLICY_FIXED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.health.connect.HealthConnectManager.ACTION_REQUEST_HEALTH_PERMISSIONS
import android.health.connect.HealthConnectManager.isHealthPermission
import android.health.connect.HealthPermissions.HEALTH_PERMISSION_GROUP
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.permission.PermissionManager
import android.util.Log
import androidx.core.util.Consumer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.modules.utils.build.SdkLevel
import com.android.permission.safetylabel.SafetyLabel
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.DeviceUtils
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.GRANT_PERMISSIONS_ACTIVITY_BUTTON_ACTIONS
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_DENIED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_GRANTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_POLICY_FIXED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_RESTRICTED_PERMISSION
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_USER_FIXED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__PHOTOS_SELECTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_IN_SETTINGS
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE_IN_SETTINGS
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_IN_SETTINGS
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_ONE_TIME
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_IGNORED
import com.android.permissioncontroller.auto.DrivingDecisionReminderService
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.LightPackageInfoLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.v34.SafetyLabelInfoLiveData
import com.android.permissioncontroller.permission.model.AppPermissionGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermGroupInfo
import com.android.permissioncontroller.permission.service.PermissionChangeStorageImpl
import com.android.permissioncontroller.permission.service.v33.PermissionDecisionStorageImpl
import com.android.permissioncontroller.permission.ui.AutoGrantPermissionsNotifier
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.INTENT_PHOTOS_SELECTED
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.CANCELED
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_MORE
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ONE_TIME
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_USER_SELECTED
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_INTERACTED
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_RESULT
import com.android.permissioncontroller.permission.ui.model.grantPermissions.BackgroundGrantBehavior
import com.android.permissioncontroller.permission.ui.model.grantPermissions.BasicGrantBehavior
import com.android.permissioncontroller.permission.ui.model.grantPermissions.GrantBehavior
import com.android.permissioncontroller.permission.ui.model.grantPermissions.HealthGrantBehavior
import com.android.permissioncontroller.permission.ui.model.grantPermissions.LocationGrantBehavior
import com.android.permissioncontroller.permission.ui.model.grantPermissions.NotificationGrantBehavior
import com.android.permissioncontroller.permission.ui.model.grantPermissions.StorageGrantBehavior
import com.android.permissioncontroller.permission.ui.v34.PermissionRationaleActivity
import com.android.permissioncontroller.permission.utils.ContextCompat
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.KotlinUtils.grantBackgroundRuntimePermissions
import com.android.permissioncontroller.permission.utils.KotlinUtils.grantForegroundRuntimePermissions
import com.android.permissioncontroller.permission.utils.KotlinUtils.openPhotoPickerForApp
import com.android.permissioncontroller.permission.utils.KotlinUtils.revokeBackgroundRuntimePermissions
import com.android.permissioncontroller.permission.utils.KotlinUtils.revokeForegroundRuntimePermissions
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.PermissionMapping.getPartialStorageGrantPermissionsForGroup
import com.android.permissioncontroller.permission.utils.SafetyNetLogger
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.v31.AdminRestrictedPermissionsUtils
import com.android.permissioncontroller.permission.utils.v34.SafetyLabelUtils

/**
 * ViewModel for the GrantPermissionsActivity. Tracks all permission groups that are affected by the
 * permissions requested by the user, and generates a RequestInfo object for each group, if action
 * is needed. It will not return any data if one of the requests is malformed.
 *
 * @param app: The current application
 * @param packageName: The packageName permissions are being requested for
 * @param requestedPermissions: The list of permissions requested
 * @param systemRequestedPermissions: The list of permissions requested as a result of a system
 *   triggered dialog, not an app-triggered dialog
 * @param sessionId: A long to identify this session
 * @param storedState: Previous state, if this activity was stopped and is being recreated
 */
class GrantPermissionsViewModel(
    private val app: Application,
    private val packageName: String,
    private val deviceId: Int,
    private val requestedPermissions: List<String>,
    private val systemRequestedPermissions: List<String>,
    private val sessionId: Long,
    private val storedState: Bundle?
) : ViewModel() {
    private val LOG_TAG = GrantPermissionsViewModel::class.java.simpleName
    private val user = Process.myUserHandle()
    private val packageInfoLiveData = LightPackageInfoLiveData[packageName, user, deviceId]
    private val safetyLabelInfoLiveData =
        if (
            SdkLevel.isAtLeastU() &&
                requestedPermissions
                    .mapNotNull { PermissionMapping.getGroupOfPlatformPermission(it) }
                    .any { PermissionMapping.isSafetyLabelAwarePermissionGroup(it) }
        ) {
            SafetyLabelInfoLiveData[packageName, user]
        } else {
            null
        }
    private val dpm = app.getSystemService(DevicePolicyManager::class.java)!!
    private val permissionPolicy = dpm.getPermissionPolicy(null)
    private val groupStates = mutableMapOf<String, GroupState>()

    private var autoGrantNotifier: AutoGrantPermissionsNotifier? = null

    private fun getAutoGrantNotifier(): AutoGrantPermissionsNotifier {
        autoGrantNotifier = AutoGrantPermissionsNotifier(app, packageInfo.toPackageInfo(app)!!)
        return autoGrantNotifier!!
    }

    private lateinit var packageInfo: LightPackageInfo

    // All permissions that could possibly be affected by the provided requested permissions, before
    // filtering system fixed, auto grant, etc.
    private var unfilteredAffectedPermissions = requestedPermissions

    private var appPermGroupLiveDatas = mutableMapOf<String, LightAppPermGroupLiveData>()

    internal data class ResultCallback(val consumer: Consumer<Intent?>, val requestCode: Int)

    private var activityResultCallback: ResultCallback? = null

    init {
        if (storedState?.containsKey(SAVED_REQUEST_CODE_KEY) == true) {
            if (storedState.getInt(SAVED_REQUEST_CODE_KEY) == PHOTO_PICKER_REQUEST_CODE) {
                setPhotoPickerCallback()
            }
        }
    }

    /**
     * An internal class which represents the state of a current AppPermissionGroup grant request.
     * It is made up of the following:
     *
     * @param group The LightAppPermGroup representing the current state of the permissions for this
     *   app
     * @param affectedPermissions The permissions that should be affected by this
     */
    internal class GroupState(
        internal val group: LightAppPermGroup,
        internal val affectedPermissions: MutableSet<String> = mutableSetOf(),
        internal var state: Int = STATE_UNKNOWN,
    ) {
        val fgPermissions = affectedPermissions - group.backgroundPermNames.toSet()
        val bgPermissions = affectedPermissions - fgPermissions

        override fun toString(): String {
            val stateStr: String =
                when (state) {
                    STATE_UNKNOWN -> "unknown"
                    STATE_GRANTED -> "granted"
                    STATE_DENIED -> "denied"
                    STATE_FG_GRANTED_BG_UNKNOWN -> "foreground granted, background unknown"
                    else -> "skipped"
                }
            return "${group.permGroupName} $stateStr $affectedPermissions"
        }
    }

    data class RequestInfo(
        val groupInfo: LightPermGroupInfo,
        val prompt: Prompt,
        val deny: DenyButton,
        val showRationale: Boolean,
        val deviceId: Int = ContextCompat.DEVICE_ID_DEFAULT
    ) {
        val groupName = groupInfo.name
    }

    val requestInfosLiveData =
        object : SmartUpdateMediatorLiveData<List<RequestInfo>>() {
            private val LOG_TAG = GrantPermissionsViewModel::class.java.simpleName
            private val packagePermissionsLiveData = PackagePermissionsLiveData[packageName, user]

            init {
                addSource(packagePermissionsLiveData) { onPackageLoaded() }
                addSource(packageInfoLiveData) { onPackageLoaded() }
                if (safetyLabelInfoLiveData != null) {
                    addSource(safetyLabelInfoLiveData) { onPackageLoaded() }
                }

                // Load package state, if available
                onPackageLoaded()
            }

            private fun onPackageLoaded() {
                if (
                    packageInfoLiveData.isStale ||
                        packagePermissionsLiveData.isStale ||
                        (safetyLabelInfoLiveData != null && safetyLabelInfoLiveData.isStale)
                ) {
                    return
                }

                val groups = packagePermissionsLiveData.value
                val pI = packageInfoLiveData.value
                if (groups.isNullOrEmpty() || pI == null) {
                    Log.e(LOG_TAG, "Package $packageName not found")
                    value = null
                    return
                }
                packageInfo = pI

                if (
                    packageInfo.requestedPermissions.isEmpty() ||
                        packageInfo.targetSdkVersion < Build.VERSION_CODES.M
                ) {
                    Log.e(
                        LOG_TAG,
                        "Package $packageName has no requested permissions, or " + "is a pre-M app"
                    )
                    value = null
                    return
                }

                val affectedPermissions = requestedPermissions.toMutableSet()
                for (requestedPerm in requestedPermissions) {
                    affectedPermissions.addAll(getAffectedSplitPermissions(requestedPerm))
                }
                if (packageInfo.targetSdkVersion < Build.VERSION_CODES.O) {
                    // For < O apps all permissions of the groups of the requested ones are affected
                    for (affectedPerm in affectedPermissions.toSet()) {
                        val otherGroupPerms =
                            groups.values.firstOrNull { affectedPerm in it } ?: emptyList()
                        affectedPermissions.addAll(otherGroupPerms)
                    }
                }
                unfilteredAffectedPermissions = affectedPermissions.toList()

                setAppPermGroupsLiveDatas(
                    groups.toMutableMap().apply {
                        remove(PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS)
                    }
                )
            }

            private fun setAppPermGroupsLiveDatas(groups: Map<String, List<String>>) {
                val requestedGroups =
                    groups.filter { (_, perms) ->
                        perms.any { it in unfilteredAffectedPermissions }
                    }

                if (requestedGroups.isEmpty()) {
                    Log.e(LOG_TAG, "None of " + "$unfilteredAffectedPermissions in $groups")
                    value = null
                    return
                }

                val getLiveDataFun = { groupName: String ->
                    LightAppPermGroupLiveData[packageName, groupName, user, deviceId]
                }
                setSourcesToDifference(requestedGroups.keys, appPermGroupLiveDatas, getLiveDataFun)
            }

            override fun onUpdate() {
                if (appPermGroupLiveDatas.any { it.value.isStale }) {
                    return
                }
                var newGroups = false
                for ((groupName, groupLiveData) in appPermGroupLiveDatas) {
                    val appPermGroup = groupLiveData.value
                    if (appPermGroup == null) {
                        Log.e(LOG_TAG, "Group $packageName $groupName invalid")
                        groupStates[groupName]?.state = STATE_SKIPPED
                        continue
                    }

                    packageInfo = appPermGroup.packageInfo

                    val state = groupStates[groupName]
                    if (state != null) {
                        val allAffectedGranted =
                            state.affectedPermissions.all { perm ->
                                appPermGroup.permissions[perm]?.isGranted == true &&
                                    appPermGroup.permissions[perm]?.isRevokeWhenRequested == false
                            }
                        if (allAffectedGranted) {
                            groupStates[groupName]!!.state = STATE_GRANTED
                        }
                    } else {
                        newGroups = true
                    }
                }

                if (newGroups) {
                    addRequiredGroupStates(appPermGroupLiveDatas.mapNotNull { it.value.value })
                }
                setRequestInfosFromGroupStates()
            }

            private fun setRequestInfosFromGroupStates() {
                val requestInfos = mutableListOf<RequestInfo>()
                for (groupState in groupStates.values) {
                    if (!isStateUnknown(groupState.state)) {
                        continue
                    }
                    val behavior = getGrantBehavior(groupState.group)
                    val isSystemTriggered =
                        groupState.affectedPermissions.any { it in systemRequestedPermissions }
                    val prompt =
                        behavior.getPrompt(
                            groupState.group,
                            groupState.affectedPermissions,
                            isSystemTriggered
                        )
                    if (prompt == Prompt.NO_UI_REJECT_ALL_GROUPS) {
                        value = null
                        return
                    }
                    if (prompt == Prompt.NO_UI_REJECT_THIS_GROUP) {
                        reportRequestResult(
                            groupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED
                        )
                        continue
                    }

                    val denyBehavior =
                        behavior.getDenyButton(
                            groupState.group,
                            groupState.affectedPermissions,
                            prompt
                        )
                    val safetyLabel = safetyLabelInfoLiveData?.value?.safetyLabel
                    requestInfos.add(
                        RequestInfo(
                            groupState.group.permGroupInfo,
                            prompt,
                            denyBehavior,
                            shouldShowPermissionRationale(
                                safetyLabel,
                                groupState.group.permGroupName
                            ),
                            deviceId
                        )
                    )
                }
                sortPermissionGroups(requestInfos)

                value =
                    if (
                        requestInfos.any { it.prompt == Prompt.NO_UI_SETTINGS_REDIRECT } &&
                            requestInfos.size > 1
                    ) {
                        Log.e(
                            LOG_TAG,
                            "For R+ apps, background permissions must be requested " +
                                "individually"
                        )
                        null
                    } else {
                        requestInfos
                    }
            }
        }

    private fun sortPermissionGroups(requestInfos: MutableList<RequestInfo>) {
        requestInfos.sortWith { rhs, lhs ->
            val rhsHasOneTime = isOneTimePrompt(rhs.prompt)
            val lhsHasOneTime = isOneTimePrompt(lhs.prompt)
            if (rhsHasOneTime && !lhsHasOneTime) {
                -1
            } else if (
                (!rhsHasOneTime && lhsHasOneTime) || Utils.isHealthPermissionGroup(rhs.groupName)
            ) {
                1
            } else {
                rhs.groupName.compareTo(lhs.groupName)
            }
        }
    }

    private fun isOneTimePrompt(prompt: Prompt): Boolean {
        return prompt in
            setOf(
                Prompt.ONE_TIME_FG,
                Prompt.SETTINGS_LINK_WITH_OT,
                Prompt.LOCATION_TWO_BUTTON_COARSE_HIGHLIGHT,
                Prompt.LOCATION_TWO_BUTTON_FINE_HIGHLIGHT,
                Prompt.LOCATION_COARSE_ONLY,
                Prompt.LOCATION_FINE_UPGRADE
            )
    }

    private fun shouldShowPermissionRationale(
        safetyLabel: SafetyLabel?,
        permissionGroupName: String?
    ): Boolean {
        if (safetyLabel == null || permissionGroupName == null) {
            return false
        }

        val purposes =
            SafetyLabelUtils.getSafetyLabelSharingPurposesForGroup(safetyLabel, permissionGroupName)
        return purposes.isNotEmpty()
    }

    /**
     * Converts a list of LightAppPermGroups into a list of GroupStates, and adds new GroupState
     * objects to the tracked groupStates.
     */
    private fun addRequiredGroupStates(groups: List<LightAppPermGroup>) {
        val filteredPermissions =
            unfilteredAffectedPermissions.filter { perm ->
                val group = getGroupWithPerm(perm, groups)
                group != null && isPermissionGrantableAndNotFixed(perm, group)
            }
        val newGroupStates = mutableMapOf<String, GroupState>()
        for (perm in filteredPermissions) {
            val group = getGroupWithPerm(perm, groups)!!

            val oldGroupState = groupStates[group.permGroupName]
            if (!isStateUnknown(oldGroupState?.state)) {
                // we've already dealt with this group
                continue
            }

            val groupState = newGroupStates.getOrPut(group.permGroupName) { GroupState(group) }

            var currGroupState = groupState.state
            if (storedState != null && !isStateUnknown(groupState.state)) {
                currGroupState = storedState.getInt(group.permGroupName, STATE_UNKNOWN)
            }

            val otherAffectedPermissionsInGroup =
                filteredPermissions.filter { it in group.permissions }.toSet()
            val groupStateOfPerm = getGroupState(perm, group, otherAffectedPermissionsInGroup)
            if (groupStateOfPerm != STATE_UNKNOWN) {
                // update the state if it is allowed, denied, or granted in foreground
                currGroupState = groupStateOfPerm
            }

            if (currGroupState != STATE_UNKNOWN) {
                groupState.state = currGroupState
            }

            groupState.affectedPermissions.add(perm)
        }
        newGroupStates.forEach { (groupName, groupState) -> groupStates[groupName] = groupState }
    }

    /**
     * Add additional permissions that should be granted in this request. For permissions that have
     * split permissions, and apps that target an SDK before the split, this method automatically
     * adds the split off permission.
     *
     * @param perm The requested permission
     * @return The requested permissions plus any needed split permissions
     */
    private fun getAffectedSplitPermissions(
        perm: String,
    ): List<String> {
        val requestingAppTargetSDK = packageInfo.targetSdkVersion

        // If a permission is split, all permissions the original permission is split into are
        // affected
        val extendedBySplitPerms = mutableListOf(perm)

        val splitPerms = app.getSystemService(PermissionManager::class.java)!!.splitPermissions
        for (splitPerm in splitPerms) {
            if (requestingAppTargetSDK < splitPerm.targetSdk && perm == splitPerm.splitPermission) {
                extendedBySplitPerms.addAll(splitPerm.newPermissions)
            }
        }
        return extendedBySplitPerms
    }

    private fun isPermissionGrantableAndNotFixed(perm: String, group: LightAppPermGroup): Boolean {
        // If the permission is restricted it does not show in the UI and
        // is not added to the group at all, so check that first.
        if (perm in group.packageInfo.requestedPermissions && perm !in group.permissions) {
            reportRequestResult(
                perm,
                PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_RESTRICTED_PERMISSION
            )
            return false
        }

        val subGroup =
            if (perm in group.backgroundPermNames) {
                group.background
            } else {
                group.foreground
            }

        val lightPermission = group.permissions[perm] ?: return false

        if (!subGroup.isGrantable) {
            reportRequestResult(perm, PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED)
            // Skip showing groups that we know cannot be granted.
            return false
        }

        if (subGroup.isPolicyFixed && !subGroup.isGranted || lightPermission.isPolicyFixed) {
            reportRequestResult(
                perm,
                PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_POLICY_FIXED
            )
            return false
        }

        val behavior = getGrantBehavior(group)
        if (behavior.isPermissionFixed(group, perm)) {
            reportRequestResult(
                perm,
                PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_USER_FIXED
            )
            return false
        }

        return true
    }

    private fun getGroupState(
        perm: String,
        group: LightAppPermGroup,
        groupRequestedPermissions: Set<String>
    ): Int {
        val policyState = getStateFromPolicy(perm, group)
        if (!isStateUnknown(policyState)) {
            return policyState
        }

        val isBackground = perm in group.backgroundPermNames

        val behavior = getGrantBehavior(group)
        return if (behavior.isGroupFullyGranted(group, groupRequestedPermissions)) {
            if (group.permissions[perm]?.isGranted == false) {
                if (isBackground) {
                    grantBackgroundRuntimePermissions(app, group, listOf(perm))
                } else {
                    grantForegroundRuntimePermissions(app, group, listOf(perm), group.isOneTime)
                }
                KotlinUtils.setGroupFlags(
                    app,
                    group,
                    FLAG_PERMISSION_USER_SET to false,
                    FLAG_PERMISSION_USER_FIXED to false,
                    filterPermissions = listOf(perm)
                )
                reportRequestResult(
                    perm,
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_GRANTED
                )
            }
            STATE_GRANTED
        } else if (behavior.isForegroundFullyGranted(group, groupRequestedPermissions)) {
            STATE_FG_GRANTED_BG_UNKNOWN
        } else {
            STATE_UNKNOWN
        }
    }

    private fun getStateFromPolicy(perm: String, group: LightAppPermGroup): Int {
        val isBackground = perm in group.backgroundPermNames
        var state = STATE_UNKNOWN
        when (permissionPolicy) {
            DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT -> {
                if (
                    AdminRestrictedPermissionsUtils.mayAdminGrantPermission(
                        app,
                        perm,
                        user.identifier
                    )
                ) {
                    if (isBackground) {
                        grantBackgroundRuntimePermissions(app, group, listOf(perm))
                    } else {
                        grantForegroundRuntimePermissions(app, group, listOf(perm))
                    }
                    KotlinUtils.setGroupFlags(
                        app,
                        group,
                        FLAG_PERMISSION_POLICY_FIXED to true,
                        FLAG_PERMISSION_USER_SET to false,
                        FLAG_PERMISSION_USER_FIXED to false,
                        filterPermissions = listOf(perm)
                    )
                    state = STATE_GRANTED
                    getAutoGrantNotifier().onPermissionAutoGranted(perm)
                    reportRequestResult(
                        perm,
                        PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_GRANTED
                    )
                }
            }
            DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY -> {
                if (group.permissions[perm]?.isPolicyFixed == false) {
                    KotlinUtils.setGroupFlags(
                        app,
                        group,
                        FLAG_PERMISSION_POLICY_FIXED to true,
                        FLAG_PERMISSION_USER_SET to false,
                        FLAG_PERMISSION_USER_FIXED to false,
                        filterPermissions = listOf(perm)
                    )
                }
                state = STATE_DENIED
                reportRequestResult(
                    perm,
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_DENIED
                )
            }
        }
        return state
    }

    /**
     * Upon the user clicking a button, grant permissions, if applicable.
     *
     * @param groupName The name of the permission group which was changed
     * @param affectedForegroundPermissions The name of the foreground permission which was changed
     * @param result The choice the user made regarding the group.
     */
    fun onPermissionGrantResult(
        groupName: String?,
        affectedForegroundPermissions: List<String>?,
        result: Int
    ) {
        onPermissionGrantResult(groupName, affectedForegroundPermissions, result, false)
    }

    private fun onPermissionGrantResult(
        groupName: String?,
        affectedForegroundPermissions: List<String>?,
        result: Int,
        alreadyRequestedStorageGroupsIfNeeded: Boolean
    ) {
        if (groupName == null) {
            return
        }

        // If this is a legacy app, and a storage group is requested: request all storage groups
        if (
            !alreadyRequestedStorageGroupsIfNeeded &&
                groupName in PermissionMapping.STORAGE_SUPERGROUP_PERMISSIONS &&
                packageInfo.targetSdkVersion <= Build.VERSION_CODES.S_V2
        ) {
            for (storageGroupName in PermissionMapping.STORAGE_SUPERGROUP_PERMISSIONS) {
                val groupPerms =
                    appPermGroupLiveDatas[storageGroupName]?.value?.allPermissions?.keys?.toList()
                onPermissionGrantResult(storageGroupName, groupPerms, result, true)
            }
            return
        }

        val groupState = groupStates[groupName] ?: return
        when (result) {
            CANCELED -> {
                reportRequestResult(
                    groupState.affectedPermissions,
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_IGNORED
                )
                groupState.state = STATE_SKIPPED
                requestInfosLiveData.update()
                return
            }
            GRANTED_ALWAYS -> {
                onPermissionGrantResultSingleState(
                    groupState,
                    affectedForegroundPermissions,
                    granted = true,
                    isOneTime = false,
                    foregroundOnly = false,
                    doNotAskAgain = false
                )
            }
            GRANTED_FOREGROUND_ONLY -> {
                onPermissionGrantResultSingleState(
                    groupState,
                    affectedForegroundPermissions,
                    granted = true,
                    isOneTime = false,
                    foregroundOnly = true,
                    doNotAskAgain = false
                )
            }
            GRANTED_ONE_TIME -> {
                onPermissionGrantResultSingleState(
                    groupState,
                    affectedForegroundPermissions,
                    granted = true,
                    isOneTime = true,
                    foregroundOnly = false,
                    doNotAskAgain = false
                )
            }
            GRANTED_USER_SELECTED,
            DENIED_MORE -> {
                grantUserSelectedVisualGroupPermissions(groupState)
            }
            DENIED -> {
                onPermissionGrantResultSingleState(
                    groupState,
                    affectedForegroundPermissions,
                    granted = false,
                    isOneTime = false,
                    foregroundOnly = false,
                    doNotAskAgain = false
                )
            }
            DENIED_DO_NOT_ASK_AGAIN -> {
                onPermissionGrantResultSingleState(
                    groupState,
                    affectedForegroundPermissions,
                    granted = false,
                    isOneTime = false,
                    foregroundOnly = false,
                    doNotAskAgain = true
                )
            }
        }
    }

    private fun grantUserSelectedVisualGroupPermissions(groupState: GroupState) {
        val userSelectedPerm =
            groupState.group.permissions[READ_MEDIA_VISUAL_USER_SELECTED] ?: return
        if (userSelectedPerm.isImplicit) {
            val nonSelectedPerms =
                groupState.group.permissions.keys.filter { it != READ_MEDIA_VISUAL_USER_SELECTED }
            // If the permission is implicit, grant USER_SELECTED as user set, and all other
            // permissions as one time, and without app ops.
            grantForegroundRuntimePermissions(
                app,
                groupState.group,
                listOf(READ_MEDIA_VISUAL_USER_SELECTED)
            )
            grantForegroundRuntimePermissions(
                app,
                groupState.group,
                nonSelectedPerms,
                isOneTime = true,
                userFixed = false,
                withoutAppOps = true
            )
            val appPermGroup =
                AppPermissionGroup.create(
                    app,
                    packageName,
                    groupState.group.permGroupName,
                    groupState.group.userHandle,
                    false
                )
            appPermGroup.setSelfRevoked()
            appPermGroup.persistChanges(false, null, nonSelectedPerms.toSet())
        } else {
            val partialPerms =
                getPartialStorageGrantPermissionsForGroup(groupState.group).filter {
                    it in groupState.affectedPermissions
                }
            val nonSelectedPerms = groupState.affectedPermissions.filter { it !in partialPerms }
            val setUserFixed = userSelectedPerm.isUserFixed || userSelectedPerm.isUserSet
            grantForegroundRuntimePermissions(
                app,
                groupState.group,
                partialPerms.toList(),
                userFixed = setUserFixed
            )
            revokeForegroundRuntimePermissions(
                app,
                groupState.group,
                userFixed = setUserFixed,
                oneTime = false,
                filterPermissions = nonSelectedPerms
            )
        }
        groupState.state = STATE_GRANTED
        reportButtonClickResult(
            groupState,
            groupState.affectedPermissions,
            true,
            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__PHOTOS_SELECTED
        )
    }

    @SuppressLint("NewApi")
    private fun onPermissionGrantResultSingleState(
        groupState: GroupState,
        affectedForegroundPermissions: List<String>?,
        granted: Boolean,
        foregroundOnly: Boolean,
        isOneTime: Boolean,
        doNotAskAgain: Boolean
    ) {
        if (!isStateUnknown(groupState.state)) {
            // We already dealt with this group, don't re-grant/re-revoke
            return
        }
        val shouldAffectBackgroundPermissions =
            groupState.bgPermissions.isNotEmpty() && !foregroundOnly
        val shouldAffectForegroundPermssions = groupState.state != STATE_FG_GRANTED_BG_UNKNOWN
        val result: Int
        if (granted) {
            result =
                if (isOneTime) {
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_ONE_TIME
                } else {
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED
                }
            var affectedPermissions: Collection<String> = groupState.affectedPermissions
            if (shouldAffectBackgroundPermissions) {
                grantBackgroundRuntimePermissions(app, groupState.group, affectedPermissions)
            } else if (shouldAffectForegroundPermssions) {
                if (affectedForegroundPermissions == null) {
                    grantForegroundRuntimePermissions(
                        app,
                        groupState.group,
                        affectedPermissions,
                        isOneTime
                    )
                    // This prevents weird flag state when app targetSDK switches from S+ to R-
                    if (groupState.affectedPermissions.contains(ACCESS_FINE_LOCATION)) {
                        KotlinUtils.setFlagsWhenLocationAccuracyChanged(app, groupState.group, true)
                    }
                } else {
                    affectedPermissions = affectedForegroundPermissions
                    val newGroup =
                        grantForegroundRuntimePermissions(
                            app,
                            groupState.group,
                            affectedPermissions,
                            isOneTime
                        )
                    if (!isOneTime || newGroup.isOneTime) {
                        KotlinUtils.setFlagsWhenLocationAccuracyChanged(
                            app,
                            newGroup,
                            affectedForegroundPermissions.contains(ACCESS_FINE_LOCATION)
                        )
                    }
                }
            }
            val shouldDenyFullGroupGrant =
                groupState.group.isPlatformPermissionGroup &&
                    affectedPermissions.none {
                        groupState.group.permissions[it]?.isPlatformOrSystem == true
                    }
            groupState.state =
                if (shouldDenyFullGroupGrant) {
                    STATE_UNKNOWN
                } else {
                    STATE_GRANTED
                }
        } else {
            if (shouldAffectBackgroundPermissions) {
                revokeBackgroundRuntimePermissions(
                    app,
                    groupState.group,
                    userFixed = doNotAskAgain,
                    filterPermissions = groupState.affectedPermissions
                )
            } else if (shouldAffectForegroundPermssions) {
                if (
                    affectedForegroundPermissions == null ||
                        affectedForegroundPermissions.contains(ACCESS_COARSE_LOCATION)
                ) {
                    revokeForegroundRuntimePermissions(
                        app,
                        groupState.group,
                        userFixed = doNotAskAgain,
                        filterPermissions = groupState.affectedPermissions,
                        oneTime = isOneTime
                    )
                } else {
                    revokeForegroundRuntimePermissions(
                        app,
                        groupState.group,
                        userFixed = doNotAskAgain,
                        filterPermissions = affectedForegroundPermissions,
                        oneTime = isOneTime
                    )
                }
            }
            result =
                if (doNotAskAgain) {
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE
                } else {
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED
                }
            groupState.state = STATE_DENIED
        }
        val permissionsChanged =
            if (foregroundOnly) {
                groupState.fgPermissions
            } else {
                groupState.affectedPermissions
            }
        reportButtonClickResult(groupState, permissionsChanged, granted, result)
    }

    private fun reportButtonClickResult(
        groupState: GroupState,
        permissions: Set<String>,
        granted: Boolean,
        result: Int
    ) {
        reportRequestResult(permissions, result)
        // group state has changed, reload liveData
        requestInfosLiveData.update()

        if (SdkLevel.isAtLeastT()) {
            PermissionDecisionStorageImpl.recordPermissionDecision(
                app.applicationContext,
                packageName,
                groupState.group.permGroupName,
                granted
            )
            PermissionChangeStorageImpl.recordPermissionChange(packageName)
        }
        if (granted) {
            startDrivingDecisionReminderServiceIfNecessary(groupState.group.permGroupName)
        }
    }

    /**
     * When distraction optimization is required (the vehicle is in motion), the user may want to
     * review their permission grants when they are less distracted.
     */
    private fun startDrivingDecisionReminderServiceIfNecessary(permGroupName: String) {
        if (!DeviceUtils.isAuto(app.applicationContext)) {
            return
        }
        DrivingDecisionReminderService.startServiceIfCurrentlyRestricted(
            Utils.getUserContext(app, user),
            packageName,
            permGroupName
        )
    }

    private fun getGroupWithPerm(
        perm: String,
        groups: List<LightAppPermGroup>
    ): LightAppPermGroup? {
        val groupsWithPerm = groups.filter { perm in it.permissions }
        if (groupsWithPerm.isEmpty()) {
            return null
        }
        return groupsWithPerm.first()
    }

    private fun reportRequestResult(permissions: Collection<String>, result: Int) {
        permissions.forEach { reportRequestResult(it, result) }
    }

    /**
     * Report the result of a grant of a permission.
     *
     * @param permission The permission that was granted or denied
     * @param result The permission grant result
     */
    private fun reportRequestResult(permission: String, result: Int) {
        val isImplicit = permission !in requestedPermissions
        val isPermissionRationaleShown =
            shouldShowPermissionRationale(
                safetyLabelInfoLiveData?.value?.safetyLabel,
                PermissionMapping.getGroupOfPlatformPermission(permission)
            )

        Log.i(
            LOG_TAG,
            "Permission grant result requestId=$sessionId " +
                "callingUid=${packageInfo.uid} " +
                "callingPackage=$packageName " +
                "permission=$permission " +
                "isImplicit=$isImplicit result=$result " +
                "isPermissionRationaleShown=$isPermissionRationaleShown"
        )

        PermissionControllerStatsLog.write(
            PERMISSION_GRANT_REQUEST_RESULT_REPORTED,
            sessionId,
            packageInfo.uid,
            packageName,
            permission,
            isImplicit,
            result,
            isPermissionRationaleShown
        )
    }

    /**
     * Save the group states of the view model, to allow for state restoration after lifecycle
     * events
     *
     * @param outState The bundle in which to store state
     */
    fun saveInstanceState(outState: Bundle) {
        for ((groupName, groupState) in groupStates) {
            outState.putInt(groupName, groupState.state)
        }
        activityResultCallback?.let { outState.putInt(SAVED_REQUEST_CODE_KEY, it.requestCode) }
    }

    /**
     * Determine if the activity should return permission state to the caller
     *
     * @return Whether or not state should be returned. False only if the package is pre-M, true
     *   otherwise.
     */
    fun shouldReturnPermissionState(): Boolean {
        return if (packageInfoLiveData.value != null) {
            packageInfoLiveData.value!!.targetSdkVersion >= Build.VERSION_CODES.M
        } else {
            // Should not be reached, as this method shouldn't be called before data is passed to
            // the activity for the first time
            try {
                Utils.getUserContext(app, user)
                    .packageManager
                    .getApplicationInfo(packageName, 0)
                    .targetSdkVersion >= Build.VERSION_CODES.M
            } catch (e: PackageManager.NameNotFoundException) {
                true
            }
        }
    }

    fun handleCallback(data: Intent?, requestCode: Int) {
        val currCallback = activityResultCallback
        if (currCallback == null || requestCode != currCallback.requestCode) {
            return
        }
        currCallback.consumer.accept(data)
        activityResultCallback = null
    }

    fun handleHealthConnectPermissions(activity: Activity) {
        if (activityResultCallback == null) {
            activityResultCallback =
                ResultCallback(
                    {
                        groupStates[HEALTH_PERMISSION_GROUP]?.state = STATE_SKIPPED
                        requestInfosLiveData.update()
                    },
                    APP_PERMISSION_REQUEST_CODE
                )
            val healthPermissions =
                unfilteredAffectedPermissions
                    .filter { permission -> isHealthPermission(activity, permission) }
                    .toTypedArray()
            val intent: Intent =
                Intent(ACTION_REQUEST_HEALTH_PERMISSIONS)
                    .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                    .putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES, healthPermissions)
                    .putExtra(Intent.EXTRA_USER, Process.myUserHandle())
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            activity.startActivityForResult(intent, APP_PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * Send the user directly to the AppPermissionFragment. Used for R+ apps.
     *
     * @param activity The current activity
     * @param groupName The name of the permission group whose fragment should be opened
     */
    fun sendDirectlyToSettings(activity: Activity, groupName: String) {
        if (activityResultCallback == null) {
            activityResultCallback =
                ResultCallback(
                    Consumer { data ->
                        if (data?.getStringExtra(EXTRA_RESULT_PERMISSION_INTERACTED) == null) {
                            // User didn't interact, count against rate limit
                            val group = groupStates[groupName]?.group ?: return@Consumer
                            if (group.background.isUserSet) {
                                KotlinUtils.setGroupFlags(
                                    app,
                                    group,
                                    FLAG_PERMISSION_USER_FIXED to true,
                                    filterPermissions = group.backgroundPermNames
                                )
                            } else {
                                KotlinUtils.setGroupFlags(
                                    app,
                                    group,
                                    FLAG_PERMISSION_USER_SET to true,
                                    filterPermissions = group.backgroundPermNames
                                )
                            }
                        }

                        groupStates[groupName]?.state = STATE_SKIPPED
                        // Update our liveData now that there is a new skipped group
                        requestInfosLiveData.update()
                    },
                    APP_PERMISSION_REQUEST_CODE
                )
            startAppPermissionFragment(activity, groupName)
        }
    }

    fun openPhotoPicker(activity: Activity) {
        if (activityResultCallback != null) {
            return
        }
        if (groupStates[READ_MEDIA_VISUAL]?.affectedPermissions == null) {
            return
        }
        setPhotoPickerCallback()
        openPhotoPickerForApp(
            activity,
            packageInfo.uid,
            unfilteredAffectedPermissions,
            PHOTO_PICKER_REQUEST_CODE
        )
    }

    private fun setPhotoPickerCallback() {
        activityResultCallback =
            ResultCallback(
                { data ->
                    val anySelected = data?.getBooleanExtra(INTENT_PHOTOS_SELECTED, true) == true
                    if (anySelected) {
                        onPermissionGrantResult(READ_MEDIA_VISUAL, null, GRANTED_USER_SELECTED)
                    } else {
                        onPermissionGrantResult(READ_MEDIA_VISUAL, null, CANCELED)
                    }
                    requestInfosLiveData.update()
                },
                PHOTO_PICKER_REQUEST_CODE
            )
    }

    /**
     * Send the user to the AppPermissionFragment from a link. Used for Q- apps
     *
     * @param activity The current activity
     * @param groupName The name of the permission group whose fragment should be opened
     */
    fun sendToSettingsFromLink(activity: Activity, groupName: String) {
        startAppPermissionFragment(activity, groupName)
        activityResultCallback =
            ResultCallback(
                { data ->
                    val returnGroupName = data?.getStringExtra(EXTRA_RESULT_PERMISSION_INTERACTED)
                    if (returnGroupName != null) {
                        groupStates[returnGroupName]?.state = STATE_SKIPPED
                        val result = data.getIntExtra(EXTRA_RESULT_PERMISSION_RESULT, -1)
                        logSettingsInteraction(returnGroupName, result)
                        requestInfosLiveData.update()
                    }
                },
                APP_PERMISSION_REQUEST_CODE
            )
    }

    /**
     * Shows the Permission Rationale Dialog. For use with U+ only, otherwise no-op.
     *
     * @param activity The current activity
     * @param groupName The name of the permission group whose fragment should be opened
     */
    fun showPermissionRationaleActivity(activity: Activity, groupName: String) {
        if (!SdkLevel.isAtLeastU()) {
            return
        }

        val intent =
            Intent(activity, PermissionRationaleActivity::class.java).apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName)
                putExtra(Constants.EXTRA_SESSION_ID, sessionId)
            }
        activityResultCallback =
            ResultCallback(
                { data ->
                    val returnGroupName = data?.getStringExtra(EXTRA_RESULT_PERMISSION_INTERACTED)
                    if (returnGroupName != null) {
                        groupStates[returnGroupName]?.state = STATE_SKIPPED
                        val result = data.getIntExtra(EXTRA_RESULT_PERMISSION_RESULT, CANCELED)
                        logSettingsInteraction(returnGroupName, result)
                        requestInfosLiveData.update()
                    }
                },
                APP_PERMISSION_REQUEST_CODE
            )
        activity.startActivityForResult(intent, APP_PERMISSION_REQUEST_CODE)
    }

    private fun startAppPermissionFragment(activity: Activity, groupName: String) {
        val intent =
            Intent(Intent.ACTION_MANAGE_APP_PERMISSION)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName)
                .putExtra(Intent.EXTRA_USER, user)
                .putExtra(
                    ManagePermissionsActivity.EXTRA_CALLER_NAME,
                    GrantPermissionsActivity::class.java.name
                )
                .putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivityForResult(intent, APP_PERMISSION_REQUEST_CODE)
    }

    private fun getGrantBehavior(group: LightAppPermGroup): GrantBehavior {
        return when (group.permGroupName) {
            LOCATION -> LocationGrantBehavior
            HEALTH_PERMISSION_GROUP -> HealthGrantBehavior
            NOTIFICATIONS -> NotificationGrantBehavior
            STORAGE,
            READ_MEDIA_VISUAL,
            READ_MEDIA_AURAL -> StorageGrantBehavior
            else -> {
                if (Utils.hasPermWithBackgroundModeCompat(group)) {
                    BackgroundGrantBehavior
                } else {
                    BasicGrantBehavior
                }
            }
        }
    }

    private fun logSettingsInteraction(groupName: String, result: Int) {
        val groupState = groupStates[groupName] ?: return
        val backgroundPerms =
            groupState.affectedPermissions.filter { it in groupState.group.backgroundPermNames }
        val foregroundPerms = groupState.affectedPermissions.filter { it !in backgroundPerms }
        val deniedPrejudiceInSettings =
            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE_IN_SETTINGS
        when (result) {
            GRANTED_ALWAYS -> {
                reportRequestResult(
                    groupState.affectedPermissions,
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_IN_SETTINGS
                )
            }
            GRANTED_FOREGROUND_ONLY -> {
                reportRequestResult(
                    foregroundPerms,
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_IN_SETTINGS
                )
                if (backgroundPerms.isNotEmpty()) {
                    reportRequestResult(
                        backgroundPerms,
                        PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_IN_SETTINGS
                    )
                }
            }
            DENIED -> {
                reportRequestResult(
                    groupState.affectedPermissions,
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_IN_SETTINGS
                )
            }
            DENIED_DO_NOT_ASK_AGAIN -> {
                reportRequestResult(groupState.affectedPermissions, deniedPrejudiceInSettings)
            }
        }
    }

    /** Log all permission groups which were requested */
    fun logRequestedPermissionGroups() {
        if (groupStates.isEmpty()) {
            return
        }
        val groups = groupStates.map { it.value.group }
        SafetyNetLogger.logPermissionsRequested(packageName, packageInfo.uid, groups)
    }

    /**
     * Log information about the buttons which were shown and clicked by the user.
     *
     * @param groupName The name of the permission group which was interacted with
     * @param selectedPrecision Selected precision of the location permission - bit flags indicate
     *   which locations were chosen
     * @param clickedButton The button that was clicked by the user
     * @param presentedButtons All buttons which were shown to the user
     */
    fun logClickedButtons(
        groupName: String?,
        selectedPrecision: Int,
        clickedButton: Int,
        presentedButtons: Int,
        isPermissionRationaleShown: Boolean
    ) {
        if (groupName == null) {
            return
        }

        if (!requestInfosLiveData.isInitialized || !packageInfoLiveData.isInitialized) {
            Log.wtf(
                LOG_TAG,
                "Logged buttons presented and clicked permissionGroupName=" +
                    "$groupName package=$packageName presentedButtons=$presentedButtons " +
                    "clickedButton=$clickedButton isPermissionRationaleShown=" +
                    "$isPermissionRationaleShown sessionId=$sessionId, but requests were not yet" +
                    "initialized",
                IllegalStateException()
            )
            return
        }

        PermissionControllerStatsLog.write(
            GRANT_PERMISSIONS_ACTIVITY_BUTTON_ACTIONS,
            groupName,
            packageInfo.uid,
            packageName,
            presentedButtons,
            clickedButton,
            sessionId,
            packageInfo.targetSdkVersion,
            selectedPrecision,
            isPermissionRationaleShown
        )
        Log.i(
            LOG_TAG,
            "Logged buttons presented and clicked permissionGroupName=" +
                "$groupName uid=${packageInfo.uid} selectedPrecision=$selectedPrecision " +
                "package=$packageName presentedButtons=$presentedButtons " +
                "clickedButton=$clickedButton isPermissionRationaleShown=" +
                "$isPermissionRationaleShown sessionId=$sessionId " +
                "targetSdk=${packageInfo.targetSdkVersion}"
        )
    }

    /** Use the autoGrantNotifier to notify of auto-granted permissions. */
    fun autoGrantNotify() {
        autoGrantNotifier?.notifyOfAutoGrantPermissions(true)
    }

    private fun isStateUnknown(state: Int?): Boolean {
        return state == null || state == STATE_UNKNOWN || state == STATE_FG_GRANTED_BG_UNKNOWN
    }

    companion object {
        const val APP_PERMISSION_REQUEST_CODE = 1
        const val PHOTO_PICKER_REQUEST_CODE = 2
        const val ECM_REQUEST_CODE = 3
        const val SAVED_REQUEST_CODE_KEY = "saved_request_code"
        private const val STATE_UNKNOWN = 0
        private const val STATE_GRANTED = 1
        private const val STATE_DENIED = 2
        private const val STATE_SKIPPED = 3
        private const val STATE_FG_GRANTED_BG_UNKNOWN = 4
    }
}

/**
 * Factory for an AppPermissionViewModel
 *
 * @param app The current application
 * @param packageName The name of the package this ViewModel represents
 */
class NewGrantPermissionsViewModelFactory(
    private val app: Application,
    private val packageName: String,
    private val deviceId: Int,
    private val requestedPermissions: List<String>,
    private val systemRequestedPermissions: List<String>,
    private val sessionId: Long,
    private val savedState: Bundle?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GrantPermissionsViewModel(
            app,
            packageName,
            deviceId,
            requestedPermissions,
            systemRequestedPermissions,
            sessionId,
            savedState
        )
            as T
    }
}

enum class Prompt {
    BASIC, // Allow/Deny
    ONE_TIME_FG, // Allow in foreground/one time/deny
    FG_ONLY, // Allow in foreground/deny
    SETTINGS_LINK_FOR_BG, // Allow in foreground/deny, with link to settings to change background
    SETTINGS_LINK_WITH_OT, // Same as above, but with a one time button
    UPGRADE_SETTINGS_LINK, // Keep foreground, with link to settings to grant background
    OT_UPGRADE_SETTINGS_LINK, // Same as above, but the button is "keep one time"
    LOCATION_TWO_BUTTON_COARSE_HIGHLIGHT, // Choose coarse/fine, foreground/one time/deny, coarse
    // button highlighted
    LOCATION_TWO_BUTTON_FINE_HIGHLIGHT, // Same as above, but fine location highlighted
    LOCATION_COARSE_ONLY, // Only coarse location, foreground/one time/deny
    LOCATION_FINE_UPGRADE, // Upgrade coarse to fine, upgrade to fine/ one time/ keep coarse
    SELECT_PHOTOS, // Select photos/allow all photos/deny
    SELECT_MORE_PHOTOS, // Select more photos/allow all photos/don't allow more
    // These next two are for T+ devices, and < T apps. They request the old "storage" group, and
    // we "grant" it, while actually granting the new visual and audio groups
    STORAGE_SUPERGROUP_Q_TO_S, // Allow/deny, special message
    STORAGE_SUPERGROUP_PRE_Q, // Allow/deny, special message (different from above)
    NO_UI_SETTINGS_REDIRECT, // Send the user directly to permission settings
    NO_UI_PHOTO_PICKER_REDIRECT, // Send the user directly to the photo picker
    NO_UI_HEALTH_REDIRECT, // Send the user directly to the Health Connect settings
    NO_UI_REJECT_THIS_GROUP, // Auto deny this permission group
    NO_UI_REJECT_ALL_GROUPS, // Auto deny all permission groups in this request
    NO_UI_FILTER_THIS_GROUP, // Do not act on this permission group. Remove it from results.
}

enum class DenyButton {
    DENY,
    DENY_DONT_ASK_AGAIN,
    NO_UPGRADE,
    NO_UPGRADE_OT,
    NO_UPGRADE_AND_DONT_ASK_AGAIN,
    NO_UPGRADE_AND_DONT_ASK_AGAIN_OT,
    DONT_SELECT_MORE, // used in the SELECT_MORE_PHOTOS dialog
    NONE,
}
