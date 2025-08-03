/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.auto.dashboard

import android.app.Activity
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModelProvider
import com.android.car.ui.preference.CarUiPreference
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__SHOW_SYSTEM_CLICKED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.auto.AutoSettingsFrameFragment
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.AppDataLoader
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.PermissionApp
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage
import com.android.permissioncontroller.permission.model.v31.PermissionUsages
import com.android.permissioncontroller.permission.model.v31.PermissionUsages.PermissionsUsagesChangeCallback
import com.android.permissioncontroller.permission.ui.legacy.PermissionUsageViewModelFactoryLegacy
import com.android.permissioncontroller.permission.ui.legacy.PermissionUsageViewModelLegacy
import com.android.permissioncontroller.permission.ui.legacy.PermissionUsageViewModelLegacy.PermissionGroupWithUsageCount
import com.android.permissioncontroller.permission.ui.model.ManagePermissionsViewModel
import com.android.permissioncontroller.permission.ui.model.v31.PermissionUsageControlPreferenceUtils
import com.android.permissioncontroller.permission.utils.Utils

@RequiresApi(Build.VERSION_CODES.S)
class AutoPermissionUsageFragment : AutoSettingsFrameFragment(), PermissionsUsagesChangeCallback {

    companion object {
        private const val KEY_SESSION_ID = "_session_id"
    }

    private val SESSION_ID_KEY = (AutoPermissionUsageFragment::class.java.name + KEY_SESSION_ID)

    private lateinit var permissionUsages: PermissionUsages
    private lateinit var usageViewModel: PermissionUsageViewModelLegacy
    private lateinit var managePermissionsViewModel: ManagePermissionsViewModel

    private var appPermissionUsages: List<AppPermissionUsage> = listOf()
    private var permissionGroups: List<PermGroupPackagesUiInfo> = listOf()
    private var showSystem = false

    // Auto currently doesn't show last 7 days due to the UX constraint that there is no pattern to
    // support multiple actions (showSystem & show7Days). Support will likely be added once this
    // pattern is resolved.
    private val show7Days = false
    private var finishedInitialLoad = false
    private var hasSystemApps = false

    /** Unique Id of a request */
    private var sessionId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headerLabel = getString(R.string.permission_usage_title)
        sessionId =
            savedInstanceState?.getLong(SESSION_ID_KEY)
                ?: (arguments?.getLong(Constants.EXTRA_SESSION_ID, Constants.INVALID_SESSION_ID)
                    ?: Constants.INVALID_SESSION_ID)

        val context: Context = preferenceManager.getContext()
        permissionUsages = PermissionUsages(context)
        val roleManager = Utils.getSystemServiceSafe(context, RoleManager::class.java)
        val application: Application = requireActivity().getApplication()
        val managePermissionsViewModelFactory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        managePermissionsViewModel =
            ViewModelProvider(this, managePermissionsViewModelFactory)[
                ManagePermissionsViewModel::class.java]
        val usageViewModelFactory = PermissionUsageViewModelFactoryLegacy(roleManager)
        usageViewModel =
            ViewModelProvider(this, usageViewModelFactory)[
                PermissionUsageViewModelLegacy::class.java]

        managePermissionsViewModel.standardPermGroupsLiveData.observe(
            this,
            this::onPermissionGroupsChanged
        )
        setLoading(true)
        reloadData()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(SESSION_ID_KEY, sessionId)
    }

    private fun onPermissionGroupsChanged(permissionGroups: List<PermGroupPackagesUiInfo>) {
        this.permissionGroups = permissionGroups
        updateUI()
    }

    override fun onCreatePreferences(bundlle: Bundle?, s: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(context!!)
    }

    private fun updateSystemToggle() {
        if (!showSystem) {
            PermissionControllerStatsLog.write(
                PERMISSION_USAGE_FRAGMENT_INTERACTION,
                sessionId,
                PERMISSION_USAGE_FRAGMENT_INTERACTION__ACTION__SHOW_SYSTEM_CLICKED
            )
        }
        showSystem = !showSystem
        updateAction()
        updateUI()
    }

    private fun updateAction() {
        if (!hasSystemApps) {
            setAction(null, null)
            return
        }
        val label =
            if (showSystem) {
                getString(R.string.menu_hide_system)
            } else {
                getString(R.string.menu_show_system)
            }
        setAction(label) { updateSystemToggle() }
    }

    /** Reloads the data to show. */
    private fun reloadData() {
        usageViewModel.loadPermissionUsages(
            requireActivity().getLoaderManager(),
            permissionUsages,
            this
        )
        if (finishedInitialLoad) {
            setLoading(false)
        }
    }

    override fun onPermissionUsagesChanged() {
        if (permissionUsages.usages.isEmpty()) {
            return
        }
        appPermissionUsages = ArrayList(permissionUsages.usages)
        updateUI()
    }

    private fun updateUI() {
        if (permissionGroups.isEmpty() || appPermissionUsages.isEmpty()) {
            return
        }
        getPreferenceScreen().removeAll()

        val permissionUsagesUiData =
            usageViewModel.buildPermissionUsagesUiData(
                appPermissionUsages,
                show7Days,
                showSystem,
                requireContext()
            )
        val permissionApps = permissionUsagesUiData.permissionApps
        val displayShowSystemToggle = permissionUsagesUiData.displayShowSystemToggle

        if (hasSystemApps != displayShowSystemToggle) {
            hasSystemApps = displayShowSystemToggle
            updateAction()
        }

        val permissionGroupWithUsageCounts: List<PermissionGroupWithUsageCount> =
            permissionUsagesUiData.orderedPermissionGroupsWithUsageCount

        addUIContent(permissionGroupWithUsageCounts, permissionApps)
    }

    /** Use the usages and permApps that are previously constructed to add UI content to the page */
    private fun addUIContent(
        permissionGroupWithUsageCounts: List<PermissionGroupWithUsageCount>,
        permApps: java.util.ArrayList<PermissionApp>
    ) {
        AppDataLoader(context) {
                // Show permission groups with permissions granted to an app, including groups
                // where the permission is only granted to a system app. This still excludes groups
                // that don't have grants from any apps. Showing the same groups regardless of
                // whether showSystem is selected avoids permission groups hiding and appearing,
                // which is a confusing user experience.
                val usedPermissionGroups =
                    permissionGroups
                        .filter {
                            (it.nonSystemUserSetOrPreGranted > 0) or
                                (it.systemUserSetOrPreGranted > 0)
                        }
                        .filterNot { it.onlyShellPackageGranted }

                for (i in permissionGroupWithUsageCounts.indices) {
                    val groupName = permissionGroupWithUsageCounts[i].permGroup
                    val count = permissionGroupWithUsageCounts[i].appCount
                    if ((usedPermissionGroups.filter { it.name == groupName }).isEmpty()) {
                        continue
                    }
                    val permissionUsagePreference = CarUiPreference(requireContext())
                    PermissionUsageControlPreferenceUtils.initPreference(
                        permissionUsagePreference,
                        requireContext(),
                        groupName,
                        count,
                        showSystem,
                        sessionId,
                        show7Days
                    )
                    getPreferenceScreen().addPreference(permissionUsagePreference)
                }
                finishedInitialLoad = true
                setLoading(false)
                val activity: Activity? = activity
                if (activity != null) {
                    permissionUsages.stopLoader(activity.loaderManager)
                }
            }
            .execute(*permApps.toTypedArray())
    }
}
