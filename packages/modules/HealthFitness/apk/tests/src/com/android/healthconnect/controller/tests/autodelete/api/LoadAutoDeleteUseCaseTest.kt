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
import com.android.healthconnect.controller.autodelete.api.LoadAutoDeleteUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class LoadAutoDeleteUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val healthConnectManager: HealthConnectManager =
        Mockito.mock(HealthConnectManager::class.java)

    private lateinit var loadAutoDeleteUseCase: LoadAutoDeleteUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        loadAutoDeleteUseCase = LoadAutoDeleteUseCase(healthConnectManager, Dispatchers.Main)
    }

    @Test
    fun loadAutoDeleteUseCase_whenRecordRetention90days_returns3months() = runTest {
        whenever(healthConnectManager.recordRetentionPeriodInDays).thenReturn(90)

        val result = loadAutoDeleteUseCase.invoke()
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEqualTo(3)
    }

    @Test
    fun loadAutoDeleteUseCase_whenRecordRetention540days_returns18months() = runTest {
        whenever(healthConnectManager.recordRetentionPeriodInDays).thenReturn(540)

        val result = loadAutoDeleteUseCase.invoke()
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEqualTo(18)
    }

    @Test
    fun loadAutoDeleteUseCase_whenRecordRetention0days_returns0months() = runTest {
        whenever(healthConnectManager.recordRetentionPeriodInDays).thenReturn(0)

        val result = loadAutoDeleteUseCase.invoke()
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data).isEqualTo(0)
    }

    @Test
    fun loadAutoDeleteUseCase_whenRecordRetentionFails_returnsFailure() = runTest {
        whenever(healthConnectManager.recordRetentionPeriodInDays)
            .thenThrow(HealthConnectException(HealthConnectException.ERROR_UNKNOWN))

        val result = loadAutoDeleteUseCase.invoke()
        assertThat(result is UseCaseResults.Failed).isTrue()
        assertThat((result as UseCaseResults.Failed).exception is HealthConnectException).isTrue()
        assertThat((result.exception as HealthConnectException).errorCode)
            .isEqualTo(HealthConnectException.ERROR_UNKNOWN)
    }
}
