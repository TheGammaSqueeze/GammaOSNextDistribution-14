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
package com.android.healthconnect.controller.tests.permissions.api

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.HEIGHT
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType.WRITE
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify

class GrantHealthPermissionUseCaseTest {
    private lateinit var context: Context
    private lateinit var useCase: GrantHealthPermissionUseCase
    private val healthPermissionManager: HealthPermissionManager =
        Mockito.mock(HealthPermissionManager::class.java)

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        useCase = GrantHealthPermissionUseCase(healthPermissionManager)
    }

    @Test
    fun invoke_callsHealthPermissionManager() {
        useCase.invoke("TEST_APP", HealthPermission(HEIGHT, WRITE).toString())

        verify(healthPermissionManager)
            .grantHealthPermission("TEST_APP", "android.permission.health.WRITE_HEIGHT")
    }
}
