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
package com.android.healthconnect.controller.categories

import android.health.connect.HealthConnectManager
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.datatypes.Record
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HEALTH_DATA_CATEGORIES
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LoadHealthCategoriesUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /** Returns list of health categories to be shown in Health Connect UI. */
    suspend operator fun invoke(): UseCaseResults<List<HealthCategoryUiState>> =
        withContext(dispatcher) {
            try {
                val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
                    suspendCancellableCoroutine { continuation ->
                        healthConnectManager.queryAllRecordTypesInfo(
                            Runnable::run, continuation.asOutcomeReceiver())
                    }
                val categories =
                    HEALTH_DATA_CATEGORIES.map {
                        HealthCategoryUiState(it, hasData(it, recordTypeInfoMap))
                    }
                UseCaseResults.Success(categories)
            } catch (e: Exception) {
                UseCaseResults.Failed(e)
            }
        }

    private fun hasData(
        category: @HealthDataCategoryInt Int,
        recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse>
    ): Boolean {
        return recordTypeInfoMap.values.firstOrNull {
            it.dataCategory == category && it.contributingPackages.isNotEmpty()
        } != null
    }
}

/**
 * Represents Health Category group to be shown in health connect screens.
 *
 * @param category Category id
 * @param hasData represent this category with related data in health connect.
 */
data class HealthCategoryUiState(val category: @HealthDataCategoryInt Int, val hasData: Boolean)
