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
import android.bluetooth.BluetoothManager
import android.content.Context
import android.provider.Telephony.*
import android.telephony.TelephonyManager
import android.util.Log
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import pandora.OsGrpc.OsImplBase
import pandora.OsProto.*

private const val TAG = "PandoraOs"

@kotlinx.coroutines.ExperimentalCoroutinesApi
class Os(val context: Context) : OsImplBase() {

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val bluetoothAdapter = bluetoothManager.adapter
    private var telephonyManager = context.getSystemService(TelephonyManager::class.java)!!
    private val DEFAULT_MESSAGE_LEN = 130

    override fun log(request: LogRequest, responseObserver: StreamObserver<LogResponse>) {
        grpcUnary(scope, responseObserver) {
            Log.i(TAG, request.text)
            LogResponse.getDefaultInstance()
        }
    }

    override fun setAccessPermission(
        request: SetAccessPermissionRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        grpcUnary<Empty>(scope, responseObserver) {
            val bluetoothDevice = request.address.toBluetoothDevice(bluetoothAdapter)
            when (request.accessType!!) {
                AccessType.ACCESS_MESSAGE ->
                    bluetoothDevice.setMessageAccessPermission(BluetoothDevice.ACCESS_ALLOWED)
                AccessType.ACCESS_PHONEBOOK ->
                    bluetoothDevice.setPhonebookAccessPermission(BluetoothDevice.ACCESS_ALLOWED)
                AccessType.ACCESS_SIM ->
                    bluetoothDevice.setSimAccessPermission(BluetoothDevice.ACCESS_ALLOWED)
                else -> {}
            }
            Empty.getDefaultInstance()
        }
    }

    override fun sendPing(request: SendPingRequest, responseObserver: StreamObserver<Empty>) {
        grpcUnary<Empty>(scope, responseObserver) {
            val pingStatus =
                Runtime.getRuntime().exec("ping -I bt-pan -c 1 ${request.ipAddress}").waitFor()
            Empty.getDefaultInstance()
        }
    }
}
