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
package com.android.healthconnect.controller.dataentries.formatters

import android.content.Context
import android.icu.text.MessageFormat
import com.android.healthconnect.controller.R
import java.text.NumberFormat
import java.time.Duration

/** Formatter for printing durations. */
object DurationFormatter {

    /**
     * Formats the given duration in the format 'Xh Xm'.
     *
     * <p>Returns '0m' for durations less than a minute. Returns only the minute component for
     * durations less than an hour. Returns only the hour component for durations with hours and
     * zero minutes.
     */
    fun formatDurationShort(context: Context, duration: Duration): String {
        val hours: Long = duration.toHours()
        val minutes: Long = duration.toMinutes() % 60
        val integerFormat: NumberFormat = NumberFormat.getIntegerInstance()
        val hourString: String = integerFormat.format(hours)
        val minuteString: String = integerFormat.format(minutes)
        if (hours > 0 && minutes > 0) { // Shows as: Nh Nm
            return context.getString(R.string.hour_minute_duration_short, hourString, minuteString)
        }
        return if (hours > 0) { // Shows as: Nh
            context.getString(R.string.hour_duration, hourString)
        } else context.getString(R.string.minute_duration, minuteString)
        // Shows as: Nm
    }

    /**
     * Formats the given duration in the format 'X hours X minutes'.
     *
     * <p>Returns '0 minutes' for durations less than a minute. Returns only the minute component
     * for durations less than an hour. Returns only the hour component for durations with hours and
     * zero minutes.
     */
    fun formatDurationLong(context: Context, duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val hourString: String =
            MessageFormat.format(
                context.getString(R.string.hour_duration_accessibility), mapOf("count" to hours))
        val minuteString: String =
            MessageFormat.format(
                context.getString(R.string.minute_duration_accessibility),
                mapOf("count" to minutes))
        if (hours > 0 && minutes > 0) { // Shows as: N hour(s) N minute(s)
            return context.getString(
                R.string.hour_minute_duration_accessibility, hourString, minuteString)
        }
        return if (hours > 0) { // Shows as: N hour(s)
            hourString
        } else minuteString
        // Shows as: N minute(s)
    }

    /**
     * Formats the given duration in the format 'X days' or 'Y hours'.
     *
     * <p> Returns only the hour component for durations less than one day. Returns only the day
     * component for durations with days.
     */
    fun formatDurationDaysOrHours(context: Context, duration: Duration): String {
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val dayString: String =
            MessageFormat.format(
                context.getString(R.string.day_duration_accessibility), mapOf("count" to days))
        val hourString: String =
            MessageFormat.format(
                context.getString(R.string.hour_duration_accessibility), mapOf("count" to hours))

        return if (days > 0) { // Shows as: X day(s)
            dayString
        } else {
            hourString // Shows as: Y hour(s)
        }
    }
}
