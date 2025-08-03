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
package com.android.healthconnect.controller.shared.dialog

import android.content.Context
import android.content.DialogInterface
import android.text.SpannableString
import android.view.Gravity.CENTER
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.increaseViewTouchTargetSize
import com.android.healthconnect.controller.utils.logging.ElementName
import com.android.healthconnect.controller.utils.logging.ErrorPageElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/** {@link AlertDialog.Builder} wrapper for applying theming attributes. */
class AlertDialogBuilder(private val context: Context, private val containerLogName: ElementName) {

    private var alertDialogBuilder: AlertDialog.Builder
    private var customTitleLayout: View =
        LayoutInflater.from(context).inflate(R.layout.dialog_title, null)
    private var customMessageLayout: View =
        LayoutInflater.from(context).inflate(R.layout.dialog_message, null)
    private var customDialogLayout: View =
        LayoutInflater.from(context).inflate(R.layout.dialog_custom_layout, null)
    private var logger: HealthConnectLogger

    constructor(
        fragment: Fragment,
        containerLogName: ElementName
    ) : this(fragment.requireContext(), containerLogName)

    constructor(
        activity: FragmentActivity,
        containerLogName: ElementName
    ) : this(activity as Context, containerLogName)

    private var iconView: ImageView? = null

    private var positiveButtonKey: ElementName = ErrorPageElement.UNKNOWN_ELEMENT
    private var negativeButtonKey: ElementName = ErrorPageElement.UNKNOWN_ELEMENT
    private var loggingAction = {}

    private var hasPositiveButton = false
    private var hasNegativeButton = false

    init {
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                this.context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()

        alertDialogBuilder = AlertDialog.Builder(context)
        alertDialogBuilder.setView(customDialogLayout)
    }

    fun setCancelable(isCancelable: Boolean): AlertDialogBuilder {
        alertDialogBuilder.setCancelable(isCancelable)
        return this
    }

    fun setIcon(@AttrRes iconId: Int): AlertDialogBuilder {
        iconView = customDialogLayout.findViewById(R.id.dialog_icon)
        val iconDrawable = AttributeResolver.getNullableDrawable(context, iconId)
        iconDrawable?.let {
            iconView?.setImageDrawable(it)
            iconView?.visibility = View.VISIBLE
        }

        return this
    }

    fun setCustomIcon(@AttrRes iconId: Int): AlertDialogBuilder {
        iconView = customTitleLayout.findViewById(R.id.dialog_icon)
        val iconDrawable = AttributeResolver.getNullableDrawable(context, iconId)
        iconDrawable?.let {
            iconView?.setImageDrawable(it)
            iconView?.visibility = View.VISIBLE
            alertDialogBuilder.setCustomTitle(customTitleLayout)
        }

        return this
    }

    /** Sets the title in the title text view using the given resource id. */
    fun setTitle(@StringRes titleId: Int): AlertDialogBuilder {
        val titleView: TextView = customDialogLayout.findViewById(R.id.dialog_title)
        titleView.setText(titleId)
        return this
    }

    /** Sets the title in the title text view using the given string. */
    fun setTitle(titleString: String): AlertDialogBuilder {
        val titleView: TextView = customDialogLayout.findViewById(R.id.dialog_title)
        titleView.text = titleString
        return this
    }

    /** Sets the title with custom view in the custom title layout using the given resource id. */
    fun setCustomTitle(@StringRes titleId: Int): AlertDialogBuilder {
        val titleView: TextView = customTitleLayout.findViewById(R.id.dialog_title)
        titleView.setText(titleId)
        alertDialogBuilder.setCustomTitle(customTitleLayout)
        return this
    }

    /** Sets the title with custom view in the custom title layout. */
    fun setCustomTitle(titleString: String): AlertDialogBuilder {
        val titleView: TextView = customTitleLayout.findViewById(R.id.dialog_title)
        titleView.text = titleString
        alertDialogBuilder.setCustomTitle(customTitleLayout)
        return this
    }

    /** Sets the title with custom view in the custom title layout using a Spannable String. */
    fun setCustomTitle(titleString: SpannableString): AlertDialogBuilder {
        val titleView: TextView = customTitleLayout.findViewById(R.id.dialog_title)
        titleView.text = titleString
        alertDialogBuilder.setCustomTitle(customTitleLayout)
        return this
    }

    /** Sets the message to be displayed in the dialog using the given resource id. */
    fun setMessage(@StringRes messageId: Int): AlertDialogBuilder {
        val messageView: TextView = customDialogLayout.findViewById(R.id.dialog_custom_message)
        messageView.text = context.getString(messageId)
        return this
    }

    /** Sets the message to be displayed in the dialog. */
    fun setMessage(message: CharSequence?): AlertDialogBuilder {
        val messageView: TextView = customDialogLayout.findViewById(R.id.dialog_custom_message)
        messageView.text = message
        return this
    }

    fun setMessage(message: String): AlertDialogBuilder {
        val messageView: TextView = customDialogLayout.findViewById(R.id.dialog_custom_message)
        messageView.text = message
        return this
    }

    /**
     * Sets the message with custom view to be displayed in the dialog using the given resource id.
     */
    fun setCustomMessage(@StringRes messageId: Int): AlertDialogBuilder {
        val messageView: TextView = customMessageLayout.findViewById(R.id.dialog_custom_message)
        messageView.text = context.getString(messageId)
        alertDialogBuilder.setView(customMessageLayout)
        return this
    }

    /** Sets the message with custom view to be displayed in the dialog. */
    fun setCustomMessage(message: CharSequence?): AlertDialogBuilder {
        val messageView: TextView = customMessageLayout.findViewById(R.id.dialog_custom_message)
        messageView.text = message
        alertDialogBuilder.setView(customMessageLayout)
        return this
    }

    fun setCustomMessage(message: String): AlertDialogBuilder {
        val messageView: TextView = customMessageLayout.findViewById(R.id.dialog_custom_message)
        messageView.text = message
        alertDialogBuilder.setView(customMessageLayout)
        return this
    }

    fun setView(view: View): AlertDialogBuilder {
        alertDialogBuilder.setView(view)
        return this
    }

    fun setNegativeButton(
        @StringRes textId: Int,
        buttonId: ElementName,
        onClickListener: DialogInterface.OnClickListener? = null
    ): AlertDialogBuilder {
        hasNegativeButton = true
        negativeButtonKey = buttonId

        val loggingClickListener =
            DialogInterface.OnClickListener { dialog, which ->
                logger.logInteraction(negativeButtonKey)
                onClickListener?.onClick(dialog, which)
            }

        alertDialogBuilder.setNegativeButton(textId, loggingClickListener)
        return this
    }

    /**
     * To ensure a clear and accessible layout for all users, this button replaces a traditional
     * negative button with a neutral button and used as a negative button when a positive button is
     * also present. This prevents button borders from overlapping, when display and font sizes are
     * set to their largest in accessibility settings.
     */
    fun setNeutralButton(
        @StringRes textId: Int,
        buttonId: ElementName,
        onClickListener: DialogInterface.OnClickListener? = null
    ): AlertDialogBuilder {
        hasNegativeButton = true
        negativeButtonKey = buttonId

        val loggingClickListener =
            DialogInterface.OnClickListener { dialog, which ->
                logger.logInteraction(negativeButtonKey)
                onClickListener?.onClick(dialog, which)
            }

        alertDialogBuilder.setNeutralButton(textId, loggingClickListener)
        return this
    }

    fun setPositiveButton(
        @StringRes textId: Int,
        buttonId: ElementName,
        onClickListener: DialogInterface.OnClickListener? = null
    ): AlertDialogBuilder {
        hasPositiveButton = true
        positiveButtonKey = buttonId
        val loggingClickListener =
            DialogInterface.OnClickListener { dialog, which ->
                logger.logInteraction(positiveButtonKey)
                onClickListener?.onClick(dialog, which)
            }

        alertDialogBuilder.setPositiveButton(textId, loggingClickListener)
        return this
    }

    /**
     * Allows setting additional logging actions for custom dialog elements, such as messages,
     * checkboxes or radio buttons.
     *
     * Impressions should be logged only once the dialog has been created.
     */
    fun setAdditionalLogging(loggingAction: () -> Unit): AlertDialogBuilder {
        this.loggingAction = loggingAction
        return this
    }

    fun create(): AlertDialog {
        val dialog = alertDialogBuilder.create()
        setDialogGravityFromTheme(dialog)

        dialog.setOnShowListener { increaseDialogTouchTargetSize(dialog) }

        // Dialog container
        logger.logImpression(this.containerLogName)

        // Dialog buttons
        if (hasPositiveButton) {
            logger.logImpression(positiveButtonKey)
        }
        if (hasNegativeButton) {
            logger.logImpression(negativeButtonKey)
        }

        // Any additional logging e.g. for dialog messages
        loggingAction()

        return dialog
    }

    private fun increaseDialogTouchTargetSize(dialog: AlertDialog) {
        if (hasPositiveButton) {
            val positiveButtonView = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            val parentView = positiveButtonView.parent as View
            increaseViewTouchTargetSize(context, positiveButtonView, parentView)
        }

        if (hasNegativeButton) {
            val negativeButtonView = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            val parentView = negativeButtonView.parent.parent as View
            increaseViewTouchTargetSize(context, negativeButtonView, parentView)
        }
    }

    private fun setDialogGravityFromTheme(dialog: AlertDialog) {
        val typedArray = context.obtainStyledAttributes(intArrayOf(R.attr.dialogGravity))
        try {
            if (typedArray.hasValue(0)) {
                requireNotNull(dialog.window).setGravity(typedArray.getInteger(0, CENTER))
            }
        } finally {
            typedArray.recycle()
        }
    }
}
