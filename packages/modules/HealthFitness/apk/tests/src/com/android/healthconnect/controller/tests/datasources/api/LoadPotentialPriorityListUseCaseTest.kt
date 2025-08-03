/**
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.datasources.api

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.datatypes.DataOrigin
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.StepsRecord
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.datasources.api.LoadPotentialPriorityListUseCase
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.permissiontypes.api.LoadPriorityListUseCase
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_3
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock

@ExperimentalCoroutinesApi
@HiltAndroidTest
class LoadPotentialPriorityListUseCaseTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private lateinit var getGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase
    private lateinit var loadPriorityListUseCase: LoadPriorityListUseCase
    private lateinit var loadPotentialPriorityListUseCase: LoadPotentialPriorityListUseCase
    @Inject lateinit var appInfoReader: AppInfoReader

    private val healthPermissionManager: HealthPermissionManager =
        Mockito.mock(HealthPermissionManager::class.java)
    private val healthConnectManager: HealthConnectManager =
        Mockito.mock(HealthConnectManager::class.java)
    private val healthPermissionReader: HealthPermissionReader =
        Mockito.mock(HealthPermissionReader::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        getGrantedHealthPermissionsUseCase =
            GetGrantedHealthPermissionsUseCase(healthPermissionManager)
        loadPriorityListUseCase =
            LoadPriorityListUseCase(healthConnectManager, appInfoReader, Dispatchers.Main)
        loadPotentialPriorityListUseCase =
            LoadPotentialPriorityListUseCase(
                appInfoReader,
                healthConnectManager,
                healthPermissionReader,
                getGrantedHealthPermissionsUseCase,
                loadPriorityListUseCase,
                Dispatchers.Main)
    }

    @Test
    fun getAppsWithData_forActivity_returnsAppsForActivity() = runTest {
        Mockito.doAnswer(prepareQueryAllRecordTypesAnswer())
            .`when`(healthConnectManager)
            .queryAllRecordTypesInfo(any(), any())

        val result = loadPotentialPriorityListUseCase.getAppsWithData(HealthDataCategory.ACTIVITY)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data)
            .isEqualTo(setOf(TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2))
    }

    @Test
    fun getAppsWithData_forSleep_returnsAppsForSleep() = runTest {
        Mockito.doAnswer(prepareQueryAllRecordTypesAnswer())
            .`when`(healthConnectManager)
            .queryAllRecordTypesInfo(any(), any())

        val result = loadPotentialPriorityListUseCase.getAppsWithData(HealthDataCategory.SLEEP)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data)
            .isEqualTo(setOf(TEST_APP_PACKAGE_NAME_2))
    }

    // TODO (b/299920950) Unignore test when we can use mockito-kotlin
    @Test
    @Ignore
    fun getAppsWithWritePermission_forActivity_returnsAppsForActivity() = runTest {
        whenever(healthPermissionReader.getAppsWithHealthPermissions())
            .thenReturn(
                listOf(TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2, TEST_APP_PACKAGE_NAME_3))

        whenever(healthPermissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(
                    HealthPermission(HealthPermissionType.DISTANCE, PermissionsAccessType.WRITE)
                        .toString()))

        whenever(healthPermissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME_2))
            .thenReturn(
                listOf(
                    HealthPermission(HealthPermissionType.SLEEP, PermissionsAccessType.WRITE)
                        .toString()))

        whenever(healthPermissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME_3))
            .thenReturn(
                listOf(
                    HealthPermission(HealthPermissionType.HEART_RATE, PermissionsAccessType.READ)
                        .toString()))

        val result =
            loadPotentialPriorityListUseCase.getAppsWithWritePermission(HealthDataCategory.ACTIVITY)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEqualTo(setOf(TEST_APP_PACKAGE_NAME))
    }

    // TODO (b/299920950) Unignore test when we can use mockito-kotlin
    @Test
    @Ignore
    fun getAppsWithWritePermission_forSleep_returnsAppsForSleep() = runTest {
        whenever(healthPermissionReader.getAppsWithHealthPermissions())
            .thenReturn(
                listOf(TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2, TEST_APP_PACKAGE_NAME_3))
        whenever(healthPermissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(
                    HealthPermission(HealthPermissionType.SLEEP, PermissionsAccessType.READ)
                        .toString()))

        whenever(healthPermissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME_2))
            .thenReturn(
                listOf(
                    HealthPermission(HealthPermissionType.SLEEP, PermissionsAccessType.WRITE)
                        .toString()))

        whenever(healthPermissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME_3))
            .thenReturn(
                listOf(
                    HealthPermission(HealthPermissionType.HEART_RATE, PermissionsAccessType.READ)
                        .toString()))

        val result =
            loadPotentialPriorityListUseCase.getAppsWithWritePermission(HealthDataCategory.SLEEP)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data)
            .isEqualTo(setOf(TEST_APP_PACKAGE_NAME_2))
    }

    private fun getRecordTypeInfoMap(): Map<Class<out Record>, RecordTypeInfoResponse> {
        val map = mutableMapOf<Class<out Record>, RecordTypeInfoResponse>()
        map[StepsRecord::class.java] =
            RecordTypeInfoResponse(
                HealthPermissionType.STEPS.category,
                HealthDataCategory.ACTIVITY,
                listOf(getDataOriginTestApp()))
        map[DistanceRecord::class.java] =
            RecordTypeInfoResponse(
                HealthPermissionType.DISTANCE.category,
                HealthDataCategory.ACTIVITY,
                listOf(getDataOriginTestApp2()))
        map[HeartRateRecord::class.java] =
            RecordTypeInfoResponse(
                HealthPermissionType.HEART_RATE.category,
                HealthDataCategory.VITALS,
                listOf(getDataOriginTestApp3()))
        map[SleepSessionRecord::class.java] =
            RecordTypeInfoResponse(
                HealthPermissionType.SLEEP.category,
                HealthDataCategory.SLEEP,
                listOf(getDataOriginTestApp2()))
        return map
    }

    private fun prepareQueryAllRecordTypesAnswer(): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver =
                args.arguments[1]
                    as OutcomeReceiver<Map<Class<out Record>, RecordTypeInfoResponse>, *>
            receiver.onResult(getRecordTypeInfoMap())
            null
        }
        return answer
    }

    private fun getDataOriginTestApp(): DataOrigin =
        DataOrigin.Builder().setPackageName(TEST_APP_PACKAGE_NAME).build()

    private fun getDataOriginTestApp2(): DataOrigin =
        DataOrigin.Builder().setPackageName(TEST_APP_PACKAGE_NAME_2).build()

    private fun getDataOriginTestApp3(): DataOrigin =
        DataOrigin.Builder().setPackageName(TEST_APP_PACKAGE_NAME_3).build()
}
