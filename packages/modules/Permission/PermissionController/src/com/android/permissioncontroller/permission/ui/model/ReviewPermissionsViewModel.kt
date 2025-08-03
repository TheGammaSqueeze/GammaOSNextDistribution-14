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

package com.android.permissioncontroller.permission.ui.model

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData.Companion.NON_RUNTIME_NORMAL_PERMS
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.utils.PermissionMapping
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.navigateSafe
import com.android.settingslib.RestrictedLockUtils
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin
import java.util.stream.Collectors

/** View model for legacy {@link ReviewPermissionsFragment}. */
class ReviewPermissionsViewModel(val app: Application, val packageInfo: PackageInfo) : ViewModel() {

    private val mUser = android.os.Process.myUserHandle()

    /** Holds permission groups for a package or an empty map in case no user review is required. */
    val permissionGroupsLiveData =
        object : SmartUpdateMediatorLiveData<Map<String, LightAppPermGroup>>() {
            val packagePermsLiveData = PackagePermissionsLiveData[packageInfo.packageName, mUser]

            init {
                addSource(packagePermsLiveData) { update() }
            }

            val permissionGroups = mutableMapOf<String, LightAppPermGroupLiveData>()

            override fun onUpdate() {
                val permissionGroupsMap = packagePermsLiveData.value ?: return
                val filteredGroups =
                    permissionGroupsMap.keys
                        .stream()
                        .filter { it -> !it.equals(NON_RUNTIME_NORMAL_PERMS) }
                        .collect(Collectors.toList())

                val getPermGroupLiveData = { permGroupName: String ->
                    LightAppPermGroupLiveData[packageInfo.packageName, permGroupName, mUser]
                }
                setSourcesToDifference(filteredGroups, permissionGroups, getPermGroupLiveData)
                if (
                    permissionGroups.values.all { it.isInitialized } &&
                        permissionGroups.values.all { !it.isStale }
                ) {
                    val permGroups: List<LightAppPermGroup?> =
                        permissionGroups.values.map { it.value }
                    val reviewGroups =
                        permGroups
                            .filterNotNull()
                            .filter {
                                shouldShowPermission(it) &&
                                    Utils.OS_PKG == it.permGroupInfo.packageName
                            }
                            .associateBy { it.permGroupName }
                    value =
                        if (reviewGroups.any { it.value.isReviewRequired }) reviewGroups
                        else emptyMap()
                }
            }
        }

    fun isInitialized(): Boolean {
        return permissionGroupsLiveData.isInitialized
    }

    private fun shouldShowPermission(group: LightAppPermGroup): Boolean {
        if (!(group.foreground.isGrantable || group.background.isGrantable)) {
            return false
        }
        val isPlatformPermission = group.packageName == Utils.OS_PKG
        // Show legacy permissions only if the user chose that.
        return !(isPlatformPermission &&
            !PermissionMapping.isPlatformPermissionGroup(group.permGroupName))
    }

    fun isPackageUpdated(): Boolean {
        val permGroupsMap: Map<String, LightAppPermGroup> = permissionGroupsLiveData.value!!
        return permGroupsMap.any { !it.value.isReviewRequired }
    }

    /**
     * Update the summary of a permission group that has background permission. This does not apply
     * to permission groups that are fixed by policy
     */
    fun getSummaryForPermGroupWithBackgroundPermission(state: PermissionTarget): PermissionSummary {
        if (state != PermissionTarget.PERMISSION_NONE) {
            if (
                state.and(PermissionTarget.PERMISSION_BACKGROUND) !=
                    PermissionTarget.PERMISSION_NONE.value
            ) {
                return SummaryMessage.ACCESS_ALWAYS.toPermSummary()
            } else {
                return SummaryMessage.ACCESS_ONLY_FOREGROUND.toPermSummary()
            }
        } else {
            return SummaryMessage.ACCESS_NEVER.toPermSummary()
        }
    }

    fun getSummaryForIndividuallyControlledPermGroup(
        permGroup: LightAppPermGroup
    ): PermissionSummary {
        var revokedCount = 0
        val lightPerms = permGroup.allPermissions.values.toList()
        val permissionCount = lightPerms.size
        for (i in 0 until permissionCount) {
            if (!lightPerms[i].isGranted) {
                revokedCount++
            }
        }
        return when (revokedCount) {
            0 -> {
                SummaryMessage.REVOKED_NONE.toPermSummary()
            }
            permissionCount -> {
                SummaryMessage.REVOKED_ALL.toPermSummary()
            }
            else -> {
                PermissionSummary(SummaryMessage.REVOKED_COUNT, false, revokedCount)
            }
        }
    }

    /** Show all individual permissions in this group in a new fragment. */
    fun showAllPermissions(fragment: Fragment, args: Bundle) {
        val navController: NavController = NavHostFragment.findNavController(fragment)
        navController.navigateSafe(R.id.app_to_all_perms, args)
    }

    enum class SummaryMessage {
        NO_SUMMARY,
        DISABLED_BY_ADMIN,
        ENABLED_BY_ADMIN,
        ENABLED_SYSTEM_FIXED,
        ENFORCED_BY_POLICY,
        ENABLED_BY_ADMIN_FOREGROUND_ONLY,
        ENABLED_BY_POLICY_FOREGROUND_ONLY,
        ENABLED_BY_ADMIN_BACKGROUND_ONLY,
        ENABLED_BY_POLICY_BACKGROUND_ONLY,
        DISABLED_BY_ADMIN_BACKGROUND_ONLY,
        DISABLED_BY_POLICY_BACKGROUND_ONLY,
        REVOKED_NONE,
        REVOKED_ALL,
        REVOKED_COUNT,
        ACCESS_ALWAYS,
        ACCESS_ONLY_FOREGROUND,
        ACCESS_NEVER;

        fun toPermSummary(): PermissionSummary {
            return PermissionSummary(this, false)
        }

        fun toPermSummary(isEnterprise: Boolean): PermissionSummary {
            return PermissionSummary(this, isEnterprise)
        }
    }

    data class PermissionSummary(
        val msg: SummaryMessage,
        val isEnterprise: Boolean = false,
        val revokeCount: Int = 0
    )

    fun getSummaryForFixedByPolicyPermissionGroup(
        mState: PermissionTarget,
        permGroup: LightAppPermGroup,
        context: Context
    ): PermissionSummary {
        val admin = getAdmin(context, permGroup)
        val hasAdmin = admin != null
        if (permGroup.isSystemFixed) {
            // Permission is fully controlled by the system and cannot be switched
            return SummaryMessage.ENABLED_SYSTEM_FIXED.toPermSummary()
        } else if (isForegroundDisabledByPolicy(permGroup)) {
            // Permission is fully controlled by policy and cannot be switched
            return if (hasAdmin) {
                SummaryMessage.DISABLED_BY_ADMIN.toPermSummary()
            } else {
                // Disabled state will be displayed by switch, so no need to add text for that
                SummaryMessage.ENFORCED_BY_POLICY.toPermSummary()
            }
        } else if (permGroup.isPolicyFullyFixed) {
            // Permission is fully controlled by policy and cannot be switched
            if (!permGroup.hasBackgroundGroup) {
                return if (hasAdmin) {
                    SummaryMessage.ENABLED_BY_ADMIN.toPermSummary()
                } else {
                    // Enabled state will be displayed by switch, so no need to add text for that
                    SummaryMessage.ENFORCED_BY_POLICY.toPermSummary()
                }
            } else {
                if (
                    mState.and(PermissionTarget.PERMISSION_BACKGROUND) !=
                        PermissionTarget.PERMISSION_NONE.value
                ) {
                    return if (hasAdmin) {
                        SummaryMessage.ENABLED_BY_ADMIN.toPermSummary()
                    } else {
                        // Enabled state will be displayed by switch, so no need to add text for
                        // that
                        SummaryMessage.ENFORCED_BY_POLICY.toPermSummary()
                    }
                } else {
                    return if (hasAdmin) {
                        SummaryMessage.ENABLED_BY_ADMIN_FOREGROUND_ONLY.toPermSummary()
                    } else {
                        SummaryMessage.ENABLED_BY_POLICY_BACKGROUND_ONLY.toPermSummary()
                    }
                }
            }
        } else {
            // Part of the permission group can still be switched
            if (permGroup.background.isPolicyFixed) {
                return if (
                    mState.and(PermissionTarget.PERMISSION_BACKGROUND) !=
                        PermissionTarget.PERMISSION_NONE.value
                ) {
                    if (hasAdmin) {
                        SummaryMessage.ENABLED_BY_ADMIN_BACKGROUND_ONLY.toPermSummary(true)
                    } else {
                        SummaryMessage.ENABLED_BY_POLICY_BACKGROUND_ONLY.toPermSummary()
                    }
                } else {
                    if (hasAdmin) {
                        SummaryMessage.DISABLED_BY_ADMIN_BACKGROUND_ONLY.toPermSummary(true)
                    } else {
                        SummaryMessage.DISABLED_BY_POLICY_BACKGROUND_ONLY.toPermSummary()
                    }
                }
            } else if (permGroup.foreground.isPolicyFixed) {
                return if (hasAdmin) {
                    SummaryMessage.ENABLED_BY_ADMIN_FOREGROUND_ONLY.toPermSummary(true)
                } else {
                    SummaryMessage.ENABLED_BY_POLICY_FOREGROUND_ONLY.toPermSummary()
                }
            }
        }
        return SummaryMessage.NO_SUMMARY.toPermSummary()
    }

    /**
     * Is the foreground part of this group disabled. If the foreground is disabled, there is no
     * need to possible grant background access.
     *
     * @return `true` iff the permissions of this group are fixed
     */
    private fun isForegroundDisabledByPolicy(mGroup: LightAppPermGroup): Boolean {
        return mGroup.foreground.isPolicyFixed && !mGroup.isGranted
    }

    /** Whether policy is system fixed or fully fixed or foreground disabled */
    fun isFixedOrForegroundDisabled(mGroup: LightAppPermGroup): Boolean {
        return mGroup.isSystemFixed ||
            mGroup.isPolicyFullyFixed ||
            isForegroundDisabledByPolicy(mGroup)
    }

    /**
     * Get the app that acts as admin for this profile.
     *
     * @return The admin or `null` if there is no admin.
     */
    fun getAdmin(context: Context, mGroup: LightAppPermGroup): EnforcedAdmin? {
        return RestrictedLockUtils.getProfileOrDeviceOwner(context, mGroup.userHandle)
    }

    enum class PermissionTarget(val value: Int) {
        PERMISSION_NONE(0),
        PERMISSION_FOREGROUND(1),
        PERMISSION_BACKGROUND(2),
        PERMISSION_BOTH(3);

        infix fun and(other: PermissionTarget): Int {
            return value and other.value
        }

        infix fun and(other: Int): Int {
            return value and other
        }

        infix fun or(other: PermissionTarget): Int {
            return value or other.value
        }

        companion object {
            fun fromInt(value: Int) = values().first { it.value == value }
        }
    }
}

class ReviewPermissionViewModelFactory(
    private val app: Application,
    private val packageInfo: PackageInfo
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ReviewPermissionsViewModel(app, packageInfo = packageInfo) as T
    }
}
