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

package com.android.healthconnect.controller.permissions.request

import android.app.Activity.*
import android.content.Intent
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.content.pm.PackageManager
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.maybeShowWhatsNewDialog
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.showDataRestoreInProgressDialog
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.showMigrationInProgressDialog
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.showMigrationPendingDialog
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.onboarding.OnboardingActivity
import com.android.healthconnect.controller.onboarding.OnboardingActivity.Companion.shouldRedirectToOnboardingActivity
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.PermissionState
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.activity.EmbeddingUtils.maybeRedirectIntoTwoPaneSettings
import com.android.healthconnect.controller.utils.increaseViewTouchTargetSize
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PermissionsElement
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Permissions activity for Health Connect. */
@AndroidEntryPoint(FragmentActivity::class)
class
PermissionsActivity : Hilt_PermissionsActivity() {

    companion object {
        private const val TAG = "PermissionsActivity"
    }

    @Inject lateinit var logger: HealthConnectLogger

    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    @Inject lateinit var deviceInfoUtils: DeviceInfoUtils

    private val requestPermissionsViewModel: RequestPermissionViewModel by viewModels()

    private val migrationViewModel: MigrationViewModel by viewModels()

    private val openOnboardingActivity =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_CANCELED) {
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This flag ensures a non system app cannot show an overlay on Health Connect. b/313425281
        window.addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)

        // Handles unsupported devices and user profiles.
        if (!deviceInfoUtils.isHealthConnectAvailable(this)) {
            Log.e(TAG, "Health connect is not available for this user or hardware, finishing!")
            finish()
            return
        }

        if (!intent.hasExtra(EXTRA_PACKAGE_NAME)) {
            Log.e(TAG, "Invalid Intent Extras, finishing")
            finish()
            return
        }

        if (maybeRedirectIntoTwoPaneSettings(this)) {
            return
        }

        setContentView(R.layout.activity_permissions)

        if (savedInstanceState == null && shouldRedirectToOnboardingActivity(this)) {
            openOnboardingActivity.launch(OnboardingActivity.createIntent(this))
        }

        val rationalIntentDeclared =
            healthPermissionReader.isRationalIntentDeclared(getPackageNameExtra())
        if (!rationalIntentDeclared) {
            Log.e(TAG, "App should support rational intent, finishing!")
            finish()
        }

        requestPermissionsViewModel.init(getPackageNameExtra(), getPermissionStrings())
        requestPermissionsViewModel.permissionsList.observe(this) { notGrantedPermissions ->
            if (notGrantedPermissions.isEmpty()) {
                handleResults(requestPermissionsViewModel.request(getPackageNameExtra()))
            }
        }
        migrationViewModel.migrationState.observe(this) { migrationState ->
            when (migrationState) {
                is MigrationViewModel.MigrationFragmentState.WithData -> {
                    maybeShowMigrationDialog(migrationState.migrationRestoreState)
                }
                else -> {
                    // do nothing
                }
            }
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.permission_content, PermissionsFragment())
            .commit()
    }

    override fun onResume() {
        super.onResume()
        setupAllowButton()
        setupCancelButton()
    }

    private fun setupCancelButton() {
        val cancelButton: View = findViewById(R.id.cancel)
        logger.logImpression(PermissionsElement.CANCEL_PERMISSIONS_BUTTON)

        val parentView = cancelButton.parent as View
        increaseViewTouchTargetSize(this, cancelButton, parentView)

        cancelButton.setOnClickListener {
            logger.logInteraction(PermissionsElement.CANCEL_PERMISSIONS_BUTTON)
            requestPermissionsViewModel.updatePermissions(false)
            handleResults(requestPermissionsViewModel.request(getPackageNameExtra()))
        }
    }

    private fun setupAllowButton() {
        val allowButton: View = findViewById(R.id.allow)
        logger.logImpression(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)

        val parentView = allowButton.parent.parent as View
        increaseViewTouchTargetSize(this, allowButton, parentView)

        requestPermissionsViewModel.grantedPermissions.observe(this) { grantedPermissions ->
            allowButton.isEnabled = grantedPermissions.isNotEmpty()
        }
        allowButton.setOnClickListener {
            logger.logInteraction(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)
            handleResults(requestPermissionsViewModel.request(getPackageNameExtra()))
        }
    }

    private fun maybeShowMigrationDialog(migrationRestoreState: MigrationRestoreState) {
        val (migrationUiState, dataRestoreUiState, dataErrorState) = migrationRestoreState

        if (dataRestoreUiState == DataRestoreUiState.IN_PROGRESS) {
            showDataRestoreInProgressDialog(this) { _, _ -> finish() }
        } else if (migrationUiState == MigrationUiState.IN_PROGRESS) {
            showMigrationInProgressDialog(
                this,
                getString(
                    R.string.migration_in_progress_permissions_dialog_content,
                    requestPermissionsViewModel.appMetadata.value?.appName)) { _, _ ->
                    finish()
                }
        } else if (migrationUiState in
            listOf(
                MigrationUiState.ALLOWED_PAUSED,
                MigrationUiState.ALLOWED_NOT_STARTED,
                MigrationUiState.MODULE_UPGRADE_REQUIRED,
                MigrationUiState.APP_UPGRADE_REQUIRED)) {
            showMigrationPendingDialog(
                this,
                getString(
                    R.string.migration_pending_permissions_dialog_content,
                    requestPermissionsViewModel.appMetadata.value?.appName),
                null,
            ) { _, _ ->
                requestPermissionsViewModel.updatePermissions(false)
                handleResults(requestPermissionsViewModel.request(getPackageNameExtra()))
                finish()
            }
        } else if (migrationUiState == MigrationUiState.COMPLETE) {
            maybeShowWhatsNewDialog(this)
        }
    }

    fun handleResults(results: Map<HealthPermission, PermissionState>) {
        val grants =
            results.values
                .map { permissionSelection ->
                    if (PermissionState.GRANTED == permissionSelection) {
                        PackageManager.PERMISSION_GRANTED
                    } else {
                        PackageManager.PERMISSION_DENIED
                    }
                }
                .toIntArray()
        val result = Intent()
        result.putExtra(EXTRA_REQUEST_PERMISSIONS_NAMES, getPermissionStrings())
        result.putExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS, grants)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun getPermissionStrings(): Array<out String> {
        return intent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES).orEmpty()
    }

    private fun getPackageNameExtra(): String {
        return intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
    }
}
