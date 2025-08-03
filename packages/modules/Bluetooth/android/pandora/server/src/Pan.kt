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

import android.bluetooth.BluetoothDevice.TRANSPORT_BREDR
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothPan
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.net.TetheringManager
import android.net.TetheringManager.TETHERING_BLUETOOTH
import android.util.Log
import io.grpc.stub.StreamObserver
import java.io.Closeable
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import pandora.HostProto.*
import pandora.PANGrpc.PANImplBase
import pandora.PanProto.*

@kotlinx.coroutines.ExperimentalCoroutinesApi
class Pan(private val context: Context) : PANImplBase(), Closeable {
    private val TAG = "PandoraPan"
    private val mScope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothPan = getProfileProxy<BluetoothPan>(context, BluetoothProfile.PAN)

    private var mTetheringEnabled = MutableStateFlow(false)

    private val mTetheringManager: TetheringManager
    private val mStartTetheringCallback =
        object : TetheringManager.StartTetheringCallback {
            override fun onTetheringStarted() {
                Log.i(TAG, "onTetheringStarted")
                mTetheringEnabled.value = true
            }

            override fun onTetheringFailed(error: Int) {
                Log.e(TAG, "onTetheringFailed $error")
                mTetheringEnabled.value = false
            }
        }

    init {
        mTetheringManager = context.getSystemService(TetheringManager::class.java)!!
    }

    override fun close() {
        bluetoothAdapter.closeProfileProxy(BluetoothProfile.PAN, bluetoothPan)
        mScope.cancel()
    }

    override fun enableTethering(
        request: EnableTetheringRequest,
        responseObserver: StreamObserver<EnableTetheringResponse>
    ) {
        grpcUnary<EnableTetheringResponse>(mScope, responseObserver) {
            Log.i(TAG, "enableTethering")
            if (mTetheringEnabled.value != true) {
                mTetheringManager.startTethering(
                    TETHERING_BLUETOOTH,
                    Executors.newSingleThreadExecutor(),
                    mStartTetheringCallback
                )
                mTetheringEnabled.first { it == true }
            }
            EnableTetheringResponse.newBuilder().build()
        }
    }

    override fun connectPan(
        request: ConnectPanRequest,
        responseObserver: StreamObserver<ConnectPanResponse>
    ) {
        grpcUnary<ConnectPanResponse>(mScope, responseObserver) {
            Log.i(TAG, "connectPan")
            val device = request.address.toBluetoothDevice(bluetoothAdapter)
            bluetoothPan.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED)
            bluetoothPan.connect(device)
            ConnectPanResponse.newBuilder()
                .setConnection(device.toConnection(TRANSPORT_BREDR))
                .build()
        }
    }
}
