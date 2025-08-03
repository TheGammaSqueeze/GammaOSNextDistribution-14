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
import android.health.connect.datatypes.CyclingPedalingCadenceRecord
import android.icu.text.MessageFormat.*
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.SessionDetailsFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Formatter for printing CyclingCadenceRecord data. */
class CyclingPedalingCadenceFormatter
@Inject
constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<CyclingPedalingCadenceRecord>(context),
    SessionDetailsFormatter<CyclingPedalingCadenceRecord> {

    private val timeFormatter = LocalDateTimeFormatter(context)

    override suspend fun formatRecord(
        record: CyclingPedalingCadenceRecord,
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
        record: CyclingPedalingCadenceRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatCadence(R.string.cycling_cadence_series_range, record) { rpm ->
            format(context.getString(R.string.cycling_rpm), mapOf("count" to rpm))
        }
    }

    override suspend fun formatA11yValue(
        record: CyclingPedalingCadenceRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatCadence(R.string.cycling_cadence_series_range_long, record) { rpm ->
            format(context.getString(R.string.cycling_rpm_long), mapOf("count" to rpm))
        }
    }

    override suspend fun formatRecordDetails(
        record: CyclingPedalingCadenceRecord
    ): List<FormattedEntry> {
        return record.samples
            .sortedBy { it.time }
            .map { sample ->
                FormattedEntry.FormattedSessionDetail(
                    uuid = record.metadata.id,
                    header = timeFormatter.formatTime(sample.time),
                    headerA11y = timeFormatter.formatTime(sample.time),
                    title =
                        format(
                            context.getString(R.string.cycling_rpm),
                            mapOf("count" to sample.revolutionsPerMinute)),
                    titleA11y =
                        format(
                            context.getString(R.string.cycling_rpm_long),
                            mapOf("count" to sample.revolutionsPerMinute)))
            }
    }

    private fun formatCadence(
        @StringRes res: Int,
        record: CyclingPedalingCadenceRecord,
        getCadenceString: (rpm: Double) -> String
    ): String {
        if (record.samples.isEmpty()) {
            return context.getString(R.string.no_data)
        }

        val min = record.samples.minOf { it.revolutionsPerMinute }
        val max = record.samples.maxOf { it.revolutionsPerMinute }

        if (min.equals(max)) {
            return getCadenceString(min)
        }

        return context.getString(res, getCadenceString(min), getCadenceString(max))
    }
}
