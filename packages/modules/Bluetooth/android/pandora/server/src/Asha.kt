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
import android.bluetooth.BluetoothHearingAid
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import pandora.asha.AshaGrpc.AshaImplBase
import pandora.asha.AshaProto.*

@kotlinx.coroutines.ExperimentalCoroutinesApi
class Asha(val context: Context) : AshaImplBase(), Closeable {
    private val TAG = "PandoraAsha"
    private val scope: CoroutineScope
    private val flow: Flow<Intent>

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothHearingAid =
        getProfileProxy<BluetoothHearingAid>(context, BluetoothProfile.HEARING_AID)
    private val bluetoothAdapter = bluetoothManager.adapter
    private val audioManager = context.getSystemService(AudioManager::class.java)!!

    private var audioTrack: AudioTrack? = null

    init {
        // Init the CoroutineScope
        scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED)
        flow = intentFlow(context, intentFilter, scope).shareIn(scope, SharingStarted.Eagerly)
    }

    override fun close() {
        // Deinit the CoroutineScope
        scope.cancel()
    }

    override fun waitPeripheral(
        request: WaitPeripheralRequest,
        responseObserver: StreamObserver<WaitPeripheralResponse>
    ) {
        grpcUnary<WaitPeripheralResponse>(scope, responseObserver) {
            Log.i(TAG, "waitPeripheral")

            val device = request.connection.toBluetoothDevice(bluetoothAdapter)
            Log.d(TAG, "connection address ${device.getAddress()}")

            if (
                bluetoothHearingAid.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED
            ) {
                Log.d(TAG, "wait for bluetoothHearingAid profile connection")
                flow
                    .filter {
                        it.getAction() == BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED
                    }
                    .filter { it.getBluetoothDeviceExtra() == device }
                    .map { it.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothAdapter.ERROR) }
                    .filter { it == BluetoothProfile.STATE_CONNECTED }
                    .first()
            }

            WaitPeripheralResponse.getDefaultInstance()
        }
    }

    override fun start(request: StartRequest, responseObserver: StreamObserver<StartResponse>) {
        grpcUnary<StartResponse>(scope, responseObserver) {
            Log.i(TAG, "play")

            // wait until BluetoothHearingAid profile is connected
            val device = request.connection.toBluetoothDevice(bluetoothAdapter)
            Log.d(TAG, "connection address ${device.getAddress()}")

            if (
                bluetoothHearingAid.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED
            ) {
                throw RuntimeException("Hearing aid device is not connected, cannot start")
            }

            // wait for hearing aid is added as an audio device
            val audioDeviceAddedFlow = callbackFlow {
                val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (outputDevice in outputDevices) {
                    if (
                        outputDevice.type == AudioDeviceInfo.TYPE_HEARING_AID &&
                            outputDevice.address.equals(device.getAddress())
                    ) {
                        trySendBlocking(null)
                    }
                }

                val audioDeviceCallback =
                    object : AudioDeviceCallback() {
                        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                            for (addedDevice in addedDevices) {
                                if (
                                    addedDevice.type == AudioDeviceInfo.TYPE_HEARING_AID &&
                                        addedDevice.address.equals(device.getAddress())
                                ) {
                                    Log.d(
                                        TAG,
                                        "TYPE_HEARING_AID added with address: ${addedDevice.address}"
                                    )
                                    trySendBlocking(null)
                                }
                            }
                        }
                    }

                audioManager.registerAudioDeviceCallback(
                    audioDeviceCallback,
                    Handler(Looper.getMainLooper())
                )
                awaitClose { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
            }
            audioDeviceAddedFlow.first()

            if (audioTrack == null) {
                audioTrack = buildAudioTrack()
                Log.i(TAG, "buildAudioTrack")
            }
            audioTrack!!.play()

            // wait for hearing aid is selected as routed device
            val audioRoutingFlow = callbackFlow {
                if (audioTrack!!.routedDevice.type == AudioDeviceInfo.TYPE_HEARING_AID) {
                    Log.d(TAG, "already route to TYPE_HEARING_AID")
                    trySendBlocking(null)
                }

                val audioRoutingListener =
                    object : AudioRouting.OnRoutingChangedListener {
                        override fun onRoutingChanged(router: AudioRouting) {
                            if (router.routedDevice.type == AudioDeviceInfo.TYPE_HEARING_AID) {
                                Log.d(TAG, "Route to TYPE_HEARING_AID")
                                trySendBlocking(null)
                            } else {
                                val outputDevices =
                                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                                for (outputDevice in outputDevices) {
                                    Log.d(
                                        TAG,
                                        "available output device in listener:${outputDevice.type}"
                                    )
                                    if (outputDevice.type == AudioDeviceInfo.TYPE_HEARING_AID) {
                                        val result = router.setPreferredDevice(outputDevice)
                                        Log.d(TAG, "setPreferredDevice result:$result")
                                        trySendBlocking(null)
                                    }
                                }
                            }
                        }
                    }

                audioTrack!!.addOnRoutingChangedListener(
                    audioRoutingListener,
                    Handler(Looper.getMainLooper())
                )
                awaitClose { audioTrack!!.removeOnRoutingChangedListener(audioRoutingListener) }
            }
            audioRoutingFlow.first()

            val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                minVolume,
                AudioManager.FLAG_SHOW_UI
            )

            StartResponse.getDefaultInstance()
        }
    }

    override fun stop(request: StopRequest, responseObserver: StreamObserver<StopResponse>) {
        grpcUnary<StopResponse>(scope, responseObserver) {
            Log.i(TAG, "stop")
            audioTrack!!.pause()
            audioTrack!!.flush()

            StopResponse.getDefaultInstance()
        }
    }

    override fun playbackAudio(
        responseObserver: StreamObserver<PlaybackAudioResponse>
    ): StreamObserver<PlaybackAudioRequest> {
        Log.i(TAG, "playbackAudio")
        if (audioTrack!!.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            responseObserver.onError(
                Status.UNKNOWN.withDescription("AudioTrack is not started").asException()
            )
        }

        // Volume is maxed out to avoid any amplitude modification of the provided audio data,
        // enabling the test runner to do comparisons between input and output audio signal.
        // Any volume modification should be done before providing the audio data.
        if (audioManager.isVolumeFixed) {
            Log.w(TAG, "Volume is fixed, cannot max out the volume")
        } else {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) < maxVolume) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    maxVolume,
                    AudioManager.FLAG_SHOW_UI
                )
            }
        }

        return object : StreamObserver<PlaybackAudioRequest> {
            override fun onNext(request: PlaybackAudioRequest) {
                val data = request.data.toByteArray()
                Log.d(TAG, "audio track writes data=$data")
                val written = synchronized(audioTrack!!) { audioTrack!!.write(data, 0, data.size) }
                if (written != data.size) {
                    Log.e(TAG, "AudioTrack write failed")
                    responseObserver.onError(
                        Status.UNKNOWN.withDescription("AudioTrack write failed").asException()
                    )
                }
            }
            override fun onError(t: Throwable?) {
                Log.e(TAG, t.toString())
                responseObserver.onError(t)
            }
            override fun onCompleted() {
                Log.i(TAG, "onCompleted")
                responseObserver.onNext(PlaybackAudioResponse.getDefaultInstance())
                responseObserver.onCompleted()
            }
        }
    }
}
