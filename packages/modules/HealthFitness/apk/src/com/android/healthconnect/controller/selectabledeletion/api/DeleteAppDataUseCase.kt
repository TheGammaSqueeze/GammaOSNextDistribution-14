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
import com.android.healthconnect.controller.permissions.api.RevokeAllHealthPermissionsUseCase
import com.android.healthconnect.controller.selectabledeletion.DeletionType
import com.android.healthconnect.controller.service.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Use case to delete all records written by a given app. */
@Singleton
class DeleteAppDataUseCase
@Inject
constructor(
    private val healthConnectManager: HealthConnectManager,
    private val revokeAllHealthPermissionsUseCase: RevokeAllHealthPermissionsUseCase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend fun invoke(
        deleteAppData: DeletionType.DeletionTypeAppData,
        removePermissions: Boolean = false
    ) {
        val deleteRequest = DeleteUsingFiltersRequest.Builder()
        deleteRequest.addDataOrigin(
            DataOrigin.Builder().setPackageName(deleteAppData.packageName).build())
        withContext(dispatcher) {
            healthConnectManager.deleteRecords(deleteRequest.build(), Runnable::run) {}

            if (removePermissions) {
                revokeAllHealthPermissionsUseCase.invoke(deleteAppData.packageName)
            }
        }
    }
}
