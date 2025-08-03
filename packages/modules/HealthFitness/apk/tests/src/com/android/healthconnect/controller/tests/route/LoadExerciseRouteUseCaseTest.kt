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
package com.android.healthconnect.controller.tests.route

import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingIds
import android.health.connect.ReadRecordsResponse
import android.health.connect.datatypes.ExerciseRoute
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.ExerciseSessionType
import android.health.connect.datatypes.Metadata
import android.os.OutcomeReceiver
import com.android.healthconnect.controller.route.LoadExerciseRouteUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
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
import org.mockito.Matchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@HiltAndroidTest
class LoadExerciseRouteUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var useCase: LoadExerciseRouteUseCase
    var manager: HealthConnectManager = mock(HealthConnectManager::class.java)

    @Captor
    lateinit var listCaptor: ArgumentCaptor<ReadRecordsRequestUsingIds<ExerciseSessionRecord>>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        useCase = LoadExerciseRouteUseCase(manager, Dispatchers.Main)
    }

    @Test
    fun invoke_noSession() = runTest {
        doAnswer(prepareAnswer(listOf<ExerciseSessionRecord>()))
            .`when`(manager)
            .readRecords(any(ReadRecordsRequestUsingIds::class.java), any(), any())

        val result = useCase.invoke("test_id") as UseCaseResults.Success<ExerciseSessionRecord>

        verify(manager, times(1)).readRecords(listCaptor.capture(), any(), any())
        assertThat(listCaptor.value.getRecordIdFilters()[0].id).isEqualTo("test_id")
        assertThat(result.data).isEqualTo(null)
    }

    @Test
    fun invoke_noRouteInSession() = runTest {
        val start = Instant.ofEpochMilli(1234567891011)
        val end = start.plusMillis(123456)
        doAnswer(
                prepareAnswer(
                    listOf(
                        ExerciseSessionRecord.Builder(
                                Metadata.Builder().build(),
                                start,
                                end,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
                            .build())))
            .`when`(manager)
            .readRecords(any(ReadRecordsRequestUsingIds::class.java), any(), any())

        val result = useCase.invoke("test_id") as UseCaseResults.Success<ExerciseSessionRecord>

        verify(manager, times(1)).readRecords(listCaptor.capture(), any(), any())
        assertThat(listCaptor.value.getRecordIdFilters()[0].id).isEqualTo("test_id")
        assertThat(result.data).isEqualTo(null)
    }

    @Test
    fun invoke_returnsRoute() = runTest {
        val start = Instant.ofEpochMilli(1234567891011)
        val end = start.plusMillis(123456)
        val expectedSession =
            ExerciseSessionRecord.Builder(
                    Metadata.Builder().build(),
                    start,
                    end,
                    ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
                .setRoute(
                    ExerciseRoute(
                        listOf(
                            ExerciseRoute.Location.Builder(
                                    start.plusSeconds(12), 52.26019, 21.02268)
                                .build(),
                            ExerciseRoute.Location.Builder(
                                    start.plusSeconds(40), 52.26000, 21.02360)
                                .build())))
                .build()

        doAnswer(prepareAnswer(listOf(expectedSession)))
            .`when`(manager)
            .readRecords(any(ReadRecordsRequestUsingIds::class.java), any(), any())

        val result = useCase.invoke("test_id") as UseCaseResults.Success<ExerciseSessionRecord>

        verify(manager, times(1)).readRecords(listCaptor.capture(), any(), any())
        assertThat(listCaptor.value.getRecordIdFilters()[0].id).isEqualTo("test_id")
        assertThat(result.data).isEqualTo(expectedSession)
    }

    private fun prepareAnswer(
        sessions: List<ExerciseSessionRecord>
    ): (InvocationOnMock) -> List<ExerciseSessionRecord> {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[2] as OutcomeReceiver<Any?, *>
            receiver.onResult(ReadRecordsResponse(sessions, -1))
            sessions
        }
        return answer
    }
}
