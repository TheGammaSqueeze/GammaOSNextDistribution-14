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
package com.android.healthconnect.controller.deletion.api

import android.health.connect.HealthConnectManager
import android.health.connect.RecordIdFilter
import com.android.healthconnect.controller.deletion.DeletionType.DeleteDataEntry
import com.android.healthconnect.controller.service.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
class DeleteEntryUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend fun invoke(deleteEntry: DeleteDataEntry) =
        withContext(dispatcher) {
            val recordIdFilter =
                RecordIdFilter.fromId(deleteEntry.dataType.recordClass, deleteEntry.id)

            healthConnectManager.deleteRecords(listOf(recordIdFilter), Runnable::run) {}
        }
}
