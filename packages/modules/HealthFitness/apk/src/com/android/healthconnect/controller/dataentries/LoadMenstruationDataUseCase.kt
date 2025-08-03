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

package com.android.healthconnect.controller.dataentries

import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.MenstruationFlowRecord
import android.health.connect.datatypes.MenstruationPeriodRecord
import android.health.connect.datatypes.Record
import android.util.Log
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.MenstruationPeriodFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.HealthDataEntryFormatter
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.usecase.BaseUseCase
import java.time.Duration.ofDays
import java.time.Duration.ofHours
import java.time.Duration.ofMinutes
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine

class LoadMenstruationDataUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    private val healthDataEntryFormatter: HealthDataEntryFormatter,
    private val menstruationPeriodFormatter: MenstruationPeriodFormatter,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : BaseUseCase<Instant, List<FormattedEntry>>(dispatcher) {

    companion object {
        private const val TAG = "LoadMenstruationDataUse"
        private val SEARCH_RANGE = ofDays(30)
    }

    override suspend fun execute(input: Instant): List<FormattedEntry> {
        val data = buildList {
            addAll(getMenstruationPeriodRecords(input))
            addAll(getMenstruationFlowRecords(input))
        }
        return data
    }

    private suspend fun getMenstruationPeriodRecords(selectedDate: Instant): List<FormattedEntry> {
        val startDate =
            selectedDate
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
        val end = startDate.plus(ofHours(23)).plus(ofMinutes(59))
        val start = end.minus(SEARCH_RANGE)

        // Special-casing MenstruationPeriod as it spans multiple days and we show it on all these
        // days in the UI (not just the first day).
        // Hardcode max period length to 30 days (completely arbitrary number).
        val timeRange = TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build()
        val filter =
            ReadRecordsRequestUsingFilters.Builder(MenstruationPeriodRecord::class.java)
                .setTimeRangeFilter(timeRange)
                .build()

        val records =
            suspendCancellableCoroutine<ReadRecordsResponse<MenstruationPeriodRecord>> {
                    continuation ->
                    healthConnectManager.readRecords(
                        filter, Runnable::run, continuation.asOutcomeReceiver())
                }
                .records
                .filter { menstruationPeriodRecord ->
                    menstruationPeriodRecord.startTime.isBefore(end) &&
                        (menstruationPeriodRecord.endTime.isAfter(startDate) ||
                            menstruationPeriodRecord.endTime.equals(startDate))
                }

        return records.map { record -> menstruationPeriodFormatter.format(startDate, record) }
    }

    private suspend fun getMenstruationFlowRecords(selectedDate: Instant): List<FormattedEntry> {
        val start =
            selectedDate
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
        val end = start.plus(ofHours(23)).plus(ofMinutes(59))
        val timeRange = TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build()
        val filter =
            ReadRecordsRequestUsingFilters.Builder(MenstruationFlowRecord::class.java)
                .setTimeRangeFilter(timeRange)
                .build()

        val records =
            suspendCancellableCoroutine<ReadRecordsResponse<MenstruationFlowRecord>> { continuation
                    ->
                    healthConnectManager.readRecords(
                        filter, Runnable::run, continuation.asOutcomeReceiver())
                }
                .records

        return records.mapNotNull { record -> getFormatterRecord(record) }
    }

    private suspend fun getFormatterRecord(record: Record): FormattedEntry? {
        return try {
            healthDataEntryFormatter.format(record)
        } catch (ex: Exception) {
            Log.i(TAG, "Failed to format record!")
            null
        }
    }
}
