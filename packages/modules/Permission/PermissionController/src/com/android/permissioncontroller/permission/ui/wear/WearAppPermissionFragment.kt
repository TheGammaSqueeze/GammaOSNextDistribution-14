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

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_ALWAYS
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_FOREGROUND
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ASK_EVERY_TIME
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY_FOREGROUND
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__GRANT_FINE_LOCATION
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__REVOKE_FINE_LOCATION
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_CALLER_NAME
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ChangeRequest
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ConfirmDialogShowingFragment
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModelFactory
import com.android.permissioncontroller.permission.ui.v33.AdvancedConfirmDialogArgs
import com.android.permissioncontroller.permission.ui.wear.model.AppPermissionConfirmDialogViewModel
import com.android.permissioncontroller.permission.ui.wear.model.AppPermissionConfirmDialogViewModelFactory
import com.android.permissioncontroller.permission.ui.wear.model.ConfirmDialogArgs
import com.android.permissioncontroller.permission.ui.wear.theme.WearPermissionTheme
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupLabel
import com.android.settingslib.RestrictedLockUtils

/**
 * Show and manage a single permission group for an app.
 *
 * <p>Allows the user to control whether the app is granted the permission
 *
 * <p>
 * Based on AppPermissionFragment in handheld code.
 */
class WearAppPermissionFragment : Fragment(), ConfirmDialogShowingFragment {

    private lateinit var confirmDialogViewModel: AppPermissionConfirmDialogViewModel

    companion object {
        private const val GRANT_CATEGORY = "grant_category"

        /**
         * Create a bundle with the arguments needed by this fragment
         *
         * @param packageName The name of the package
         * @param permName The name of the permission whose group this fragment is for (optional)
         * @param groupName The name of the permission group (required if permName not specified)
         * @param userHandle The user of the app permission group
         * @param caller The name of the fragment we called from
         * @param sessionId The current session ID
         * @param grantCategory The grant status of this app permission group. Used to initially set
         *   the button state
         * @return A bundle with all of the args placed
         */
        @JvmStatic
        fun createArgs(
            packageName: String?,
            permName: String?,
            groupName: String?,
            userHandle: UserHandle?,
            caller: String?,
            sessionId: Long,
            grantCategory: String?
        ): Bundle {
            val arguments = Bundle()
            arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName)
            if (groupName == null) {
                arguments.putString(Intent.EXTRA_PERMISSION_NAME, permName)
            } else {
                arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName)
            }
            arguments.putParcelable(Intent.EXTRA_USER, userHandle)
            arguments.putString(EXTRA_CALLER_NAME, caller)
            arguments.putLong(EXTRA_SESSION_ID, sessionId)
            arguments.putString(GRANT_CATEGORY, grantCategory)
            return arguments
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val activity = requireActivity()
        val packageName =
            arguments?.getString(Intent.EXTRA_PACKAGE_NAME)
                ?: throw RuntimeException("Package name must not be null.")
        val permGroupName =
            arguments?.getString(Intent.EXTRA_PERMISSION_GROUP_NAME)
                ?: arguments?.getString(Intent.EXTRA_PERMISSION_NAME)
                    ?: throw RuntimeException("Permission name must not be null.")

        val isStorageGroup = permGroupName == Manifest.permission_group.STORAGE

        val user =
            arguments?.let {
                BundleCompat.getParcelable(it, Intent.EXTRA_USER, UserHandle::class.java)
            }
                ?: UserHandle.SYSTEM
        val permGroupLabel = getPermGroupLabel(activity, permGroupName).toString()

        val sessionId = arguments?.getLong(EXTRA_SESSION_ID) ?: Constants.INVALID_SESSION_ID

        val factory =
            AppPermissionViewModelFactory(
                activity.getApplication(),
                packageName,
                permGroupName,
                user,
                sessionId
            )
        val viewModel = ViewModelProvider(this, factory).get(AppPermissionViewModel::class.java)
        confirmDialogViewModel =
            ViewModelProvider(this, AppPermissionConfirmDialogViewModelFactory())
                .get(AppPermissionConfirmDialogViewModel::class.java)

        @Suppress("ktlint:standard:max-line-length")
        val onLocationSwitchChanged: (Boolean) -> Unit = { checked ->
            run {
                val changeRequest =
                    if (checked) {
                        ChangeRequest.GRANT_FINE_LOCATION
                    } else {
                        ChangeRequest.REVOKE_FINE_LOCATION
                    }
                val buttonClicked =
                    if (checked) {
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__GRANT_FINE_LOCATION
                    } else {
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__REVOKE_FINE_LOCATION
                    }
                viewModel.requestChange(false, this, this, changeRequest, buttonClicked)
            }
        }
        val onGrantedStateChanged: (ButtonType, Boolean) -> Unit = { buttonType, checked ->
            run {
                if (!checked) {
                    return@run
                }
                val param = getGrantedStateChangeParam(buttonType)
                if (!isStorageGroup || !param.requiresCustomStorageBehavior) {
                    viewModel.requestChange(
                        param.setOneTime,
                        this,
                        this,
                        param.request,
                        param.buttonClickAction
                    )
                } else {
                    showConfirmDialog(
                        ChangeRequest.GRANT_ALL_FILE_ACCESS,
                        R.string.special_file_access_dialog,
                        -1,
                        false
                    )
                }
                setResult(param.result, permGroupName)
            }
        }
        val onFooterClicked: (RestrictedLockUtils.EnforcedAdmin) -> Unit = { admin ->
            run { RestrictedLockUtils.sendShowAdminSupportDetailsIntent(requireContext(), admin) }
        }
        val onConfirmDialogOkButtonClick: (ConfirmDialogArgs) -> Unit = { args ->
            run {
                if (args.changeRequest == ChangeRequest.GRANT_ALL_FILE_ACCESS) {
                    viewModel.setAllFilesAccess(true)
                    viewModel.requestChange(
                        false,
                        this,
                        this,
                        ChangeRequest.GRANT_BOTH,
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW
                    )
                } else {
                    viewModel.onDenyAnyWay(args.changeRequest, args.buttonPressed, args.oneTime)
                }
                confirmDialogViewModel.showConfirmDialogLiveData.value = false
            }
        }
        val onConfirmDialogCancelButtonClick: () -> Unit = {
            confirmDialogViewModel.showConfirmDialogLiveData.value = false
        }
        val onAdvancedConfirmDialogOkButtonClick: (AdvancedConfirmDialogArgs) -> Unit = { args ->
            run {
                viewModel.requestChange(
                    args.setOneTime!!,
                    this,
                    this,
                    args.changeRequest!!,
                    args.buttonClicked!!
                )
                confirmDialogViewModel.showAdvancedConfirmDialogLiveData.value = false
            }
        }
        val onAdvancedConfirmDialogCancelButtonClick: () -> Unit = {
            confirmDialogViewModel.showAdvancedConfirmDialogLiveData.value = false
        }

        return ComposeView(activity).apply {
            setContent {
                WearPermissionTheme {
                    WearAppPermissionScreen(
                        permGroupLabel,
                        viewModel,
                        confirmDialogViewModel,
                        onLocationSwitchChanged,
                        onGrantedStateChanged,
                        onFooterClicked,
                        onConfirmDialogOkButtonClick,
                        onConfirmDialogCancelButtonClick,
                        onAdvancedConfirmDialogOkButtonClick,
                        onAdvancedConfirmDialogCancelButtonClick
                    )
                }
            }
        }
    }

    override fun showConfirmDialog(
        changeRequest: ChangeRequest,
        @StringRes messageId: Int,
        buttonPressed: Int,
        oneTime: Boolean
    ) {
        confirmDialogViewModel.confirmDialogArgs =
            ConfirmDialogArgs(
                messageId = messageId,
                changeRequest = changeRequest,
                buttonPressed = buttonPressed,
                oneTime = oneTime
            )
        confirmDialogViewModel.showConfirmDialogLiveData.value = true
    }

    override fun showAdvancedConfirmDialog(args: AdvancedConfirmDialogArgs) {
        confirmDialogViewModel.advancedConfirmDialogArgs = args
        confirmDialogViewModel.showAdvancedConfirmDialogLiveData.value = true
    }

    private fun setResult(@GrantPermissionsViewHandler.Result result: Int, permGroupName: String) {
        val intent: Intent =
            Intent()
                .putExtra(
                    ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_INTERACTED,
                    permGroupName
                )
                .putExtra(ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_RESULT, result)
        requireActivity().setResult(Activity.RESULT_OK, intent)
    }

    fun getGrantedStateChangeParam(buttonType: ButtonType) =
        when (buttonType) {
            ButtonType.ALLOW ->
                GrantedStateChangeParam(
                    false,
                    ChangeRequest.GRANT_FOREGROUND,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW,
                    GRANTED_ALWAYS,
                    false
                )
            ButtonType.ALLOW_ALWAYS ->
                GrantedStateChangeParam(
                    false,
                    ChangeRequest.GRANT_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_ALWAYS,
                    GRANTED_ALWAYS,
                    true
                )
            ButtonType.ALLOW_FOREGROUND ->
                GrantedStateChangeParam(
                    false,
                    ChangeRequest.GRANT_FOREGROUND_ONLY,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_FOREGROUND,
                    GRANTED_FOREGROUND_ONLY,
                    true
                )
            ButtonType.ASK ->
                GrantedStateChangeParam(
                    true,
                    ChangeRequest.REVOKE_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ASK_EVERY_TIME,
                    DENIED,
                    false
                )
            ButtonType.DENY ->
                GrantedStateChangeParam(
                    false,
                    ChangeRequest.REVOKE_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY,
                    DENIED_DO_NOT_ASK_AGAIN,
                    false
                )
            ButtonType.DENY_FOREGROUND ->
                GrantedStateChangeParam(
                    false,
                    ChangeRequest.REVOKE_FOREGROUND,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY_FOREGROUND,
                    DENIED_DO_NOT_ASK_AGAIN,
                    false
                )
            else -> throw RuntimeException("Wrong button type: $buttonType")
        }
}

data class GrantedStateChangeParam(
    val setOneTime: Boolean,
    val request: ChangeRequest,
    val buttonClickAction: Int,
    val result: Int,
    val requiresCustomStorageBehavior: Boolean
)
