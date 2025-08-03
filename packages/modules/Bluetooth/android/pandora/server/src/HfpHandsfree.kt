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

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadsetClient
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telecom.InCallService
import android.telecom.TelecomManager
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import pandora.HFPGrpc.HFPImplBase
import pandora.HfpProto.*

private const val TAG = "PandoraHfpHandsfree"

@kotlinx.coroutines.ExperimentalCoroutinesApi
class HfpHandsfree(val context: Context) : HFPImplBase(), Closeable {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    private val flow: Flow<Intent>

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val telecomManager = context.getSystemService(TelecomManager::class.java)!!
    private val bluetoothAdapter = bluetoothManager.adapter

    private val bluetoothHfpClient =
        getProfileProxy<BluetoothHeadsetClient>(context, BluetoothProfile.HEADSET_CLIENT)

    companion object {
        @SuppressLint("StaticFieldLeak") private lateinit var inCallService: InCallService
    }

    init {
        val intentFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        flow = intentFlow(context, intentFilter, scope).shareIn(scope, SharingStarted.Eagerly)
    }

    override fun close() {
        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET_CLIENT, bluetoothHfpClient)
        scope.cancel()
    }

    override fun answerCallAsHandsfree(
        request: AnswerCallAsHandsfreeRequest,
        responseObserver: StreamObserver<AnswerCallAsHandsfreeResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            bluetoothHfpClient.acceptCall(
                request.connection.toBluetoothDevice(bluetoothAdapter),
                BluetoothHeadsetClient.CALL_ACCEPT_NONE
            )
            AnswerCallAsHandsfreeResponse.getDefaultInstance()
        }
    }

    override fun endCallAsHandsfree(
        request: EndCallAsHandsfreeRequest,
        responseObserver: StreamObserver<EndCallAsHandsfreeResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            for (call in
                bluetoothHfpClient.getCurrentCalls(
                    request.connection.toBluetoothDevice(bluetoothAdapter)
                )) {
                bluetoothHfpClient.terminateCall(
                    request.connection.toBluetoothDevice(bluetoothAdapter),
                    call
                )
            }
            EndCallAsHandsfreeResponse.getDefaultInstance()
        }
    }

    override fun declineCallAsHandsfree(
        request: DeclineCallAsHandsfreeRequest,
        responseObserver: StreamObserver<DeclineCallAsHandsfreeResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            bluetoothHfpClient.rejectCall(request.connection.toBluetoothDevice(bluetoothAdapter))
            DeclineCallAsHandsfreeResponse.getDefaultInstance()
        }
    }

    override fun connectToAudioAsHandsfree(
        request: ConnectToAudioAsHandsfreeRequest,
        responseObserver: StreamObserver<ConnectToAudioAsHandsfreeResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            bluetoothHfpClient.connectAudio(request.connection.toBluetoothDevice(bluetoothAdapter))
            ConnectToAudioAsHandsfreeResponse.getDefaultInstance()
        }
    }

    override fun disconnectFromAudioAsHandsfree(
        request: DisconnectFromAudioAsHandsfreeRequest,
        responseObserver: StreamObserver<DisconnectFromAudioAsHandsfreeResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            bluetoothHfpClient.disconnectAudio(
                request.connection.toBluetoothDevice(bluetoothAdapter)
            )
            DisconnectFromAudioAsHandsfreeResponse.getDefaultInstance()
        }
    }

    override fun makeCallAsHandsfree(
        request: MakeCallAsHandsfreeRequest,
        responseObserver: StreamObserver<MakeCallAsHandsfreeResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            bluetoothHfpClient.dial(
                request.connection.toBluetoothDevice(bluetoothAdapter),
                request.number
            )
            MakeCallAsHandsfreeResponse.getDefaultInstance()
        }
    }

    override fun callTransferAsHandsfree(
        request: CallTransferAsHandsfreeRequest,
        responseObserver: StreamObserver<CallTransferAsHandsfreeResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            bluetoothHfpClient.explicitCallTransfer(
                request.connection.toBluetoothDevice(bluetoothAdapter)
            )
            CallTransferAsHandsfreeResponse.getDefaultInstance()
        }
    }

    override fun enableSlcAsHandsfree(
        request: EnableSlcAsHandsfreeRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        grpcUnary(scope, responseObserver) {
            bluetoothHfpClient.setConnectionPolicy(
                request.connection.toBluetoothDevice(bluetoothAdapter),
                BluetoothProfile.CONNECTION_POLICY_ALLOWED
            )
            Empty.getDefaultInstance()
        }
    }

    override fun disableSlcAsHandsfree(
        request: DisableSlcAsHandsfreeRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        grpcUnary(scope, responseObserver) {
            bluetoothHfpClient.setConnectionPolicy(
                request.connection.toBluetoothDevice(bluetoothAdapter),
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN
            )
            Empty.getDefaultInstance()
        }
    }

    override fun setVoiceRecognitionAsHandsfree(
        request: SetVoiceRecognitionAsHandsfreeRequest,
        responseObserver: StreamObserver<SetVoiceRecognitionAsHandsfreeResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            if (request.enabled) {
                bluetoothHfpClient.startVoiceRecognition(
                    request.connection.toBluetoothDevice(bluetoothAdapter)
                )
            } else {
                bluetoothHfpClient.stopVoiceRecognition(
                    request.connection.toBluetoothDevice(bluetoothAdapter)
                )
            }
            SetVoiceRecognitionAsHandsfreeResponse.getDefaultInstance()
        }
    }

    override fun sendDtmfFromHandsfree(
        request: SendDtmfFromHandsfreeRequest,
        responseObserver: StreamObserver<SendDtmfFromHandsfreeResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            bluetoothHfpClient.sendDTMF(
                request.connection.toBluetoothDevice(bluetoothAdapter),
                request.code.toByte()
            )
            SendDtmfFromHandsfreeResponse.getDefaultInstance()
        }
    }
}
