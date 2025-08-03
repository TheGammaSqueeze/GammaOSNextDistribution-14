/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.wear

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.IntDef
import com.android.permissioncontroller.R
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object WearUtils {
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(value = [LAST_24H_TODAY, LAST_24H_YESTERDAY, LAST_7D, NOT_IN_LAST_7D])
    annotation class AppPermsLastAccessType

    const val LAST_24H_TODAY = 1
    const val LAST_24H_YESTERDAY = 2
    const val LAST_7D = 3
    const val NOT_IN_LAST_7D = 4

    /** Get the preference summary in app permission groups and permission apps screens for Wear. */
    @JvmStatic
    fun getPreferenceSummary(context: Context, lastAccessTime: Long?): String {
        val summaryTimestamp = getPermissionLastAccessSummaryTimestamp(lastAccessTime, context)
        val res = context.resources
        return when (summaryTimestamp.second) {
            LAST_24H_TODAY ->
                res.getString(R.string.wear_app_perms_24h_access, summaryTimestamp.first)
            LAST_24H_YESTERDAY ->
                res.getString(R.string.wear_app_perms_24h_access_yest, summaryTimestamp.first)
            LAST_7D ->
                res.getString(
                    R.string.wear_app_perms_7d_access,
                    summaryTimestamp.third,
                    summaryTimestamp.first
                )
            else -> ""
        }
    }

    @JvmStatic
    private fun getPermissionLastAccessSummaryTimestamp(
        lastAccessTime: Long?,
        context: Context
    ): Triple<String, Int, String> {
        val midnightToday =
            (ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toEpochSecond() * 1000L)
        val midnightYesterday =
            ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.DAYS).toEpochSecond() * 1000L
        val isLastAccessToday = (lastAccessTime != null && midnightToday <= lastAccessTime)
        val isLastAccessTodayOrYesterday =
            (lastAccessTime != null && midnightYesterday <= lastAccessTime)
        var lastAccessTimeFormatted = ""
        var lastAccessDateFormatted = ""
        @AppPermsLastAccessType var lastAccessType = NOT_IN_LAST_7D
        if (lastAccessTime != null) {
            lastAccessTimeFormatted = DateFormat.getTimeFormat(context).format(lastAccessTime)
            lastAccessDateFormatted = DateFormat.getDateFormat(context).format(lastAccessTime)
            lastAccessType =
                if (isLastAccessToday) LAST_24H_TODAY
                else if (isLastAccessTodayOrYesterday) LAST_24H_YESTERDAY else LAST_7D
        }
        return Triple(lastAccessTimeFormatted, lastAccessType, lastAccessDateFormatted)
    }
}
