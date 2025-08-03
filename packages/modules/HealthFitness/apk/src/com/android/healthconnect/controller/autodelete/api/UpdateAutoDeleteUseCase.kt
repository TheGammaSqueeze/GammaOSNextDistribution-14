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
package com.android.healthconnect.controller.autodelete.api

import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class UpdateAutoDeleteUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    companion object {
        private const val DAYS_IN_MONTH = 30
    }

    /** Updates the stored auto-delete range. */
    suspend operator fun invoke(numberOfMonths: Int): UseCaseResults<Unit> =
        withContext(dispatcher) {
            try {
                healthConnectManager.setRecordRetentionPeriodInDays(
                    numberOfMonths * DAYS_IN_MONTH, Runnable::run) {}
                UseCaseResults.Success(Unit)
            } catch (ex: HealthConnectException) {
                UseCaseResults.Failed(ex)
            }
        }
}
