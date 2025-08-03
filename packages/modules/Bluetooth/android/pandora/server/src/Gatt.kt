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
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.grpc.stub.StreamObserver
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import pandora.GATTGrpc.GATTImplBase
import pandora.GattProto.*

@kotlinx.coroutines.ExperimentalCoroutinesApi
class Gatt(private val context: Context) : GATTImplBase(), Closeable {
    private val TAG = "PandoraGatt"

    private val mScope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    private val mBluetoothManager = context.getSystemService(BluetoothManager::class.java)!!
    private val mBluetoothAdapter = mBluetoothManager.adapter

    private val serverManager by lazy { GattServerManager(mBluetoothManager, context, mScope) }

    private val flow: Flow<Intent>
    init {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_UUID)

        flow = intentFlow(context, intentFilter, mScope).shareIn(mScope, SharingStarted.Eagerly)
    }

    override fun close() {
        serverManager.server.close()
        mScope.cancel()
        // Clear existing Gatt instances to fix flakiness: b/279599889
        GattInstance.clearAllInstances()
    }

    override fun exchangeMTU(
        request: ExchangeMTURequest,
        responseObserver: StreamObserver<ExchangeMTUResponse>
    ) {
        grpcUnary<ExchangeMTUResponse>(mScope, responseObserver) {
            val mtu = request.mtu
            Log.i(TAG, "exchangeMTU MTU=$mtu")
            if (!GattInstance.get(request.connection.address).mGatt.requestMtu(mtu)) {
                throw RuntimeException("Error on requesting MTU $mtu")
            }
            ExchangeMTUResponse.newBuilder().build()
        }
    }

    override fun writeAttFromHandle(
        request: WriteRequest,
        responseObserver: StreamObserver<WriteResponse>
    ) {
        grpcUnary<WriteResponse>(mScope, responseObserver) {
            Log.i(TAG, "writeAttFromHandle handle=${request.handle}")
            val gattInstance = GattInstance.get(request.connection.address)
            var characteristic: BluetoothGattCharacteristic? =
                getCharacteristicWithHandle(request.handle, gattInstance)
            if (characteristic == null) {
                val descriptor: BluetoothGattDescriptor? =
                    getDescriptorWithHandle(request.handle, gattInstance)
                checkNotNull(descriptor) {
                    "Found no characteristic or descriptor with handle ${request.handle}"
                }
                val valueWrote =
                    gattInstance.writeDescriptorBlocking(descriptor, request.value.toByteArray())
                WriteResponse.newBuilder()
                    .setHandle(valueWrote.handle)
                    .setStatus(valueWrote.status)
                    .build()
            } else {
                val valueWrote =
                    gattInstance.writeCharacteristicBlocking(
                        characteristic,
                        request.value.toByteArray()
                    )
                WriteResponse.newBuilder()
                    .setHandle(valueWrote.handle)
                    .setStatus(valueWrote.status)
                    .build()
            }
        }
    }

    override fun discoverServiceByUuid(
        request: DiscoverServiceByUuidRequest,
        responseObserver: StreamObserver<DiscoverServicesResponse>
    ) {
        grpcUnary<DiscoverServicesResponse>(mScope, responseObserver) {
            val gattInstance = GattInstance.get(request.connection.address)
            Log.i(TAG, "discoverServiceByUuid uuid=${request.uuid}")
            // In some cases, GATT starts a discovery immediately after being connected, so
            // we need to wait until the service discovery is finished to be able to discover again.
            // This takes between 20s and 28s, and there is no way to know if the service is busy or
            // not.
            // Delay was originally 30s, but due to flakyness increased to 32s.
            delay(32000L)
            check(gattInstance.mGatt.discoverServiceByUuid(UUID.fromString(request.uuid)))
            // BluetoothGatt#discoverServiceByUuid does not trigger any callback and does not return
            // any service, the API was made for PTS testing only.
            DiscoverServicesResponse.newBuilder().build()
        }
    }

    override fun discoverServices(
        request: DiscoverServicesRequest,
        responseObserver: StreamObserver<DiscoverServicesResponse>
    ) {
        grpcUnary<DiscoverServicesResponse>(mScope, responseObserver) {
            Log.i(TAG, "discoverServices")
            val gattInstance = GattInstance.get(request.connection.address)
            check(gattInstance.mGatt.discoverServices())
            gattInstance.waitForDiscoveryEnd()
            DiscoverServicesResponse.newBuilder()
                .addAllServices(generateServicesList(gattInstance.mGatt.services, 1))
                .build()
        }
    }

    override fun discoverServicesSdp(
        request: DiscoverServicesSdpRequest,
        responseObserver: StreamObserver<DiscoverServicesSdpResponse>
    ) {
        grpcUnary<DiscoverServicesSdpResponse>(mScope, responseObserver) {
            Log.i(TAG, "discoverServicesSdp")
            val bluetoothDevice = request.address.toBluetoothDevice(mBluetoothAdapter)
            check(bluetoothDevice.fetchUuidsWithSdp())
            // Several ACTION_UUID could be sent and some of them are empty (null)
            flow
                .filter { it.getAction() == BluetoothDevice.ACTION_UUID }
                .filter { it.getBluetoothDeviceExtra() == bluetoothDevice }
                .take(2)
                .filter { bluetoothDevice.getUuids() != null }
                .first()
            val uuidsList = arrayListOf<String>()
            for (parcelUuid in bluetoothDevice.getUuids()) {
                uuidsList.add(parcelUuid.toString().uppercase())
            }
            DiscoverServicesSdpResponse.newBuilder().addAllServiceUuids(uuidsList).build()
        }
    }

    override fun clearCache(
        request: ClearCacheRequest,
        responseObserver: StreamObserver<ClearCacheResponse>
    ) {
        grpcUnary<ClearCacheResponse>(mScope, responseObserver) {
            Log.i(TAG, "clearCache")
            val gattInstance = GattInstance.get(request.connection.address)
            check(gattInstance.mGatt.refresh())
            ClearCacheResponse.newBuilder().build()
        }
    }

    override fun readCharacteristicFromHandle(
        request: ReadCharacteristicRequest,
        responseObserver: StreamObserver<ReadCharacteristicResponse>
    ) {
        grpcUnary<ReadCharacteristicResponse>(mScope, responseObserver) {
            Log.i(TAG, "readCharacteristicFromHandle handle=${request.handle}")
            val gattInstance = GattInstance.get(request.connection.address)
            val characteristic: BluetoothGattCharacteristic? =
                getCharacteristicWithHandle(request.handle, gattInstance)
            checkNotNull(characteristic) { "Characteristic handle ${request.handle} not found." }
            val readValue = gattInstance.readCharacteristicBlocking(characteristic)
            ReadCharacteristicResponse.newBuilder()
                .setValue(
                    AttValue.newBuilder().setHandle(readValue.handle).setValue(readValue.value)
                )
                .setStatus(readValue.status)
                .build()
        }
    }

    override fun readCharacteristicsFromUuid(
        request: ReadCharacteristicsFromUuidRequest,
        responseObserver: StreamObserver<ReadCharacteristicsFromUuidResponse>
    ) {
        grpcUnary<ReadCharacteristicsFromUuidResponse>(mScope, responseObserver) {
            Log.i(TAG, "readCharacteristicsFromUuid uuid=${request.uuid}")
            val gattInstance = GattInstance.get(request.connection.address)
            tryDiscoverServices(gattInstance)
            val readValues =
                gattInstance.readCharacteristicUuidBlocking(
                    UUID.fromString(request.uuid),
                    request.startHandle,
                    request.endHandle
                )
            ReadCharacteristicsFromUuidResponse.newBuilder()
                .addAllCharacteristicsRead(generateReadValuesList(readValues))
                .build()
        }
    }

    override fun readCharacteristicDescriptorFromHandle(
        request: ReadCharacteristicDescriptorRequest,
        responseObserver: StreamObserver<ReadCharacteristicDescriptorResponse>
    ) {
        grpcUnary<ReadCharacteristicDescriptorResponse>(mScope, responseObserver) {
            Log.i(TAG, "readCharacteristicDescriptorFromHandle handle=${request.handle}")
            val gattInstance = GattInstance.get(request.connection.address)
            val descriptor: BluetoothGattDescriptor? =
                getDescriptorWithHandle(request.handle, gattInstance)
            checkNotNull(descriptor) { "Descriptor handle ${request.handle} not found." }
            val readValue = gattInstance.readDescriptorBlocking(descriptor)
            ReadCharacteristicDescriptorResponse.newBuilder()
                .setValue(
                    AttValue.newBuilder().setHandle(readValue.handle).setValue(readValue.value)
                )
                .setStatus(readValue.status)
                .build()
        }
    }

    override fun registerService(
        request: RegisterServiceRequest,
        responseObserver: StreamObserver<RegisterServiceResponse>
    ) {
        grpcUnary(mScope, responseObserver) {
            Log.i(TAG, "registerService")
            val service =
                BluetoothGattService(UUID.fromString(request.service.uuid), SERVICE_TYPE_PRIMARY)
            for (characteristic_params in request.service.characteristicsList) {
                val characteristic =
                    BluetoothGattCharacteristic(
                        UUID.fromString(characteristic_params.uuid),
                        characteristic_params.properties,
                        characteristic_params.permissions
                    )
                for (descriptor_params in characteristic_params.descriptorsList) {
                    characteristic.addDescriptor(
                        BluetoothGattDescriptor(
                            UUID.fromString(descriptor_params.uuid),
                            descriptor_params.properties,
                            descriptor_params.permissions
                        )
                    )
                }
                service.addCharacteristic(characteristic)
            }

            serverManager.server.addService(service)
            val addedService = serverManager.serviceFlow.first()

            RegisterServiceResponse.newBuilder()
                .setService(
                    GattService.newBuilder()
                        .setHandle(addedService.instanceId)
                        .setType(addedService.type)
                        .setUuid(addedService.uuid.toString().uppercase())
                        .addAllIncludedServices(generateServicesList(service.includedServices, 1))
                        .addAllCharacteristics(generateCharacteristicsList(service.characteristics))
                        .build()
                )
                .build()
        }
    }

    override fun setCharacteristicNotificationFromHandle(
        request: SetCharacteristicNotificationFromHandleRequest,
        responseObserver: StreamObserver<SetCharacteristicNotificationFromHandleResponse>
    ) {
        grpcUnary<SetCharacteristicNotificationFromHandleResponse>(mScope, responseObserver) {
            Log.i(TAG, "SetCharcteristicNotificationFromHandle")
            val gattInstance = GattInstance.get(request.connection.address)
            val descriptor: BluetoothGattDescriptor? =
                getDescriptorWithHandle(request.handle, gattInstance)
            checkNotNull(descriptor) { "Found no descriptor with handle ${request.handle}" }
            var characteristic = descriptor.getCharacteristic()
            gattInstance.mGatt.setCharacteristicNotification(characteristic, true)
            if (request.enableValue == EnableValue.ENABLE_INDICATION_VALUE) {
                val valueWrote =
                    gattInstance.writeDescriptorBlocking(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    )
                SetCharacteristicNotificationFromHandleResponse.newBuilder()
                    .setHandle(valueWrote.handle)
                    .setStatus(valueWrote.status)
                    .build()
            } else {
                val valueWrote =
                    gattInstance.writeDescriptorBlocking(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                SetCharacteristicNotificationFromHandleResponse.newBuilder()
                    .setHandle(valueWrote.handle)
                    .setStatus(valueWrote.status)
                    .build()
            }
        }
    }

    override fun waitCharacteristicNotification(
        request: WaitCharacteristicNotificationRequest,
        responseObserver: StreamObserver<WaitCharacteristicNotificationResponse>
    ) {
        grpcUnary<WaitCharacteristicNotificationResponse>(mScope, responseObserver) {
            val gattInstance = GattInstance.get(request.connection.address)
            val descriptor: BluetoothGattDescriptor? =
                getDescriptorWithHandle(request.handle, gattInstance)
            checkNotNull(descriptor) { "Found no descriptor with handle ${request.handle}" }
            var characteristic = descriptor.getCharacteristic()
            val characteristicNotificationReceived =
                gattInstance.waitForOnCharacteristicChanged(characteristic)
            WaitCharacteristicNotificationResponse.newBuilder()
                .setCharacteristicNotificationReceived(characteristicNotificationReceived)
                .build()
        }
    }

    /**
     * Discovers services, then returns characteristic with given handle. BluetoothGatt API is
     * package-private so we have to redefine it here.
     */
    private suspend fun getCharacteristicWithHandle(
        handle: Int,
        gattInstance: GattInstance
    ): BluetoothGattCharacteristic? {
        tryDiscoverServices(gattInstance)
        for (service: BluetoothGattService in gattInstance.mGatt.services.orEmpty()) {
            for (characteristic: BluetoothGattCharacteristic in service.characteristics) {
                if (characteristic.instanceId == handle) {
                    return characteristic
                }
            }
        }
        return null
    }

    /**
     * Discovers services, then returns descriptor with given handle. BluetoothGatt API is
     * package-private so we have to redefine it here.
     */
    private suspend fun getDescriptorWithHandle(
        handle: Int,
        gattInstance: GattInstance
    ): BluetoothGattDescriptor? {
        tryDiscoverServices(gattInstance)
        for (service: BluetoothGattService in gattInstance.mGatt.services.orEmpty()) {
            for (characteristic: BluetoothGattCharacteristic in service.characteristics) {
                for (descriptor: BluetoothGattDescriptor in characteristic.descriptors) {
                    if (descriptor.getInstanceId() == handle) {
                        return descriptor
                    }
                }
            }
        }
        return null
    }

    /** Generates a list of GattService from a list of BluetoothGattService. */
    private fun generateServicesList(
        servicesList: List<BluetoothGattService>,
        dpth: Int
    ): ArrayList<GattService> {
        val newServicesList = arrayListOf<GattService>()
        for (service in servicesList) {
            val serviceBuilder =
                GattService.newBuilder()
                    .setHandle(service.getInstanceId())
                    .setType(service.getType())
                    .setUuid(service.getUuid().toString().uppercase())
                    .addAllIncludedServices(
                        generateServicesList(service.getIncludedServices(), dpth + 1)
                    )
                    .addAllCharacteristics(generateCharacteristicsList(service.characteristics))
            newServicesList.add(serviceBuilder.build())
        }
        return newServicesList
    }

    /** Generates a list of GattCharacteristic from a list of BluetoothGattCharacteristic. */
    private fun generateCharacteristicsList(
        characteristicsList: List<BluetoothGattCharacteristic>
    ): ArrayList<GattCharacteristic> {
        val newCharacteristicsList = arrayListOf<GattCharacteristic>()
        for (characteristic in characteristicsList) {
            val characteristicBuilder =
                GattCharacteristic.newBuilder()
                    .setProperties(characteristic.getProperties())
                    .setPermissions(characteristic.getPermissions())
                    .setUuid(characteristic.getUuid().toString().uppercase())
                    .addAllDescriptors(generateDescriptorsList(characteristic.getDescriptors()))
                    .setHandle(characteristic.getInstanceId())
            newCharacteristicsList.add(characteristicBuilder.build())
        }
        return newCharacteristicsList
    }

    /** Generates a list of GattCharacteristicDescriptor from a list of BluetoothGattDescriptor. */
    private fun generateDescriptorsList(
        descriptorsList: List<BluetoothGattDescriptor>
    ): ArrayList<GattCharacteristicDescriptor> {
        val newDescriptorsList = arrayListOf<GattCharacteristicDescriptor>()
        for (descriptor in descriptorsList) {
            val descriptorBuilder =
                GattCharacteristicDescriptor.newBuilder()
                    .setHandle(descriptor.getInstanceId())
                    .setPermissions(descriptor.getPermissions())
                    .setUuid(descriptor.getUuid().toString().uppercase())
            newDescriptorsList.add(descriptorBuilder.build())
        }
        return newDescriptorsList
    }

    /** Generates a list of ReadCharacteristicResponse from a list of GattInstanceValueRead. */
    private fun generateReadValuesList(
        readValuesList: ArrayList<GattInstance.GattInstanceValueRead>
    ): ArrayList<ReadCharacteristicResponse> {
        val newReadValuesList = arrayListOf<ReadCharacteristicResponse>()
        for (readValue in readValuesList) {
            val readValueBuilder =
                ReadCharacteristicResponse.newBuilder()
                    .setValue(
                        AttValue.newBuilder().setHandle(readValue.handle).setValue(readValue.value)
                    )
                    .setStatus(readValue.status)
            newReadValuesList.add(readValueBuilder.build())
        }
        return newReadValuesList
    }

    private suspend fun tryDiscoverServices(gattInstance: GattInstance) {
        if (!gattInstance.servicesDiscovered() && !gattInstance.mGatt.discoverServices()) {
            throw RuntimeException("Error on discovering services for $gattInstance")
        } else {
            gattInstance.waitForDiscoveryEnd()
        }
    }
}
