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
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage
import com.android.permissioncontroller.permission.model.v31.PermissionUsages
import com.android.permissioncontroller.permission.model.v31.PermissionUsages.PermissionsUsagesChangeCallback
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionFragment
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModel
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModelFactory
import com.android.permissioncontroller.permission.ui.wear.model.WearAppPermissionUsagesViewModel
import com.android.permissioncontroller.permission.ui.wear.model.WearAppPermissionUsagesViewModelFactory

/**
 * This is a condensed version of
 * [com.android.permissioncontroller.permission.ui.handheld.PermissionAppsFragment], tailored for
 * Wear.
 *
 * Show and manage apps which request a single permission group.
 *
 * <p>Shows a list of apps which request at least on permission of this group.
 */
class WearPermissionAppsFragment : Fragment(), PermissionsUsagesChangeCallback {
    private val LOG_TAG = "PermissionAppsFragment"

    private lateinit var permissionUsages: PermissionUsages
    private lateinit var wearViewModel: WearAppPermissionUsagesViewModel

    // Suppress warning of the deprecated class [android.app.LoaderManager] since other form factors
    // are using the class to load PermissionUsages.
    @Suppress("DEPRECATION")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val permGroupName =
            arguments?.getString(Intent.EXTRA_PERMISSION_GROUP_NAME)
                ?: arguments?.getString(Intent.EXTRA_PERMISSION_NAME)
                    ?: throw RuntimeException("Permission group name must not be null.")
        val sessionId: Long =
            arguments?.getLong(Constants.EXTRA_SESSION_ID) ?: Constants.INVALID_SESSION_ID
        val isStorageAndLessThanT =
            !SdkLevel.isAtLeastT() && permGroupName == Manifest.permission_group.STORAGE

        val activity = requireActivity()
        val factory =
            PermissionAppsViewModelFactory(activity.getApplication(), permGroupName, this, Bundle())
        val viewModel = ViewModelProvider(this, factory).get(PermissionAppsViewModel::class.java)
        wearViewModel =
            ViewModelProvider(this, WearAppPermissionUsagesViewModelFactory())
                .get(WearAppPermissionUsagesViewModel::class.java)

        val onAppClick: (String, UserHandle, String) -> Unit = { packageName, user, category ->
            run {
                viewModel.navigateToAppPermission(
                    this,
                    packageName,
                    user,
                    AppPermissionFragment.createArgs(
                        packageName,
                        null,
                        permGroupName,
                        user,
                        this::class.java.name,
                        sessionId,
                        category
                    )
                )
            }
        }

        val onShowSystemClick: (Boolean) -> Unit = { showSystem ->
            run { viewModel.updateShowSystem(showSystem) }
        }

        val logPermissionAppsFragmentCreated:
            (String, UserHandle, Long, Boolean, Boolean, Boolean) -> Unit =
            { packageName, user, viewId, isAllowed, isAllowedForeground, isDenied ->
                run {
                    viewModel.logPermissionAppsFragmentCreated(
                        packageName,
                        user,
                        viewId,
                        isAllowed,
                        isAllowedForeground,
                        isDenied,
                        sessionId,
                        activity.getApplication(),
                        permGroupName,
                        LOG_TAG
                    )
                }
            }

        // If the build type is below S, the app ops for permission usage can't be found. Thus, we
        // shouldn't load permission usages, for them.
        if (SdkLevel.isAtLeastS()) {
            permissionUsages = PermissionUsages(requireContext())

            val filterTimeBeginMillis: Long = viewModel.getFilterTimeBeginMillis()
            permissionUsages.load(
                null,
                null,
                filterTimeBeginMillis,
                Long.MAX_VALUE,
                PermissionUsages.USAGE_FLAG_LAST,
                requireActivity().getLoaderManager(),
                false,
                false,
                this,
                false
            )
        }

        return ComposeView(requireContext()).apply {
            setContent {
                WearPermissionAppsScreen(
                    WearPermissionAppsHelper(
                        activity.getApplication(),
                        permGroupName,
                        viewModel,
                        wearViewModel,
                        isStorageAndLessThanT,
                        onAppClick,
                        onShowSystemClick,
                        logPermissionAppsFragmentCreated
                    )
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onPermissionUsagesChanged() {
        if (permissionUsages.usages.isEmpty()) {
            return
        }
        if (context == null) {
            // Async result has come in after our context is gone.
            return
        }
        wearViewModel.appPermissionUsages.value =
            ArrayList<AppPermissionUsage>(permissionUsages.usages)
    }
}
