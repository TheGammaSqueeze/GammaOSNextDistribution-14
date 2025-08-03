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

package com.android.healthconnect.controller.tests.permissions.additionalaccess

import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.health.connect.HealthPermissions.READ_EXERCISE
import android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRouteState
import com.android.healthconnect.controller.permissions.additionalaccess.LoadDeclaredHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.LoadExerciseRoutePermissionUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ALWAYS_ALLOW
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ASK_EVERY_TIME
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.NEVER_ALLOW
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.NOT_DECLARED
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.safeEq
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

@HiltAndroidTest
class LoadExerciseRoutePermissionUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val loadDeclaredHealthPermissionUseCase: LoadDeclaredHealthPermissionUseCase = mock()
    @BindValue val getHealthPermissionsFlagsUseCase: GetHealthPermissionsFlagsUseCase = mock()
    @BindValue val getGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase = mock()

    @Inject lateinit var useCase: LoadExerciseRoutePermissionUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        whenever(getGrantedHealthPermissionsUseCase.invoke(safeEq(TEST_APP_PACKAGE_NAME))).then {
            emptyList<String>()
        }
        whenever(loadDeclaredHealthPermissionUseCase.invoke(safeEq(TEST_APP_PACKAGE_NAME))).then {
            listOf(READ_EXERCISE_ROUTES, READ_EXERCISE)
        }
        whenever(getHealthPermissionsFlagsUseCase.invoke(any(), any())).then {
            mapOf(
                READ_EXERCISE_ROUTES to FLAG_PERMISSION_USER_SET,
                READ_EXERCISE to FLAG_PERMISSION_USER_SET)
        }
    }

    @Test
    fun execute_exerciseRoutePermissionGranted_returnGrantedState() = runTest {
        whenever(getGrantedHealthPermissionsUseCase.invoke(safeEq(TEST_APP_PACKAGE_NAME))).then {
            listOf(READ_EXERCISE_ROUTES)
        }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        val expected =
            ExerciseRouteState(
                exerciseRoutePermissionState = ALWAYS_ALLOW,
                exercisePermissionState = ASK_EVERY_TIME)
        assertThat(state).isEqualTo(UseCaseResults.Success(expected))
    }

    @Test
    fun execute_exerciseRoutePermissionDeclared_returnDeclaredState() = runTest {
        whenever(loadDeclaredHealthPermissionUseCase.invoke(safeEq(TEST_APP_PACKAGE_NAME))).then {
            listOf(READ_EXERCISE_ROUTES, READ_EXERCISE)
        }

        val state = useCase.invoke(safeEq(TEST_APP_PACKAGE_NAME))

        val expected =
            ExerciseRouteState(
                exerciseRoutePermissionState = ASK_EVERY_TIME,
                exercisePermissionState = ASK_EVERY_TIME)
        assertThat(state).isEqualTo(UseCaseResults.Success(expected))
    }

    @Test
    fun execute_exerciseRoutePermissionRevoked_returnRevokedState() = runTest {
        val flags = mapOf(READ_EXERCISE_ROUTES to PackageManager.FLAG_PERMISSION_USER_FIXED)
        whenever(getHealthPermissionsFlagsUseCase.invoke(any(), any())).then { flags }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)
        val expected =
            ExerciseRouteState(
                exerciseRoutePermissionState = NEVER_ALLOW, exercisePermissionState = NOT_DECLARED)
        assertThat(state).isEqualTo(UseCaseResults.Success(expected))
    }

    @Test
    fun execute_emptyFlags_returnNotDeclaredState() = runTest {
        whenever(getHealthPermissionsFlagsUseCase.invoke(any(), any())).then {
            mapOf<String, Int>()
        }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        val expected =
            ExerciseRouteState(
                exerciseRoutePermissionState = NOT_DECLARED, exercisePermissionState = NOT_DECLARED)
        assertThat(state).isEqualTo(UseCaseResults.Success(expected))
    }

    @Test
    fun execute_noFlagsForExerciseRoutes_returnNotDeclaredState() = runTest {
        whenever(getHealthPermissionsFlagsUseCase.invoke(any(), any())).then {
            emptyMap<String, Int>()
        }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        val expected =
            ExerciseRouteState(
                exerciseRoutePermissionState = NOT_DECLARED, exercisePermissionState = NOT_DECLARED)
        assertThat(state).isEqualTo(UseCaseResults.Success(expected))
    }

    @Test
    fun execute_exerciseRoutePermissionNotDeclared_returnNotDeclaredState() = runTest {
        whenever(loadDeclaredHealthPermissionUseCase.invoke(TEST_APP_PACKAGE_NAME)).then {
            listOf<String>()
        }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        val expected =
            ExerciseRouteState(
                exerciseRoutePermissionState = NOT_DECLARED, exercisePermissionState = NOT_DECLARED)
        assertThat(state).isEqualTo(UseCaseResults.Success(expected))
    }

    @Test
    fun execute_onException_returnFailedResults() = runTest {
        val ex = IllegalStateException()
        whenever(loadDeclaredHealthPermissionUseCase.invoke(TEST_APP_PACKAGE_NAME)).then {
            throw ex
        }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        assertThat(state).isEqualTo(UseCaseResults.Failed(ex))
    }
}
