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
 *
 *
 */
package com.android.healthconnect.controller.permissions.app

import com.android.healthconnect.controller.permissions.api.IGetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthPermissionReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class LoadAppPermissionsStatusUseCase
@Inject
constructor(
    private val loadGrantedHealthPermissionsUseCase: IGetGrantedHealthPermissionsUseCase,
    private val healthPermissionReader: HealthPermissionReader,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(packageName: String): List<HealthPermissionStatus> =
        withContext(dispatcher) {
            val permissions = healthPermissionReader.getDeclaredHealthPermissions(packageName)
            val grantedPermissions = loadGrantedHealthPermissionsUseCase(packageName)
            permissions.map { permission ->
                HealthPermissionStatus(
                    permission, grantedPermissions.contains(permission.toString()))
            }
        }
}

data class HealthPermissionStatus(val healthPermission: HealthPermission, val isGranted: Boolean)
