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
import android.bluetooth.BluetoothAssignedNumbers
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.ADDRESS_TYPE_PUBLIC
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.TRANSPORT_BREDR
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothUuid
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.MacAddress
import android.os.ParcelUuid
import android.util.Log
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import java.io.Closeable
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import pandora.HostGrpc.HostImplBase
import pandora.HostProto.*

object ByteArrayOps {
    public fun getUShortAt(input: ByteArray, index: Int): UShort {
        return (((input[index + 1].toUInt() and 0xffU) shl 8) or (input[index].toUInt() and 0xffU))
            .toUShort()
    }

    public fun getShortAt(input: ByteArray, index: Int): Short {
        return getUShortAt(input, index).toShort()
    }

    public fun getUIntAt(input: ByteArray, index: Int): UInt {
        return (((input[index + 3].toUInt() and 0xffU) shl 24) or
            ((input[index + 2].toUInt() and 0xffU) shl 16) or
            ((input[index + 1].toUInt() and 0xffU) shl 8) or
            (input[index].toUInt() and 0xffU))
    }

    public fun getIntAt(input: ByteArray, index: Int): Int {
        return getUIntAt(input, index).toInt()
    }

    public fun getUInt24At(input: ByteArray, index: Int): UInt {
        return (((input[index + 2].toUInt() and 0xffU) shl 16) or
            ((input[index + 1].toUInt() and 0xffU) shl 8) or
            (input[index].toUInt() and 0xffU))
    }

    public fun getInt24At(input: ByteArray, index: Int): Int {
        return getUInt24At(input, index).toInt()
    }
}

@kotlinx.coroutines.ExperimentalCoroutinesApi
class Host(
    private val context: Context,
    private val security: Security,
    private val server: Server
) : HostImplBase(), Closeable {
    private val TAG = "PandoraHost"

    private val scope: CoroutineScope
    private val flow: Flow<Intent>

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val bluetoothAdapter = bluetoothManager.adapter

    private var connectability = ConnectabilityMode.NOT_CONNECTABLE
    private var discoverability = DiscoverabilityMode.NOT_DISCOVERABLE

    private val advertisers = mutableMapOf<UUID, AdvertiseCallback>()

    init {
        scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

        // Add all intent actions to be listened.
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND)

        // Creates a shared flow of intents that can be used in all methods in the coroutine scope.
        // This flow is started eagerly to make sure that the broadcast receiver is registered
        // before
        // any function call. This flow is only cancelled when the corresponding scope is cancelled.
        flow = intentFlow(context, intentFilter, scope).shareIn(scope, SharingStarted.Eagerly)
    }

    override fun close() {
        scope.cancel()
    }

    private suspend fun rebootBluetooth() {
        Log.i(TAG, "rebootBluetooth")

        val stateFlow =
            flow
                .filter { it.getAction() == BluetoothAdapter.ACTION_STATE_CHANGED }
                .map { it.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) }

        if (bluetoothAdapter.isEnabled) {
            bluetoothAdapter.disable()
            stateFlow.filter { it == BluetoothAdapter.STATE_OFF }.first()
        }

        // TODO: b/234892968
        delay(3000L)

        bluetoothAdapter.enable()
        stateFlow.filter { it == BluetoothAdapter.STATE_ON }.first()
    }

    override fun factoryReset(request: Empty, responseObserver: StreamObserver<Empty>) {
        grpcUnary<Empty>(scope, responseObserver, 30) {
            Log.i(TAG, "factoryReset")

            val stateFlow =
                flow
                    .filter { it.getAction() == BluetoothAdapter.ACTION_STATE_CHANGED }
                    .map { it.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) }

            initiatedConnection.clear()
            waitedAclConnection.clear()
            waitedAclDisconnection.clear()

            bluetoothAdapter.clearBluetooth()

            stateFlow.filter { it == BluetoothAdapter.STATE_ON }.first()
            // Delay to initialize the Bluetooth completely and to fix flakiness: b/266611263
            delay(1000L)
            Log.i(TAG, "Shutdown the gRPC Server")
            server.shutdown()

            // The last expression is the return value.
            Empty.getDefaultInstance()
        }
    }

    override fun reset(request: Empty, responseObserver: StreamObserver<Empty>) {
        grpcUnary<Empty>(scope, responseObserver) {
            Log.i(TAG, "reset")
            initiatedConnection.clear()
            waitedAclConnection.clear()
            waitedAclDisconnection.clear()

            rebootBluetooth()

            Empty.getDefaultInstance()
        }
    }

    override fun readLocalAddress(
        request: Empty,
        responseObserver: StreamObserver<ReadLocalAddressResponse>
    ) {
        grpcUnary<ReadLocalAddressResponse>(scope, responseObserver) {
            Log.i(TAG, "readLocalAddress")
            val localMacAddress = MacAddress.fromString(bluetoothAdapter.getAddress())
            ReadLocalAddressResponse.newBuilder()
                .setAddress(ByteString.copyFrom(localMacAddress.toByteArray()))
                .build()
        }
    }

    private suspend fun waitPairingRequestIntent(bluetoothDevice: BluetoothDevice) {
        Log.i(TAG, "waitPairingRequestIntent: device=$bluetoothDevice")
        var pairingVariant =
            flow
                .filter { it.getAction() == BluetoothDevice.ACTION_PAIRING_REQUEST }
                .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
                .first()
                .getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)

        val confirmationCases =
            intArrayOf(
                BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION,
                BluetoothDevice.PAIRING_VARIANT_CONSENT,
                BluetoothDevice.PAIRING_VARIANT_PIN,
            )

        if (pairingVariant in confirmationCases) {
            bluetoothDevice.setPairingConfirmation(true)
        }
    }

    private suspend fun waitConnectionIntent(bluetoothDevice: BluetoothDevice) {
        Log.i(TAG, "waitConnectionIntent: device=$bluetoothDevice")
        flow
            .filter { it.action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED }
            .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
            .map { it.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR) }
            .filter { it == BluetoothAdapter.STATE_CONNECTED }
            .first()
    }

    suspend fun waitBondIntent(bluetoothDevice: BluetoothDevice) {
        // We only wait for bonding to be completed since we only need the ACL connection to be
        // established with the peer device (on Android state connected is sent when all profiles
        // have been connected).
        Log.i(TAG, "waitBondIntent: device=$bluetoothDevice")
        flow
            .filter { it.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED }
            .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
            .map { it.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothAdapter.ERROR) }
            .filter { it == BOND_BONDED }
            .first()
    }

    suspend fun waitIncomingAclConnectedIntent(address: String?, transport: Int): Intent {
        return flow
            .filter { it.action == BluetoothDevice.ACTION_ACL_CONNECTED }
            .filter { address == null || it.getBluetoothDeviceExtra().address == address }
            .filter { !initiatedConnection.contains(it.getBluetoothDeviceExtra()) }
            .filter {
                it.getIntExtra(BluetoothDevice.EXTRA_TRANSPORT, BluetoothDevice.ERROR) == transport
            }
            .first()
    }

    private suspend fun acceptPairingAndAwaitBonded(bluetoothDevice: BluetoothDevice) {
        val acceptPairingJob = scope.launch { waitPairingRequestIntent(bluetoothDevice) }
        waitBondIntent(bluetoothDevice)
        if (acceptPairingJob.isActive) {
            acceptPairingJob.cancel()
        }
    }

    override fun waitConnection(
        request: WaitConnectionRequest,
        responseObserver: StreamObserver<WaitConnectionResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            if (request.address.isEmpty())
                throw IllegalArgumentException("Request address field must be set")
            var bluetoothDevice = request.address.toBluetoothDevice(bluetoothAdapter)

            Log.i(TAG, "waitConnection: device=$bluetoothDevice")

            if (!bluetoothAdapter.isEnabled) {
                throw RuntimeException("Bluetooth is not enabled, cannot waitConnection")
            }

            if (!bluetoothDevice.isConnected() || waitedAclConnection.contains(bluetoothDevice)) {
                bluetoothDevice =
                    waitIncomingAclConnectedIntent(bluetoothDevice.address, TRANSPORT_BREDR)
                        .getBluetoothDeviceExtra()
            }

            waitedAclConnection.add(bluetoothDevice)

            WaitConnectionResponse.newBuilder()
                .setConnection(bluetoothDevice.toConnection(TRANSPORT_BREDR))
                .build()
        }
    }

    override fun waitDisconnection(
        request: WaitDisconnectionRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        grpcUnary(scope, responseObserver) {
            val bluetoothDevice = request.connection.toBluetoothDevice(bluetoothAdapter)
            Log.i(TAG, "waitDisconnection: device=$bluetoothDevice")
            if (!bluetoothAdapter.isEnabled) {
                throw RuntimeException("Bluetooth is not enabled, cannot waitDisconnection")
            }
            if (
                bluetoothDevice.isConnected() && !waitedAclDisconnection.contains(bluetoothDevice)
            ) {
                flow
                    .filter { it.action == BluetoothDevice.ACTION_ACL_DISCONNECTED }
                    .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
                    .first()
            }

            waitedAclDisconnection.add(bluetoothDevice)

            Empty.getDefaultInstance()
        }
    }

    override fun connect(
        request: ConnectRequest,
        responseObserver: StreamObserver<ConnectResponse>
    ) {
        grpcUnary(scope, responseObserver) {
            if (request.address.isEmpty())
                throw IllegalArgumentException("Request address field must be set")
            val bluetoothDevice = request.address.toBluetoothDevice(bluetoothAdapter)

            Log.i(TAG, "connect: address=$bluetoothDevice")

            initiatedConnection.add(bluetoothDevice)
            bluetoothAdapter.cancelDiscovery()

            if (!bluetoothDevice.isConnected()) {
                if (bluetoothDevice.bondState == BOND_BONDED) {
                    // already bonded, just reconnect
                    bluetoothDevice.connect()
                    waitConnectionIntent(bluetoothDevice)
                } else {
                    // need to bond
                    bluetoothDevice.createBond()
                    if (!security.manuallyConfirm) {
                        acceptPairingAndAwaitBonded(bluetoothDevice)
                    }
                }
            }

            ConnectResponse.newBuilder()
                .setConnection(bluetoothDevice.toConnection(TRANSPORT_BREDR))
                .build()
        }
    }

    override fun disconnect(request: DisconnectRequest, responseObserver: StreamObserver<Empty>) {
        grpcUnary<Empty>(scope, responseObserver) {
            val bluetoothDevice = request.connection.toBluetoothDevice(bluetoothAdapter)
            Log.i(TAG, "disconnect: device=$bluetoothDevice")

            if (!bluetoothDevice.isConnected()) {
                throw RuntimeException("Device is not connected, cannot disconnect")
            }

            when (request.connection.transport) {
                TRANSPORT_BREDR -> {
                    Log.i(TAG, "disconnect BR_EDR")
                    bluetoothDevice.disconnect()
                }
                TRANSPORT_LE -> {
                    Log.i(TAG, "disconnect LE")
                    val gattInstance =
                        try {
                            GattInstance.get(bluetoothDevice.address)
                        } catch (e: Exception) {
                            Log.w(TAG, "Gatt instance doesn't exist. Android might be peripheral")
                            val instance = GattInstance(bluetoothDevice, TRANSPORT_LE, context)
                            instance.waitForState(BluetoothProfile.STATE_CONNECTED)
                            instance
                        }
                    if (gattInstance.isDisconnected()) {
                        throw RuntimeException("Device is not connected, cannot disconnect")
                    }

                    bluetoothDevice.disconnect()
                    gattInstance.disconnectInstance()
                }
                else -> {
                    throw RuntimeException("Device type UNKNOWN")
                }
            }
            flow
                .filter { it.action == BluetoothDevice.ACTION_ACL_DISCONNECTED }
                .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
                .first()

            Empty.getDefaultInstance()
        }
    }

    override fun connectLE(
        request: ConnectLERequest,
        responseObserver: StreamObserver<ConnectLEResponse>
    ) {
        grpcUnary<ConnectLEResponse>(scope, responseObserver) {
            val ownAddressType = request.ownAddressType
            if (
                ownAddressType != OwnAddressType.RANDOM &&
                    ownAddressType != OwnAddressType.RESOLVABLE_OR_RANDOM
            ) {
                throw RuntimeException("connectLE: Unsupported OwnAddressType: $ownAddressType")
            }
            val (address, type) =
                when (request.getAddressCase()!!) {
                    ConnectLERequest.AddressCase.PUBLIC ->
                        Pair(request.public, BluetoothDevice.ADDRESS_TYPE_PUBLIC)
                    ConnectLERequest.AddressCase.RANDOM ->
                        Pair(request.random, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                    ConnectLERequest.AddressCase.PUBLIC_IDENTITY ->
                        Pair(request.publicIdentity, BluetoothDevice.ADDRESS_TYPE_PUBLIC)
                    ConnectLERequest.AddressCase.RANDOM_STATIC_IDENTITY ->
                        Pair(request.randomStaticIdentity, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                    ConnectLERequest.AddressCase.ADDRESS_NOT_SET ->
                        throw IllegalArgumentException("Request address field must be set")
                }
            Log.i(TAG, "connectLE: $address")
            val bluetoothDevice =
                bluetoothAdapter.getRemoteLeDevice(address.decodeAsMacAddressToString(), type)
            initiatedConnection.add(bluetoothDevice)
            GattInstance(bluetoothDevice, TRANSPORT_LE, context)
                .waitForState(BluetoothProfile.STATE_CONNECTED)
            ConnectLEResponse.newBuilder()
                .setConnection(bluetoothDevice.toConnection(TRANSPORT_LE))
                .build()
        }
    }

    override fun advertise(
        request: AdvertiseRequest,
        responseObserver: StreamObserver<AdvertiseResponse>
    ) {
        Log.d(TAG, "advertise")
        grpcServerStream(scope, responseObserver) {
            callbackFlow {
                val callback =
                    object : AdvertisingSetCallback() {
                        override fun onAdvertisingSetStarted(
                            advertisingSet: AdvertisingSet,
                            txPower: Int,
                            status: Int
                        ) {
                            Log.d(TAG, "advertising started with status " + status)
                            if (status != 0) {
                                error("failed to start advertisingSet: $status")
                            }
                        }
                    }
                val advertisingDataBuilder = AdvertiseData.Builder()
                val dataTypesRequest = request.data

                if (
                    !dataTypesRequest.getIncompleteServiceClassUuids16List().isEmpty() or
                        !dataTypesRequest.getIncompleteServiceClassUuids32List().isEmpty() or
                        !dataTypesRequest.getIncompleteServiceClassUuids128List().isEmpty()
                ) {
                    throw RuntimeException("Incomplete Service Class Uuids not supported")
                }

                // Handle service uuids
                for (uuid16 in dataTypesRequest.getCompleteServiceClassUuids16List()) {
                    val parcel_uuid16 =
                        ParcelUuid.fromString("0000${uuid16}-0000-1000-8000-00805F9B34FB")
                    advertisingDataBuilder.addServiceUuid(parcel_uuid16)
                }
                for (uuid32 in dataTypesRequest.getCompleteServiceClassUuids32List()) {
                    val parcel_uuid32 =
                        ParcelUuid.fromString("${uuid32}-0000-1000-8000-00805F9B34FB")
                    advertisingDataBuilder.addServiceUuid(parcel_uuid32)
                }
                for (uuid128 in dataTypesRequest.getCompleteServiceClassUuids128List()) {
                    advertisingDataBuilder.addServiceUuid(ParcelUuid.fromString(uuid128))
                }

                // Handle Service solicitation uuids
                for (uuid16 in dataTypesRequest.getServiceSolicitationUuids16List()) {
                    val parcel_uuid16 =
                        ParcelUuid.fromString("0000${uuid16}-0000-1000-8000-00805F9B34FB")
                    advertisingDataBuilder.addServiceSolicitationUuid(parcel_uuid16)
                }
                for (uuid32 in dataTypesRequest.getServiceSolicitationUuids32List()) {
                    val parcel_uuid32 =
                        ParcelUuid.fromString("${uuid32}-0000-1000-8000-00805F9B34FB")
                    advertisingDataBuilder.addServiceSolicitationUuid(parcel_uuid32)
                }
                for (uuid128 in dataTypesRequest.getServiceSolicitationUuids128List()) {
                    advertisingDataBuilder.addServiceSolicitationUuid(
                        ParcelUuid.fromString(uuid128)
                    )
                }

                // Handle service data uuids
                for ((uuid16, data) in dataTypesRequest.getServiceDataUuid16()) {
                    val parcel_uuid16 =
                        ParcelUuid.fromString("0000${uuid16}-0000-1000-8000-00805F9B34FB")
                    advertisingDataBuilder.addServiceData(parcel_uuid16, data.toByteArray())
                }
                for ((uuid32, data) in dataTypesRequest.getServiceDataUuid32()) {
                    val parcel_uuid32 =
                        ParcelUuid.fromString("${uuid32}-0000-1000-8000-00805F9B34FB")
                    advertisingDataBuilder.addServiceData(parcel_uuid32, data.toByteArray())
                }
                for ((uuid128, data) in dataTypesRequest.getServiceDataUuid128()) {
                    advertisingDataBuilder.addServiceData(
                        ParcelUuid.fromString(uuid128),
                        data.toByteArray()
                    )
                }

                advertisingDataBuilder
                    .setIncludeDeviceName(
                        dataTypesRequest.includeCompleteLocalName ||
                            dataTypesRequest.includeShortenedLocalName
                    )
                    .setIncludeTxPowerLevel(dataTypesRequest.includeTxPowerLevel)
                    .addManufacturerData(
                        BluetoothAssignedNumbers.GOOGLE,
                        dataTypesRequest.manufacturerSpecificData.toByteArray()
                    )
                val advertisingData = advertisingDataBuilder.build()

                val ownAddressType =
                    when (request.ownAddressType) {
                        OwnAddressType.RESOLVABLE_OR_PUBLIC,
                        OwnAddressType.PUBLIC -> AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC
                        OwnAddressType.RESOLVABLE_OR_RANDOM,
                        OwnAddressType.RANDOM -> AdvertisingSetParameters.ADDRESS_TYPE_RANDOM
                        else -> AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT
                    }

                val advertisingSetParameters =
                    AdvertisingSetParameters.Builder()
                        .setConnectable(request.connectable)
                        .setOwnAddressType(ownAddressType)
                        .setLegacyMode(request.legacy)
                        .setScannable(request.legacy && request.connectable)
                        .build()

                bluetoothAdapter.bluetoothLeAdvertiser.startAdvertisingSet(
                    advertisingSetParameters,
                    advertisingData,
                    null, /* scanResponse */
                    null, /* periodicParameters */
                    null, /* periodicData */
                    callback,
                )

                if (request.connectable) {
                    while (true) {
                        Log.d(TAG, "Waiting for incoming connection")
                        val connection =
                            waitIncomingAclConnectedIntent(null, TRANSPORT_LE)
                                .getBluetoothDeviceExtra()
                                .toConnection(TRANSPORT_LE)
                        Log.d(TAG, "Receive connection")
                        trySendBlocking(
                            AdvertiseResponse.newBuilder().setConnection(connection).build()
                        )
                    }
                }

                awaitClose { bluetoothAdapter.bluetoothLeAdvertiser.stopAdvertisingSet(callback) }
            }
        }
    }

    // TODO: Handle request parameters
    override fun scan(request: ScanRequest, responseObserver: StreamObserver<ScanningResponse>) {
        Log.d(TAG, "scan")
        grpcServerStream(scope, responseObserver) {
            callbackFlow {
                val callback =
                    object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult) {
                            val bluetoothDevice = result.device
                            val scanRecord = result.scanRecord
                            checkNotNull(scanRecord)
                            val scanData = scanRecord.getAdvertisingDataMap()
                            val serviceData = scanRecord.serviceData
                            checkNotNull(serviceData)

                            var dataTypesBuilder =
                                DataTypes.newBuilder().setTxPowerLevel(scanRecord.getTxPowerLevel())

                            scanData[ScanRecord.DATA_TYPE_LOCAL_NAME_SHORT]?.let {
                                dataTypesBuilder.setShortenedLocalName(it.decodeToString())
                            }
                                ?: run { dataTypesBuilder.setIncludeShortenedLocalName(false) }

                            scanData[ScanRecord.DATA_TYPE_LOCAL_NAME_COMPLETE]?.let {
                                dataTypesBuilder.setCompleteLocalName(it.decodeToString())
                            }
                                ?: run { dataTypesBuilder.setIncludeCompleteLocalName(false) }

                            scanData[ScanRecord.DATA_TYPE_ADVERTISING_INTERVAL]?.let {
                                dataTypesBuilder.setAdvertisingInterval(
                                    ByteArrayOps.getShortAt(it, 0).toInt()
                                )
                            }

                            scanData[ScanRecord.DATA_TYPE_ADVERTISING_INTERVAL_LONG]?.let {
                                dataTypesBuilder.setAdvertisingInterval(
                                    ByteArrayOps.getIntAt(it, 0)
                                )
                            }

                            scanData[ScanRecord.DATA_TYPE_APPEARANCE]?.let {
                                dataTypesBuilder.setAppearance(
                                    ByteArrayOps.getShortAt(it, 0).toInt()
                                )
                            }

                            scanData[ScanRecord.DATA_TYPE_CLASS_OF_DEVICE]?.let {
                                dataTypesBuilder.setClassOfDevice(ByteArrayOps.getInt24At(it, 0))
                            }

                            scanData[ScanRecord.DATA_TYPE_URI]?.let {
                                dataTypesBuilder.setUri(it.decodeToString())
                            }

                            scanData[ScanRecord.DATA_TYPE_LE_SUPPORTED_FEATURES]?.let {
                                dataTypesBuilder.setLeSupportedFeatures(ByteString.copyFrom(it))
                            }

                            scanData[ScanRecord.DATA_TYPE_SLAVE_CONNECTION_INTERVAL_RANGE]?.let {
                                dataTypesBuilder.setPeripheralConnectionIntervalMin(
                                    ByteArrayOps.getShortAt(it, 0).toInt()
                                )
                                dataTypesBuilder.setPeripheralConnectionIntervalMax(
                                    ByteArrayOps.getShortAt(it, 2).toInt()
                                )
                            }

                            for (serviceDataEntry in serviceData) {
                                val parcelUuid = serviceDataEntry.key
                                Log.d(TAG, parcelUuid.uuid.toString().uppercase())

                                // use upper case uuid as the key
                                if (BluetoothUuid.is16BitUuid(parcelUuid)) {
                                    val uuid16 =
                                        parcelUuid.uuid.toString().substring(4, 8).uppercase()
                                    dataTypesBuilder.putServiceDataUuid16(
                                        uuid16,
                                        ByteString.copyFrom(serviceDataEntry.value)
                                    )
                                } else if (BluetoothUuid.is32BitUuid(parcelUuid)) {
                                    val uuid32 =
                                        parcelUuid.uuid.toString().substring(0, 8).uppercase()
                                    dataTypesBuilder.putServiceDataUuid32(
                                        uuid32,
                                        ByteString.copyFrom(serviceDataEntry.value)
                                    )
                                } else {
                                    val uuid128 = parcelUuid.uuid.toString().uppercase()
                                    dataTypesBuilder.putServiceDataUuid128(
                                        uuid128,
                                        ByteString.copyFrom(serviceDataEntry.value)
                                    )
                                }
                            }

                            for (serviceUuid in
                                scanRecord.serviceSolicitationUuids ?: listOf<ParcelUuid>()) {
                                Log.d(TAG, serviceUuid.uuid.toString().uppercase())
                                if (BluetoothUuid.is16BitUuid(serviceUuid)) {
                                    val uuid16 =
                                        serviceUuid.uuid.toString().substring(4, 8).uppercase()
                                    dataTypesBuilder.addServiceSolicitationUuids16(uuid16)
                                } else if (BluetoothUuid.is32BitUuid(serviceUuid)) {
                                    val uuid32 =
                                        serviceUuid.uuid.toString().substring(0, 8).uppercase()
                                    dataTypesBuilder.addServiceSolicitationUuids32(uuid32)
                                } else {
                                    val uuid128 = serviceUuid.uuid.toString().uppercase()
                                    dataTypesBuilder.addServiceSolicitationUuids128(uuid128)
                                }
                            }

                            for (serviceUuid in scanRecord.serviceUuids ?: listOf<ParcelUuid>()) {
                                Log.d(TAG, serviceUuid.uuid.toString().uppercase())
                                if (BluetoothUuid.is16BitUuid(serviceUuid)) {
                                    val uuid16 =
                                        serviceUuid.uuid.toString().substring(4, 8).uppercase()
                                    dataTypesBuilder.addIncompleteServiceClassUuids16(uuid16)
                                } else if (BluetoothUuid.is32BitUuid(serviceUuid)) {
                                    val uuid32 =
                                        serviceUuid.uuid.toString().substring(0, 8).uppercase()
                                    dataTypesBuilder.addIncompleteServiceClassUuids32(uuid32)
                                } else {
                                    val uuid128 = serviceUuid.uuid.toString().uppercase()
                                    dataTypesBuilder.addIncompleteServiceClassUuids128(uuid128)
                                }
                            }

                            // Flags DataTypes CSSv10 1.3 Flags
                            val mode: DiscoverabilityMode =
                                when (scanRecord.advertiseFlags and 0b11) {
                                    0b01 -> DiscoverabilityMode.DISCOVERABLE_LIMITED
                                    0b10 -> DiscoverabilityMode.DISCOVERABLE_GENERAL
                                    else -> DiscoverabilityMode.NOT_DISCOVERABLE
                                }
                            dataTypesBuilder.setLeDiscoverabilityMode(mode)
                            var manufacturerData = ByteBuffer.allocate(512)
                            val manufacturerSpecificDatas = scanRecord.getManufacturerSpecificData()
                            for (i in 0..manufacturerSpecificDatas.size() - 1) {
                                val id = manufacturerSpecificDatas.keyAt(i)
                                manufacturerData
                                    .put(id.toByte())
                                    .put(id.shr(8).toByte())
                                    .put(manufacturerSpecificDatas.get(id))
                            }
                            dataTypesBuilder.setManufacturerSpecificData(
                                ByteString.copyFrom(
                                    manufacturerData.array(),
                                    0,
                                    manufacturerData.position()
                                )
                            )
                            val primaryPhy =
                                when (result.getPrimaryPhy()) {
                                    BluetoothDevice.PHY_LE_1M -> PrimaryPhy.PRIMARY_1M
                                    BluetoothDevice.PHY_LE_CODED -> PrimaryPhy.PRIMARY_CODED
                                    else -> PrimaryPhy.UNRECOGNIZED
                                }
                            val secondaryPhy =
                                when (result.getSecondaryPhy()) {
                                    ScanResult.PHY_UNUSED -> SecondaryPhy.SECONDARY_NONE
                                    BluetoothDevice.PHY_LE_1M -> SecondaryPhy.SECONDARY_1M
                                    BluetoothDevice.PHY_LE_2M -> SecondaryPhy.SECONDARY_2M
                                    BluetoothDevice.PHY_LE_CODED -> SecondaryPhy.SECONDARY_CODED
                                    else -> SecondaryPhy.UNRECOGNIZED
                                }
                            var scanningResponseBuilder =
                                ScanningResponse.newBuilder()
                                    .setLegacy(result.isLegacy())
                                    .setConnectable(result.isConnectable())
                                    .setTruncated(
                                        result.getDataStatus() == ScanResult.DATA_TRUNCATED
                                    )
                                    .setSid(result.getAdvertisingSid())
                                    .setPrimaryPhy(primaryPhy)
                                    .setSecondaryPhy(secondaryPhy)
                                    .setTxPower(result.getTxPower())
                                    .setRssi(result.getRssi())
                                    .setPeriodicAdvertisingInterval(
                                        result.getPeriodicAdvertisingInterval().toFloat()
                                    )
                                    .setData(dataTypesBuilder.build())
                            when (bluetoothDevice.addressType) {
                                BluetoothDevice.ADDRESS_TYPE_PUBLIC ->
                                    scanningResponseBuilder.setPublic(
                                        bluetoothDevice.toByteString()
                                    )
                                BluetoothDevice.ADDRESS_TYPE_RANDOM ->
                                    scanningResponseBuilder.setRandom(
                                        bluetoothDevice.toByteString()
                                    )
                                else ->
                                    Log.w(
                                        TAG,
                                        "Address type UNKNOWN: ${bluetoothDevice.type} addr: $bluetoothDevice"
                                    )
                            }
                            // TODO: Complete the missing field as needed, all the examples are here
                            trySendBlocking(scanningResponseBuilder.build())
                        }

                        override fun onScanFailed(errorCode: Int) {
                            error("scan failed")
                        }
                    }
                bluetoothAdapter.bluetoothLeScanner.startScan(callback)

                awaitClose { bluetoothAdapter.bluetoothLeScanner.stopScan(callback) }
            }
        }
    }

    override fun inquiry(request: Empty, responseObserver: StreamObserver<InquiryResponse>) {
        Log.d(TAG, "Inquiry")
        grpcServerStream(scope, responseObserver) {
            launch {
                try {
                    bluetoothAdapter.startDiscovery()
                    awaitCancellation()
                } finally {
                    bluetoothAdapter.cancelDiscovery()
                }
            }
            flow
                .filter { it.action == BluetoothDevice.ACTION_FOUND }
                .map {
                    val bluetoothDevice = it.getBluetoothDeviceExtra()
                    Log.i(TAG, "Device found: $bluetoothDevice")
                    InquiryResponse.newBuilder().setAddress(bluetoothDevice.toByteString()).build()
                }
        }
    }

    override fun setDiscoverabilityMode(
        request: SetDiscoverabilityModeRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        Log.d(TAG, "setDiscoverabilityMode")
        grpcUnary(scope, responseObserver) {
            discoverability = request.mode!!

            val scanMode =
                when (discoverability) {
                    DiscoverabilityMode.UNRECOGNIZED -> null
                    DiscoverabilityMode.NOT_DISCOVERABLE ->
                        if (connectability == ConnectabilityMode.CONNECTABLE) {
                            BluetoothAdapter.SCAN_MODE_CONNECTABLE
                        } else {
                            BluetoothAdapter.SCAN_MODE_NONE
                        }
                    DiscoverabilityMode.DISCOVERABLE_LIMITED,
                    DiscoverabilityMode.DISCOVERABLE_GENERAL ->
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
                }

            if (scanMode != null) {
                bluetoothAdapter.setScanMode(scanMode)
            }

            if (discoverability == DiscoverabilityMode.DISCOVERABLE_LIMITED) {
                bluetoothAdapter.setDiscoverableTimeout(
                    Duration.ofSeconds(120)
                ) // limited discoverability needs a timeout, 120s is Android default
            }
            Empty.getDefaultInstance()
        }
    }

    override fun setConnectabilityMode(
        request: SetConnectabilityModeRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        grpcUnary(scope, responseObserver) {
            Log.d(TAG, "setConnectabilityMode")
            connectability = request.mode!!

            val scanMode =
                when (connectability) {
                    ConnectabilityMode.UNRECOGNIZED -> null
                    ConnectabilityMode.NOT_CONNECTABLE -> {
                        BluetoothAdapter.SCAN_MODE_NONE
                    }
                    ConnectabilityMode.CONNECTABLE -> {
                        if (
                            discoverability == DiscoverabilityMode.DISCOVERABLE_LIMITED ||
                                discoverability == DiscoverabilityMode.DISCOVERABLE_GENERAL
                        ) {
                            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
                        } else {
                            BluetoothAdapter.SCAN_MODE_CONNECTABLE
                        }
                    }
                }
            if (scanMode != null) {
                bluetoothAdapter.setScanMode(scanMode)
            }
            Empty.getDefaultInstance()
        }
    }
}
