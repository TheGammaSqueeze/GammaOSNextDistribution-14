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
package com.android.healthconnect.controller.utils

import android.content.Context
import android.text.format.DateFormat.*
import android.text.format.DateUtils
import com.android.healthconnect.controller.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Formatter for printing time and time ranges. */
class LocalDateTimeFormatter @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        // Example: "Sun, Aug 20, 2023"
        private const val WEEKDAY_DATE_FORMAT_FLAGS_WITH_YEAR: Int =
            DateUtils.FORMAT_SHOW_WEEKDAY or
                DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_ABBREV_ALL

        // Example: "Sun, Aug 20"
        private const val WEEKDAY_DATE_FORMAT_FLAGS_WITHOUT_YEAR: Int =
            WEEKDAY_DATE_FORMAT_FLAGS_WITH_YEAR or DateUtils.FORMAT_NO_YEAR

        // Example: "Aug 20, 2023"
        private const val DATE_FORMAT_FLAGS_WITH_YEAR: Int =
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL

        // Example: "Aug 20"
        private const val DATE_FORMAT_FLAGS_WITHOUT_YEAR: Int =
            DATE_FORMAT_FLAGS_WITH_YEAR or DateUtils.FORMAT_NO_YEAR

        // Example: "August 2023".
        private const val MONTH_FORMAT_FLAGS_WITH_YEAR: Int =
            DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_SHOW_YEAR or
                DateUtils.FORMAT_NO_MONTH_DAY

        // Example: "August".
        private const val MONTH_FORMAT_FLAGS_WITHOUT_YEAR: Int =
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_NO_MONTH_DAY
    }

    private val timeFormat by lazy { getTimeFormat(context) }
    private val longDateFormat by lazy { getLongDateFormat(context) }
    private val shortDateFormat by lazy {
        val systemFormat = getBestDateTimePattern(Locale.getDefault(), "dMMMM")
        DateTimeFormatter.ofPattern(systemFormat, Locale.getDefault())
    }
    private val monthAndYearFormat by lazy {
        val systemFormat = getBestDateTimePattern(Locale.getDefault(), "MMMMYYYY")
        DateTimeFormatter.ofPattern(systemFormat, Locale.getDefault())
    }
    private val monthFormat by lazy {
        val systemFormat = getBestDateTimePattern(Locale.getDefault(), "MMMM")
        DateTimeFormatter.ofPattern(systemFormat, Locale.getDefault())
    }

    /** Returns localized time. */
    fun formatTime(instant: Instant): String {
        return timeFormat.format(instant.toEpochMilli())
    }

    /** Returns localized long versions of date. */
    fun formatLongDate(instant: Instant): String {
        return longDateFormat.format(instant.toEpochMilli())
    }

    /** Returns localized short versions of date, such as "15 August" */
    fun formatShortDate(instant: Instant): String {
        return instant.atZone(ZoneId.systemDefault()).format(shortDateFormat)
    }

    /** Returns localized time range. */
    fun formatTimeRange(start: Instant, end: Instant): String {
        return context.getString(R.string.time_range, formatTime(start), formatTime(end))
    }

    /** Returns accessible and localized time range. */
    fun formatTimeRangeA11y(start: Instant, end: Instant): String {
        return context.getString(R.string.time_range_long, formatTime(start), formatTime(end))
    }

    /** Formats date with weekday and year (e.g. "Sun, Aug 20, 2023"). */
    fun formatWeekdayDateWithYear(time: Instant): String {
        return DateUtils.formatDateTime(
            context,
            time.toEpochMilli(),
            WEEKDAY_DATE_FORMAT_FLAGS_WITH_YEAR or DateUtils.FORMAT_ABBREV_ALL)
    }

    /** Formats date with weekday (e.g. "Sun, Aug 20"). */
    fun formatWeekdayDateWithoutYear(time: Instant): String {
        return DateUtils.formatDateTime(
            context,
            time.toEpochMilli(),
            WEEKDAY_DATE_FORMAT_FLAGS_WITHOUT_YEAR or DateUtils.FORMAT_ABBREV_ALL)
    }

    /** Formats date range with year(e.g. "Aug 21 - 27, 2023", "Aug 28 - Sept 3, 2023"). */
    fun formatDateRangeWithYear(startTime: Instant, endTime: Instant): String {
        return DateUtils.formatDateRange(
            context, startTime.toEpochMilli(), endTime.toEpochMilli(), DATE_FORMAT_FLAGS_WITH_YEAR)
    }

    /** Formats date range (e.g. "Aug 21 - 27", "Aug 28 - Sept 3"). */
    fun formatDateRangeWithoutYear(startTime: Instant, endTime: Instant): String {
        return DateUtils.formatDateRange(
            context,
            startTime.toEpochMilli(),
            endTime.toEpochMilli(),
            DATE_FORMAT_FLAGS_WITHOUT_YEAR)
    }

    /** Formats month and year (e.g. "August 2023"). */
    fun formatMonthWithYear(time: Instant): String {
        return DateUtils.formatDateTime(context, time.toEpochMilli(), MONTH_FORMAT_FLAGS_WITH_YEAR)
    }

    /** Formats month (e.g. "August"). */
    fun formatMonthWithoutYear(time: Instant): String {
        return DateUtils.formatDateTime(
            context, time.toEpochMilli(), MONTH_FORMAT_FLAGS_WITHOUT_YEAR)
    }
}
