/**
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.autodelete.api

import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.os.OutcomeReceiver
import com.android.healthconnect.controller.autodelete.api.UpdateAutoDeleteUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class UpdateAutoDeleteUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val healthConnectManager: HealthConnectManager =
        Mockito.mock(HealthConnectManager::class.java)

    private lateinit var updateAutoDeleteUseCase: UpdateAutoDeleteUseCase

    @Captor lateinit var captor: ArgumentCaptor<Int>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        hiltRule.inject()
        updateAutoDeleteUseCase = UpdateAutoDeleteUseCase(healthConnectManager, Dispatchers.Main)
    }

    @Test
    fun updateAutoDeleteUseCase_3months_callsManagerWithCorrectArgs() = runTest {
        doAnswer(prepareAnswer())
            .`when`(healthConnectManager)
            .setRecordRetentionPeriodInDays(any(), any(), any())

        val result = updateAutoDeleteUseCase.invoke(3)

        verify(healthConnectManager, times(1))
            .setRecordRetentionPeriodInDays(captor.capture(), any(), any())
        assertThat(captor.value).isEqualTo(90)
        assertThat(result is UseCaseResults.Success)
    }

    @Test
    fun updateAutoDeleteUseCase_18months_callsManagerWithCorrectArgs() = runTest {
        doAnswer(prepareAnswer())
            .`when`(healthConnectManager)
            .setRecordRetentionPeriodInDays(any(), any(), any())

        val result = updateAutoDeleteUseCase.invoke(18)

        verify(healthConnectManager, times(1))
            .setRecordRetentionPeriodInDays(captor.capture(), any(), any())
        assertThat(captor.value).isEqualTo(540)
        assertThat(result is UseCaseResults.Success)
    }

    @Test
    fun updateAutoDeleteUseCase_0months_callsManagerWithCorrectArgs() = runTest {
        doAnswer(prepareAnswer())
            .`when`(healthConnectManager)
            .setRecordRetentionPeriodInDays(any(), any(), any())

        val result = updateAutoDeleteUseCase.invoke(0)

        verify(healthConnectManager, times(1))
            .setRecordRetentionPeriodInDays(captor.capture(), any(), any())
        assertThat(captor.value).isEqualTo(0)
        assertThat(result is UseCaseResults.Success)
    }

    @Test
    fun updateAutoDeleteUseCase_whenSetRecordRetentionFails_returnsFailure() = runTest {
        whenever(healthConnectManager.setRecordRetentionPeriodInDays(any(), any(), any()))
            .thenThrow(HealthConnectException(HealthConnectException.ERROR_UNKNOWN))

        val result = updateAutoDeleteUseCase.invoke(1)
        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception is HealthConnectException).isTrue()
        Truth.assertThat((result.exception as HealthConnectException).errorCode)
            .isEqualTo(HealthConnectException.ERROR_UNKNOWN)
    }

    private fun prepareAnswer(): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[2] as OutcomeReceiver<*, *>
            receiver.onResult(null)
            null
        }
        return answer
    }
}
