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

package com.android.pandora

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class GattServerManager(
    bluetoothManager: BluetoothManager,
    context: Context,
    scope: CoroutineScope
) {
    val TAG = "PandoraGattServerManager"

    val services = mutableMapOf<UUID, BluetoothGattService>()
    val serviceFlow = MutableSharedFlow<BluetoothGattService>()
    var negociatedMtu = -1

    val callback =
        object : BluetoothGattServerCallback() {
            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                Log.i(TAG, "onServiceAdded status=$status")
                check(status == BluetoothGatt.GATT_SUCCESS)
                scope.launch { serviceFlow.emit(service) }
            }
            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                Log.i(TAG, "onMtuChanged mtu=$mtu")
                negociatedMtu = mtu
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                Log.i(TAG, "onCharacteristicReadRequest requestId=$requestId")
                if (negociatedMtu != -1) {
                    server.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        ByteArray(negociatedMtu)
                    )
                } else {
                    server.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        ByteArray(512 - offset)
                    )
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                Log.i(TAG, "onCharacteristicWriteRequest requestId=$requestId")
            }

            override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
                Log.i(TAG, "onExecuteWrite requestId=$requestId")
            }
        }

    val server: BluetoothGattServer = bluetoothManager.openGattServer(context, callback)
}
