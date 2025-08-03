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
package com.android.healthconnect.controller.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.StepsCadenceRecord
import android.health.connect.datatypes.StepsCadenceRecord.StepsCadenceRecordSample
import android.icu.text.MessageFormat
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.SessionDetailsFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Formatter for printing StepsCadence series data. */
class StepsCadenceFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<StepsCadenceRecord>(context), SessionDetailsFormatter<StepsCadenceRecord> {

    private val timeFormatter = LocalDateTimeFormatter(context)

    override suspend fun formatRecord(
        record: StepsCadenceRecord,
        header: String,
        headerA11y: String,
        unitPreferences: UnitPreferences
    ): FormattedEntry {
        return FormattedEntry.SeriesDataEntry(
            uuid = record.metadata.id,
            header = header,
            headerA11y = headerA11y,
            title = formatValue(record, unitPreferences),
            titleA11y = formatA11yValue(record, unitPreferences),
            dataType = getDataType(record))
    }

    /** Returns localized average StepsCadence from multiple data points. */
    override suspend fun formatValue(
        record: StepsCadenceRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatRange(R.string.steps_per_minute, record.samples)
    }

    /** Returns localized StepsCadence value. */
    override suspend fun formatA11yValue(
        record: StepsCadenceRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatRange(R.string.steps_per_minute_long, record.samples)
    }

    override suspend fun formatRecordDetails(record: StepsCadenceRecord): List<FormattedEntry> {
        return record.samples.sortedBy { it.time }.map { formatSample(record.metadata.id, it) }
    }

    private fun formatSample(
        id: String,
        sample: StepsCadenceRecordSample
    ): FormattedEntry.FormattedSessionDetail {
        return FormattedEntry.FormattedSessionDetail(
            uuid = id,
            header = timeFormatter.formatTime(sample.time),
            headerA11y = timeFormatter.formatTime(sample.time),
            title =
                MessageFormat.format(
                    context.getString(R.string.steps_per_minute), mapOf("value" to sample.rate)),
            titleA11y =
                MessageFormat.format(
                    context.getString(R.string.steps_per_minute_long),
                    mapOf("value" to sample.rate)))
    }

    private fun formatRange(@StringRes res: Int, samples: List<StepsCadenceRecordSample>): String {
        return if (samples.isEmpty()) {
            context.getString(R.string.no_data)
        } else {
            val avrStepsCadence = samples.sumOf { it.rate } / samples.size
            MessageFormat.format(context.getString(res), mapOf("value" to avrStepsCadence))
        }
    }
}
