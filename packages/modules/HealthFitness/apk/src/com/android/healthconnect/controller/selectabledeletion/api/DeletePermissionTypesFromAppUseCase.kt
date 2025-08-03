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
package com.android.healthconnect.controller.selectabledeletion.api

import android.health.connect.DeleteUsingFiltersRequest
import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.DataOrigin
import com.android.healthconnect.controller.selectabledeletion.DeletionType
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthPermissionToDatatypeMapper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Use case to delete all records from the given permission type (e.g. Steps) written by a given
 * app.
 */
@Singleton
class DeletePermissionTypesFromAppUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    suspend operator fun invoke(
        deletePermissionTypesFromApp: DeletionType.DeletionTypeHealthPermissionTypesFromApp,
    ) {
        val deleteRequest = DeleteUsingFiltersRequest.Builder()

        deletePermissionTypesFromApp.healthPermissionTypes.map { permissionType ->
            HealthPermissionToDatatypeMapper.getDataTypes(permissionType).map { recordType ->
                deleteRequest.addRecordType(recordType)
            }
        }

        deleteRequest.addDataOrigin(
            DataOrigin.Builder().setPackageName(deletePermissionTypesFromApp.packageName).build())

        withContext(dispatcher) {
            healthConnectManager.deleteRecords(deleteRequest.build(), Runnable::run) {}
        }
    }
}
