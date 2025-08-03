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
package com.android.healthconnect.controller.autodelete

import android.app.Dialog
import android.icu.text.MessageFormat
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.utils.logging.AutoDeleteElement
import dagger.hilt.android.AndroidEntryPoint

/** A {@link DialogFragment} to get confirmation from user to turn auto-delete on. */
@AndroidEntryPoint(DialogFragment::class)
class AutoDeleteConfirmationDialogFragment : Hilt_AutoDeleteConfirmationDialogFragment() {

    companion object {
        const val TAG = "AutoDeleteConfirmationDialogFragment"
        const val AUTO_DELETE_SAVED_EVENT = "AUTO_DELETE_SAVED_EVENT"
        const val AUTO_DELETE_CANCELLED_EVENT = "AUTO_DELETE_CANCELLED_EVENT"
        const val AUTO_DELETE_CONFIRMATION_DIALOG_EVENT = "AUTO_DELETE_CONFIRMATION_DIALOG_EVENT"
        const val NEW_AUTO_DELETE_RANGE_BUNDLE = "NEW_AUTO_DELETE_RANGE_BUNDLE"
        const val OLD_AUTO_DELETE_RANGE_BUNDLE = "OLD_AUTO_DELETE_RANGE_BUNDLE"
    }

    private val viewModel: AutoDeleteViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        check(viewModel.newAutoDeleteRange.value != AutoDeleteRange.AUTO_DELETE_RANGE_NEVER) {
            "ConfirmationDialog not supported for AUTO_DELETE_RANGE_NEVER."
        }
        val alertDialog =
            AlertDialogBuilder(this, AutoDeleteElement.AUTO_DELETE_DIALOG_CONTAINER)
                .setIcon(R.attr.deletionSettingsIcon)

        viewModel.newAutoDeleteRange.value?.let {
            alertDialog
                .setTitle(buildTitle(it))
                .setMessage(buildMessage(it))
                .setPositiveButton(
                    R.string.set_auto_delete_button,
                    AutoDeleteElement.AUTO_DELETE_DIALOG_CONFIRM_BUTTON) { _, _ ->
                        setFragmentResult(
                            AUTO_DELETE_SAVED_EVENT,
                            bundleOf(AUTO_DELETE_SAVED_EVENT to viewModel.newAutoDeleteRange.value))
                    }
                .setNeutralButton(
                    android.R.string.cancel, AutoDeleteElement.AUTO_DELETE_DIALOG_CANCEL_BUTTON) {
                        _,
                        _ ->
                        setFragmentResult(
                            AUTO_DELETE_CANCELLED_EVENT,
                            bundleOf(
                                AUTO_DELETE_CANCELLED_EVENT to viewModel.oldAutoDeleteRange.value))
                    }
        }
        val dialog = alertDialog.create()
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    private fun buildTitle(autoDeleteRange: AutoDeleteRange): String {
        val count = autoDeleteRange.numberOfMonths
        return MessageFormat.format(
            requireContext().getString(R.string.confirming_question_x_months),
            mapOf("count" to count))
    }

    private fun buildMessage(autoDeleteRange: AutoDeleteRange): String {
        val count = autoDeleteRange.numberOfMonths
        return MessageFormat.format(
            requireContext().getString(R.string.confirming_message_x_months),
            mapOf("count" to count))
    }
}
