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
package com.android.healthconnect.controller.migration

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.navigation.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.shared.Constants.APP_UPDATE_NEEDED_SEEN
import com.android.healthconnect.controller.shared.Constants.INTEGRATION_PAUSED_SEEN_KEY
import com.android.healthconnect.controller.shared.Constants.MODULE_UPDATE_NEEDED_SEEN
import com.android.healthconnect.controller.shared.Constants.USER_ACTIVITY_TRACKER
import com.android.healthconnect.controller.shared.Constants.WHATS_NEW_DIALOG_SEEN
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.utils.logging.DataRestoreElement
import com.android.healthconnect.controller.utils.logging.MigrationElement
import dagger.hilt.android.AndroidEntryPoint

/** Activity in charge of coordinating migration navigation, fragments and dialogs. */
@AndroidEntryPoint(FragmentActivity::class)
class MigrationActivity : Hilt_MigrationActivity() {

    companion object {
        const val MIGRATION_ACTIVITY_INTENT = "android.health.connect.action.MIGRATION"

        fun maybeRedirectToMigrationActivity(
            activity: Activity,
            migrationRestoreState: MigrationRestoreState
        ): Boolean {

            val sharedPreference =
                activity.getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)

            if (migrationRestoreState.migrationUiState ==
                MigrationUiState.MODULE_UPGRADE_REQUIRED) {
                val moduleUpdateSeen = sharedPreference.getBoolean(MODULE_UPDATE_NEEDED_SEEN, false)

                if (!moduleUpdateSeen) {
                    activity.startActivity(createMigrationActivityIntent(activity))
                    activity.finish()
                    return true
                }
            } else if (migrationRestoreState.migrationUiState ==
                MigrationUiState.APP_UPGRADE_REQUIRED) {
                val appUpdateSeen = sharedPreference.getBoolean(APP_UPDATE_NEEDED_SEEN, false)

                if (!appUpdateSeen) {
                    activity.startActivity(createMigrationActivityIntent(activity))
                    activity.finish()
                    return true
                }
            } else if (migrationRestoreState.migrationUiState == MigrationUiState.ALLOWED_PAUSED ||
                migrationRestoreState.migrationUiState == MigrationUiState.ALLOWED_NOT_STARTED) {
                val allowedPausedSeen =
                    sharedPreference.getBoolean(INTEGRATION_PAUSED_SEEN_KEY, false)

                if (!allowedPausedSeen) {
                    activity.startActivity(createMigrationActivityIntent(activity))
                    activity.finish()
                    return true
                }
            } else if (migrationRestoreState.migrationUiState == MigrationUiState.IN_PROGRESS ||
                migrationRestoreState.dataRestoreState == DataRestoreUiState.IN_PROGRESS) {
                activity.startActivity(createMigrationActivityIntent(activity))
                activity.finish()
                return true
            }

            return false
        }

        fun maybeShowMigrationDialog(
            migrationRestoreState: MigrationRestoreState,
            activity: FragmentActivity,
            appName: String
        ) {
            val (migrationUiState, dataRestoreUiState, dataErrorState) = migrationRestoreState

            when {
                dataRestoreUiState == DataRestoreUiState.IN_PROGRESS -> {
                    showDataRestoreInProgressDialog(activity) { _, _ -> activity.finish() }
                }
                migrationUiState == MigrationUiState.IN_PROGRESS -> {
                    val message =
                        activity.getString(
                            R.string.migration_in_progress_permissions_dialog_content, appName)
                    showMigrationInProgressDialog(activity, message) { _, _ -> activity.finish() }
                }
                migrationUiState in
                    listOf(
                        MigrationUiState.ALLOWED_PAUSED,
                        MigrationUiState.ALLOWED_NOT_STARTED,
                        MigrationUiState.APP_UPGRADE_REQUIRED,
                        MigrationUiState.MODULE_UPGRADE_REQUIRED) -> {
                    val message =
                        activity.getString(
                            R.string.migration_pending_permissions_dialog_content, appName)
                    showMigrationPendingDialog(
                        activity,
                        message,
                        positiveButtonAction = null,
                        negativeButtonAction = { _, _ ->
                            activity.startActivity(Intent(MIGRATION_ACTIVITY_INTENT))
                            activity.finish()
                        })
                }
                migrationUiState == MigrationUiState.COMPLETE -> {
                    maybeShowWhatsNewDialog(activity)
                }
                else -> {
                    // show nothing
                }
            }
        }

        fun showMigrationPendingDialog(
            context: Context,
            message: String,
            positiveButtonAction: DialogInterface.OnClickListener? = null,
            negativeButtonAction: DialogInterface.OnClickListener? = null
        ) {
            AlertDialogBuilder(context, MigrationElement.MIGRATION_PENDING_DIALOG_CONTAINER)
                .setTitle(R.string.migration_pending_permissions_dialog_title)
                .setMessage(message)
                .setCancelable(false)
                .setNeutralButton(
                    R.string.migration_pending_permissions_dialog_button_start_integration,
                    MigrationElement.MIGRATION_PENDING_DIALOG_CANCEL_BUTTON,
                    negativeButtonAction)
                .setPositiveButton(
                    R.string.migration_pending_permissions_dialog_button_continue,
                    MigrationElement.MIGRATION_PENDING_DIALOG_CONTINUE_BUTTON,
                    positiveButtonAction)
                .create()
                .show()
        }

        fun showMigrationInProgressDialog(
            context: Context,
            message: String,
            negativeButtonAction: DialogInterface.OnClickListener? = null
        ) {
            AlertDialogBuilder(context, MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_CONTAINER)
                .setTitle(R.string.migration_in_progress_permissions_dialog_title)
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton(
                    R.string.migration_in_progress_permissions_dialog_button_got_it,
                    MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_BUTTON,
                    negativeButtonAction)
                .create()
                .show()
        }

        fun showDataRestoreInProgressDialog(
            context: Context,
            negativeButtonAction: DialogInterface.OnClickListener? = null
        ) {
            AlertDialogBuilder(context, DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_CONTAINER)
                .setTitle(R.string.data_restore_in_progress_dialog_title)
                .setMessage(R.string.data_restore_in_progress_content)
                .setCancelable(false)
                .setNegativeButton(
                    R.string.data_restore_in_progress_dialog_button,
                    DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_BUTTON,
                    negativeButtonAction)
                .create()
                .show()
        }

        fun maybeShowWhatsNewDialog(
            context: Context,
            negativeButtonAction: DialogInterface.OnClickListener? = null
        ) {
            val sharedPreference =
                context.getSharedPreferences(USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
            val dialogSeen = sharedPreference.getBoolean(WHATS_NEW_DIALOG_SEEN, false)

            if (!dialogSeen) {
                AlertDialogBuilder(context, MigrationElement.MIGRATION_DONE_DIALOG_CONTAINER)
                    .setTitle(R.string.migration_whats_new_dialog_title)
                    .setMessage(R.string.migration_whats_new_dialog_content)
                    .setCancelable(false)
                    .setNegativeButton(
                        R.string.migration_whats_new_dialog_button,
                        MigrationElement.MIGRATION_DONE_DIALOG_BUTTON) {
                            unusedDialogInterface,
                            unusedInt ->
                            sharedPreference.edit().apply {
                                putBoolean(WHATS_NEW_DIALOG_SEEN, true)
                                apply()
                            }
                            negativeButtonAction?.onClick(unusedDialogInterface, unusedInt)
                        }
                    .create()
                    .show()
            }
        }

        private fun createMigrationActivityIntent(context: Context): Intent {
            return Intent(context, MigrationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This flag ensures a non system app cannot show an overlay on Health Connect. b/313425281
        window.addSystemFlags(WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)
        setContentView(R.layout.activity_migration)
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
