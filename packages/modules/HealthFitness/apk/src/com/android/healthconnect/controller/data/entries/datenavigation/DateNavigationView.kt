/*
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
package com.android.healthconnect.controller.data.entries.datenavigation

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.Spinner
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod.PERIOD_DAY
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod.PERIOD_MONTH
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod.PERIOD_WEEK
import com.android.healthconnect.controller.utils.SystemTimeSource
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.logging.DataEntriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import com.android.healthconnect.controller.utils.toLocalDate
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/** Allows the user to navigate in time to see their past data. */
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
    private lateinit var datePickerSpinner: Spinner
    private var selectedDate = Instant.ofEpochMilli(timeSource.currentTimeMillis())
    private var period: DateNavigationPeriod = PERIOD_DAY
    private var onDateChangedListener: OnDateChangedListener? = null

    init {
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()

        val view = inflate(context, R.layout.widget_date_navigation_with_spinner, this)
        bindDateTextView(view)
        bindNextDayButton(view)
        bindPreviousDayButton(view)
        updateDisplayedDates()
    }

    fun setDateChangedListener(mDateChangedListener: OnDateChangedListener?) {
        this.onDateChangedListener = mDateChangedListener
    }

    fun setDate(date: Instant) {
        selectedDate = date
        updateDisplayedDates()
    }

    fun setPeriod(period: DateNavigationPeriod) {
        this.period = period
        updateDisplayedDates()
    }

    fun getDate(): Instant {
        return selectedDate
    }

    fun getPeriod(): DateNavigationPeriod {
        return period
    }

    private fun bindNextDayButton(view: View) {
        nextDayButton = view.findViewById(R.id.navigation_next_day) as ImageButton
        logger.logImpression(DataEntriesElement.NEXT_DAY_BUTTON)
        nextDayButton.setOnClickListener {
            logger.logInteraction(DataEntriesElement.NEXT_DAY_BUTTON)
            selectedDate =
                selectedDate.atZone(ZoneId.systemDefault()).plus(toPeriod(period)).toInstant()
            updateDisplayedDates()
        }
    }

    private fun bindPreviousDayButton(view: View) {
        previousDayButton = view.findViewById(R.id.navigation_previous_day) as ImageButton
        logger.logImpression(DataEntriesElement.PREVIOUS_DAY_BUTTON)
        previousDayButton.setOnClickListener {
            logger.logInteraction(DataEntriesElement.PREVIOUS_DAY_BUTTON)
            selectedDate =
                selectedDate.atZone(ZoneId.systemDefault()).minus(toPeriod(period)).toInstant()
            updateDisplayedDates()
        }
    }

    private fun bindDateTextView(view: View) {
        datePickerSpinner = view.findViewById(R.id.date_picker_spinner) as Spinner

        val adapter =
            DatePickerSpinnerAdapter(view.context, getDisplayedStartDate(), period, timeSource)
        adapter.setDropDownViewResource(R.layout.date_navigation_spinner_item)
        datePickerSpinner.adapter = adapter

        datePickerSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    unused: View?,
                    position: Int,
                    id: Long
                ) {
                    val period: DateNavigationPeriod =
                        when (position) {
                            0 -> PERIOD_DAY
                            1 -> PERIOD_WEEK
                            2 -> PERIOD_MONTH
                            else -> throw IllegalStateException("Not supported time period.")
                        }
                    setPeriod(period)
                    updateDisplayedDates()
                }
            }

        datePickerSpinner.accessibilityDelegate =
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

    private fun updateDisplayedDates() {
        onDateChangedListener?.onDateChanged(getDisplayedStartDate(), period)
        val today =
            LocalDate.ofInstant(
                    Instant.ofEpochMilli(timeSource.currentTimeMillis()),
                    timeSource.deviceZoneOffset())
                .atStartOfDay(timeSource.deviceZoneOffset())
                .toInstant()

        // This can happen if e.g. today is Monday, user navigates back to Sunday, sets the period
        // from Day to Week (underlying selected day is still Sunday), navigates to the next week
        // (underlying selected day is next Sunday), sets the period back to Day => displayed day
        // would be next Sunday. Instead, display today.
        if (today.isBefore(selectedDate)) {
            selectedDate = today
        }

        val displayedEndDate =
            getDisplayedStartDate()
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .plus(toPeriod(period))
                .toInstant()
        nextDayButton.isEnabled = !displayedEndDate.isAfter(today)
        (datePickerSpinner.adapter as DatePickerSpinnerAdapter).setStartTimeAndPeriod(
            getDisplayedStartDate(), period)
    }

    private fun getDisplayedStartDate(): Instant =
        when (period) {
            PERIOD_DAY -> {
                selectedDate
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
            }
            PERIOD_WEEK -> {
                val dayOfWeek: DayOfWeek =
                    selectedDate.atZone(ZoneId.systemDefault()).toLocalDate().dayOfWeek
                val dayOfWeekOffset: Int = dayOfWeek.value - 1
                selectedDate
                    .atZone(ZoneId.systemDefault())
                    .minus(Period.ofDays(dayOfWeekOffset))
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
            }
            PERIOD_MONTH -> {
                val dayOfMonth =
                    selectedDate.atZone(ZoneId.systemDefault()).toLocalDate().dayOfMonth
                val dayOfMonthOffset: Int = dayOfMonth - 1
                selectedDate
                    .atZone(ZoneId.systemDefault())
                    .minus(Period.ofDays(dayOfMonthOffset))
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
            }
        }

    interface OnDateChangedListener {
        fun onDateChanged(displayedStartDate: Instant, period: DateNavigationPeriod)
    }
}
