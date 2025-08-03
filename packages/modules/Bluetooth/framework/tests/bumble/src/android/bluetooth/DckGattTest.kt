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

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.test.core.app.ApplicationProvider
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Empty
import io.grpc.Deadline
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import pandora.HostProto
import pandora.HostProto.AdvertiseRequest
import pandora.HostProto.OwnAddressType

/** DCK GATT Tests */
@RunWith(Parameterized::class)
public class DckGattTest(private val connected: Boolean) {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val bluetoothAdapter = bluetoothManager.adapter
    private val leScanner = bluetoothAdapter.bluetoothLeScanner

    private val scanResultCaptor = argumentCaptor<ScanResult>()
    private val scanCallbackMock = mock<ScanCallback>()
    private val gattCaptor = argumentCaptor<BluetoothGatt>()
    private val gattCallbackMock =
        mock<BluetoothGattCallback> {
            on { onConnectionStateChange(gattCaptor.capture(), any(), any()) } doAnswer {}
        }

    // A Rule live from a test setup through it's teardown.
    // Gives shell permissions during the test.
    @Rule @JvmField val mPermissionRule = AdoptShellPermissionsRule()

    // Setup a Bumble Pandora device for the duration of the test.
    // Acting as a Pandora client, it can be interacted with through the Pandora APIs.
    @Rule @JvmField val mBumble = PandoraDevice()

    @Before
    fun setUp() {
        if (connected) {
            advertiseWithBumble()

            // Connect DUT to Ref as prerequisite
            val device =
                bluetoothAdapter.getRemoteLeDevice(
                    Utils.BUMBLE_RANDOM_ADDRESS,
                    BluetoothDevice.ADDRESS_TYPE_RANDOM
                )
            val gatt = device.connectGatt(context, false, gattCallbackMock)
            verify(gattCallbackMock, timeout(TIMEOUT))
                .onConnectionStateChange(
                    eq(gatt),
                    eq(BluetoothGatt.GATT_SUCCESS),
                    eq(BluetoothProfile.STATE_CONNECTED)
                )
        }

        clearInvocations(gattCallbackMock)
    }

    @After
    fun tearDown() {
        for (gatt in gattCaptor.allValues.toSet()) {
            gatt.close()
        }
    }

    /**
     * Tests the discovery of the Digital Car Key (DCK) GATT service via Bluetooth on an Android
     * device.
     *
     * <p>This test method goes through the following steps:
     * <ul>
     * <li>1. Register the Dck Gatt service on Bumble over a gRPC call.</li>
     * <li>2. Advertises the host's (potentially the car's) Bluetooth capabilities through a gRPC
     *   call.</li>
     * <li>3. Fetches a remote LE (Low Energy) Bluetooth device instance.</li>
     * <li>4. Sets up a mock GATT callback for Bluetooth related events.</li>
     * <li>5. Connects to the Bumble device and verifies a successful connection.</li>
     * <li>6. Discovers the GATT services offered by Bumble and checks for a successful service
     *   discovery.</li>
     * <li>7. Validates the presence of the required GATT service (CCC_DK_UUID) on the Bumble
     *   device.</li>
     * <li>8. Disconnects from the Bumble device and ensures a successful disconnection.</li>
     * </ul>
     *
     * </p>
     *
     * @throws AssertionError if any of the assertions (like service discovery or connection checks)
     *   fail.
     * @see BluetoothGatt
     * @see BluetoothGattCallback
     */
    @Test
    fun testDiscoverDkGattService() {
        // 1. Register Bumble's DCK (Digital Car Key) service via a gRPC call:
        // - `dckBlocking()` is likely a stub that accesses the DCK service over gRPC in a
        //   blocking/synchronous manner.
        // - `withDeadline(Deadline.after(TIMEOUT, TimeUnit.MILLISECONDS))` sets a timeout for the
        //   gRPC call.
        // - `register(Empty.getDefaultInstance())` sends a registration request to the server.
        mBumble
            .dckBlocking()
            .withDeadline(Deadline.after(TIMEOUT, TimeUnit.MILLISECONDS))
            .register(Empty.getDefaultInstance())

        // 2. Advertise the host's (presumably the car's) Bluetooth capabilities using another
        //    gRPC call:
        // - `hostBlocking()` accesses another gRPC service related to the host.
        //   The following `advertise(...)` sends an advertise request to the server, setting
        //   specific attributes.
        mBumble
            .hostBlocking()
            .advertise(
                AdvertiseRequest.newBuilder()
                    .setLegacy(
                        true
                    ) // As of now, Bumble only support legacy advertising (b/266124496).
                    .setConnectable(true)
                    .setOwnAddressType(
                        OwnAddressType.RANDOM
                    ) // Ask Bumble to advertise it's `RANDOM` address.
                    .build()
            )

        // 3. Fetch a remote Bluetooth device instance (here, Bumble).
        val bumbleDevice =
            bluetoothAdapter.getRemoteLeDevice(
                // To keep things straightforward, the Bumble RANDOM address is set to a predefined
                // constant.
                // Typically, an LE scan would be conducted to identify the Bumble device, matching
                // it based on its
                // Advertising data.
                Utils.BUMBLE_RANDOM_ADDRESS,
                BluetoothDevice
                    .ADDRESS_TYPE_RANDOM // Specify address type as RANDOM because the device
                // advertises with this address type.
            )

        // 4. Create a mock callback to handle Bluetooth GATT (Generic Attribute Profile) related
        // events.
        val gattCallback = mock<BluetoothGattCallback>()

        // 5. Connect to the Bumble device and expect a successful connection callback.
        var bumbleGatt = bumbleDevice.connectGatt(context, false, gattCallback)
        verify(gattCallback, timeout(TIMEOUT))
            .onConnectionStateChange(
                any(),
                eq(BluetoothGatt.GATT_SUCCESS),
                eq(BluetoothProfile.STATE_CONNECTED)
            )

        // 6. Discover GATT services offered by Bumble and expect successful service discovery.
        bumbleGatt.discoverServices()
        verify(gattCallback, timeout(TIMEOUT))
            .onServicesDiscovered(any(), eq(BluetoothGatt.GATT_SUCCESS))

        // 7. Check if the required service (CCC_DK_UUID) is available on Bumble.
        assertThat(bumbleGatt.getService(CCC_DK_UUID)).isNotNull()

        // 8. Disconnect from the Bumble device and expect a successful disconnection callback.
        bumbleGatt.disconnect()
        verify(gattCallback, timeout(TIMEOUT))
            .onConnectionStateChange(
                any(),
                eq(BluetoothGatt.GATT_SUCCESS),
                eq(BluetoothProfile.STATE_DISCONNECTED)
            )
    }

    /*
     * 2.1 GATT Connect - discovered using scan with Identity Address and IRK
     *
     * http://docs/document/d/1oQOpgI83HSJBdr5mBU00za_6XrDGo2KDGnCcX-hXPHk#heading=h.9nvtna3zum23
     */
    @Test
    fun testGattConnect_fromIrkScan() {
        // TODO(b/317091743): Enable test when bug is fixed.
        assumeFalse(connected)

        // Start advertisement on Ref
        advertiseWithBumble()

        // Start IRK scan for Ref on DUT
        val scanSettings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_AMBIENT_DISCOVERY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
        val scanFilter =
            ScanFilter.Builder()
                .setDeviceAddress(
                    TEST_ADDRESS_RANDOM_STATIC,
                    BluetoothDevice.ADDRESS_TYPE_RANDOM,
                    Utils.BUMBLE_IRK
                )
                .build()
        leScanner.startScan(listOf(scanFilter), scanSettings, scanCallbackMock)

        // Await scan results
        verify(scanCallbackMock, timeout(TIMEOUT).atLeastOnce())
            .onScanResult(eq(ScanSettings.CALLBACK_TYPE_ALL_MATCHES), scanResultCaptor.capture())

        // Verify correct scan result as prerequisite
        val scanResult = scanResultCaptor.firstValue
        assertThat(scanResult).isNotNull()
        assertThat(scanResult.device.identityAddress).isEqualTo(TEST_ADDRESS_RANDOM_STATIC)

        // Verify successful GATT connection
        val device = scanResult.device
        val gatt = device.connectGatt(context, false, gattCallbackMock)
        verify(gattCallbackMock, timeout(TIMEOUT))
            .onConnectionStateChange(
                eq(gatt),
                eq(BluetoothGatt.GATT_SUCCESS),
                eq(BluetoothProfile.STATE_CONNECTED)
            )

        // Stop scan on DUT after GATT connect
        leScanner.stopScan(scanCallbackMock)
    }

    /*
     * 2.3 GATT Connect - discovered using scan with UUID
     *
     * http://docs/document/d/1oQOpgI83HSJBdr5mBU00za_6XrDGo2KDGnCcX-hXPHk#heading=h.7ofaj7vwknsr
     */
    @Test
    fun testGattConnect_fromUuidScan() {
        // Start UUID advertisement on Ref
        advertiseWithBumble(withUuid = true)

        // Start UUID scan for Ref on DUT
        val scanSettings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
        val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(CCC_DK_UUID)).build()
        leScanner.startScan(listOf(scanFilter), scanSettings, scanCallbackMock)

        // Await scan results
        verify(scanCallbackMock, timeout(TIMEOUT).atLeastOnce())
            .onScanResult(eq(ScanSettings.CALLBACK_TYPE_ALL_MATCHES), scanResultCaptor.capture())

        // Stop scan on DUT before GATT connect
        leScanner.stopScan(scanCallbackMock)

        // Verify correct scan result as prerequisite
        val scanResult = scanResultCaptor.firstValue
        assertThat(scanResult).isNotNull()
        assertThat(scanResult.scanRecord?.serviceUuids).contains(ParcelUuid(CCC_DK_UUID))

        // Verify successful GATT connection
        val device = scanResult.device
        val gatt = device.connectGatt(context, false, gattCallbackMock)
        verify(gattCallbackMock, timeout(TIMEOUT))
            .onConnectionStateChange(
                eq(gatt),
                eq(BluetoothGatt.GATT_SUCCESS),
                eq(BluetoothProfile.STATE_CONNECTED)
            )
    }

    private fun advertiseWithBumble(withUuid: Boolean = false) {
        val requestBuilder =
            AdvertiseRequest.newBuilder()
                .setLegacy(true)
                .setConnectable(true)
                .setOwnAddressType(OwnAddressType.RANDOM)

        if (withUuid) {
            requestBuilder.data =
                HostProto.DataTypes.newBuilder()
                    .addCompleteServiceClassUuids128(CCC_DK_UUID.toString())
                    .build()
        }
        mBumble.hostBlocking().advertise(requestBuilder.build())
    }

    companion object {
        private const val TAG = "DckTest"
        private const val TIMEOUT: Long = 2000
        private const val TEST_ADDRESS_RANDOM_STATIC = "F0:43:A8:23:10:11"

        // CCC DK Specification R3 1.2.0 r14 section 19.2.1.2 Bluetooth Le Pairing
        private val CCC_DK_UUID = UUID.fromString("0000FFF5-0000-1000-8000-00805f9b34fb")

        @Parameters(name = "connected = {0}")
        @JvmStatic
        fun parameters(): Iterable<Array<Any?>> =
            Sets.cartesianProduct(
                    setOf(false, true), // connected
                )
                .map { it.toTypedArray() }
    }
}
