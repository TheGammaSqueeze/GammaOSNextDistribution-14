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
package com.android.healthconnect.controller.permissiontypes.api

import android.health.connect.FetchDataOriginsPriorityOrderResponse
import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.usecase.BaseUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class LoadPriorityListUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    private val appInfoReader: AppInfoReader,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : BaseUseCase<@HealthDataCategoryInt Int, List<AppMetadata>>(dispatcher), ILoadPriorityListUseCase {

    /** Returns list of [AppMetadata]s for given [HealthDataCategory] in priority order. */
    override suspend fun execute(input: @HealthDataCategoryInt Int): List<AppMetadata> {
        val dataOriginPriorityOrderResponse: FetchDataOriginsPriorityOrderResponse =
            suspendCancellableCoroutine { continuation ->
                healthConnectManager.fetchDataOriginsPriorityOrder(
                    input, Runnable::run, continuation.asOutcomeReceiver())
            }
        return dataOriginPriorityOrderResponse.dataOriginsPriorityOrder.map { dataOrigin ->
            appInfoReader.getAppMetadata(dataOrigin.packageName)
        }
    }
}

interface ILoadPriorityListUseCase {
    suspend fun invoke(input: @HealthDataCategoryInt Int) : UseCaseResults<List<AppMetadata>>

    suspend fun execute(input: @HealthDataCategoryInt Int) : List<AppMetadata>
}
