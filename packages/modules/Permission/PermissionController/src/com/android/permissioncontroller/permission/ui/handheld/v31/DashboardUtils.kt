/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.handheld.v31

import android.content.Context
import android.icu.util.Calendar
import android.os.Build
import android.text.format.DateFormat.getMediumDateFormat
import android.text.format.DateFormat.getTimeFormat
import android.util.Pair
import androidx.annotation.RequiresApi
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage.GroupUsage
import com.android.permissioncontroller.permission.utils.StringUtils
import java.util.Locale

const val SECONDS = 1
const val MINUTES = 2
const val HOURS = 3
const val DAYS = 4

/**
 * Whether to show the subattribution in the Permissions Dashboard
 *
 * @return whether to show subattribution in the Permissions Dashboard.
 */
fun shouldShowSubattributionInPermissionsDashboard(): Boolean {
    return SdkLevel.isAtLeastS()
}

/**
 * Build a string representing the given time if it happened on the current day and the date
 * otherwise.
 *
 * @param context the context.
 * @param lastAccessTime the time in milliseconds.
 * @return a string representing the time or date of the given time or null if the time is 0.
 */
fun getAbsoluteTimeString(context: Context, lastAccessTime: Long): String? {
    if (lastAccessTime == 0L) {
        return null
    }
    return if (isToday(lastAccessTime)) {
        getTimeFormat(context).format(lastAccessTime)
    } else {
        getMediumDateFormat(context).format(lastAccessTime)
    }
}

/**
 * Build a string representing the time of the most recent permission usage if it happened on the
 * current day and the date otherwise.
 *
 * @param context the context.
 * @param groupUsage the permission usage.
 * @return a string representing the time or date of the most recent usage or null if there are no
 *   usages.
 */
@RequiresApi(Build.VERSION_CODES.S)
fun getAbsoluteLastUsageString(context: Context, groupUsage: GroupUsage?): String? {
    return if (groupUsage == null) {
        null
    } else getAbsoluteTimeString(context, groupUsage.lastAccessTime)
}

/**
 * Build a string representing the duration of a permission usage.
 *
 * @return a string representing the duration of this app's usage or null if there are no usages.
 */
@RequiresApi(Build.VERSION_CODES.S)
fun getUsageDurationString(context: Context, groupUsage: GroupUsage?): String? {
    return if (groupUsage == null) {
        null
    } else getTimeDiffStr(context, groupUsage.accessDuration)
}

/**
 * Build a string representing the number of milliseconds passed in. It rounds to the nearest unit.
 * For example, given a duration of 3500 and an English locale, this can return "3 seconds".
 *
 * @param context The context.
 * @param duration The number of milliseconds.
 * @return a string representing the given number of milliseconds.
 */
fun getTimeDiffStr(context: Context, duration: Long): String {
    val timeDiffAndUnit = calculateTimeDiffAndUnit(duration)
    return when (timeDiffAndUnit.second) {
        SECONDS ->
            StringUtils.getIcuPluralsString(
                context,
                R.string.seconds,
                timeDiffAndUnit.first.toInt()
            )
        MINUTES ->
            StringUtils.getIcuPluralsString(
                context,
                R.string.minutes,
                timeDiffAndUnit.first.toInt()
            )
        HOURS ->
            StringUtils.getIcuPluralsString(context, R.string.hours, timeDiffAndUnit.first.toInt())
        else ->
            StringUtils.getIcuPluralsString(context, R.string.days, timeDiffAndUnit.first.toInt())
    }
}

/**
 * Build a string representing the duration used of milliseconds passed in.
 *
 * @return a string representing the duration used in the nearest unit. ex: Used for 3 mins
 */
fun getDurationUsedStr(context: Context, duration: Long): String {
    val timeDiffAndUnit = calculateTimeDiffAndUnit(duration)
    return when (timeDiffAndUnit.second) {
        SECONDS ->
            StringUtils.getIcuPluralsString(
                context,
                R.string.duration_used_seconds,
                timeDiffAndUnit.first.toInt()
            )
        MINUTES ->
            StringUtils.getIcuPluralsString(
                context,
                R.string.duration_used_minutes,
                timeDiffAndUnit.first.toInt()
            )
        HOURS ->
            StringUtils.getIcuPluralsString(
                context,
                R.string.duration_used_hours,
                timeDiffAndUnit.first.toInt()
            )
        else ->
            StringUtils.getIcuPluralsString(
                context,
                R.string.duration_used_days,
                timeDiffAndUnit.first.toInt()
            )
    }
}

/**
 * Given the duration in milliseconds, calculate the time of that duration in the nearest unit.
 *
 * @return a Pair of the <duration in the nearest unit, the nearest unit>
 */
fun calculateTimeDiffAndUnit(duration: Long): Pair<Long, Int> {
    val seconds = Math.max(1, duration / 1000)

    if (seconds < 60) {
        return Pair.create(seconds, SECONDS)
    }
    val minutes = seconds / 60
    if (minutes < 60) {
        return Pair.create(minutes, MINUTES)
    }
    val hours = minutes / 60
    if (hours < 24) {
        return Pair.create(hours, HOURS)
    }
    val days = hours / 24
    return Pair.create(days, DAYS)
}

/**
 * Check whether the given time (in milliseconds) is in the current day.
 *
 * @param time the time in milliseconds
 * @return whether the given time is in the current day.
 */
private fun isToday(time: Long): Boolean {
    val today: Calendar = Calendar.getInstance(Locale.getDefault())
    today.setTimeInMillis(System.currentTimeMillis())
    today.set(Calendar.HOUR_OF_DAY, 0)
    today.set(Calendar.MINUTE, 0)
    today.set(Calendar.SECOND, 0)
    today.set(Calendar.MILLISECOND, 0)
    val date: Calendar = Calendar.getInstance(Locale.getDefault())
    date.setTimeInMillis(time)
    return !date.before(today)
}
