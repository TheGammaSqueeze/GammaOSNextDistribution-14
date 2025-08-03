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

import android.health.connect.HealthConnectManager
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.HealthDataCategory
import android.health.connect.datatypes.Record
import android.util.Log
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.fromHealthPermissionCategory
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.healthPermissionTypes
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LoadPermissionTypesUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "GetPermissionTypesWithData"
    }

    /** Returns list of available [HealthPermissionType]s within given [HealthDataCategory]. */
    suspend fun invoke(category: @HealthDataCategoryInt Int): List<HealthPermissionType> =
        withContext(dispatcher) {
            try {
                val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
                    suspendCancellableCoroutine { continuation ->
                        healthConnectManager.queryAllRecordTypesInfo(
                            Runnable::run, continuation.asOutcomeReceiver())
                    }
                category.healthPermissionTypes().filter { hasData(it, recordTypeInfoMap) }
            } catch (e: Exception) {
                Log.e(TAG, "GetPermissionTypesWithDataUseCase", e)
                emptyList()
            }
        }

    private fun hasData(
        permissionType: HealthPermissionType,
        recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse>
    ): Boolean =
        recordTypeInfoMap.values.firstOrNull {
            fromHealthPermissionCategory(it.permissionCategory) == permissionType &&
                it.contributingPackages.isNotEmpty()
        } != null
}
