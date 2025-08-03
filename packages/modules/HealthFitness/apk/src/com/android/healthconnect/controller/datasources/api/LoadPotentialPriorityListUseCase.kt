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
package com.android.healthconnect.controller.datasources.api

import android.health.connect.RecordTypeInfoResponse
import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.Record
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.healthPermissionTypes
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.permissiontypes.api.LoadPriorityListUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults

@Singleton
class LoadPotentialPriorityListUseCase
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val healthConnectManager: HealthConnectManager,
    private val healthPermissionReader: HealthPermissionReader,
    private val loadGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase,
    private val loadPriorityListUseCase: LoadPriorityListUseCase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : ILoadPotentialPriorityListUseCase {

    private val TAG = "LoadAppSourcesUseCase"

    /**
     * Returns a list of unique [AppMetadata]s that are potential priority list candidates.
     */
    override suspend operator fun invoke(category: @HealthDataCategoryInt Int): UseCaseResults<List<AppMetadata>> =
        withContext(dispatcher) {
            val appsWithDataResult = getAppsWithData(category)
            val appsWithWritePermissionResult = getAppsWithWritePermission(category)
            val appsOnPriorityListResult = loadPriorityListUseCase.invoke(category)

            // Propagate error if any calls fail
            if (appsWithDataResult is UseCaseResults.Failed) {
                UseCaseResults.Failed(appsWithDataResult.exception)
            } else if (appsWithWritePermissionResult is UseCaseResults.Failed) {
                UseCaseResults.Failed(appsWithWritePermissionResult.exception)
            } else if (appsOnPriorityListResult is UseCaseResults.Failed) {
                UseCaseResults.Failed(appsOnPriorityListResult.exception)
            } else {
                val appsWithData = (appsWithDataResult as UseCaseResults.Success).data
                val appsWithWritePermission = (appsWithWritePermissionResult as UseCaseResults.Success).data
                val appsOnPriorityList = (appsOnPriorityListResult as UseCaseResults.Success).data
                    .map { it.packageName }.toSet()

                val potentialPriorityListApps =
                    appsWithData.union(appsWithWritePermission)
                        .minus(appsOnPriorityList)
                        .toList()
                        .map { appInfoReader.getAppMetadata(it) }

                UseCaseResults.Success(potentialPriorityListApps)
            }

        }

    /**
     * Returns a list of unique packageNames that have data in this [HealthDataCategory].
     */
    @VisibleForTesting
    suspend fun getAppsWithData(category: @HealthDataCategoryInt Int): UseCaseResults<Set<String>> =
        withContext(dispatcher) {
            try {
                val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
                    suspendCancellableCoroutine { continuation ->
                        healthConnectManager.queryAllRecordTypesInfo(
                            Runnable::run, continuation.asOutcomeReceiver())
                    }
                val packages =
                    recordTypeInfoMap.values
                        .filter {
                            it.contributingPackages.isNotEmpty() && it.dataCategory == category
                        }
                        .map { it.contributingPackages }
                        .flatten()
                UseCaseResults.Success(packages
                    .map { it.packageName }
                    .toSet())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get apps with data ", e)
                UseCaseResults.Failed(e)
            }
        }


    /** Returns a set of packageNames which have at least one WRITE permission in this [HealthDataCategory] **/
    @VisibleForTesting
    suspend fun getAppsWithWritePermission(category: @HealthDataCategoryInt Int): UseCaseResults<Set<String>> =
        withContext(dispatcher) {
            try {
                val writeAppPackageNameSet: MutableSet<String> = mutableSetOf()
                val appsWithHealthPermissions: List<String> =
                    healthPermissionReader.getAppsWithHealthPermissions()
                val healthPermissionsInCategory: List<String> =
                    category.healthPermissionTypes().map {
                            healthPermissionType ->
                        HealthPermission(
                            healthPermissionType,
                            PermissionsAccessType.WRITE).toString()
                    }

                appsWithHealthPermissions.forEach {packageName ->
                    val permissionsPerPackage: List<String> =
                        loadGrantedHealthPermissionsUseCase(packageName)

                    // Apps that can WRITE the given HealthDataCategory
                    if (healthPermissionsInCategory.any { permissionsPerPackage.contains(it) }) {
                        writeAppPackageNameSet.add(packageName)
                    }
                }

                UseCaseResults.Success(writeAppPackageNameSet)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get apps with write permission ", e)
                UseCaseResults.Failed(e)
            }
        }
}

interface ILoadPotentialPriorityListUseCase {
    suspend fun invoke(category: @HealthDataCategoryInt Int): UseCaseResults<List<AppMetadata>>
}