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
package com.android.healthconnect.controller.dataentries.formatters.shared

import android.content.Context
import android.health.connect.datatypes.InstantRecord
import android.health.connect.datatypes.IntervalRecord
import android.health.connect.datatypes.Record
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import java.time.Instant

/** Abstract formatter for Record types. This formatter handles header for FormattedEntries. */
abstract class BaseFormatter<T : Record>(private val context: Context) : Formatter<T> {

    private val timeFormatter = LocalDateTimeFormatter(context)
    protected val unitPreferences = UnitPreferences(context)

    override suspend fun format(record: T, appName: String): FormattedEntry {
        return formatRecord(
            record = record,
            header = getHeader(record, appName),
            headerA11y = getHeaderA11y(record, appName),
            unitPreferences = unitPreferences)
    }

    abstract suspend fun formatRecord(
        record: T,
        header: String,
        headerA11y: String,
        unitPreferences: UnitPreferences
    ): FormattedEntry

    protected fun getDataType(record: T): DataType {
        return DataType.values().first { it.recordClass == record::class.java }
    }

    protected fun getStartTime(record: T): Instant {
        return when (record) {
            is IntervalRecord -> record.startTime
            is InstantRecord -> record.time
            else -> throw IllegalArgumentException("${record::class.java} Not supported!")
        }
    }

    private fun getHeader(record: T, appName: String): String {
        if (appName == "")
            return context.getString(
                R.string.data_entry_header_without_source_app, getFormattedTime(record))
        return context.getString(
            R.string.data_entry_header_with_source_app, getFormattedTime(record), appName)
    }

    private fun getHeaderA11y(record: T, appName: String): String {
        if (appName == "")
            return context.getString(
                R.string.data_entry_header_without_source_app, getFormattedTime(record))
        return context.getString(
            R.string.data_entry_header_with_source_app, getFormattedA11yTime(record), appName)
    }

    private fun getFormattedTime(record: T): String {
        return when (record) {
            is IntervalRecord -> timeFormatter.formatTimeRange(record.startTime, record.endTime)
            is InstantRecord -> timeFormatter.formatTime(record.time)
            else -> throw IllegalArgumentException("${record::class.java} Not supported!")
        }
    }

    private fun getFormattedA11yTime(record: T): String {
        return when (record) {
            is IntervalRecord -> timeFormatter.formatTimeRangeA11y(record.startTime, record.endTime)
            is InstantRecord -> timeFormatter.formatTime(record.time)
            else -> throw IllegalArgumentException("${record::class.java} Not supported!")
        }
    }
}
