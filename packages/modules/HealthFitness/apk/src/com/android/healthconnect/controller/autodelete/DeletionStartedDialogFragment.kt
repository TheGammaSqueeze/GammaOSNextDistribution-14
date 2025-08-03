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
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.utils.logging.AutoDeleteElement
import dagger.hilt.android.AndroidEntryPoint

/** A {@link DialogFragment} to inform the user about data deletion. */
@AndroidEntryPoint(DialogFragment::class)
class DeletionStartedDialogFragment : Hilt_DeletionStartedDialogFragment() {

    companion object {
        const val TAG = "DeletionStartedDialogFragment"
    }

    private val viewModel: AutoDeleteViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        check(viewModel.newAutoDeleteRange.value != AutoDeleteRange.AUTO_DELETE_RANGE_NEVER) {
            "DeletionStartedDialog not supported for AUTO_DELETE_RANGE_NEVER."
        }
        val alertDialog =
            AlertDialogBuilder(this, AutoDeleteElement.AUTO_DELETE_CONFIRMATION_DIALOG_CONTAINER)
                .setTitle(R.string.deletion_started_title)
                .setIcon(R.attr.successIcon)
                .setPositiveButton(
                    R.string.deletion_started_done_button,
                    AutoDeleteElement.AUTO_DELETE_CONFIRMATION_DIALOG_DONE_BUTTON)
        viewModel.newAutoDeleteRange.value?.let { alertDialog.setMessage(buildMessage(it)) }
        return alertDialog.create()
    }

    private fun buildMessage(autoDeleteRange: AutoDeleteRange): String {
        val count = autoDeleteRange.numberOfMonths
        return MessageFormat.format(
            requireContext().getString(R.string.deletion_started_x_months), mapOf("count" to count))
    }
}
