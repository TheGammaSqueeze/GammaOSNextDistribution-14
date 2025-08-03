/*
 * Copyright (C) 2022 The Android Open Source Project
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
class LoadAutoDeleteUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    companion object {
        private const val DAYS_IN_MONTH = 30.0
    }

    /** Returns the number of months that is the auto-delete range. */
    suspend operator fun invoke(): UseCaseResults<Int> =
        withContext(dispatcher) {
            try {
                val retentionInMonths =
                    healthConnectManager.recordRetentionPeriodInDays / DAYS_IN_MONTH
                UseCaseResults.Success(retentionInMonths.toInt())
            } catch (ex: HealthConnectException) {
                UseCaseResults.Failed(ex)
            }
        }
}
