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
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import androidx.annotation.IdRes
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ALWAYS_ALLOW
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ASK_EVERY_TIME
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.NEVER_ALLOW
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement.EXERCISE_ROUTES_ALLOW_ALL_BUTTON
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement.EXERCISE_ROUTES_ASK_BUTTON
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement.EXERCISE_ROUTES_DIALOG_CONTAINER
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement.EXERCISE_ROUTES_DIALOG_DENY_BUTTON
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Dialog fragment to allow the user to change Exercise routes permission state. */
@AndroidEntryPoint(DialogFragment::class)
class ExerciseRoutesPermissionDialogFragment : Hilt_ExerciseRoutesPermissionDialogFragment() {

    @Inject lateinit var logger: HealthConnectLogger

    private lateinit var permissionRadioGroup: RadioGroup
    private lateinit var appIcon: ImageView
    private lateinit var packageName: String

    private val viewModel: AdditionalAccessViewModel by activityViewModels()
    private val permissionsViewModel: AppPermissionViewModel by activityViewModels()
    private val permissionChangedListener =
        RadioGroup.OnCheckedChangeListener { _, selectedId ->
            when (selectedId) {
                R.id.radio_button_always_allow -> {
                    viewModel.updateExerciseRouteState(packageName, ALWAYS_ALLOW)
                    logger.logInteraction(EXERCISE_ROUTES_ALLOW_ALL_BUTTON)
                }
                R.id.radio_button_ask -> {
                    viewModel.updateExerciseRouteState(packageName, ASK_EVERY_TIME)
                    logger.logInteraction(EXERCISE_ROUTES_ASK_BUTTON)
                }
                R.id.radio_button_revoke -> {
                    viewModel.updateExerciseRouteState(packageName, NEVER_ALLOW)
                    logger.logInteraction(EXERCISE_ROUTES_DIALOG_DENY_BUTTON)
                }
            }
            this.dismiss()
        }
    private val extraLogger = {
        logger.logImpression(EXERCISE_ROUTES_ALLOW_ALL_BUTTON)
        logger.logImpression(EXERCISE_ROUTES_ASK_BUTTON)
        logger.logImpression(EXERCISE_ROUTES_DIALOG_DENY_BUTTON)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_exercise_routes_permission, null)

        val packageNameExtra = requireArguments().getString(EXTRA_PACKAGE_NAME)
        if (packageNameExtra.isNullOrEmpty()) {
            Log.e(TAG, "Invalid Intent Extras, finishing.")
            return super.onCreateDialog(savedInstanceState)
        }
        packageName = packageNameExtra

        setupAppIcon(view)
        setupPermissionRadioGroup(view)

        return AlertDialogBuilder(this, EXERCISE_ROUTES_DIALOG_CONTAINER)
            .setAdditionalLogging(extraLogger)
            .setView(view)
            .create()
    }

    private fun setupAppIcon(view: View) {
        appIcon = view.findViewById(R.id.dialog_icon)
        permissionsViewModel.appInfo.observe(this) { app -> appIcon.setImageDrawable(app.icon) }
    }

    private fun setupPermissionRadioGroup(view: View) {
        permissionRadioGroup = view.findViewById(R.id.radio_group_route_permission)
        permissionRadioGroup.apply {
            val state = viewModel.additionalAccessState.value?.exerciseRoutePermissionUIState
            if (state != null) {
                setOnCheckedChangeListener(null)
                check(getId(state))
            }
            setOnCheckedChangeListener(permissionChangedListener)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.additionalAccessState.observe(viewLifecycleOwner) { screenState ->
            permissionRadioGroup.check(getId(screenState.exerciseRoutePermissionUIState))
        }
    }

    fun setPackageName(packageName: String) {
        this.packageName = packageName
    }

    @IdRes
    private fun getId(currentState: PermissionUiState): Int {
        return when (currentState) {
            ALWAYS_ALLOW -> R.id.radio_button_always_allow
            NEVER_ALLOW -> R.id.radio_button_revoke
            else -> R.id.radio_button_ask
        }
    }

    companion object {
        private const val TAG = "ERPermissionDialog"

        fun createDialog(packageName: String): ExerciseRoutesPermissionDialogFragment {
            return ExerciseRoutesPermissionDialogFragment().apply {
                arguments = bundleOf(EXTRA_PACKAGE_NAME to packageName)
            }
        }
    }
}
