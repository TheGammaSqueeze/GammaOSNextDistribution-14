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
package com.android.healthconnect.controller.dataentries

import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.InstantRecord
import android.health.connect.datatypes.IntervalRecord
import android.health.connect.datatypes.Record
import android.util.Log
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.shared.HealthDataEntryFormatter
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthPermissionToDatatypeMapper.getDataTypes
import com.android.healthconnect.controller.shared.usecase.BaseUseCase
import java.time.Duration.ofHours
import java.time.Duration.ofMinutes
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class LoadDataEntriesUseCase
@Inject
constructor(
    private val healthDataEntryFormatter: HealthDataEntryFormatter,
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : BaseUseCase<LoadDataEntriesInput, List<FormattedEntry>>(dispatcher) {

    companion object {
        private const val TAG = "LoadDataEntriesUseCase"
    }

    override suspend fun execute(input: LoadDataEntriesInput): List<FormattedEntry> {
        val timeFilterRange = getTimeFilter(input.selectedDate)
        val dataTypes = getDataTypes(input.permissionType)
        return dataTypes.map { dataType -> readDataType(dataType, timeFilterRange) }.flatten()
    }

    private fun getTimeFilter(selectedDate: Instant): TimeInstantRangeFilter {
        val start =
            selectedDate
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
        val end = start.plus(ofHours(23)).plus(ofMinutes(59))
        return TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build()
    }

    private suspend fun readDataType(
        data: Class<out Record>,
        timeFilterRange: TimeInstantRangeFilter
    ): List<FormattedEntry> {
        val filter =
            ReadRecordsRequestUsingFilters.Builder(data).setTimeRangeFilter(timeFilterRange).build()
        val records =
            suspendCancellableCoroutine<ReadRecordsResponse<*>> { continuation ->
                    healthConnectManager.readRecords(
                        filter, Runnable::run, continuation.asOutcomeReceiver())
                }
                .records
                .sortedByDescending { record -> getStartTime(record) }
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

    private fun getStartTime(record: Record): Instant {
        return when (record) {
            is InstantRecord -> {
                record.time
            }
            is IntervalRecord -> {
                record.startTime
            }
            else -> {
                throw IllegalArgumentException("unsupported record type!")
            }
        }
    }
}

data class LoadDataEntriesInput(
    val permissionType: HealthPermissionType,
    val selectedDate: Instant
)
