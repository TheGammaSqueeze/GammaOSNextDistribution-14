/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.wear

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.UserHandle
import android.util.ArraySet
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.permission.flags.Flags
import com.android.permissioncontroller.R
import com.android.permissioncontroller.hibernation.isHibernationEnabled
import com.android.permissioncontroller.permission.model.AppPermissionGroup
import com.android.permissioncontroller.permission.model.AppPermissions
import com.android.permissioncontroller.permission.model.Permission
import com.android.permissioncontroller.permission.model.livedatatypes.HibernationSettingState
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage
import com.android.permissioncontroller.permission.ui.Category
import com.android.permissioncontroller.permission.ui.LocationProviderInterceptDialog
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionFragment
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModel
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModel.GroupUiInfo
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModel.PermSubtitle
import com.android.permissioncontroller.permission.ui.wear.model.AppPermissionGroupsRevokeDialogViewModel
import com.android.permissioncontroller.permission.ui.wear.model.RevokeDialogArgs
import com.android.permissioncontroller.permission.ui.wear.model.WearAppPermissionUsagesViewModel
import com.android.permissioncontroller.permission.utils.ArrayUtils
import com.android.permissioncontroller.permission.utils.LocationUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.legacy.LegacySafetyNetLogger
import com.android.permissioncontroller.permission.utils.navigateSafe

class WearAppPermissionGroupsHelper(
    val context: Context,
    val fragment: Fragment,
    val user: UserHandle,
    val packageName: String,
    val sessionId: Long,
    private val appPermissions: AppPermissions,
    val viewModel: AppPermissionGroupsViewModel,
    val wearViewModel: WearAppPermissionUsagesViewModel,
    val revokeDialogViewModel: AppPermissionGroupsRevokeDialogViewModel,
    private val toggledGroups: ArraySet<AppPermissionGroup> = ArraySet()
) {
    fun getPermissionGroupChipParams(
        appPermissionUsages: List<AppPermissionUsage>
    ): List<PermissionGroupChipParam> {
        if (DEBUG) {
            Log.d(TAG, "getPermissionGroupChipParams() called")
        }
        val groupUsageLastAccessTime: MutableMap<String, Long> = HashMap()
        viewModel.extractGroupUsageLastAccessTime(
            groupUsageLastAccessTime,
            appPermissionUsages,
            packageName
        )
        val groupUiInfos = viewModel.packagePermGroupsLiveData.value
        val groups: List<AppPermissionGroup> = appPermissions.permissionGroups

        val grantedTypes: MutableMap<String, Category> = HashMap()
        val bookKeeping: MutableMap<String, GroupUiInfo> = HashMap()
        if (groupUiInfos != null) {
            for (category in groupUiInfos.keys) {
                val groupInfoList: List<GroupUiInfo> = groupUiInfos[category] ?: emptyList()
                for (groupInfo in groupInfoList) {
                    bookKeeping[groupInfo.groupName] = groupInfo
                    grantedTypes[groupInfo.groupName] = category
                }
            }
        }

        val list: MutableList<PermissionGroupChipParam> = ArrayList()

        groups
            .filter { Utils.shouldShowPermission(context, it) }
            .partition { it.declaringPackage == Utils.OS_PKG }
            .let { it.first.plus(it.second) }
            .forEach { group ->
                if (Utils.areGroupPermissionsIndividuallyControlled(context, group.name)) {
                    // If permission is controlled individually, we show all requested permission
                    // inside this group.
                    for (perm in getPermissionInfosFromGroup(group)) {
                        list.add(
                            PermissionGroupChipParam(
                                group = group,
                                perm = perm,
                                label = perm.loadLabel(context.packageManager).toString(),
                                checked = group.areRuntimePermissionsGranted(arrayOf(perm.name)),
                                onCheckedChanged = { checked ->
                                    run { onPermissionGrantedStateChanged(group, perm, checked) }
                                }
                            )
                        )
                    }
                } else {
                    val category = grantedTypes[group.name]
                    if (category != null) {
                        list.add(
                            PermissionGroupChipParam(
                                group = group,
                                label = group.label.toString(),
                                summary =
                                    bookKeeping[group.name]?.let {
                                        getSummary(
                                            category,
                                            it,
                                            groupUsageLastAccessTime[it.groupName]
                                        )
                                    },
                                onClick = { onPermissionGroupClicked(group, category.categoryName) }
                            )
                        )
                    }
                }
            }
        return list
    }

    private fun getSummary(
        category: Category?,
        groupUiInfo: GroupUiInfo,
        lastAccessTime: Long?
    ): String {
        val grantSummary =
            getGrantSummary(category, groupUiInfo)?.let { context.getString(it) } ?: ""
        val summary = StringBuilder(grantSummary)
        if (Flags.wearPrivacyDashboardEnabledReadOnly()) {
            WearUtils.getPreferenceSummary(context, lastAccessTime).let {
                if (it.isNotEmpty()) {
                    summary.append(System.lineSeparator()).append(it)
                }
            }
        }
        return summary.toString()
    }

    private fun getGrantSummary(category: Category?, groupUiInfo: GroupUiInfo): Int? {
        val subtitle = groupUiInfo.subtitle
        if (category != null) {
            when (category) {
                Category.ALLOWED ->
                    return if (subtitle == PermSubtitle.BACKGROUND) {
                        R.string.allowed_always_header
                    } else {
                        R.string.allowed_header
                    }
                Category.ASK -> return R.string.ask_header
                Category.DENIED -> return R.string.denied_header
                else -> {
                    /* Fallback though */
                }
            }
        }
        return when (subtitle) {
            PermSubtitle.FOREGROUND_ONLY -> R.string.permission_subtitle_only_in_foreground
            PermSubtitle.MEDIA_ONLY -> R.string.permission_subtitle_media_only
            PermSubtitle.ALL_FILES -> R.string.permission_subtitle_all_files
            else -> null
        }
    }

    private fun getPermissionInfosFromGroup(group: AppPermissionGroup): List<PermissionInfo> =
        group.permissions
            .map {
                it?.let {
                    try {
                        context.packageManager.getPermissionInfo(it.name, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(TAG, "No permission:" + it.name)
                        null
                    }
                }
            }
            .filterNotNull()
            .toList()

    private fun onPermissionGrantedStateChanged(
        group: AppPermissionGroup,
        perm: PermissionInfo,
        checked: Boolean
    ) {
        if (checked) {
            group.grantRuntimePermissions(true, false, arrayOf(perm.name))

            if (
                Utils.areGroupPermissionsIndividuallyControlled(context, group.name) &&
                    group.doesSupportRuntimePermissions()
            ) {
                // We are granting a permission from a group but since this is an
                // individual permission control other permissions in the group may
                // be revoked, hence we need to mark them user fixed to prevent the
                // app from requesting a non-granted permission and it being granted
                // because another permission in the group is granted. This applies
                // only to apps that support runtime permissions.
                var revokedPermissionsToFix: Array<String?>? = null
                val permissionCount = group.permissions.size
                for (i in 0 until permissionCount) {
                    val current = group.permissions[i]
                    if (!current.isGranted && !current.isUserFixed) {
                        revokedPermissionsToFix =
                            ArrayUtils.appendString(revokedPermissionsToFix, current.name)
                    }
                }
                if (revokedPermissionsToFix != null) {
                    // If some permissions were not granted then they should be fixed.
                    group.revokeRuntimePermissions(true, revokedPermissionsToFix)
                }
            }
        } else {
            val appPerm: Permission = getPermissionFromGroup(group, perm.name) ?: return

            val grantedByDefault = appPerm.isGrantedByDefault
            if (
                grantedByDefault ||
                    (!group.doesSupportRuntimePermissions() &&
                        !revokeDialogViewModel.hasConfirmedRevoke)
            ) {
                showRevocationWarningDialog(
                    messageId =
                        if (grantedByDefault) {
                            R.string.system_warning
                        } else {
                            R.string.old_sdk_deny_warning
                        },
                    onOkButtonClick = {
                        revokePermissionInGroup(group, perm.name)
                        if (!appPerm.isGrantedByDefault) {
                            revokeDialogViewModel.hasConfirmedRevoke = true
                        }
                        revokeDialogViewModel.dismissDialog()
                    }
                )
            } else {
                revokePermissionInGroup(group, perm.name)
            }
        }
    }

    private fun getPermissionFromGroup(group: AppPermissionGroup, permName: String): Permission? {
        return group.permissions.find { it.name == permName }
            ?: let {
                if ("user" == Build.TYPE) {
                    Log.e(
                        TAG,
                        "The impossible happens, permission $permName is not in group $group.name."
                    )
                    null
                } else {
                    // This is impossible, throw a fatal error in non-user build.
                    throw IllegalArgumentException(
                        "Permission $permName is not in group $group.name%s"
                    )
                }
            }
    }

    private fun revokePermissionInGroup(group: AppPermissionGroup, permName: String) {
        group.revokeRuntimePermissions(true, arrayOf(permName))

        if (
            Utils.areGroupPermissionsIndividuallyControlled(context, group.name) &&
                group.doesSupportRuntimePermissions() &&
                !group.areRuntimePermissionsGranted()
        ) {
            // If we just revoked the last permission we need to clear
            // the user fixed state as now the app should be able to
            // request them at runtime if supported.
            group.revokeRuntimePermissions(false)
        }
    }

    private fun showRevocationWarningDialog(
        messageId: Int,
        onOkButtonClick: () -> Unit,
        onCancelButtonClick: () -> Unit = { revokeDialogViewModel.dismissDialog() }
    ) {
        revokeDialogViewModel.revokeDialogArgs =
            RevokeDialogArgs(
                messageId = messageId,
                onOkButtonClick = onOkButtonClick,
                onCancelButtonClick = onCancelButtonClick
            )
        revokeDialogViewModel.showDialogLiveData.value = true
    }

    private fun onPermissionGroupClicked(group: AppPermissionGroup, grantCategory: String) {
        val permGroupName = group.name
        val packageName = group.app?.packageName ?: ""
        val caller = WearAppPermissionGroupsFragment::class.java.name

        addToggledGroup(group)

        if (LocationUtils.isLocationGroupAndProvider(context, permGroupName, packageName)) {
            val intent = Intent(context, LocationProviderInterceptDialog::class.java)
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            context.startActivityAsUser(intent, user)
        } else if (
            LocationUtils.isLocationGroupAndControllerExtraPackage(
                context,
                permGroupName,
                packageName
            )
        ) {
            // Redirect to location controller extra package settings.
            LocationUtils.startLocationControllerExtraPackageSettings(context, user)
        } else {
            val args =
                AppPermissionFragment.createArgs(
                    packageName,
                    null,
                    permGroupName,
                    user,
                    caller,
                    sessionId,
                    grantCategory
                )
            fragment.findNavController().navigateSafe(R.id.perm_groups_to_app, args)
        }
    }

    private fun addToggledGroup(group: AppPermissionGroup) {
        toggledGroups.add(group)
    }

    fun logAndClearToggledGroups() {
        LegacySafetyNetLogger.logPermissionsToggled(toggledGroups)
        toggledGroups.clear()
    }

    fun getAutoRevokeChipParam(state: HibernationSettingState?): AutoRevokeChipParam? =
        state?.let {
            AutoRevokeChipParam(
                labelRes =
                    if (isHibernationEnabled()) {
                        R.string.unused_apps_label_v2
                    } else {
                        R.string.auto_revoke_label
                    },
                visible = it.revocableGroupNames.isNotEmpty(),
                checked = it.isEligibleForHibernation(),
                onCheckedChanged = { checked ->
                    run {
                        viewModel.setAutoRevoke(checked)
                        Log.w(TAG, "setAutoRevoke $checked")
                    }
                }
            )
        }

    companion object {
        const val DEBUG = false
        const val TAG = WearAppPermissionGroupsFragment.LOG_TAG
    }
}

data class PermissionGroupChipParam(
    val group: AppPermissionGroup,
    val perm: PermissionInfo? = null,
    val label: String,
    val summary: String? = null,
    val enabled: Boolean = true,
    val checked: Boolean? = null,
    val onClick: () -> Unit = {},
    val onCheckedChanged: (Boolean) -> Unit = {}
)

data class AutoRevokeChipParam(
    val labelRes: Int,
    val visible: Boolean,
    val checked: Boolean = false,
    val onCheckedChanged: (Boolean) -> Unit
)
