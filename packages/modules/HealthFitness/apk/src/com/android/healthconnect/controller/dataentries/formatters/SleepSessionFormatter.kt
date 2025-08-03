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
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_AWAKE
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_IN_BED
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_OUT_OF_BED
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_DEEP
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_LIGHT
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_SLEEPING_REM
import android.health.connect.datatypes.SleepSessionRecord.StageType.STAGE_TYPE_UNKNOWN
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedSessionDetail
import com.android.healthconnect.controller.dataentries.formatters.DurationFormatter.formatDurationLong
import com.android.healthconnect.controller.dataentries.formatters.DurationFormatter.formatDurationShort
import com.android.healthconnect.controller.dataentries.formatters.shared.BaseFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.SessionDetailsFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.UnitFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.google.common.annotations.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject

/** Formatter for printing SleepSessionRecord data. */
class SleepSessionFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    BaseFormatter<SleepSessionRecord>(context),
    SessionDetailsFormatter<SleepSessionRecord>,
    UnitFormatter<Long> {

    private val timeFormatter = LocalDateTimeFormatter(context)

    override suspend fun formatRecord(
        record: SleepSessionRecord,
        header: String,
        headerA11y: String,
        unitPreferences: UnitPreferences
    ): FormattedEntry {
        return FormattedEntry.SleepSessionEntry(
            uuid = record.metadata.id,
            header = header,
            headerA11y = headerA11y,
            title = formatValue(record),
            titleA11y = formatA11yValue(record),
            dataType = getDataType(record),
            notes = getNotes(record))
    }

    @VisibleForTesting
    fun formatValue(record: SleepSessionRecord): String {
        return formatSleepSession(record) { duration -> formatDurationShort(context, duration) }
    }

    @VisibleForTesting
    fun formatA11yValue(record: SleepSessionRecord): String {
        return formatSleepSession(record) { duration -> formatDurationLong(context, duration) }
    }

    override fun formatA11yUnit(unit: Long): String {
        return formatDurationLong(context, Duration.ofMillis(unit))
    }

    override fun formatUnit(unit: Long): String {
        return formatDurationShort(context, Duration.ofMillis(unit))
    }

    private fun getNotes(record: SleepSessionRecord): String? {
        return record.notes?.toString()
    }

    private fun formatSleepSession(
        record: SleepSessionRecord,
        formatDuration: (duration: Duration) -> String
    ): String {
        return if (!record.title.isNullOrBlank()) {
            context.getString(R.string.sleep_session_with_one_field, record.title)
        } else {
            val duration = Duration.between(record.startTime, record.endTime)
            context.getString(R.string.sleep_session_default, formatDuration(duration))
        }
    }

    override suspend fun formatRecordDetails(record: SleepSessionRecord): List<FormattedEntry> {
        val sortedStages = record.stages.sortedBy { it.startTime }
        return sortedStages.map { stage -> formatSleepStage(record.metadata.id, stage) }
    }

    private fun formatSleepStage(id: String, stage: SleepSessionRecord.Stage): FormattedEntry {
        return FormattedSessionDetail(
            uuid = id,
            header = timeFormatter.formatTimeRange(stage.startTime, stage.endTime),
            headerA11y = timeFormatter.formatTimeRangeA11y(stage.startTime, stage.endTime),
            title = formatStageType(stage) { duration -> formatDurationShort(context, duration) },
            titleA11y =
                formatStageType(stage) { duration -> formatDurationLong(context, duration) },
        )
    }

    private fun formatStageType(
        stage: SleepSessionRecord.Stage,
        formatDuration: (duration: Duration) -> String
    ): String {
        val stageStringRes =
            when (stage.type) {
                STAGE_TYPE_UNKNOWN -> R.string.sleep_stage_unknown
                STAGE_TYPE_AWAKE -> R.string.sleep_stage_awake
                STAGE_TYPE_AWAKE_IN_BED -> R.string.sleep_stage_awake_in_bed
                STAGE_TYPE_SLEEPING_DEEP -> R.string.sleep_stage_deep
                STAGE_TYPE_SLEEPING_LIGHT -> R.string.sleep_stage_light
                STAGE_TYPE_AWAKE_OUT_OF_BED -> R.string.sleep_stage_out_of_bed
                STAGE_TYPE_SLEEPING_REM -> R.string.sleep_stage_rem
                STAGE_TYPE_SLEEPING -> R.string.sleep_stage_sleeping
                else -> {
                    throw IllegalArgumentException("Unrecognised sleep stage.")
                }
            }
        val stageString = context.getString(stageStringRes)
        val duration = Duration.between(stage.startTime, stage.endTime)
        return context.getString(
            R.string.sleep_stage_default, formatDuration(duration), stageString)
    }
}
