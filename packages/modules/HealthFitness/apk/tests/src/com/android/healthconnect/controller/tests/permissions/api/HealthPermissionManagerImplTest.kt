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

import android.health.connect.HealthConnectManager
import com.android.healthconnect.controller.permissions.api.HealthPermissionManagerImpl
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class HealthPermissionManagerImplTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val healthConnectManager = Mockito.mock(HealthConnectManager::class.java)

    private lateinit var healthPermissionManager: HealthPermissionManagerImpl

    @Before
    fun setup() {
        hiltRule.inject()
        healthPermissionManager = HealthPermissionManagerImpl(healthConnectManager)
    }

    @Test
    fun getGrantedHealthPermissions_callsHealthConnectManager() {
        healthPermissionManager.getGrantedHealthPermissions("packageName")

        verify(healthConnectManager, times(1)).getGrantedHealthPermissions("packageName")
    }

    @Test
    fun getHealthPermissionsFlags_callsHealthConnectManager() {
        val packageName = "package.name"
        val permissions = listOf("Permission 1", "Permission 2")
        healthPermissionManager.getHealthPermissionsFlags(packageName, permissions)

        verify(healthConnectManager, times(1)).getHealthPermissionsFlags(packageName, permissions)
    }

    @Test
    @Ignore("b/314918667")
    fun setHealthPermissionsUserFixedFlagValue_callsHealthConnectManager() {
        val packageName = "package.name"
        val permissions = listOf("Permission 1", "Permission 2")
        healthPermissionManager.setHealthPermissionsUserFixedFlagValue(
            packageName, permissions, true)

        verify(healthConnectManager, times(1))
            .setHealthPermissionsUserFixedFlagValue(packageName, permissions, true)
    }

    @Test
    fun grantHealthPermission_callsHealthConnectManager() {
        val packageName = "package.name"
        val permission = "Permission 1"
        healthPermissionManager.grantHealthPermission(packageName, permission)

        verify(healthConnectManager, times(1)).grantHealthPermission(packageName, permission)
    }

    @Test
    fun revokeHealthPermission_callsHealthConnectManager() {
        val packageName = "package.name"
        val permission = "Permission 1"
        val reason = ""

        healthPermissionManager.revokeHealthPermission(packageName, permission)

        verify(healthConnectManager, times(1))
            .revokeHealthPermission(packageName, permission, reason)
    }

    @Test
    fun revokeAllHealthPermissions_callsHealthConnectManager() {
        val packageName = "package.name"
        val reason = ""
        healthPermissionManager.revokeAllHealthPermissions(packageName)

        verify(healthConnectManager, times(1)).revokeAllHealthPermissions(packageName, reason)
    }

    @Test
    fun loadStartAccessDate_callsHealthConnectManager() {
        val packageName = "package.name"
        healthPermissionManager.loadStartAccessDate(packageName)

        verify(healthConnectManager, times(1)).getHealthDataHistoricalAccessStartDate(packageName)
    }
}
