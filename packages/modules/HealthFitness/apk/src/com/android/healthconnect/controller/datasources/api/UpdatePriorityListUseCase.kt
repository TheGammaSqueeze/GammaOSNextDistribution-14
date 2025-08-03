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
package com.android.healthconnect.controller.datasources.api

import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.health.connect.UpdateDataOriginPriorityOrderRequest
import android.health.connect.datatypes.DataOrigin
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class UpdatePriorityListUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : IUpdatePriorityListUseCase {

    /** Updates the priority list of the stored [DataOrigin]s for given [HealthDataCategory]. */
    override suspend operator fun invoke(
        priorityList: List<String>,
        category: @HealthDataCategoryInt Int
    ) {
        withContext(dispatcher) {
            val dataOrigins: List<DataOrigin> =
                priorityList
                    .stream()
                    .map { packageName -> DataOrigin.Builder().setPackageName(packageName).build() }
                    .toList()
            healthConnectManager.updateDataOriginPriorityOrder(
                UpdateDataOriginPriorityOrderRequest(dataOrigins, category), Runnable::run) {}
        }
    }
}

interface IUpdatePriorityListUseCase {
    suspend fun invoke(priorityList: List<String>, category: @HealthDataCategoryInt Int)
}
