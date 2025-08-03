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

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingIds
import android.health.connect.ReadRecordsResponse
import android.health.connect.datatypes.ExerciseRoute
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.ExerciseSessionType
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.permissions.api.LoadAccessDateUseCase
import com.android.healthconnect.controller.route.ExerciseRouteViewModel
import com.android.healthconnect.controller.route.LoadExerciseRouteUseCase
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.getMetaData
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Matchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class ExerciseRouteViewModelTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    @Inject lateinit var appInfoReader: AppInfoReader

    var manager: HealthConnectManager = mock(HealthConnectManager::class.java)
    private val healthPermissionManager = mock(HealthPermissionManager::class.java)

    private lateinit var viewModel: ExerciseRouteViewModel
    private lateinit var context: Context

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        viewModel =
            ExerciseRouteViewModel(
                context,
                LoadExerciseRouteUseCase(manager, Dispatchers.Main),
                GetGrantedHealthPermissionsUseCase(healthPermissionManager),
                GetHealthPermissionsFlagsUseCase(healthPermissionManager),
                GrantHealthPermissionUseCase(healthPermissionManager),
                LoadAccessDateUseCase(healthPermissionManager),
                appInfoReader)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun loadExerciseRoute_noSession() = runTest {
        doAnswer(prepareAnswer(listOf()))
            .`when`(manager)
            .readRecords(any(ReadRecordsRequestUsingIds::class.java), any(), any())

        val testObserver = TestObserver<ExerciseRouteViewModel.SessionWithAttribution?>()
        viewModel.exerciseSession.observeForever(testObserver)
        viewModel.getExerciseWithRoute("testId")
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).isEqualTo(null)
    }

    @Test
    fun loadExerciseRoute_noRoute() = runTest {
        val start = Instant.ofEpochMilli(1234567891011)
        val end = start.plusMillis(123456)
        doAnswer(
                prepareAnswer(
                    listOf(
                        ExerciseSessionRecord.Builder(
                                getMetaData(),
                                start,
                                end,
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
                            .build())))
            .`when`(manager)
            .readRecords(any(ReadRecordsRequestUsingIds::class.java), any(), any())

        val testObserver = TestObserver<ExerciseRouteViewModel.SessionWithAttribution?>()
        viewModel.exerciseSession.observeForever(testObserver)
        viewModel.getExerciseWithRoute("testId")
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).isEqualTo(null)
    }

    @Test
    fun loadExerciseRoute_postsRoute() = runTest {
        val start = Instant.ofEpochMilli(1234567891011)
        val end = start.plusMillis(123456)
        val expectedSession =
            ExerciseSessionRecord.Builder(
                    getMetaData(), start, end, ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
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

        val testObserver = TestObserver<ExerciseRouteViewModel.SessionWithAttribution?>()
        viewModel.exerciseSession.observeForever(testObserver)
        viewModel.getExerciseWithRoute("testId")
        advanceUntilIdle()

        val result = testObserver.getLastValue()

        assertThat(result?.session as ExerciseSessionRecord).isEqualTo(expectedSession)
        assertThat(result.appInfo.appName).isEqualTo(TEST_APP_NAME)
        assertThat(result.appInfo.packageName).isEqualTo(TEST_APP_PACKAGE_NAME)
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
