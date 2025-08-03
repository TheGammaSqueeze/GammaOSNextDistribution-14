/**
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.permissions.shared

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.logging.DisconnectAppDialogElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** A Dialog Fragment to get confirmation from user for disconnecting from Health Connect. */
@AndroidEntryPoint(DialogFragment::class)
class DisconnectDialogFragment constructor() : Hilt_DisconnectDialogFragment() {

    constructor(appName: String, enableDeleteData: Boolean = true) : this() {
        this.appName = appName
        this.enableDeleteData = enableDeleteData
    }

    companion object {
        const val TAG = "DisconnectDialogFragment"
        const val DISCONNECT_CANCELED_EVENT = "DISCONNECT_CANCELED_EVENT"
        const val DISCONNECT_ALL_EVENT = "DISCONNECT_ALL_EVENT"
        const val KEY_DELETE_DATA = "KEY_DELETE_DATA"
        const val KEY_APP_NAME = "KEY_APP_NAME"
        const val KEY_ENABLE_DELETE_DATA = "KEY_ENABLE_DELETE_DATA"
    }

    lateinit var appName: String
    var enableDeleteData: Boolean = true

    @Inject lateinit var logger: HealthConnectLogger

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState != null) {
            appName = savedInstanceState.getString(KEY_APP_NAME, "")
            enableDeleteData = savedInstanceState.getBoolean(KEY_ENABLE_DELETE_DATA, true)
        }

        val body = layoutInflater.inflate(R.layout.dialog_message_with_checkbox, null)
        body.findViewById<TextView>(R.id.dialog_message).apply {
            text = getString(R.string.permissions_disconnect_dialog_message, appName)
        }
        body.findViewById<TextView>(R.id.dialog_title).apply {
            text = getString(R.string.permissions_disconnect_dialog_title)
        }
        val iconView = body.findViewById(R.id.dialog_icon) as ImageView
        val iconDrawable =
            AttributeResolver.getNullableDrawable(body.context, R.attr.disconnectIcon)
        iconDrawable?.let {
            iconView.setImageDrawable(it)
            iconView.visibility = View.VISIBLE
        }
        val checkBox =
            body.findViewById<CheckBox>(R.id.dialog_checkbox).apply {
                text = getString(R.string.permissions_disconnect_dialog_checkbox, appName)
                isVisible = enableDeleteData
            }
        checkBox.setOnCheckedChangeListener { _, _ ->
            logger.logInteraction(DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_DELETE_CHECKBOX)
        }

        val dialog =
            AlertDialogBuilder(this, DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_CONTAINER)
                .setView(body)
                .setNeutralButton(
                    android.R.string.cancel,
                    DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_CANCEL_BUTTON) { _, _ ->
                        setFragmentResult(DISCONNECT_CANCELED_EVENT, bundleOf())
                    }
                .setPositiveButton(
                    R.string.permissions_disconnect_dialog_disconnect,
                    DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_CONFIRM_BUTTON) { _, _ ->
                        setFragmentResult(
                            DISCONNECT_ALL_EVENT, bundleOf(KEY_DELETE_DATA to checkBox.isChecked))
                    }
                .setAdditionalLogging {
                    logger.logImpression(
                        DisconnectAppDialogElement.DISCONNECT_APP_DIALOG_DELETE_CHECKBOX)
                }
                .create()
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_APP_NAME, appName)
        outState.putBoolean(KEY_ENABLE_DELETE_DATA, enableDeleteData)
    }
}
