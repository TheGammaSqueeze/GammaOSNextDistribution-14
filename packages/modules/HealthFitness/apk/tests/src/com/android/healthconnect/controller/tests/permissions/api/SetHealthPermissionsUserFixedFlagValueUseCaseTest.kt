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

package com.android.healthconnect.controller.tests.permissions.api

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.permissions.api.SetHealthPermissionsUserFixedFlagValueUseCase
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify

class SetHealthPermissionsUserFixedFlagValueUseCaseTest {
    private lateinit var context: Context
    private lateinit var useCase: SetHealthPermissionsUserFixedFlagValueUseCase
    private val healthPermissionManager: HealthPermissionManager =
        Mockito.mock(HealthPermissionManager::class.java)

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        useCase = SetHealthPermissionsUserFixedFlagValueUseCase(healthPermissionManager)
    }

    @Test
    fun invoke_setFlag_callsHealthPermissionManager() {
        useCase.invoke("TEST_APP", listOf("PERMISSION_1", "PERMISSION_2", "PERMISSION_3"), true)

        verify(healthPermissionManager)
            .setHealthPermissionsUserFixedFlagValue(
                "TEST_APP", listOf("PERMISSION_1", "PERMISSION_2", "PERMISSION_3"), true)
    }

    @Test
    fun invoke_clearFlag_callsHealthPermissionManager() {
        useCase.invoke("TEST_APP", listOf("PERMISSION_1", "PERMISSION_2"), false)

        verify(healthPermissionManager)
            .setHealthPermissionsUserFixedFlagValue(
                "TEST_APP", listOf("PERMISSION_1", "PERMISSION_2"), false)
    }
}
