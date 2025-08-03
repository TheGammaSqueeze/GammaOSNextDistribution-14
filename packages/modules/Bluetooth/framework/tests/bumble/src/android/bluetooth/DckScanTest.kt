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

package android.bluetooth

import android.bluetooth.DckTestRule.LeScanResult
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/** DCK LE Scan Tests */
@RunWith(Parameterized::class)
class DckScanTest(
    isRemoteAdvertisingWithUuid: Boolean,
    isBluetoothToggled: Boolean,
    isGattConnected: Boolean,
) {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // Gives shell permissions during the test.
    @Rule(order = 0) @JvmField val shellPermissionRule = AdoptShellPermissionsRule()

    // Setup a Bumble Pandora device for the duration of the test.
    // Acting as a Pandora client, it can be interacted with through the Pandora APIs.
    @Rule(order = 1) @JvmField val bumble = PandoraDevice()

    // Test rule for common DCK test setup and teardown procedures, along with utility APIs.
    @Rule(order = 2)
    @JvmField
    val dck =
        DckTestRule(
            context,
            bumble,
            isBluetoothToggled = isBluetoothToggled,
            isRemoteAdvertisingWithUuid = isRemoteAdvertisingWithUuid,
            isGattConnected = isGattConnected
        )

    @Test
    fun scanForIrkAndIdentityAddress_remoteFound() {
        // TODO(316001793): Retrieve identity address from Bumble
        val scanFilter =
            ScanFilter.Builder()
                .setDeviceAddress(
                    TEST_ADDRESS_RANDOM_STATIC,
                    BluetoothDevice.ADDRESS_TYPE_RANDOM,
                    Utils.BUMBLE_IRK
                )
                .build()
        val scanSettings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_AMBIENT_DISCOVERY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                .build()

        val result: LeScanResult = runBlocking {
            withTimeout(TIMEOUT_MS) { dck.scanWithPendingIntent(scanFilter, scanSettings).first() }
        }

        assertThat(result).isInstanceOf(LeScanResult.Success::class.java)
        assertThat((result as LeScanResult.Success).callbackType)
            .isEqualTo(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        assertThat((result as LeScanResult.Success).scanResult.device.address)
            .isEqualTo(TEST_ADDRESS_RANDOM_STATIC)
    }

    companion object {
        private const val TIMEOUT_MS = 3000L
        private const val TEST_ADDRESS_RANDOM_STATIC = "F0:43:A8:23:10:11"

        // TODO(315852141): Include variations for LE only vs. Dual mode Bumble when supported
        // TODO(315852141): Include variations for two advertisements at the same time
        // TODO(303502437): Include variations for other callback types when supported in rootcanal
        @Parameters(
            name =
                "{index}: isRemoteAdvertisingWithUuid = {0}, " +
                    "isBluetoothToggled = {1}, isGattConnected = {2}"
        )
        @JvmStatic
        fun parameters(): Iterable<Array<Any>> {
            val booleanVariations = setOf(true, false)

            return Sets.cartesianProduct(
                    listOf(
                        /* isRemoteAdvertisingWithUuid */ booleanVariations,
                        /* isBluetoothToggled */ booleanVariations,
                        /* isGattConnected */ booleanVariations
                    )
                )
                .map { it.toTypedArray() }
        }
    }
}
