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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageDetailsViewModel.PermissionUsageDetailsViewModelFactory
import com.android.permissioncontroller.permission.utils.KotlinUtils.is7DayToggleEnabled

/**
 * This is a condensed version of
 * [com.android.permissioncontroller.permission.ui.handheld.v31.PermissionUsageDetailsFragment],
 * tailored for Wear.
 */
@RequiresApi(Build.VERSION_CODES.S)
class WearPermissionUsageDetailsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val permissionGroup =
            arguments?.getString(Intent.EXTRA_PERMISSION_GROUP_NAME)
                ?: let {
                    Log.e(TAG, "No permission group was provided for PermissionDetailsFragment")
                    return null
                }
        val showSystem =
            arguments?.getBoolean(ManagePermissionsActivity.EXTRA_SHOW_SYSTEM, false) ?: false
        val show7Days =
            arguments?.getBoolean(ManagePermissionsActivity.EXTRA_SHOW_7_DAYS, false) ?: false

        val factory =
            PermissionUsageDetailsViewModelFactory(
                PermissionControllerApplication.get(),
                this,
                permissionGroup
            )
        val viewModel =
            ViewModelProvider(this, factory).get(PermissionUsageDetailsViewModel::class.java)
        viewModel.updateShowSystemAppsToggle(showSystem)
        viewModel.updateShow7DaysToggle(is7DayToggleEnabled() && show7Days)

        return ComposeView(requireContext()).apply {
            setContent { WearPermissionUsageDetailsScreen(permissionGroup, viewModel) }
        }
    }

    companion object {
        private const val TAG = "WearPermissionUsageDetails"

        @JvmStatic
        fun newInstance(
            groupName: String?,
            showSystem: Boolean,
            show7Days: Boolean
        ): WearPermissionUsageDetailsFragment {
            return WearPermissionUsageDetailsFragment().apply {
                val arguments =
                    Bundle().apply {
                        if (groupName != null) {
                            putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName)
                        }
                        putBoolean(ManagePermissionsActivity.EXTRA_SHOW_SYSTEM, showSystem)
                        putBoolean(ManagePermissionsActivity.EXTRA_SHOW_7_DAYS, show7Days)
                    }
                setArguments(arguments)
            }
        }
    }
}
