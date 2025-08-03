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
package com.android.healthconnect.controller.permissions.shared

import android.content.Intent.EXTRA_PACKAGE_NAME
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.navigation.DestinationChangedListener
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint(CollapsingToolbarBaseActivity::class)
class SettingsActivity : Hilt_SettingsActivity() {

    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    private val viewModel: AppPermissionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This flag ensures a non system app cannot show an overlay on Health Connect. b/313425281
        window.addSystemFlags(WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)
        setContentView(R.layout.activity_settings)
        setTitle(R.string.permgrouplab_health)
        if (savedInstanceState == null && intent.hasExtra(EXTRA_PACKAGE_NAME)) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)!!
                    maybeNavigateToAppPermissions(
                        viewModel.shouldNavigateToAppPermissionsFragment(packageName))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        findNavController(R.id.nav_host_fragment)
            .addOnDestinationChangedListener(DestinationChangedListener(this))
    }

    private fun maybeNavigateToAppPermissions(shouldNavigate: Boolean) {
        val navController = findNavController(R.id.nav_host_fragment)

        if (shouldNavigate) {
            navController.navigate(
                R.id.action_deeplink_to_settingsManageAppPermissionsFragment,
                bundleOf(EXTRA_PACKAGE_NAME to intent.getStringExtra(EXTRA_PACKAGE_NAME)))
        } else {
            finish()
        }
    }

    override fun onBackPressed() {
        val navController = findNavController(R.id.nav_host_fragment)
        if (!navController.popBackStack()) {
            finish()
        }
    }

    override fun onNavigateUp(): Boolean {
        val navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        if (!navController.popBackStack()) {
            finish()
        }
        return true
    }
}
