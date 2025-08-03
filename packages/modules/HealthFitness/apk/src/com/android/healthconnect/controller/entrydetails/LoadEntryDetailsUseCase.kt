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
package com.android.healthconnect.controller.entrydetails

import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingIds
import android.health.connect.ReadRecordsResponse
import android.health.connect.datatypes.Record
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.shared.HealthDataEntryDetailsFormatter
import com.android.healthconnect.controller.dataentries.formatters.shared.HealthDataEntryFormatter
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthPermissionToDatatypeMapper
import com.android.healthconnect.controller.shared.usecase.BaseUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine

class LoadEntryDetailsUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    private val entryFormatter: HealthDataEntryFormatter,
    private val entryDetailsFormatter: HealthDataEntryDetailsFormatter,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : BaseUseCase<LoadDataEntryInput, List<FormattedEntry>>(dispatcher) {

    override suspend fun execute(input: LoadDataEntryInput): List<FormattedEntry> {
        val dataTypes = HealthPermissionToDatatypeMapper.getDataTypes(input.permissionType)
        val results = dataTypes.map { record -> readDataType(record, input.entryId) }.flatten()
        return if (results.isEmpty()) {
            throw IllegalStateException("Record not found!")
        } else if (results.size > 1) {
            throw IllegalStateException("Multiple records found with the same id: ${input.entryId}")
        } else {
            formatEntry(results[0], input.showDataOrigin)
        }
    }

    private suspend fun readDataType(record: Class<out Record>, id: String): List<Record> {
        val filter = ReadRecordsRequestUsingIds.Builder(record).addId(id).build()
        val response =
            suspendCancellableCoroutine<ReadRecordsResponse<*>> { continuation ->
                healthConnectManager.readRecords(
                    filter, Runnable::run, continuation.asOutcomeReceiver())
            }
        return response.records.orEmpty()
    }

    private suspend fun formatEntry(record: Record, showDataOrigin: Boolean): List<FormattedEntry> {
        return buildList {
            // header
            add(entryFormatter.format(record, showDataOrigin))
            // details
            addAll(entryDetailsFormatter.formatDetails(record))
        }
    }
}

data class LoadDataEntryInput(
    val permissionType: HealthPermissionType,
    val entryId: String,
    val showDataOrigin: Boolean
)
