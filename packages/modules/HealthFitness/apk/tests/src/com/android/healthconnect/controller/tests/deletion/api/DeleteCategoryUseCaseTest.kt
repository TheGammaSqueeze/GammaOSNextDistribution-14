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
import android.health.connect.HealthDataCategory
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.ActiveCaloriesBurnedRecord
import android.health.connect.datatypes.CyclingPedalingCadenceRecord
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.ElevationGainedRecord
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.FloorsClimbedRecord
import android.health.connect.datatypes.PowerRecord
import android.health.connect.datatypes.SpeedRecord
import android.health.connect.datatypes.StepsCadenceRecord
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.TotalCaloriesBurnedRecord
import android.health.connect.datatypes.Vo2MaxRecord
import android.health.connect.datatypes.WheelchairPushesRecord
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.deletion.api.DeleteCategoryUseCase
import com.google.common.truth.Truth.assertThat
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
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@HiltAndroidTest
@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
class DeleteCategoryUseCaseTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var useCase: DeleteCategoryUseCase
    var manager: HealthConnectManager = mock(HealthConnectManager::class.java)

    @Captor lateinit var filtersCaptor: ArgumentCaptor<DeleteUsingFiltersRequest>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        useCase = DeleteCategoryUseCase(manager, Dispatchers.Main)
    }

    @Test
    fun invoke_deleteCategoryData_callsHealthManager() = runTest {
        doAnswer(prepareAnswer())
            .`when`(manager)
            .deleteRecords(any(DeleteUsingFiltersRequest::class.java), any(), any())

        val startTime = Instant.now().minusSeconds(10)
        val endTime = Instant.now()

        val deleteCategory = DeletionType.DeletionTypeCategoryData(HealthDataCategory.ACTIVITY)

        useCase.invoke(
            deleteCategory,
            TimeInstantRangeFilter.Builder().setStartTime(startTime).setEndTime(endTime).build())

        Mockito.verify(manager, Mockito.times(1))
            .deleteRecords(filtersCaptor.capture(), any(), any())

        assertThat((filtersCaptor.value.timeRangeFilter as TimeInstantRangeFilter).startTime)
            .isEqualTo(startTime)
        assertThat((filtersCaptor.value.timeRangeFilter as TimeInstantRangeFilter).endTime)
            .isEqualTo(endTime)
        assertThat(filtersCaptor.value.dataOrigins).isEmpty()
        assertThat(filtersCaptor.value.recordTypes)
            .containsExactly(
                TotalCaloriesBurnedRecord::class.java,
                ActiveCaloriesBurnedRecord::class.java,
                DistanceRecord::class.java,
                StepsRecord::class.java,
                StepsCadenceRecord::class.java,
                SpeedRecord::class.java,
                PowerRecord::class.java,
                WheelchairPushesRecord::class.java,
                FloorsClimbedRecord::class.java,
                ElevationGainedRecord::class.java,
                Vo2MaxRecord::class.java,
                CyclingPedalingCadenceRecord::class.java,
                ExerciseSessionRecord::class.java)
    }

    private fun prepareAnswer(): (InvocationOnMock) -> Nothing? {
        return { _: InvocationOnMock -> null }
    }
}
