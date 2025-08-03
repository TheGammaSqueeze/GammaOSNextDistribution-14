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
import android.health.connect.datatypes.PowerRecord
import android.health.connect.datatypes.PowerRecord.PowerRecordSample
import android.icu.text.MessageFormat
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedSessionDetail
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.SessionDetailsFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Formatter for printing Power series data. */
@Singleton
class PowerFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<PowerRecord>(context), SessionDetailsFormatter<PowerRecord> {

    private val timeFormatter = LocalDateTimeFormatter(context)

    override suspend fun formatRecord(
        record: PowerRecord,
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

    override suspend fun formatValue(
        record: PowerRecord,
        unitPreferences: UnitPreferences
    ): String {
        return format(R.string.watt_format, record.samples)
    }

    override suspend fun formatA11yValue(
        record: PowerRecord,
        unitPreferences: UnitPreferences
    ): String {
        return format(R.string.watt_format_long, record.samples)
    }

    override suspend fun formatRecordDetails(record: PowerRecord): List<FormattedEntry> {
        return record.samples
            .sortedBy { it.time }
            .map { sample -> formatSample(record.metadata.id, sample) }
    }

    private fun format(@StringRes res: Int, samples: List<PowerRecordSample>): String {
        if (samples.isEmpty()) {
            return context.getString(R.string.no_data)
        }
        val avrPower = samples.sumOf { it.power.inWatts } / samples.size
        return MessageFormat.format(context.getString(res), mapOf("value" to avrPower))
    }

    private fun formatSample(id: String, sample: PowerRecordSample): FormattedSessionDetail {
        return FormattedSessionDetail(
            uuid = id,
            header = timeFormatter.formatTime(sample.time),
            headerA11y = timeFormatter.formatTime(sample.time),
            title =
                MessageFormat.format(
                    context.getString(R.string.watt_format),
                    mapOf("value" to sample.power.inWatts)),
            titleA11y =
                MessageFormat.format(
                    context.getString(R.string.watt_format_long),
                    mapOf("value" to sample.power.inWatts)),
        )
    }
}
