/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.util.Log
import com.google.protobuf.ByteString
import io.grpc.stub.StreamObserver
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import pandora.HostProto.Connection
import pandora.L2CAPGrpc.L2CAPImplBase
import pandora.L2capProto.AcceptL2CAPChannelRequest
import pandora.L2capProto.AcceptL2CAPChannelResponse
import pandora.L2capProto.CreateLECreditBasedChannelRequest
import pandora.L2capProto.CreateLECreditBasedChannelResponse
import pandora.L2capProto.ListenL2CAPChannelRequest
import pandora.L2capProto.ListenL2CAPChannelResponse
import pandora.L2capProto.ReceiveDataRequest
import pandora.L2capProto.ReceiveDataResponse
import pandora.L2capProto.SendDataRequest
import pandora.L2capProto.SendDataResponse

@kotlinx.coroutines.ExperimentalCoroutinesApi
class L2cap(val context: Context) : L2CAPImplBase(), Closeable {
    private val TAG = "PandoraL2cap"
    private val scope: CoroutineScope
    private val BLUETOOTH_SERVER_SOCKET_TIMEOUT: Int = 10000
    private val BUFFER_SIZE = 512

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var connectionInStreamMap: HashMap<Connection, InputStream> = hashMapOf()
    private var connectionOutStreamMap: HashMap<Connection, OutputStream> = hashMapOf()
    private var connectionServerSocketMap: HashMap<Connection, BluetoothServerSocket> = hashMapOf()

    init {
        // Init the CoroutineScope
        scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    }

    override fun close() {
        // Deinit the CoroutineScope
        scope.cancel()
    }

    suspend fun receive(inStream: InputStream): ByteArray {
        return withContext(Dispatchers.IO) {
            val buf = ByteArray(BUFFER_SIZE)
            inStream.read(buf, 0, BUFFER_SIZE) // blocking
            Log.i(TAG, "receive: $buf")
            buf
        }
    }

    /** Open a BluetoothServerSocket to accept connections */
    override fun listenL2CAPChannel(
        request: ListenL2CAPChannelRequest,
        responseObserver: StreamObserver<ListenL2CAPChannelResponse>,
    ) {
        grpcUnary(scope, responseObserver) {
            Log.i(TAG, "listenL2CAPChannel: secure=${request.secure}")
            val connection = request.connection
            val bluetoothServerSocket =
                if (request.secure) {
                    bluetoothAdapter.listenUsingL2capChannel()
                } else {
                    bluetoothAdapter.listenUsingInsecureL2capChannel()
                }
            connectionServerSocketMap[connection] = bluetoothServerSocket
            ListenL2CAPChannelResponse.newBuilder().build()
        }
    }

    override fun acceptL2CAPChannel(
        request: AcceptL2CAPChannelRequest,
        responseObserver: StreamObserver<AcceptL2CAPChannelResponse>,
    ) {
        grpcUnary(scope, responseObserver) {
            Log.i(TAG, "acceptL2CAPChannel")

            val connection = request.connection
            val bluetoothServerSocket = connectionServerSocketMap[connection]
            try {
                val bluetoothSocket =
                    bluetoothServerSocket!!.accept(BLUETOOTH_SERVER_SOCKET_TIMEOUT)
                connectionInStreamMap[connection] = bluetoothSocket.getInputStream()!!
                connectionOutStreamMap[connection] = bluetoothSocket.getOutputStream()!!
            } catch (e: IOException) {
                Log.e(TAG, "bluetoothServerSocket not accepted", e)
                throw e
            }

            AcceptL2CAPChannelResponse.newBuilder().build()
        }
    }

    /** Set device to send LE based connection request */
    override fun createLECreditBasedChannel(
        request: CreateLECreditBasedChannelRequest,
        responseObserver: StreamObserver<CreateLECreditBasedChannelResponse>,
    ) {
        // Creates a gRPC coroutine in a given coroutine scope which executes a given suspended
        // function
        // returning a gRPC response and sends it on a given gRPC stream observer.
        grpcUnary(scope, responseObserver) {
            Log.i(TAG, "createLECreditBasedChannel: secure=${request.secure}, psm=${request.psm}")
            val connection = request.connection
            val device = request.connection.toBluetoothDevice(bluetoothAdapter)
            val psm = request.psm

            try {
                val bluetoothSocket =
                    if (request.secure) {
                        device.createL2capChannel(psm)
                    } else {
                        device.createInsecureL2capChannel(psm)
                    }
                bluetoothSocket.connect()
                connectionInStreamMap[connection] = bluetoothSocket.getInputStream()!!
                connectionOutStreamMap[connection] = bluetoothSocket.getOutputStream()!!
            } catch (e: IOException) {
                Log.d(TAG, "bluetoothSocket not connected: $e")
                throw e
            }

            // Response sent to client
            CreateLECreditBasedChannelResponse.newBuilder().build()
        }
    }

    /** send data packet */
    override fun sendData(
        request: SendDataRequest,
        responseObserver: StreamObserver<SendDataResponse>,
    ) {
        grpcUnary(scope, responseObserver) {
            Log.i(TAG, "sendDataPacket: data=${request.data}")
            val buffer = request.data!!.toByteArray()
            val connection = request.connection
            val outputStream = connectionOutStreamMap[connection]!!

            withContext(Dispatchers.IO) {
                try {
                    outputStream.write(buffer)
                    outputStream.flush()
                } catch (e: IOException) {
                    Log.e(TAG, "Exception during writing to sendDataPacket output stream", e)
                }
            }

            // Response sent to client
            SendDataResponse.newBuilder().build()
        }
    }

    override fun receiveData(
        request: ReceiveDataRequest,
        responseObserver: StreamObserver<ReceiveDataResponse>,
    ) {
        grpcUnary(scope, responseObserver) {
            Log.i(TAG, "receiveData")
            val connection = request.connection
            val inputStream = connectionInStreamMap[connection]!!
            val buf = receive(inputStream)

            ReceiveDataResponse.newBuilder().setData(ByteString.copyFrom(buf)).build()
        }
    }
}
