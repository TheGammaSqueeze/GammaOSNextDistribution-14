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
package com.android.healthconnect.controller.dataentries

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.DatePickerFactory
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.android.healthconnect.controller.utils.SystemTimeSource
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.getInstant
import com.android.healthconnect.controller.utils.logging.DataEntriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import com.android.healthconnect.controller.utils.toInstant
import com.android.healthconnect.controller.utils.toLocalDate
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration.ofDays
import java.time.Instant

/** This DateNavigationView allows the user to navigate in time to see their past data. */
class DateNavigationView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
    private val timeSource: TimeSource = SystemTimeSource
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val logger: HealthConnectLogger

    private lateinit var previousDayButton: ImageButton
    private lateinit var nextDayButton: ImageButton
    private lateinit var selectedDateView: TextView
    private val dateFormatter = LocalDateTimeFormatter(context)
    private var selectedDate: Instant = timeSource.currentTimeMillis().toInstant()
    private var onDateChangedListener: OnDateChangedListener? = null

    init {
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()

        val view = inflate(context, R.layout.widget_date_navigation, this)
        bindDateTextView(view)
        bindNextDayButton(view)
        bindPreviousDayButton(view)
        updateSelectedDate()
    }

    fun setDateChangedListener(mDateChangedListener: OnDateChangedListener?) {
        this.onDateChangedListener = mDateChangedListener
    }

    fun setDate(date: Instant) {
        selectedDate = date
        updateSelectedDate()
    }

    fun getDate(): Instant {
        return selectedDate
    }

    private fun bindNextDayButton(view: View) {
        nextDayButton = view.findViewById(R.id.navigation_next_day) as ImageButton
        logger.logImpression(DataEntriesElement.NEXT_DAY_BUTTON)
        nextDayButton.setOnClickListener {
            logger.logInteraction(DataEntriesElement.NEXT_DAY_BUTTON)
            selectedDate = selectedDate.plus(ofDays(1))
            updateSelectedDate()
        }
    }

    private fun bindPreviousDayButton(view: View) {
        previousDayButton = view.findViewById(R.id.navigation_previous_day) as ImageButton
        logger.logImpression(DataEntriesElement.PREVIOUS_DAY_BUTTON)
        previousDayButton.setOnClickListener {
            logger.logInteraction(DataEntriesElement.PREVIOUS_DAY_BUTTON)
            selectedDate = selectedDate.minus(ofDays(1))
            updateSelectedDate()
        }
    }

    private fun bindDateTextView(view: View) {
        // TODO(b/291249677): Add log in upcoming CL.
        selectedDateView = view.findViewById(R.id.selected_date) as TextView
        logger.logImpression(DataEntriesElement.SELECT_DATE_BUTTON)
        selectedDateView.setOnClickListener {
            logger.logInteraction(DataEntriesElement.SELECT_DATE_BUTTON)
            val today = timeSource.currentTimeMillis().toInstant()
            val datePickerDialog = DatePickerFactory.create(context, selectedDate, today)
            datePickerDialog.setOnDateSetListener { _, year, month, day ->
                // OnDateSetListener returns months as Int from ( 0 - 11 ), getInstant accept month
                // as integer from 1 - 12
                selectedDate = getInstant(year, month + 1, day)
                updateSelectedDate()
            }
            datePickerDialog.show()
        }
        selectedDateView.accessibilityDelegate =
            object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(
                        AccessibilityNodeInfo.AccessibilityAction(
                            AccessibilityNodeInfoCompat.ACTION_CLICK,
                            context.getString(R.string.selected_date_view_action_description)))
                }
            }
    }

    private fun updateSelectedDate() {
        onDateChangedListener?.onDateChanged(selectedDate)
        selectedDateView.text = dateFormatter.formatLongDate(selectedDate)
        selectedDateView.contentDescription = dateFormatter.formatLongDate(selectedDate)
        selectedDateView.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
        val today = timeSource.currentTimeMillis().toInstant().toLocalDate().atStartOfDay()
        val curDate = selectedDate.toLocalDate().atStartOfDay()
        nextDayButton.isEnabled = curDate.isBefore(today)
    }

    interface OnDateChangedListener {
        fun onDateChanged(selectedDate: Instant)
    }
}
