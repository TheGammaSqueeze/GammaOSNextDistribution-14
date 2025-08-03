/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.android.healthconnect.controller.permissions.additionalaccess

import android.app.Dialog
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.health.connect.HealthPermissions.READ_EXERCISE
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.shared.Constants.EXTRA_APP_NAME
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement.DISABLE_EXERCISE_PERMISSION_DIALOG_CONTAINER
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement.DISABLE_EXERCISE_PERMISSION_DIALOG_NEGATIVE_BUTTON
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement.DISABLE_EXERCISE_PERMISSION_DIALOG_POSITIVE_BUTTON
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Dialog to ask user to disable [READ_EXERCISE_ROUTES] permission when disabling [READ_EXERCISE]
 */
@AndroidEntryPoint(DialogFragment::class)
class DisableExerciseRoutePermissionDialog : Hilt_DisableExerciseRoutePermissionDialog() {

    @Inject lateinit var logger: HealthConnectLogger

    private lateinit var packageName: String
    private lateinit var appName: String

    private val viewModel: AppPermissionViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val packageNameExtra = requireArguments().getString(EXTRA_PACKAGE_NAME)
        if (packageNameExtra.isNullOrEmpty()) {
            Log.e(TAG, "Invalid Intent Extras, finishing.")
            return super.onCreateDialog(savedInstanceState)
        }
        packageName = packageNameExtra

        val appNameExtra = requireArguments().getString(EXTRA_APP_NAME)
        if (appNameExtra == null) {
            Log.e(TAG, "Invalid Intent Extras, finishing.")
            return super.onCreateDialog(savedInstanceState)
        }
        appName = appNameExtra

        return AlertDialogBuilder(this, DISABLE_EXERCISE_PERMISSION_DIALOG_CONTAINER)
            .setTitle(R.string.exercise_permission_dialog_disable_title)
            .setMessage(getString(R.string.exercise_permission_dialog_disable_summary, appName))
            .setPositiveButton(
                R.string.exercise_permission_dialog_positive_button,
                DISABLE_EXERCISE_PERMISSION_DIALOG_POSITIVE_BUTTON) { _, _ ->
                    viewModel.disableExerciseRoutePermission(packageName)
                    dismiss()
                }
            .setNegativeButton(
                R.string.exercise_permission_dialog_negative_button,
                DISABLE_EXERCISE_PERMISSION_DIALOG_NEGATIVE_BUTTON) { _, _ ->
                    viewModel.updatePermission(
                        packageName, fromPermissionString(READ_EXERCISE), grant = true)
                    dismiss()
                }
            .setCancelable(false)
            .create()
    }

    override fun dismiss() {
        viewModel.hideExerciseRoutePermissionDialog()
        super.dismiss()
    }

    companion object {
        private const val TAG = "DisableExercisePermDialog"

        fun createDialog(
            packageName: String,
            appName: String
        ): DisableExerciseRoutePermissionDialog {
            return DisableExerciseRoutePermissionDialog().apply {
                arguments = bundleOf(EXTRA_PACKAGE_NAME to packageName, EXTRA_APP_NAME to appName)
            }
        }
    }
}
