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
package com.android.healthconnect.controller.data.access

import android.health.connect.HealthConnectManager
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.datatypes.Record
import androidx.core.os.asOutcomeReceiver
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.fromHealthPermissionCategory
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LoadPermissionTypeContributorAppsUseCase
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val healthConnectManager: HealthConnectManager,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : ILoadPermissionTypeContributorAppsUseCase {

    /** Returns a list of [AppMetadata]s that have data in this [HealthPermissionType]. */
    override suspend operator fun invoke(permissionType: HealthPermissionType): List<AppMetadata> =
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
                            fromHealthPermissionCategory(it.permissionCategory) == permissionType &&
                                it.contributingPackages.isNotEmpty()
                        }
                        .map { it.contributingPackages }
                        .flatten()
                packages
                    .map { appInfoReader.getAppMetadata(it.packageName) }
                    .sortedBy { it.appName }
            } catch (e: Exception) {
                emptyList()
            }
        }
}

interface ILoadPermissionTypeContributorAppsUseCase {
    suspend fun invoke(permissionType: HealthPermissionType): List<AppMetadata>
}
