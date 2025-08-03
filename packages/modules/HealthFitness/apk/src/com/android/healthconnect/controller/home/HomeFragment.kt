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
package com.android.healthconnect.controller.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.HealthFitnessUiStatsLog.*
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.maybeShowWhatsNewDialog
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.recentaccess.RecentAccessEntry
import com.android.healthconnect.controller.recentaccess.RecentAccessPreference
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel.RecentAccessState
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.shared.Constants.MIGRATION_NOT_COMPLETE_DIALOG_SEEN
import com.android.healthconnect.controller.shared.Constants.USER_ACTIVITY_TRACKER
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.shared.preference.BannerPreference
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.NavigationUtils
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.logging.DataRestoreElement
import com.android.healthconnect.controller.utils.logging.HomePageElement
import com.android.healthconnect.controller.utils.logging.MigrationElement
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Home fragment for Health Connect. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class HomeFragment : Hilt_HomeFragment() {

    companion object {
        private const val DATA_AND_ACCESS_PREFERENCE_KEY = "data_and_access"
        private const val RECENT_ACCESS_PREFERENCE_KEY = "recent_access"
        private const val CONNECTED_APPS_PREFERENCE_KEY = "connected_apps"
        private const val MIGRATION_BANNER_PREFERENCE_KEY = "migration_banner"
        private const val DATA_RESTORE_BANNER_PREFERENCE_KEY = "data_restore_banner"
        private const val MANAGE_DATA_PREFERENCE_KEY = "manage_data"

        @JvmStatic fun newInstance() = HomeFragment()
    }

    init {
        this.setPageName(PageName.HOME_PAGE)
    }

    @Inject lateinit var featureUtils: FeatureUtils
    @Inject lateinit var timeSource: TimeSource
    @Inject lateinit var navigationUtils: NavigationUtils

    private val recentAccessViewModel: RecentAccessViewModel by viewModels()
    private val homeFragmentViewModel: HomeFragmentViewModel by viewModels()
    private val migrationViewModel: MigrationViewModel by activityViewModels()

    private val mDataAndAccessPreference: HealthPreference? by lazy {
        preferenceScreen.findPreference(DATA_AND_ACCESS_PREFERENCE_KEY)
    }

    private val mRecentAccessPreference: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(RECENT_ACCESS_PREFERENCE_KEY)
    }

    private val mConnectedAppsPreference: HealthPreference? by lazy {
        preferenceScreen.findPreference(CONNECTED_APPS_PREFERENCE_KEY)
    }

    private val mManageDataPreference: HealthPreference? by lazy {
        preferenceScreen.findPreference(MANAGE_DATA_PREFERENCE_KEY)
    }

    private lateinit var migrationBannerSummary: String
    private var migrationBanner: BannerPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.home_preference_screen, rootKey)
        mDataAndAccessPreference?.logName = HomePageElement.DATA_AND_ACCESS_BUTTON
        mDataAndAccessPreference?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_healthDataCategoriesFragment)
            true
        }
        mConnectedAppsPreference?.logName = HomePageElement.APP_PERMISSIONS_BUTTON
        mConnectedAppsPreference?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_connectedAppsFragment)
            true
        }

        if (featureUtils.isNewAppPriorityEnabled() ||
            featureUtils.isNewInformationArchitectureEnabled()) {
            mManageDataPreference?.logName = HomePageElement.MANAGE_DATA_BUTTON
            mManageDataPreference?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_manageDataFragment)
                true
            }
        } else {
            preferenceScreen.removePreferenceRecursively(MANAGE_DATA_PREFERENCE_KEY)
        }

        migrationBannerSummary = getString(R.string.resume_migration_banner_description_fallback)
        migrationBanner = getMigrationBanner()
    }

    override fun onResume() {
        super.onResume()
        recentAccessViewModel.loadRecentAccessApps(maxNumEntries = 3)
        homeFragmentViewModel.loadConnectedApps()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recentAccessViewModel.loadRecentAccessApps(maxNumEntries = 3)
        recentAccessViewModel.recentAccessApps.observe(viewLifecycleOwner) { recentAppsState ->
            when (recentAppsState) {
                is RecentAccessState.WithData -> {
                    updateRecentApps(recentAppsState.recentAccessEntries)
                }
                else -> {
                    updateRecentApps(emptyList())
                }
            }
        }
        homeFragmentViewModel.connectedApps.observe(viewLifecycleOwner) { connectedApps ->
            updateConnectedApps(connectedApps)
        }
        migrationViewModel.migrationState.observe(viewLifecycleOwner) { migrationState ->
            when (migrationState) {
                is MigrationViewModel.MigrationFragmentState.WithData -> {
                    showMigrationState(migrationState.migrationRestoreState)
                }
                else -> {
                    // do nothing
                }
            }
        }
    }

    private fun showMigrationState(migrationRestoreState: MigrationRestoreState) {
        preferenceScreen.removePreferenceRecursively(MIGRATION_BANNER_PREFERENCE_KEY)
        preferenceScreen.removePreferenceRecursively(DATA_RESTORE_BANNER_PREFERENCE_KEY)

        val (migrationUiState, dataRestoreUiState, dataErrorState) = migrationRestoreState

        if (dataRestoreUiState == DataRestoreUiState.PENDING) {
            // TODO (b/327170886) uncomment when states are correct
            // preferenceScreen.addPreference(getDataRestorePendingBanner())
        } else if (migrationUiState in
            listOf(
                MigrationUiState.ALLOWED_PAUSED,
                MigrationUiState.ALLOWED_NOT_STARTED,
                MigrationUiState.MODULE_UPGRADE_REQUIRED,
                MigrationUiState.APP_UPGRADE_REQUIRED)) {
            migrationBanner = getMigrationBanner()
            preferenceScreen.addPreference(migrationBanner as BannerPreference)
        } else if (migrationUiState == MigrationUiState.COMPLETE) {
            maybeShowWhatsNewDialog(requireContext())
        } else if (migrationUiState == MigrationUiState.ALLOWED_ERROR) {
            maybeShowMigrationNotCompleteDialog()
        }
    }

    private fun maybeShowMigrationNotCompleteDialog() {
        val sharedPreference =
            requireActivity().getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val dialogSeen = sharedPreference.getBoolean(MIGRATION_NOT_COMPLETE_DIALOG_SEEN, false)

        if (!dialogSeen) {
            AlertDialogBuilder(this, MigrationElement.MIGRATION_NOT_COMPLETE_DIALOG_CONTAINER)
                .setTitle(R.string.migration_not_complete_dialog_title)
                .setMessage(R.string.migration_not_complete_dialog_content)
                .setCancelable(false)
                .setNegativeButton(
                    R.string.migration_whats_new_dialog_button,
                    MigrationElement.MIGRATION_NOT_COMPLETE_DIALOG_BUTTON) { _, _ ->
                        sharedPreference.edit().apply {
                            putBoolean(MIGRATION_NOT_COMPLETE_DIALOG_SEEN, true)
                            apply()
                        }
                    }
                .create()
                .show()
        }
    }

    private fun getMigrationBanner(): BannerPreference {
        return BannerPreference(requireContext(), MigrationElement.MIGRATION_RESUME_BANNER).also {
            it.setPrimaryButton(
                resources.getString(R.string.resume_migration_banner_button),
                MigrationElement.MIGRATION_RESUME_BANNER_BUTTON)
            it.title = resources.getString(R.string.resume_migration_banner_title)
            it.key = MIGRATION_BANNER_PREFERENCE_KEY
            it.summary = migrationBannerSummary
            it.icon =
                AttributeResolver.getNullableDrawable(requireContext(), R.attr.settingsAlertIcon)
            it.setPrimaryButtonOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_migrationActivity)
            }
            it.order = 1
        }
    }

    private fun getDataRestorePendingBanner(): BannerPreference {
        return BannerPreference(requireContext(), DataRestoreElement.RESTORE_PENDING_BANNER).also {
            it.setPrimaryButton(
                resources.getString(R.string.data_restore_pending_banner_button),
                DataRestoreElement.RESTORE_PENDING_BANNER_UPDATE_BUTTON)
            it.title = resources.getString(R.string.data_restore_pending_banner_title)
            it.key = DATA_RESTORE_BANNER_PREFERENCE_KEY
            it.summary = resources.getString(R.string.data_restore_pending_banner_content)
            it.icon =
                AttributeResolver.getNullableDrawable(requireContext(), R.attr.updateNeededIcon)
            it.setPrimaryButtonOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_systemUpdateActivity)
            }
            it.order = 1
        }
    }

    private fun updateConnectedApps(connectedApps: List<ConnectedAppMetadata>) {
        val connectedAppsGroup = connectedApps.groupBy { it.status }
        val numAllowedApps = connectedAppsGroup[ConnectedAppStatus.ALLOWED].orEmpty().size
        val numNotAllowedApps = connectedAppsGroup[ConnectedAppStatus.DENIED].orEmpty().size
        val numTotalApps = numAllowedApps + numNotAllowedApps

        if (numTotalApps == 0) {
            mConnectedAppsPreference?.summary =
                getString(R.string.connected_apps_button_no_permissions_subtitle)
        } else if (numAllowedApps == 1 && numAllowedApps == numTotalApps) {
            mConnectedAppsPreference?.summary =
                getString(
                    R.string.connected_apps_one_app_connected_subtitle, numAllowedApps.toString())
        } else if (numAllowedApps == numTotalApps) {
            mConnectedAppsPreference?.summary =
                getString(
                    R.string.connected_apps_all_apps_connected_subtitle, numAllowedApps.toString())
        } else {
            mConnectedAppsPreference?.summary =
                getString(
                    R.string.connected_apps_button_subtitle,
                    numAllowedApps.toString(),
                    numTotalApps.toString())
        }
    }

    private fun updateRecentApps(recentAppsList: List<RecentAccessEntry>) {
        mRecentAccessPreference?.removeAll()

        if (recentAppsList.isEmpty()) {
            mRecentAccessPreference?.addPreference(
                Preference(requireContext())
                    .also { it.setSummary(R.string.no_recent_access) }
                    .also { it.isSelectable = false })
        } else {
            recentAppsList.forEach { recentApp ->
                val newRecentAccessPreference =
                    RecentAccessPreference(requireContext(), recentApp, timeSource, false).also {
                        newPreference ->
                        if (!recentApp.isInactive) {
                            newPreference.setOnPreferenceClickListener {
                                findNavController()
                                    .navigate(
                                        R.id.action_homeFragment_to_connectedAppFragment,
                                        bundleOf(
                                            Intent.EXTRA_PACKAGE_NAME to
                                                recentApp.metadata.packageName,
                                            Constants.EXTRA_APP_NAME to recentApp.metadata.appName))
                                true
                            }
                        }
                    }
                mRecentAccessPreference?.addPreference(newRecentAccessPreference)
            }
            val seeAllPreference =
                HealthPreference(requireContext()).also {
                    it.setTitle(R.string.show_recent_access_entries_button_title)
                    it.setIcon(AttributeResolver.getResource(requireContext(), R.attr.seeAllIcon))
                    it.logName = HomePageElement.SEE_ALL_RECENT_ACCESS_BUTTON
                }
            seeAllPreference.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_recentAccessFragment)
                true
            }
            mRecentAccessPreference?.addPreference(seeAllPreference)
        }
    }
}
