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
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.CallLog
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
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

private const val TAG = "PandoraHfp"

@kotlinx.coroutines.ExperimentalCoroutinesApi
class Hfp(val context: Context) : HFPImplBase(), Closeable {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    private val flow: Flow<Intent>

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val telecomManager = context.getSystemService(TelecomManager::class.java)!!
    private val bluetoothAdapter = bluetoothManager.adapter

    private val bluetoothHfp = getProfileProxy<BluetoothHeadset>(context, BluetoothProfile.HEADSET)

    companion object {
        @SuppressLint("StaticFieldLeak") private lateinit var inCallService: InCallService
    }

    init {

        val intentFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        flow = intentFlow(context, intentFilter, scope).shareIn(scope, SharingStarted.Eagerly)

        // kill any existing call
        telecomManager.endCall()

        shell("su root setprop persist.bluetooth.disableinbandringing false")
    }

    override fun close() {
        // kill any existing call
        telecomManager.endCall()

        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHfp)
        scope.cancel()
    }

    class PandoraInCallService : InCallService() {
        override fun onBind(intent: Intent?): IBinder? {
            inCallService = this
            return super.onBind(intent)
        }
    }

    override fun enableSlc(request: EnableSlcRequest, responseObserver: StreamObserver<Empty>) {
        grpcUnary<Empty>(scope, responseObserver) {
            val device = request.connection.toBluetoothDevice(bluetoothAdapter)

            bluetoothHfp.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED)

            Empty.getDefaultInstance()
        }
    }

    override fun disableSlc(request: DisableSlcRequest, responseObserver: StreamObserver<Empty>) {
        grpcUnary<Empty>(scope, responseObserver) {
            val device = request.connection.toBluetoothDevice(bluetoothAdapter)

            bluetoothHfp.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN)

            Empty.getDefaultInstance()
        }
    }

    override fun setBatteryLevel(
        request: SetBatteryLevelRequest,
        responseObserver: StreamObserver<Empty>,
    ) {
        grpcUnary<Empty>(scope, responseObserver) {
            val action = "android.intent.action.BATTERY_CHANGED"
            shell("am broadcast -a $action --ei level ${request.batteryPercentage} --ei scale 100")
            Empty.getDefaultInstance()
        }
    }

    override fun declineCall(
        request: DeclineCallRequest,
        responseObserver: StreamObserver<DeclineCallResponse>,
    ) {
        grpcUnary(scope, responseObserver) {
            telecomManager.endCall()
            DeclineCallResponse.getDefaultInstance()
        }
    }

    override fun setAudioPath(
        request: SetAudioPathRequest,
        responseObserver: StreamObserver<SetAudioPathResponse>,
    ) {
        grpcUnary(scope, responseObserver) {
            when (request.audioPath!!) {
                AudioPath.AUDIO_PATH_UNKNOWN,
                AudioPath.UNRECOGNIZED, -> {}
                AudioPath.AUDIO_PATH_HANDSFREE -> {
                    check(bluetoothHfp.getActiveDevice() != null)
                    inCallService.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
                }
                AudioPath.AUDIO_PATH_SPEAKERS ->
                    inCallService.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }
            SetAudioPathResponse.getDefaultInstance()
        }
    }

    override fun answerCall(
        request: AnswerCallRequest,
        responseObserver: StreamObserver<AnswerCallResponse>,
    ) {
        grpcUnary(scope, responseObserver) {
            telecomManager.acceptRingingCall()
            AnswerCallResponse.getDefaultInstance()
        }
    }

    override fun swapActiveCall(
        request: SwapActiveCallRequest,
        responseObserver: StreamObserver<SwapActiveCallResponse>,
    ) {
        grpcUnary(scope, responseObserver) {
            val callsToActivate = mutableListOf<Call>()
            for (call in inCallService.calls) {
                if (call.details.state == Call.STATE_ACTIVE) {
                    call.hold()
                } else {
                    callsToActivate.add(call)
                }
            }
            for (call in callsToActivate) {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
            }
            inCallService.calls[0].hold()
            inCallService.calls[1].unhold()
            SwapActiveCallResponse.getDefaultInstance()
        }
    }

    override fun setInBandRingtone(
        request: SetInBandRingtoneRequest,
        responseObserver: StreamObserver<SetInBandRingtoneResponse>,
    ) {
        grpcUnary(scope, responseObserver) {
            shell(
                "su root setprop persist.bluetooth.disableinbandringing " +
                    (!request.enabled).toString()
            )
            SetInBandRingtoneResponse.getDefaultInstance()
        }
    }

    override fun makeCall(
        request: MakeCallRequest,
        responseObserver: StreamObserver<MakeCallResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            telecomManager.placeCall(Uri.fromParts("tel", request.number, null), Bundle())
            MakeCallResponse.getDefaultInstance()
        }
    }

    override fun setVoiceRecognition(
        request: SetVoiceRecognitionRequest,
        responseObserver: StreamObserver<SetVoiceRecognitionResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            if (request.enabled) {
                bluetoothHfp.startVoiceRecognition(
                    request.connection.toBluetoothDevice(bluetoothAdapter)
                )
            } else {
                bluetoothHfp.stopVoiceRecognition(
                    request.connection.toBluetoothDevice(bluetoothAdapter)
                )
            }
            SetVoiceRecognitionResponse.getDefaultInstance()
        }
    }

    override fun clearCallHistory(
        request: ClearCallHistoryRequest,
        responseObserver: StreamObserver<ClearCallHistoryResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            ClearCallHistoryResponse.getDefaultInstance()
        }
    }
}
