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
import android.health.connect.datatypes.SpeedRecord
import android.health.connect.datatypes.SpeedRecord.SpeedRecordSample
import android.icu.text.MessageFormat
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedSessionDetail
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.SessionDetailsFormatter
import com.android.healthconnect.controller.dataentries.units.DistanceUnit.KILOMETERS
import com.android.healthconnect.controller.dataentries.units.DistanceUnit.MILES
import com.android.healthconnect.controller.dataentries.units.SpeedConverter.convertToDistancePerHour
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Formatter for printing Speed series data. */
class SpeedFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<SpeedRecord>(context), SessionDetailsFormatter<SpeedRecord> {

    private val timeFormatter = LocalDateTimeFormatter(context)

    override suspend fun formatRecord(
        record: SpeedRecord,
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
        record: SpeedRecord,
        unitPreferences: UnitPreferences
    ): String {
        val res = getUnitRes(unitPreferences)
        return formatRecord(res, record.samples, unitPreferences)
    }

    override suspend fun formatA11yValue(
        record: SpeedRecord,
        unitPreferences: UnitPreferences
    ): String {
        val res = getA11yUnitRes(unitPreferences)
        return formatRecord(res, record.samples, unitPreferences)
    }

    override suspend fun formatRecordDetails(record: SpeedRecord): List<FormattedEntry> {
        return record.samples
            .sortedBy { it.time }
            .map { formatSample(record.metadata.id, it, unitPreferences) }
    }

    private fun formatSample(
        id: String,
        sample: SpeedRecordSample,
        unitPreferences: UnitPreferences
    ): FormattedSessionDetail {
        return FormattedSessionDetail(
            uuid = id,
            header = timeFormatter.formatTime(sample.time),
            headerA11y = timeFormatter.formatTime(sample.time),
            title =
                formatSpeedValue(
                    getUnitRes(unitPreferences), sample.speed.inMetersPerSecond, unitPreferences),
            titleA11y =
                formatSpeedValue(
                    getA11yUnitRes(unitPreferences),
                    sample.speed.inMetersPerSecond,
                    unitPreferences))
    }

    private fun formatRecord(
        @StringRes res: Int,
        samples: List<SpeedRecordSample>,
        unitPreferences: UnitPreferences
    ): String {
        if (samples.isEmpty()) {
            return context.getString(R.string.no_data)
        }
        val averageSpeed = samples.sumOf { it.speed.inMetersPerSecond } / samples.size
        return formatSpeedValue(res, averageSpeed, unitPreferences)
    }

    private fun formatSpeedValue(
        @StringRes res: Int,
        speed: Double,
        unitPreferences: UnitPreferences
    ): String {
        val speedWithUnit = convertToDistancePerHour(unitPreferences.getDistanceUnit(), speed)
        return MessageFormat.format(context.getString(res), mapOf("value" to speedWithUnit))
    }

    private fun getUnitRes(unitPreferences: UnitPreferences): Int {
        return when (unitPreferences.getDistanceUnit()) {
            MILES -> R.string.velocity_speed_miles
            KILOMETERS -> R.string.velocity_speed_km
        }
    }

    private fun getA11yUnitRes(unitPreferences: UnitPreferences): Int {
        return when (unitPreferences.getDistanceUnit()) {
            MILES -> R.string.velocity_speed_miles_long
            KILOMETERS -> R.string.velocity_speed_km_long
        }
    }
}
