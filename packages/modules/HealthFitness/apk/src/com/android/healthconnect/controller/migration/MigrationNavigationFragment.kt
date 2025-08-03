package com.android.healthconnect.controller.migration

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.shared.Constants.USER_ACTIVITY_TRACKER
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.NavigationUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(HealthPreferenceFragment::class)
class MigrationNavigationFragment : Hilt_MigrationNavigationFragment() {

    @Inject lateinit var navigationUtils: NavigationUtils

    private val migrationViewModel: MigrationViewModel by viewModels()
    private lateinit var sharedPreference: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.empty_preference_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreference =
            requireActivity().getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)

        migrationViewModel.migrationState.observe(viewLifecycleOwner) { migrationState ->
            when (migrationState) {
                is MigrationViewModel.MigrationFragmentState.Loading -> {
                    setLoading(true)
                }
                is MigrationViewModel.MigrationFragmentState.WithData -> {
                    setLoading(false)
                    updateFragment(migrationState.migrationRestoreState)
                }
                is MigrationViewModel.MigrationFragmentState.Error -> {
                    setError(true)
                }
            }
        }
    }

    private fun updateFragment(migrationRestoreState: MigrationRestoreState) {
        val (migrationUiState, dataRestoreUiState, dataErrorState) = migrationRestoreState

        if (dataRestoreUiState == DataRestoreUiState.IN_PROGRESS) {
            showDataRestoreInProgressFragment()
        } else if (migrationUiState in
            listOf(MigrationUiState.ALLOWED_NOT_STARTED, MigrationUiState.ALLOWED_PAUSED)) {
            showMigrationPausedFragment()
        } else if (migrationUiState == MigrationUiState.APP_UPGRADE_REQUIRED) {
            showAppUpdateRequiredFragment()
        } else if (migrationUiState == MigrationUiState.MODULE_UPGRADE_REQUIRED) {
            showModuleUpdateRequiredFragment()
        } else if (migrationUiState == MigrationUiState.IN_PROGRESS) {
            showMigrationInProgressFragment()
        } else {
            navigateToHomeFragment()
        }
    }

    private fun showDataRestoreInProgressFragment() {
        navigationUtils.navigate(
            this, R.id.action_migrationNavigationFragment_to_dataRestoreInProgressFragment)
    }

    private fun showMigrationInProgressFragment() {
        navigationUtils.navigate(
            this, R.id.action_migrationNavigationFragment_to_migrationInProgressFragment)
    }

    private fun showAppUpdateRequiredFragment() {
        navigationUtils.navigate(
            this, R.id.action_migrationNavigationFragment_to_migrationAppUpdateNeededFragment)
    }

    private fun showModuleUpdateRequiredFragment() {
        navigationUtils.navigate(
            this, R.id.action_migrationNavigationFragment_to_migrationModuleUpdateNeededFragment)
    }

    private fun showMigrationPausedFragment() {
        navigationUtils.navigate(
            this, R.id.action_migrationNavigationFragment_to_migrationPausedFragment)
    }

    private fun navigateToHomeFragment() {
        navigationUtils.navigate(this, R.id.action_migrationNavigationFragment_to_homeFragment)
    }
}
