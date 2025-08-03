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
 *
 *
 */

/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.permissions.connectedapps

import android.content.Intent.EXTRA_PACKAGE_NAME
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.maybeShowWhatsNewDialog
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.showDataRestoreInProgressDialog
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.showMigrationInProgressDialog
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.shared.Constants.EXTRA_APP_NAME
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.ALLOWED
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.DENIED
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.dismissLoadingDialog
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.showLoadingDialog
import com.android.settingslib.widget.AppPreference
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment to show allowed and denied apps for health permissions. It is used as an entry point
 * from PermissionController.
 */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class SettingsManagePermissionFragment : Hilt_SettingsManagePermissionFragment() {

    companion object {
        const val ALLOWED_APPS_GROUP = "allowed_apps"
        const val DENIED_APPS_GROUP = "denied_apps"
    }

    init {
        this.setPageName(PageName.SETTINGS_MANAGE_PERMISSIONS_PAGE)
    }

    private val allowedAppsGroup: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(ALLOWED_APPS_GROUP)
    }

    private val deniedAppsGroup: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(DENIED_APPS_GROUP)
    }

    private val viewModel: ConnectedAppsViewModel by viewModels()
    private val migrationViewModel: MigrationViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.settings_manage_permission_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.connectedApps.observe(viewLifecycleOwner) { connectedApps ->
            val connectedAppsGroup = connectedApps.groupBy { it.status }
            updateAllowedApps(connectedAppsGroup[ALLOWED].orEmpty())
            updateDeniedApps(connectedAppsGroup[DENIED].orEmpty())
        }
        viewModel.disconnectAllState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ConnectedAppsViewModel.DisconnectAllState.Loading -> {
                    showLoadingDialog()
                }
                else -> {
                    dismissLoadingDialog()
                }
            }
        }
        migrationViewModel.migrationState.observe(viewLifecycleOwner) { migrationState ->
            when (migrationState) {
                is MigrationViewModel.MigrationFragmentState.WithData -> {
                    maybeShowMigrationDialog(migrationState.migrationRestoreState)
                }
                else -> {
                    // do nothing
                }
            }
        }
    }

    private fun maybeShowMigrationDialog(migrationRestoreState: MigrationRestoreState) {
        val (migrationUiState, dataRestoreUiState, dataErrorState) = migrationRestoreState

        if (dataRestoreUiState == DataRestoreUiState.IN_PROGRESS) {
            showDataRestoreInProgressDialog(requireContext()) { _, _ -> requireActivity().finish() }
        } else if (migrationUiState == MigrationUiState.IN_PROGRESS) {
            showMigrationInProgressDialog(
                requireContext(),
                getString(R.string.migration_in_progress_permissions_dialog_content_apps),
            ) { _, _ ->
                requireActivity().finish()
            }
        } else if (migrationUiState == MigrationUiState.COMPLETE) {
            maybeShowWhatsNewDialog(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadConnectedApps()
    }

    private fun updateAllowedApps(appsList: List<ConnectedAppMetadata>) {
        allowedAppsGroup?.removeAll()
        if (appsList.isEmpty()) {
            allowedAppsGroup?.addPreference(getNoAppsPreference(R.string.no_apps_allowed))
        } else {
            appsList.forEach { app -> allowedAppsGroup?.addPreference(getAppPreference(app)) }
        }
    }

    private fun updateDeniedApps(appsList: List<ConnectedAppMetadata>) {
        deniedAppsGroup?.removeAll()

        if (appsList.isEmpty()) {
            deniedAppsGroup?.addPreference(getNoAppsPreference(R.string.no_apps_denied))
        } else {
            appsList.forEach { app -> deniedAppsGroup?.addPreference(getAppPreference(app)) }
        }
    }

    private fun getNoAppsPreference(@StringRes res: Int): Preference {
        return Preference(requireContext()).also {
            it.setTitle(res)
            it.isSelectable = false
        }
    }

    private fun getAppPreference(app: ConnectedAppMetadata): AppPreference {
        return HealthAppPreference(requireContext(), app.appMetadata).also {
            if (app.status == ALLOWED) {
                it.logName = AppPermissionsElement.CONNECTED_APP_BUTTON
            } else if (app.status == DENIED) {
                it.logName = AppPermissionsElement.NOT_CONNECTED_APP_BUTTON
            }
            if (app.healthUsageLastAccess != null) {
                it.setSummary(R.string.app_perms_content_provider_24h)
            } else {
                it.summary = null
            }
            it.setOnPreferenceClickListener {
                findNavController()
                    .navigate(
                        R.id.action_settingsManagePermission_to_settingsManageAppPermissions,
                        bundleOf(
                            EXTRA_PACKAGE_NAME to app.appMetadata.packageName,
                            EXTRA_APP_NAME to app.appMetadata.appName))
                true
            }
        }
    }
}
