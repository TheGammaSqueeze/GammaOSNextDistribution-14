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
package com.android.healthconnect.controller.tests.permissions.api

import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.permissions.api.LoadAccessDateUseCase
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any

@HiltAndroidTest
class LoadAccessDateUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val healthPermissionManager = Mockito.mock(HealthPermissionManager::class.java)

    private lateinit var loadAccessDateUseCase: LoadAccessDateUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        loadAccessDateUseCase = LoadAccessDateUseCase(healthPermissionManager)
    }

    @Test
    fun loadAccessDate_callsHealthPermissionManager() {
        val expected = Instant.parse("2023-04-16T12:00:00Z")
        whenever(healthPermissionManager.loadStartAccessDate(any())).thenReturn(expected)

        val result = loadAccessDateUseCase.invoke("package.name")
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun loadAccessDate_whenHealthPermissionManagerFails_returnsNull() {
        whenever(healthPermissionManager.loadStartAccessDate(any()))
            .thenThrow(RuntimeException("Exception"))

        val result = loadAccessDateUseCase.invoke("package.name")
        assertThat(result).isNull()
    }

    @Test
    fun loadAccessDate_whenPackageNameNull_returnsNull() {
        val result = loadAccessDateUseCase.invoke(null)
        assertThat(result).isNull()
    }
}
