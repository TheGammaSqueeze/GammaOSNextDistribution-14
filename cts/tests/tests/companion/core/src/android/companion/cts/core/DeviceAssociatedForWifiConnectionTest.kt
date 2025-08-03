/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.companion.cts.core

import android.Manifest.permission.COMPANION_APPROVE_WIFI_CONNECTIONS
import android.Manifest.permission.MANAGE_COMPANION_DEVICES
import android.companion.cts.common.MAC_ADDRESS_A
import android.os.UserHandle
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test isDeviceAssociatedForWifiConnection api.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:DeviceAssociatedForWifiConnectionTest
 *
 * @see android.companion.CompanionDeviceManager.associate
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class DeviceAssociatedForWifiConnectionTest : CoreTestBase() {

    @Test
    fun test_isDeviceAssociatedForWifiConnection_requiresPermission() {
        targetApp.associate(MAC_ADDRESS_A)

        assertFailsWith(SecurityException::class) {
            cdm.isDeviceAssociatedForWifiConnection(
                targetPackageName, MAC_ADDRESS_A, UserHandle(targetUserId))
        }
    }

    @Test
    fun test_isDeviceAssociatedForWifiConnection_no_association() {
        // Should return false if no COMPANION_APPROVE_WIFI_CONNECTIONS declared and no association
        // is created.
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            assertFalse(cdm.isDeviceAssociatedForWifiConnection(
                targetPackageName, MAC_ADDRESS_A, UserHandle(targetUserId)))
        }

        // Should return true by default if the caller has the COMPANION_APPROVE_WIFI_CONNECTIONS
        // permission.
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES, COMPANION_APPROVE_WIFI_CONNECTIONS) {
            assertTrue(cdm.isDeviceAssociatedForWifiConnection(
                targetPackageName, MAC_ADDRESS_A, UserHandle(targetUserId)))
        }
    }

    @Test
    fun test_isDeviceAssociatedForWifiConnection_no_wifi_permission() {
        targetApp.associate(MAC_ADDRESS_A)
        // Should return true if no COMPANION_APPROVE_WIFI_CONNECTIONS declared but association
        // is created.
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            assertTrue(cdm.isDeviceAssociatedForWifiConnection(
                targetPackageName, MAC_ADDRESS_A, UserHandle(targetUserId)))
        }
    }
}
