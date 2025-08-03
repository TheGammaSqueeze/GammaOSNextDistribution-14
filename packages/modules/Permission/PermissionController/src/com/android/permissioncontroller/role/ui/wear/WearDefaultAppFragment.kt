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

import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.permission.ui.wear.theme.WearPermissionTheme
import com.android.permissioncontroller.role.ui.DefaultAppViewModel
import com.android.permissioncontroller.role.ui.ManageRoleHolderStateLiveData
import com.android.permissioncontroller.role.ui.wear.model.DefaultAppConfirmDialogViewModel
import com.android.permissioncontroller.role.ui.wear.model.DefaultAppConfirmDialogViewModelFactory
import com.android.role.controller.model.Role
import com.android.role.controller.model.Roles

/**
 * Wear specific version of
 * [com.android.permissioncontroller.role.ui.handheld.HandheldDefaultAppFragment]
 */
class WearDefaultAppFragment : Fragment() {
    private lateinit var role: Role
    private lateinit var viewModel: DefaultAppViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val roleName = arguments?.getString(Intent.EXTRA_ROLE_NAME) ?: ""
        val user =
            arguments?.let {
                BundleCompat.getParcelable(it, Intent.EXTRA_USER, UserHandle::class.java)!!
            }
                ?: UserHandle.SYSTEM

        val activity = requireActivity()
        role =
            Roles.get(activity)[roleName]
                ?: let {
                    Log.e(TAG, "Unknown role: $roleName")
                    activity.finish()
                    return null
                }

        viewModel =
            ViewModelProvider(this, DefaultAppViewModel.Factory(role, user, activity.application))
                .get(DefaultAppViewModel::class.java)
        viewModel.manageRoleHolderStateLiveData.observe(this, this::onManageRoleHolderStateChanged)

        val confirmDialogViewModel =
            ViewModelProvider(this, DefaultAppConfirmDialogViewModelFactory())
                .get(DefaultAppConfirmDialogViewModel::class.java)

        return ComposeView(activity).apply {
            setContent {
                WearPermissionTheme {
                    WearDefaultAppScreen(
                        WearDefaultAppHelper(
                            activity,
                            user,
                            role,
                            viewModel,
                            confirmDialogViewModel
                        )
                    )
                }
            }
        }
    }

    private fun onManageRoleHolderStateChanged(state: Int) {
        val liveData = viewModel.manageRoleHolderStateLiveData
        when (state) {
            ManageRoleHolderStateLiveData.STATE_SUCCESS -> {
                val packageName = liveData.lastPackageName
                if (packageName != null) {
                    role.onHolderSelectedAsUser(packageName, liveData.lastUser, requireContext())
                }
                liveData.resetState()
            }
            ManageRoleHolderStateLiveData.STATE_FAILURE -> liveData.resetState()
        }
    }

    companion object {
        const val TAG = "WearDefaultAppFragment"

        /** Creates a new instance of [WearDefaultAppFragment]. */
        fun newInstance(roleName: String, user: UserHandle): WearDefaultAppFragment {
            return WearDefaultAppFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(Intent.EXTRA_ROLE_NAME, roleName)
                        putParcelable(Intent.EXTRA_USER, user)
                    }
            }
        }
    }
}
