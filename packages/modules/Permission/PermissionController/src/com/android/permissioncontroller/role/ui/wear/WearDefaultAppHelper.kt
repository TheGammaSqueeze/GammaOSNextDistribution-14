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

package com.android.permissioncontroller.role.ui.wear

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.UserHandle
import android.util.Pair
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.role.ui.DefaultAppViewModel
import com.android.permissioncontroller.role.ui.wear.model.ConfirmDialogArgs
import com.android.permissioncontroller.role.ui.wear.model.DefaultAppConfirmDialogViewModel
import com.android.permissioncontroller.role.utils.RoleUiBehaviorUtils
import com.android.role.controller.model.Role

/** A helper class to retrieve default apps to [WearDefaultAppScreen]. */
class WearDefaultAppHelper(
    val context: Context,
    val user: UserHandle,
    val role: Role,
    val viewModel: DefaultAppViewModel,
    val confirmDialogViewModel: DefaultAppConfirmDialogViewModel
) {
    fun getTitle() = context.getString(role.labelResource)

    fun getNonePreference(
        qualifyingApplications: List<Pair<ApplicationInfo, Boolean>>
    ): WearRoleApplicationPreference? =
        if (role.shouldShowNone()) {
            WearRoleApplicationPreference(
                    context = context,
                    label = context.getString(R.string.default_app_none),
                    checked = !hasHolderApplication(qualifyingApplications),
                    onDefaultCheckChanged = { _ -> viewModel.setNoneDefaultApp() }
                )
                .apply { icon = context.getDrawable(R.drawable.ic_remove_circle) }
        } else {
            null
        }

    fun getPreferences(
        qualifyingApplications: List<Pair<ApplicationInfo, Boolean>>
    ): List<WearRoleApplicationPreference> {
        return qualifyingApplications
            .map { pair ->
                WearRoleApplicationPreference(
                        context = context,
                        label = Utils.getFullAppLabel(pair.first, context),
                        checked = pair.second,
                        onDefaultCheckChanged = { _ ->
                            run {
                                val packageName = pair.first.packageName
                                val confirmationMessage =
                                    RoleUiBehaviorUtils.getConfirmationMessage(
                                        role,
                                        packageName,
                                        context
                                    )
                                if (confirmationMessage != null) {
                                    showConfirmDialog(packageName, confirmationMessage.toString())
                                } else {
                                    setDefaultApp(packageName)
                                }
                            }
                        }
                    )
                    .apply { icon = pair.first.loadIcon(context.packageManager) }
                    .let {
                        RoleUiBehaviorUtils.prepareApplicationPreferenceAsUser(
                            role,
                            it,
                            pair.first,
                            user,
                            context
                        )
                        return@map it
                    }
            }
            .toList()
    }

    private fun showConfirmDialog(packageName: String, message: String) {
        confirmDialogViewModel.confirmDialogArgs =
            ConfirmDialogArgs(
                message = message,
                onOkButtonClick = {
                    setDefaultApp(packageName)
                    dismissConfirmDialog()
                },
                onCancelButtonClick = { dismissConfirmDialog() }
            )
        confirmDialogViewModel.showConfirmDialogLiveData.value = true
    }

    private fun dismissConfirmDialog() {
        confirmDialogViewModel.confirmDialogArgs = null
        confirmDialogViewModel.showConfirmDialogLiveData.value = false
    }
    private fun setDefaultApp(packageName: String) {
        viewModel.setDefaultApp(packageName)
    }

    fun getDescription() = context.getString(role.descriptionResource)

    private fun hasHolderApplication(
        qualifyingApplications: List<Pair<ApplicationInfo, Boolean>>
    ): Boolean = qualifyingApplications.map { it.second }.find { true } ?: false
}
