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

import android.bluetooth.BluetoothA2dpSink
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.*
import android.util.Log
import com.google.protobuf.ByteString
import io.grpc.stub.StreamObserver
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import pandora.A2DPGrpc.A2DPImplBase
import pandora.A2DPProto.*

@kotlinx.coroutines.ExperimentalCoroutinesApi
class A2dpSink(val context: Context) : A2DPImplBase(), Closeable {
    private val TAG = "PandoraA2dpSink"

    private val scope: CoroutineScope
    private val flow: Flow<Intent>

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothA2dpSink =
        getProfileProxy<BluetoothA2dpSink>(context, BluetoothProfile.A2DP_SINK)

    init {
        scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED)

        flow = intentFlow(context, intentFilter, scope).shareIn(scope, SharingStarted.Eagerly)
    }

    override fun close() {
        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP_SINK, bluetoothA2dpSink)
        scope.cancel()
    }

    override fun waitSink(
        request: WaitSinkRequest,
        responseObserver: StreamObserver<WaitSinkResponse>
    ) {
        grpcUnary<WaitSinkResponse>(scope, responseObserver) {
            val device = request.connection.toBluetoothDevice(bluetoothAdapter)
            Log.i(TAG, "waitSink: device=$device")

            if (bluetoothA2dpSink.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
                val state =
                    flow
                        .filter {
                            it.getAction() == BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED
                        }
                        .filter { it.getBluetoothDeviceExtra() == device }
                        .map {
                            it.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothAdapter.ERROR)
                        }
                        .filter {
                            it == BluetoothProfile.STATE_CONNECTED ||
                                it == BluetoothProfile.STATE_DISCONNECTED
                        }
                        .first()

                if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    throw RuntimeException("waitStream failed, A2DP has been disconnected")
                }
            }

            val sink =
                Sink.newBuilder().setCookie(ByteString.copyFrom(device.getAddress(), "UTF-8"))
            WaitSinkResponse.newBuilder().setSink(sink).build()
        }
    }

    override fun close(request: CloseRequest, responseObserver: StreamObserver<CloseResponse>) {
        grpcUnary<CloseResponse>(scope, responseObserver) {
            val device = bluetoothAdapter.getRemoteDevice(request.sink.cookie.toString("UTF-8"))
            Log.i(TAG, "close: device=$device")
            if (bluetoothA2dpSink.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
                throw RuntimeException("Device is not connected, cannot close")
            }

            val a2dpConnectionStateChangedFlow =
                flow
                    .filter { it.getAction() == BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED }
                    .filter { it.getBluetoothDeviceExtra() == device }
                    .map { it.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothAdapter.ERROR) }

            bluetoothA2dpSink.setConnectionPolicy(
                device,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN
            )
            a2dpConnectionStateChangedFlow
                .filter { it == BluetoothProfile.STATE_DISCONNECTED }
                .first()

            CloseResponse.getDefaultInstance()
        }
    }
}
