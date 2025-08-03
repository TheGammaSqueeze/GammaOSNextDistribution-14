/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.permissions.connectedapps

import com.android.healthconnect.controller.permissions.app.HealthPermissionStatus
import com.android.healthconnect.controller.permissions.app.LoadAppPermissionsStatusUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.di.FakeGetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@HiltAndroidTest
class LoadAppPermissionsStatusUseCaseTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var loadAppPermissionsStatusUseCase: LoadAppPermissionsStatusUseCase
    private val loadGrantedHealthPermissionsUseCase = FakeGetGrantedHealthPermissionsUseCase()
    private val healthPermissionReader: HealthPermissionReader = mock()

    @Before
    fun setup() {
        hiltRule.inject()
        loadAppPermissionsStatusUseCase =
            LoadAppPermissionsStatusUseCase(
                loadGrantedHealthPermissionsUseCase, healthPermissionReader, Dispatchers.Main)
    }

    @Test
    fun allGrantedPermissionsDeclared_returnsAllPermissions() = runTest {
        val readExercisePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.READ)
        val writeExercisePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.WRITE)

        loadGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME, listOf(readExercisePermission.toString()))
        whenever(healthPermissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf(readExercisePermission, writeExercisePermission))

        val result = loadAppPermissionsStatusUseCase.invoke(TEST_APP_PACKAGE_NAME)
        assertThat(result)
            .containsExactlyElementsIn(
                listOf(
                    HealthPermissionStatus(readExercisePermission, true),
                    HealthPermissionStatus(writeExercisePermission, false)))
    }

    @Test
    fun doesNotReturn_grantedButNotDeclaredPermission() = runTest {
        val readExercisePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.READ)
        val writeExercisePermission =
            HealthPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.WRITE)

        loadGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME,
            listOf(readExercisePermission.toString(), writeExercisePermission.toString()))
        whenever(healthPermissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf(writeExercisePermission))

        val result = loadAppPermissionsStatusUseCase.invoke(TEST_APP_PACKAGE_NAME)
        assertThat(result)
            .containsExactlyElementsIn(
                listOf(HealthPermissionStatus(writeExercisePermission, true)))
    }
}
