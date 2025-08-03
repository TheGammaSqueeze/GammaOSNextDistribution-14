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
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.*
import android.net.MacAddress
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.protobuf.Any
import com.google.protobuf.ByteString
import io.grpc.Status
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.CancellationException
import java.util.stream.Collectors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import pandora.HostProto.Connection
import pandora.OsProto.InternalConnectionRef

private const val TAG = "PandoraUtils"
private val alphanumeric = ('A'..'Z') + ('a'..'z') + ('0'..'9')

val initiatedConnection = HashSet<BluetoothDevice>()
val waitedAclConnection = HashSet<BluetoothDevice>()
val waitedAclDisconnection = HashSet<BluetoothDevice>()

fun shell(cmd: String): String {
    val fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(cmd)
    val input_stream = ParcelFileDescriptor.AutoCloseInputStream(fd)
    return BufferedReader(InputStreamReader(input_stream)).lines().collect(Collectors.joining("\n"))
}

/**
 * Creates a cold flow of intents based on an intent filter. If used multiple times in a same class,
 * this flow should be transformed into a shared flow.
 *
 * @param context context on which to register the broadcast receiver.
 * @param intentFilter intent filter.
 * @return cold flow.
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
fun intentFlow(context: Context, intentFilter: IntentFilter, scope: CoroutineScope) = callbackFlow {
    val broadcastReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    waitedAclDisconnection.remove(intent.getBluetoothDeviceExtra())
                }
                if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                    initiatedConnection.remove(intent.getBluetoothDeviceExtra())
                    waitedAclConnection.remove(intent.getBluetoothDeviceExtra())
                    waitedAclDisconnection.add(intent.getBluetoothDeviceExtra())
                }
                scope.launch { trySendBlocking(intent) }
            }
        }
    context.registerReceiver(broadcastReceiver, intentFilter)

    awaitClose { context.unregisterReceiver(broadcastReceiver) }
}

/**
 * Creates a gRPC coroutine in a given coroutine scope which executes a given suspended function
 * returning a gRPC response and sends it on a given gRPC stream observer.
 *
 * @param T the type of gRPC response.
 * @param scope coroutine scope used to run the coroutine.
 * @param responseObserver the gRPC stream observer on which to send the response.
 * @param timeout the duration in seconds after which the coroutine is automatically cancelled and
 *   returns a timeout error. Default: 60s.
 * @param block the suspended function to execute to get the response.
 * @return reference to the coroutine as a Job.
 *
 * Example usage:
 * ```
 * override fun grpcMethod(
 *   request: TypeOfRequest,
 *   responseObserver: StreamObserver<TypeOfResponse> {
 *     grpcUnary(scope, responseObserver) {
 *       block
 *     }
 *   }
 * }
 * ```
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
fun <T> grpcUnary(
    scope: CoroutineScope,
    responseObserver: StreamObserver<T>,
    timeout: Long = 60,
    block: suspend () -> T
): Job {
    return scope.launch {
        try {
            val response = withTimeout(timeout * 1000) { block() }
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (e: Throwable) {
            e.printStackTrace()
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            responseObserver.onError(
                Status.UNKNOWN.withCause(e).withDescription(sw.toString()).asException()
            )
        }
    }
}

/**
 * Creates a gRPC coroutine in a given coroutine scope which executes a given suspended function
 * taking in a Flow of gRPC requests and returning a Flow of gRPC responses and sends it on a given
 * gRPC stream observer.
 *
 * @param T the type of gRPC response.
 * @param scope coroutine scope used to run the coroutine.
 * @param responseObserver the gRPC stream observer on which to send the response.
 * @param block the suspended function transforming the request Flow to the response Flow.
 * @return a StreamObserver for the incoming requests.
 *
 * Example usage:
 * ```
 * override fun grpcMethod(
 *   responseObserver: StreamObserver<TypeOfResponse> {
 *     grpcBidirectionalStream(scope, responseObserver) {
 *       block
 *     }
 *   }
 * }
 * ```
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
fun <T, U> grpcBidirectionalStream(
    scope: CoroutineScope,
    responseObserver: StreamObserver<U>,
    block: CoroutineScope.(Flow<T>) -> Flow<U>
): StreamObserver<T> {

    val inputChannel = Channel<T>()
    val serverCallStreamObserver = responseObserver as ServerCallStreamObserver<T>

    val job =
        scope.launch {
            block(inputChannel.consumeAsFlow())
                .onEach { responseObserver.onNext(it) }
                .onCompletion { error ->
                    if (error == null) {
                        responseObserver.onCompleted()
                    }
                }
                .catch {
                    it.printStackTrace()
                    val sw = StringWriter()
                    it.printStackTrace(PrintWriter(sw))
                    responseObserver.onError(
                        Status.UNKNOWN.withCause(it).withDescription(sw.toString()).asException()
                    )
                }
                .launchIn(this)
        }

    serverCallStreamObserver.setOnCancelHandler { job.cancel() }

    return object : StreamObserver<T> {
        override fun onNext(req: T) {
            // Note: this should be made a blocking call, and the handler should run in a separate
            // thread
            // so we get flow control - but for now we can live with this
            if (inputChannel.trySend(req).isFailure) {
                job.cancel(CancellationException("too many incoming requests, buffer exceeded"))
                responseObserver.onError(
                    CancellationException("too many incoming requests, buffer exceeded")
                )
            }
        }

        override fun onCompleted() {
            // stop the input flow, but keep the job running
            inputChannel.close()
        }

        override fun onError(e: Throwable) {
            job.cancel()
            e.printStackTrace()
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            responseObserver.onError(
                Status.UNKNOWN.withCause(e).withDescription(sw.toString()).asException()
            )
        }
    }
}

/**
 * Creates a gRPC coroutine in a given coroutine scope which executes a given suspended function
 * taking in a Flow of gRPC requests and returning a Flow of gRPC responses and sends it on a given
 * gRPC stream observer.
 *
 * @param T the type of gRPC response.
 * @param scope coroutine scope used to run the coroutine.
 * @param responseObserver the gRPC stream observer on which to send the response.
 * @param block the suspended function producing the response Flow.
 * @return a StreamObserver for the incoming requests.
 *
 * Example usage:
 * ```
 * override fun grpcMethod(
 *   request: TypeOfRequest,
 *   responseObserver: StreamObserver<TypeOfResponse> {
 *     grpcServerStream(scope, responseObserver) {
 *       block
 *     }
 *   }
 * }
 * ```
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
fun <T> grpcServerStream(
    scope: CoroutineScope,
    responseObserver: StreamObserver<T>,
    block: CoroutineScope.() -> Flow<T>
) {
    val serverCallStreamObserver = responseObserver as ServerCallStreamObserver<T>

    val job =
        scope.launch {
            block()
                .onEach { responseObserver.onNext(it) }
                .onCompletion { error ->
                    if (error == null) {
                        responseObserver.onCompleted()
                    }
                }
                .catch {
                    it.printStackTrace()
                    val sw = StringWriter()
                    it.printStackTrace(PrintWriter(sw))
                    responseObserver.onError(
                        Status.UNKNOWN.withCause(it).withDescription(sw.toString()).asException()
                    )
                }
                .launchIn(this)
        }

    serverCallStreamObserver.setOnCancelHandler { job.cancel() }
}

/**
 * Synchronous method to get a Bluetooth profile proxy.
 *
 * @param T the type of profile proxy (e.g. BluetoothA2dp)
 * @param context context
 * @param bluetoothAdapter local Bluetooth adapter
 * @param profile identifier of the Bluetooth profile (e.g. BluetoothProfile#A2DP)
 * @return T the desired profile proxy
 */
@Suppress("UNCHECKED_CAST")
@kotlinx.coroutines.ExperimentalCoroutinesApi
fun <T> getProfileProxy(context: Context, profile: Int): T {
    var proxy: BluetoothProfile?
    runBlocking {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
        val bluetoothAdapter = bluetoothManager.adapter

        val flow = callbackFlow {
            val serviceListener =
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        trySendBlocking(proxy)
                    }
                    override fun onServiceDisconnected(profile: Int) {}
                }

            bluetoothAdapter.getProfileProxy(context, serviceListener, profile)

            awaitClose {}
        }
        proxy = withTimeoutOrNull(5_000) { flow.first() }
    }
    if (proxy == null) {
        Log.w(TAG, "profile proxy $profile is null")
    }
    return proxy!! as T
}

fun Intent.getBluetoothDeviceExtra(): BluetoothDevice =
    this.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)!!

fun ByteString.decodeAsMacAddressToString(): String =
    MacAddress.fromBytes(this.toByteArray()).toString().uppercase()

fun ByteString.toBluetoothDevice(adapter: BluetoothAdapter): BluetoothDevice =
    adapter.getRemoteDevice(this.decodeAsMacAddressToString())

fun Connection.toBluetoothDevice(adapter: BluetoothAdapter): BluetoothDevice =
    adapter.getRemoteDevice(this.address)

val Connection.address: String
    get() = InternalConnectionRef.parseFrom(this.cookie.value).address.decodeAsMacAddressToString()

val Connection.transport: Int
    get() = InternalConnectionRef.parseFrom(this.cookie.value).transport

fun BluetoothDevice.toByteString() =
    ByteString.copyFrom(MacAddress.fromString(this.address).toByteArray())!!

fun BluetoothDevice.toConnection(transport: Int): Connection {
    val internal_connection_ref =
        InternalConnectionRef.newBuilder()
            .setAddress(ByteString.copyFrom(MacAddress.fromString(this.address).toByteArray()))
            .setTransport(transport)
            .build()
    val cookie = Any.newBuilder().setValue(internal_connection_ref.toByteString()).build()

    return Connection.newBuilder().setCookie(cookie).build()
}

/** Creates Audio track instance and returns the reference. */
fun buildAudioTrack(): AudioTrack? {
    return AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(44100 * 2 * 2)
        .build()
}

/**
 * Generates Alpha-numeric string of given length.
 *
 * @param length required string size.
 * @return a generated string
 */
fun generateAlphanumericString(length: Int): String {
    return buildString { repeat(length) { append(alphanumeric.random()) } }
}
