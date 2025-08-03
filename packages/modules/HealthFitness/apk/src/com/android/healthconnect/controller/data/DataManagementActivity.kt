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

package com.android.healthconnect.controller.data

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.maybeRedirectToMigrationActivity
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.maybeShowWhatsNewDialog
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel.MigrationFragmentState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.navigation.DestinationChangedListener
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Entry point activity for Health Connect Data Management controllers. */
@AndroidEntryPoint(CollapsingToolbarBaseActivity::class)
class DataManagementActivity : Hilt_DataManagementActivity() {

    @Inject lateinit var featureUtils: FeatureUtils

    private val migrationViewModel: MigrationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This flag ensures a non system app cannot show an overlay on Health Connect. b/313425281
        window.addSystemFlags(WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)

        setContentView(R.layout.activity_data_management)
        if (savedInstanceState == null && featureUtils.isNewInformationArchitectureEnabled()) {
            updateNavGraphToNewIA()
        }

        val currentMigrationState = migrationViewModel.getCurrentMigrationUiState()
        if (maybeRedirectToMigrationActivity(this, currentMigrationState)) {
            return
        }

        migrationViewModel.migrationState.observe(this) { migrationState ->
            when (migrationState) {
                is MigrationFragmentState.WithData -> {
                    if (migrationState.migrationRestoreState.migrationUiState ==
                        MigrationUiState.COMPLETE) {
                        maybeShowWhatsNewDialog(this)
                    }
                }
                else -> {
                    // do nothing
                }
            }
        }
    }

    private fun updateNavGraphToNewIA() {
        val navRes = R.navigation.data_nav_graph_new_ia
        val finalHost = NavHostFragment.create(navRes)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.nav_host_fragment, finalHost)
            .setPrimaryNavigationFragment(finalHost)
            .commit()
    }

    override fun onStart() {
        super.onStart()
        findNavController(R.id.nav_host_fragment)
            .addOnDestinationChangedListener(DestinationChangedListener(this))
    }

    override fun onResume() {
        super.onResume()
        val currentMigrationState = migrationViewModel.getCurrentMigrationUiState()

        if (maybeRedirectToMigrationActivity(this, currentMigrationState)) {
            return
        }
    }

    override fun onBackPressed() {
        val navController = findNavController(R.id.nav_host_fragment)
        if (!navController.popBackStack()) {
            finish()
        }
    }

    override fun onNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        if (!navController.popBackStack()) {
            finish()
        }
        return true
    }
}
