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
 *
 *
 */

package com.android.healthconnect.controller.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.MenstruationPeriodRecord
import android.health.connect.datatypes.Record
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedDataEntry
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.utils.toLocalDate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import javax.inject.Inject

/** Formatter for printing MenstruationPeriodRecord data. */
class MenstruationPeriodFormatter
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    @ApplicationContext private val context: Context
) {

    suspend fun format(
        day: Instant,
        record: MenstruationPeriodRecord,
        showDataOrigin: Boolean = true
    ): FormattedDataEntry {
        val dayOfPeriod = dayOfPeriod(record, day)
        val totalDays = totalDaysOfPeriod(record)
        val title = context.getString(R.string.period_day, dayOfPeriod, totalDays)
        val appName = if (showDataOrigin) getAppName(record) else ""

        return FormattedDataEntry(
            uuid = record.metadata.id,
            title = title,
            titleA11y = title,
            header = appName,
            headerA11y = appName,
            dataType = DataType.MENSTRUATION_PERIOD,
            startTime = record.startTime,
            endTime = record.endTime)
    }

    private fun dayOfPeriod(record: MenstruationPeriodRecord, day: Instant): Int {
        return (Period.between(record.startTime.toLocalDate(), day.toLocalDate()).days +
            1) // + 1 to return a 1-indexed counter (i.e. "Period day 1", not "day 0")
    }

    private fun totalDaysOfPeriod(record: MenstruationPeriodRecord): Int {
        return (DAYS.between(record.startTime.toLocalDate(), record.endTime.toLocalDate()).toInt() +
            1)
    }

    private suspend fun getAppName(record: Record): String {
        return appInfoReader.getAppMetadata(record.metadata.dataOrigin.packageName).appName
    }
}
