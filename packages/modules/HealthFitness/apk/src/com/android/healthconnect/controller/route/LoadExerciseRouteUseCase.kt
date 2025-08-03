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
 */
package com.android.healthconnect.controller.route

import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingIds
import android.health.connect.ReadRecordsResponse
import android.health.connect.datatypes.ExerciseSessionRecord
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.usecase.BaseUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
/** Use case loading an exercise route of a session. */
class LoadExerciseRouteUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : BaseUseCase<String, ExerciseSessionRecord?>(dispatcher) {

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") // for readability
    override suspend fun execute(sessionId: String): ExerciseSessionRecord? {
        val request =
            ReadRecordsRequestUsingIds.Builder(ExerciseSessionRecord::class.java)
                .addId(sessionId)
                .build()
        val records: List<ExerciseSessionRecord> =
            suspendCancellableCoroutine<ReadRecordsResponse<ExerciseSessionRecord>> { continuation
                    ->
                    healthConnectManager.readRecords(
                        request, Runnable::run, continuation.asOutcomeReceiver())
                }
                .records
        if (records.isEmpty() || !records[0].hasRoute()) {
            return null
        }
        return records[0]
    }
}
