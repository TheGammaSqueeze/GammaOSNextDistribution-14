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
package com.android.healthconnect.testapps.toolbox.fieldviews

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.InputType
import android.widget.DatePicker
import android.widget.EditText
import android.widget.TextView
import android.widget.TimePicker
import com.android.healthconnect.testapps.toolbox.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar

@SuppressLint("ViewConstructor")
class DateTimePicker(context: Context, fieldName: String, setPreviousDay: Boolean = false) :
    InputFieldView(context) {

    private val mCalendar: Calendar = Calendar.getInstance()
    private var mSelectedYear: Int = mCalendar.get(Calendar.YEAR)
    private var mSelectedMonth: Int = mCalendar.get(Calendar.MONTH) + 1
    private var mSelectedDay: Int = mCalendar.get(Calendar.DATE) + (if (setPreviousDay) -1 else 0)
    private var mSelectedHour = 0
    private var mSelectedMinute = 0

    init {
        inflate(context, R.layout.date_time_picker, this)
        findViewById<TextView>(R.id.title).text = fieldName
        setupDate()
        setupTime()
    }

    private fun setupDate() {
        findViewById<EditText>(R.id.select_date).let { date ->
            date.setText(getDateString())
            date.inputType = InputType.TYPE_NULL
            date.setOnClickListener { showDatePicker(date) }
        }
    }

    private fun setupTime() {
        findViewById<EditText>(R.id.select_time).let { time ->
            time.setText(getTimeString())
            time.inputType = InputType.TYPE_NULL
            time.setOnClickListener { showTimePicker(time) }
        }
    }

    private fun getDateString(): String {
        return "$mSelectedDay/$mSelectedMonth/$mSelectedYear"
    }

    private fun getTimeString(): String {
        return (((if (mSelectedHour < 10) "0" else "") + mSelectedHour) +
            (if (mSelectedMinute < 10) ":0" else ":") +
            mSelectedMinute)
    }

    private fun showDatePicker(text: EditText) {
        val picker =
            DatePickerDialog(
                context,
                { _: DatePicker?, year: Int, month: Int, dayOfMonth: Int ->
                    mSelectedYear = year
                    mSelectedMonth = month + 1
                    mSelectedDay = dayOfMonth
                    text.setText(getDateString())
                },
                mSelectedYear,
                mSelectedMonth - 1,
                mSelectedDay)
        picker.show()
    }

    private fun showTimePicker(text: EditText) {
        val picker =
            TimePickerDialog(
                context,
                { _: TimePicker?, hourOfDay: Int, minute: Int ->
                    mSelectedHour = hourOfDay
                    mSelectedMinute = minute
                    text.setText(getTimeString())
                },
                mSelectedHour,
                mSelectedMinute,
                true)
        picker.show()
    }

    override fun getFieldValue(): Instant {
        val systemZoneId: String = ZoneId.systemDefault().id
        val localDate: LocalDate = LocalDate.of(mSelectedYear, mSelectedMonth, mSelectedDay)
        val localTime: LocalTime = LocalTime.of(mSelectedHour, mSelectedMinute)
        return LocalDateTime.of(localDate, localTime).atZone(ZoneId.of(systemZoneId)).toInstant()
    }

    override fun isEmpty(): Boolean {
        return false
    }
}
