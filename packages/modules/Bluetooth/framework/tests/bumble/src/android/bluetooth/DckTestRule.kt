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

import android.app.PendingIntent
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.google.protobuf.Empty
import io.grpc.Deadline
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import pandora.HostProto
import pandora.HostProto.AdvertiseRequest
import pandora.HostProto.OwnAddressType

/** Test rule for DCK specific device and Bumble setup and teardown procedures */
class DckTestRule(
    private val context: Context,
    private val bumble: PandoraDevice,
    private val isBluetoothToggled: Boolean = false,
    private val isRemoteAdvertisingWithUuid: Boolean = false,
    private val isGattConnected: Boolean = false,
) : TestRule {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val bluetoothAdapter = bluetoothManager.adapter
    private val leScanner = bluetoothAdapter.bluetoothLeScanner

    private val scope = CoroutineScope(Dispatchers.Default)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    // Internal Types

    /** Wrapper for [ScanResult] */
    sealed class LeScanResult {

        /** Represents a [ScanResult] with the associated [callbackType] */
        data class Success(val scanResult: ScanResult, val callbackType: Int) : LeScanResult()

        /** Represents a scan failure with an [errorCode] */
        data class Failure(val errorCode: Int) : LeScanResult()
    }

    /** Wrapper for [BluetoothGatt] along with its [state] and [status] */
    data class GattState(val gatt: BluetoothGatt, val status: Int, val state: Int)

    // Public Methods

    /**
     * Starts an LE scan with the given [scanFilter] and [scanSettings], using [ScanCallback] within
     * the given [coroutine].
     *
     * The caller can stop the scan at any time by cancelling the coroutine they used to start the
     * scan. If no coroutine was provided, a default coroutine is used and the scan will be stopped
     * at the end of the test.
     *
     * @return SharedFlow of [LeScanResult] with a buffer of size 1
     */
    fun scanWithCallback(
        scanFilter: ScanFilter,
        scanSettings: ScanSettings,
        coroutine: CoroutineScope = scope
    ) =
        callbackFlow {
                val callback =
                    object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            trySend(LeScanResult.Success(result, callbackType))
                        }

                        override fun onScanFailed(errorCode: Int) {
                            trySend(LeScanResult.Failure(errorCode))
                            channel.close()
                        }
                    }

                leScanner.startScan(listOf(scanFilter), scanSettings, callback)

                awaitClose { leScanner.stopScan(callback) }
            }
            .conflate()
            .shareIn(coroutine, SharingStarted.Lazily)

    /**
     * Starts an LE scan with the given [scanFilter] and [scanSettings], using [PendingIntent]
     * within the given [coroutine].
     *
     * The caller can stop the scan at any time by cancelling the coroutine they used to start the
     * scan. If no coroutine was provided, a default coroutine is used and the scan will be stopped
     * at the end of the test.
     *
     * @return SharedFlow of [LeScanResult] with a buffer of size 1
     */
    fun scanWithPendingIntent(
        scanFilter: ScanFilter,
        scanSettings: ScanSettings,
        coroutine: CoroutineScope = scope
    ) =
        callbackFlow {
                val intentFilter = IntentFilter(ACTION_DYNAMIC_RECEIVER_SCAN_RESULT)
                val broadcastReceiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (ACTION_DYNAMIC_RECEIVER_SCAN_RESULT == intent.action) {
                                val results =
                                    intent.getParcelableArrayListExtra<ScanResult>(
                                        BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT
                                    )
                                        ?: return

                                val callbackType =
                                    intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1)

                                for (result in results) {
                                    trySend(LeScanResult.Success(result, callbackType))
                                }
                            }
                        }
                    }

                context.registerReceiver(broadcastReceiver, intentFilter)

                val scanIntent = Intent(ACTION_DYNAMIC_RECEIVER_SCAN_RESULT)
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        scanIntent,
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                leScanner.startScan(listOf(scanFilter), scanSettings, pendingIntent)

                awaitClose {
                    context.unregisterReceiver(broadcastReceiver)
                    leScanner.stopScan(pendingIntent)
                }
            }
            .conflate()
            .shareIn(coroutine, SharingStarted.Lazily)

    /**
     * Requests a GATT connection to the given [device] within the given [coroutine].
     *
     * Cancelling the coroutine will close the GATT client. If no coroutine was provided, a default
     * coroutine is used and the GATT client will be closed at the end of the test.
     *
     * @return SharedFlow of [GattState] with a buffer of size 1
     */
    fun connectGatt(device: BluetoothDevice, coroutine: CoroutineScope = ioScope) =
        callbackFlow {
                val callback =
                    object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(
                            gatt: BluetoothGatt,
                            status: Int,
                            newState: Int
                        ) {
                            trySend(GattState(gatt, status, newState))
                        }
                    }

                val gatt = device.connectGatt(context, false, callback)

                awaitClose { gatt.close() }
            }
            .conflate()
            .shareIn(coroutine, SharingStarted.Lazily)

    // TestRule Overrides

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                setup(base)
            }
        }
    }

    // Private Methods

    private fun setup(base: Statement) {
        // Register Bumble's DCK (Digital Car Key) service
        registerDckService()
        // Start LE advertisement on Bumble
        advertiseWithBumble()

        try {
            if (isBluetoothToggled) {
                toggleBluetooth()
            }

            if (isGattConnected) {
                connectGatt()
            }

            base.evaluate()
        } finally {
            reset()
        }
    }

    private fun registerDckService() {
        bumble
            .dckBlocking()
            .withDeadline(Deadline.after(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            .register(Empty.getDefaultInstance())
    }

    private fun advertiseWithBumble() {
        val requestBuilder =
            AdvertiseRequest.newBuilder()
                .setLegacy(true) // Bumble currently only supports legacy advertising.
                .setOwnAddressType(OwnAddressType.RANDOM)
                .setConnectable(true)

        if (isRemoteAdvertisingWithUuid) {
            val advertisementData =
                HostProto.DataTypes.newBuilder()
                    .addCompleteServiceClassUuids128(CCC_DK_UUID.toString())
                    .build()
            requestBuilder.setData(advertisementData)
        }

        bumble.hostBlocking().advertise(requestBuilder.build())
    }

    private fun toggleBluetooth() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val bluetoothStateFlow = getBluetoothStateFlow(scope)

        try {
            withTimeout(TIMEOUT_MS * 2) { // Combined timeout for enabling and disabling BT
                if (bluetoothAdapter.isEnabled()) {
                    // Disable Bluetooth
                    bluetoothAdapter.disable()
                    // Wait for the BT state change to STATE_OFF
                    bluetoothStateFlow.first { it == BluetoothAdapter.STATE_OFF }
                }

                // Enable Bluetooth
                bluetoothAdapter.enable()
                // Wait for the BT state change to STATE_ON
                bluetoothStateFlow.first { it == BluetoothAdapter.STATE_ON }
            }
        } finally {
            // Close the BT state change flow
            scope.cancel("Done")
        }
    }

    private fun getBluetoothStateFlow(coroutine: CoroutineScope) =
        callbackFlow {
                val bluetoothStateFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                val bluetoothStateReceiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                                trySend(
                                    intent.getIntExtra(
                                        BluetoothAdapter.EXTRA_STATE,
                                        BluetoothAdapter.ERROR
                                    )
                                )
                            }
                        }
                    }

                context.registerReceiver(bluetoothStateReceiver, bluetoothStateFilter)

                awaitClose { context.unregisterReceiver(bluetoothStateReceiver) }
            }
            .conflate()
            .shareIn(coroutine, SharingStarted.Lazily)

    private fun connectGatt() = runBlocking {
        // TODO(315852141): Use supported Bumble for the given type (LE Only vs. Dual Mode)
        val bumbleDevice =
            bluetoothAdapter.getRemoteLeDevice(
                Utils.BUMBLE_RANDOM_ADDRESS,
                BluetoothDevice.ADDRESS_TYPE_RANDOM
            )

        withTimeout(TIMEOUT_MS) {
            connectGatt(bumbleDevice).first { it.state == BluetoothProfile.STATE_CONNECTED }
        }
    }

    private fun reset() {
        scope.cancel("Test Completed")
        ioScope.cancel("Test Completed")
    }

    companion object {
        private const val TIMEOUT_MS = 3000L
        private const val ACTION_DYNAMIC_RECEIVER_SCAN_RESULT =
            "android.bluetooth.test.ACTION_DYNAMIC_RECEIVER_SCAN_RESULT"
        // CCC DK Specification R3 1.2.0 r14 section 19.2.1.2 Bluetooth Le Pairing
        private val CCC_DK_UUID = UUID.fromString("0000FFF5-0000-1000-8000-00805f9b34fb")
    }
}
