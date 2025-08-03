/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.healthconnect.controller.autodelete

import android.content.Context
import android.icu.text.MessageFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.logging.AutoDeleteElement
import com.android.healthconnect.controller.utils.logging.ErrorPageElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger

/**
 * Custom preference for displaying a custom dialog where the user can confirm the auto-delete range
 * update.
 */
class AutoDeleteRangePickerPreference
constructor(
    context: Context,
    private val childFragmentManager: FragmentManager,
    private var autoDeleteRange: AutoDeleteRange,
    private val logger: HealthConnectLogger
) : Preference(context), RadioGroup.OnCheckedChangeListener {

    companion object {
        const val SET_TO_NEVER_EVENT = "SET_TO_NEVER_EVENT"
        const val AUTO_DELETE_RANGE_PICKER_PREFERENCE_KEY = "auto_delete_range_picker"
    }

    init {
        key = AUTO_DELETE_RANGE_PICKER_PREFERENCE_KEY
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val widgetFrame: ViewGroup = holder.findViewById(android.R.id.widget_frame) as ViewGroup
        val widgetFrameParent: LinearLayout = widgetFrame.parent as LinearLayout

        val iconFrame: LinearLayout? = holder.findViewById(android.R.id.icon_frame) as LinearLayout?
        widgetFrameParent.removeView(iconFrame)

        var autoDeleteWidget: ViewGroup? =
            holder.findViewById(R.id.auto_delete_range_picker_layout) as ViewGroup?
        if (autoDeleteWidget == null) {
            val inflater: LayoutInflater = context.getSystemService(LayoutInflater::class.java)
            autoDeleteWidget =
                inflater.inflate(R.layout.widget_auto_delete_range_picker, widgetFrameParent, false)
                    as ViewGroup?
            widgetFrameParent.addView(autoDeleteWidget, 0)
        }

        val radioGroup: RadioGroup = holder.findViewById(R.id.radio_group_time_range) as RadioGroup
        setRadioButtonTexts(radioGroup)

        setRadioButtonLogging(radioGroup)

        radioGroup.setOnCheckedChangeListener(null)
        setCheckedRadioButton(radioGroup, autoDeleteRange)
        radioGroup.setOnCheckedChangeListener(this)
    }

    private fun setRadioButtonLogging(radioGroup: RadioGroup) {
        radioGroup.children.forEach {
            val element =
                when (it.id) {
                    R.id.radio_button_3_months -> AutoDeleteElement.AUTO_DELETE_3_MONTHS_BUTTON
                    R.id.radio_button_18_months -> AutoDeleteElement.AUTO_DELETE_18_MONTHS_BUTTON
                    R.id.radio_button_never -> AutoDeleteElement.AUTO_DELETE_NEVER_BUTTON
                    else -> ErrorPageElement.UNKNOWN_ELEMENT
                }

            it.setOnClickListener { logger.logInteraction(element) }

            logger.logImpression(element)
        }
    }

    private fun setRadioButtonTexts(radioGroup: RadioGroup) {
        val radioButton3Months: RadioButton = radioGroup.findViewById(R.id.radio_button_3_months)
        var count = AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS.numberOfMonths
        radioButton3Months.text =
            MessageFormat.format(
                context.getString(R.string.range_after_x_months), mapOf("count" to count))

        val radioButton18Months: RadioButton = radioGroup.findViewById(R.id.radio_button_18_months)
        count = AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS.numberOfMonths
        radioButton18Months.text =
            MessageFormat.format(
                context.getString(R.string.range_after_x_months), mapOf("count" to count))
    }

    private fun setCheckedRadioButton(radioGroup: RadioGroup, autoDeleteRange: AutoDeleteRange) {
        when (autoDeleteRange) {
            AutoDeleteRange.AUTO_DELETE_RANGE_NEVER -> radioGroup.check(R.id.radio_button_never)
            AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS ->
                radioGroup.check(R.id.radio_button_3_months)
            AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS ->
                radioGroup.check(R.id.radio_button_18_months)
        }
    }

    private fun setToNeverOrAskConfirmation(chosenId: Int) {
        if (chosenId == R.id.radio_button_never) {
            childFragmentManager.setFragmentResult(SET_TO_NEVER_EVENT, Bundle())
            return
        }
        val newAutoDeleteRange: AutoDeleteRange =
            if (chosenId == R.id.radio_button_3_months)
                AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS
            else AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS
        childFragmentManager.setFragmentResult(
            AutoDeleteConfirmationDialogFragment.AUTO_DELETE_CONFIRMATION_DIALOG_EVENT,
            bundleOf(
                AutoDeleteConfirmationDialogFragment.NEW_AUTO_DELETE_RANGE_BUNDLE to
                    newAutoDeleteRange,
                AutoDeleteConfirmationDialogFragment.OLD_AUTO_DELETE_RANGE_BUNDLE to
                    autoDeleteRange))
    }

    override fun onCheckedChanged(radioGroup: RadioGroup, checkedId: Int) {
        setToNeverOrAskConfirmation(checkedId)
    }

    fun updateAutoDeleteRange(newAutoDeleteRange: AutoDeleteRange) {
        autoDeleteRange = newAutoDeleteRange
        notifyChanged()
    }
}
