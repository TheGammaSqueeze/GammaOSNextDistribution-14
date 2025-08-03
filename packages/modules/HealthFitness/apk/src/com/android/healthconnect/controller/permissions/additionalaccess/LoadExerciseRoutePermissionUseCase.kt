/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.android.healthconnect.controller.permissions.additionalaccess

import android.content.pm.PackageManager
import android.health.connect.HealthPermissions.READ_EXERCISE
import android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.usecase.BaseUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Singleton
class LoadExerciseRoutePermissionUseCase
@Inject
constructor(
    private val loadDeclaredHealthPermissionUseCase: LoadDeclaredHealthPermissionUseCase,
    private val getHealthPermissionsFlagsUseCase: GetHealthPermissionsFlagsUseCase,
    private val getGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : BaseUseCase<String, ExerciseRouteState>(dispatcher) {

    override suspend fun execute(input: String): ExerciseRouteState {
        val grantedPermissions = getGrantedHealthPermissionsUseCase(input)
        val appPermissions = loadDeclaredHealthPermissionUseCase(input)
        val permissionFlags =
            getHealthPermissionsFlagsUseCase(input, listOf(READ_EXERCISE_ROUTES, READ_EXERCISE))
        return ExerciseRouteState(
            exerciseRoutePermissionState =
                getPermissionState(
                    READ_EXERCISE_ROUTES, grantedPermissions, appPermissions, permissionFlags),
            exercisePermissionState =
                getPermissionState(
                    READ_EXERCISE, grantedPermissions, appPermissions, permissionFlags))
    }

    private fun getPermissionState(
        permission: String,
        grantedPermissions: List<String>,
        appPermissions: List<String>,
        permissionFlags: Map<String, Int>
    ): PermissionUiState {

        if (grantedPermissions.contains(permission)) {
            return PermissionUiState.ALWAYS_ALLOW
        }

        if (!appPermissions.contains(permission)) {
            return PermissionUiState.NOT_DECLARED
        }

        val flag = permissionFlags[permission] ?: return PermissionUiState.NOT_DECLARED
        if (flag.and(PackageManager.FLAG_PERMISSION_USER_FIXED) != 0) {
            return PermissionUiState.NEVER_ALLOW
        }

        return PermissionUiState.ASK_EVERY_TIME
    }
}

data class ExerciseRouteState(
    val exerciseRoutePermissionState: PermissionUiState,
    val exercisePermissionState: PermissionUiState
)

enum class PermissionUiState {
    NOT_DECLARED,
    ASK_EVERY_TIME,
    ALWAYS_ALLOW,
    NEVER_ALLOW
}
