/**
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.deletion.api

import android.health.connect.DeleteUsingFiltersRequest
import android.health.connect.HealthConnectManager
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.DataOrigin
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.deletion.api.DeleteAppDataUseCase
import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.permissions.api.RevokeAllHealthPermissionsUseCase
import com.google.common.truth.Truth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@HiltAndroidTest
@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
class DeleteAppDataUseCaseTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var useCase: DeleteAppDataUseCase

    var dataManager: HealthConnectManager = mock(HealthConnectManager::class.java)
    var permissionManager: HealthPermissionManager = mock(HealthPermissionManager::class.java)

    @Captor lateinit var filtersCaptor: ArgumentCaptor<DeleteUsingFiltersRequest>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        val revokePermissionsUseCase = RevokeAllHealthPermissionsUseCase(permissionManager)
        useCase = DeleteAppDataUseCase(dataManager, revokePermissionsUseCase, Dispatchers.Main)
    }

    @Test
    fun invoke_deleteCategoryData_callsHealthManager() = runTest {
        doAnswer(prepareAnswer())
            .`when`(dataManager)
            .deleteRecords(any(DeleteUsingFiltersRequest::class.java), any(), any())

        val startTime = Instant.now().minusSeconds(10)
        val endTime = Instant.now()

        val deleteAppData =
            DeletionType.DeletionTypeAppData(packageName = "package.name", appName = "APP_NAME")

        useCase.invoke(
            deleteAppData,
            TimeInstantRangeFilter.Builder().setStartTime(startTime).setEndTime(endTime).build())

        verify(dataManager, times(1)).deleteRecords(filtersCaptor.capture(), any(), any())
        Truth.assertThat((filtersCaptor.value.timeRangeFilter as TimeInstantRangeFilter).startTime)
            .isEqualTo(startTime)
        Truth.assertThat((filtersCaptor.value.timeRangeFilter as TimeInstantRangeFilter).endTime)
            .isEqualTo(endTime)
        Truth.assertThat(filtersCaptor.value.dataOrigins)
            .containsExactly(DataOrigin.Builder().setPackageName("package.name").build())
        Truth.assertThat(filtersCaptor.value.recordTypes).isEmpty()
    }

    private fun prepareAnswer(): (InvocationOnMock) -> Nothing? {
        val answer = { _: InvocationOnMock -> null }
        return answer
    }
}
