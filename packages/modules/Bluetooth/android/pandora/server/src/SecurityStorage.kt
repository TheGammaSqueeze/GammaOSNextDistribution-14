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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_NONE
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.google.protobuf.BoolValue
import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import pandora.HostProto.*
import pandora.SecurityProto.*
import pandora.SecurityStorageGrpc.SecurityStorageImplBase

private const val TAG = "PandoraSecurityStorage"

@kotlinx.coroutines.ExperimentalCoroutinesApi
class SecurityStorage(private val context: Context) : SecurityStorageImplBase(), Closeable {

    private val globalScope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    private val flow: Flow<Intent>

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val bluetoothAdapter = bluetoothManager.adapter

    init {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)

        flow = intentFlow(context, intentFilter, globalScope).shareIn(globalScope, SharingStarted.Eagerly)
    }

    override fun close() {
        globalScope.cancel()
    }

    override fun isBonded(request: IsBondedRequest, responseObserver: StreamObserver<BoolValue>) {
        grpcUnary(globalScope, responseObserver) {
            val bondedDevices = bluetoothAdapter.getBondedDevices()
            val bondedDevice =
                when (request.getAddressCase()!!) {
                    IsBondedRequest.AddressCase.PUBLIC ->
                        bondedDevices.firstOrNull { it.toByteString() == request.public }
                    IsBondedRequest.AddressCase.RANDOM ->
                        bondedDevices.firstOrNull { it.toByteString() == request.random }
                    IsBondedRequest.AddressCase.ADDRESS_NOT_SET ->
                        throw Status.UNKNOWN.asException()
                }
            Log.i(TAG, "isBonded: device=$bondedDevice")
            BoolValue.newBuilder().setValue(bondedDevice != null).build()
        }
    }

    override fun deleteBond(request: DeleteBondRequest, responseObserver: StreamObserver<Empty>) {
        grpcUnary(globalScope, responseObserver) {
            val (address, type) =
                when (request.getAddressCase()!!) {
                    DeleteBondRequest.AddressCase.PUBLIC ->
                        Pair(request.public, BluetoothDevice.ADDRESS_TYPE_PUBLIC)
                    DeleteBondRequest.AddressCase.RANDOM ->
                        Pair(request.random, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                    DeleteBondRequest.AddressCase.ADDRESS_NOT_SET ->
                        throw Status.UNKNOWN.asException()
                }
            val bluetoothDevice =
                bluetoothAdapter.getRemoteLeDevice(address.decodeAsMacAddressToString(), type)
            Log.i(TAG, "deleteBond: device=$bluetoothDevice")

            val unbonded =
                globalScope.async {
                    flow
                        .filter { it.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED }
                        .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
                        .filter {
                            it.getIntExtra(
                                BluetoothDevice.EXTRA_BOND_STATE,
                                BluetoothAdapter.ERROR
                            ) == BluetoothDevice.BOND_NONE
                        }
                        .first()
                }

            if (bluetoothDevice.removeBond()) {
                Log.i(TAG, "deleteBond: device=$bluetoothDevice - wait BOND_NONE intent")
                unbonded.await()
            } else {
                Log.i(TAG, "deleteBond: device=$bluetoothDevice - Already unpaired")
                unbonded.cancel()
            }
            Empty.getDefaultInstance()
        }
    }
}
