/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.data.appdata

import android.health.connect.HealthConnectManager
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.datatypes.Record
import android.util.Log
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.fromHealthPermissionCategory
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HEALTH_DATA_CATEGORIES
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.healthPermissionTypes
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class AppDataUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /** Returns list of all health categories and permission types to be shown on the HC UI. */
    suspend fun loadAllData(): UseCaseResults<List<PermissionTypesPerCategory>> =
        withContext(dispatcher) {
            try {
                val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
                    suspendCancellableCoroutine { continuation ->
                        healthConnectManager.queryAllRecordTypesInfo(
                            Runnable::run, continuation.asOutcomeReceiver())
                    }
                val categories =
                    HEALTH_DATA_CATEGORIES.map {
                        PermissionTypesPerCategory(
                            it,
                            getPermissionTypesPerCategory(
                                it, recordTypeInfoMap, packageName = null))
                    }
                UseCaseResults.Success(categories)
            } catch (e: Exception) {
                Log.e("TAG_ERROR", "Loading error ", e)
                UseCaseResults.Failed(e)
            }
        }

    /**
     * Returns list of health categories and permission types written by the given app to be shown
     * on the HC UI.
     */
    suspend fun loadAppData(packageName: String): UseCaseResults<List<PermissionTypesPerCategory>> =
        withContext(dispatcher) {
            try {
                val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
                    suspendCancellableCoroutine { continuation ->
                        healthConnectManager.queryAllRecordTypesInfo(
                            Runnable::run, continuation.asOutcomeReceiver())
                    }
                val categories =
                    HEALTH_DATA_CATEGORIES.map {
                        PermissionTypesPerCategory(
                            it, getPermissionTypesPerCategory(it, recordTypeInfoMap, packageName))
                    }
                UseCaseResults.Success(categories)
            } catch (e: Exception) {
                UseCaseResults.Failed(e)
            }
        }

    /**
     * Returns those [HealthPermissionType]s that have some data written by the given [packageName]
     * app. If the is no app provided then return all data.
     */
    private fun getPermissionTypesPerCategory(
        category: @HealthDataCategoryInt Int,
        recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse>,
        packageName: String?
    ): List<HealthPermissionType> {
        if (packageName == null) {
            return category.healthPermissionTypes().filter { hasData(it, recordTypeInfoMap) }
        }
        return category.healthPermissionTypes().filter {
            hasDataByApp(it, recordTypeInfoMap, packageName)
        }
    }

    private fun hasData(
        permissionType: HealthPermissionType,
        recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse>,
    ): Boolean =
        recordTypeInfoMap.values.firstOrNull {
            fromHealthPermissionCategory(it.permissionCategory) == permissionType &&
                it.contributingPackages.isNotEmpty()
        } != null

    private fun hasDataByApp(
        permissionType: HealthPermissionType,
        recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse>,
        packageName: String
    ): Boolean =
        recordTypeInfoMap.values.firstOrNull {
            fromHealthPermissionCategory(it.permissionCategory) == permissionType &&
                it.contributingPackages.isNotEmpty() &&
                it.contributingPackages
                    .map { contributingApp -> contributingApp.packageName }
                    .contains(packageName)
        } != null
}

/**
 * Represents Health Category group to be shown in health connect screens.
 *
 * @param category Category id
 * @param data [HealthPermissionType]s within the category that have data written by given app.
 */
data class PermissionTypesPerCategory(
    val category: @HealthDataCategoryInt Int,
    val data: List<HealthPermissionType>
)
