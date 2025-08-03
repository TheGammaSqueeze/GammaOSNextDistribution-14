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
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.util.Log
import com.google.protobuf.ByteString
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import pandora.GattProto.*

/** GattInstance extends and simplifies Android GATT APIs without re-implementing them. */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class GattInstance(val mDevice: BluetoothDevice, val mTransport: Int, val mContext: Context) {
    private val TAG = "GattInstance"
    public val mGatt: BluetoothGatt

    private var mServiceDiscovered = MutableStateFlow(false)
    private var mConnectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    private var mValuesRead = MutableStateFlow(0)
    private var mValueWrote = MutableStateFlow(false)
    private var mOnCharacteristicChanged = MutableStateFlow(false)
    private var mCharacteristicChangedMap: MutableMap<BluetoothGattCharacteristic, Boolean> =
        mutableMapOf()

    /**
     * Wrapper for characteristic and descriptor reading. Uuid, startHandle and endHandle are used
     * to compare with the callback returned object. Value and status can be read once the read has
     * been done. ByteString and AttStatusCode are used to ensure compatibility with proto.
     */
    class GattInstanceValueRead(
        var uuid: UUID?,
        var handle: Int,
        var value: ByteString?,
        var status: AttStatusCode
    ) {}
    private var mGattInstanceValuesRead = arrayListOf<GattInstanceValueRead>()

    class GattInstanceValueWrote(var uuid: UUID?, var handle: Int, var status: AttStatusCode) {}
    private var mGattInstanceValueWrote =
        GattInstanceValueWrote(null, 0, AttStatusCode.UNKNOWN_ERROR)

    companion object GattManager {
        val gattInstances: MutableMap<String, GattInstance> = mutableMapOf<String, GattInstance>()
        fun get(address: String): GattInstance {
            val instance = gattInstances.get(address)
            requireNotNull(instance) { "Unable to find GATT instance for $address" }
            return instance
        }
        fun get(address: ByteString): GattInstance {
            val instance = gattInstances.get(address.toByteArray().decodeToString())
            requireNotNull(instance) { "Unable to find GATT instance for $address" }
            return instance
        }
        fun clearAllInstances() {
            gattInstances.clear()
        }
    }

    private val mCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                bluetoothGatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                Log.i(TAG, "$mDevice connection state changed to $newState")
                mConnectionState.value = newState
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gattInstances.remove(mDevice.address)
                }
            }

            override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Services have been discovered for $mDevice")
                    mServiceDiscovered.value = true
                }
            }

            override fun onCharacteristicRead(
                bluetoothGatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                Log.i(TAG, "onCharacteristicRead, status: $status")
                for (gattInstanceValueRead: GattInstanceValueRead in mGattInstanceValuesRead) {
                    if (
                        characteristic.getUuid() == gattInstanceValueRead.uuid &&
                            characteristic.getInstanceId() == gattInstanceValueRead.handle
                    ) {
                        gattInstanceValueRead.value = ByteString.copyFrom(value)
                        gattInstanceValueRead.status = AttStatusCode.forNumber(status)
                        mValuesRead.value++
                    }
                }
            }

            override fun onDescriptorRead(
                bluetoothGatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
                value: ByteArray
            ) {
                Log.i(TAG, "onDescriptorRead, status: $status")
                for (gattInstanceValueRead: GattInstanceValueRead in mGattInstanceValuesRead) {
                    if (
                        descriptor.getUuid() == gattInstanceValueRead.uuid &&
                            descriptor.getInstanceId() >= gattInstanceValueRead.handle
                    ) {
                        gattInstanceValueRead.value = ByteString.copyFrom(value)
                        gattInstanceValueRead.status = AttStatusCode.forNumber(status)
                        mValuesRead.value++
                    }
                }
            }

            override fun onCharacteristicWrite(
                bluetoothGatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                Log.i(TAG, "onCharacteristicWrite, status: $status")
                mGattInstanceValueWrote.status = AttStatusCode.forNumber(status)
                mValueWrote.value = true
            }

            override fun onDescriptorWrite(
                bluetoothGatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Log.i(TAG, "onDescriptorWrite, status: $status")
                mGattInstanceValueWrote.status = AttStatusCode.forNumber(status)
                mValueWrote.value = true
            }

            override fun onCharacteristicChanged(
                bluetoothGatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                Log.i(
                    TAG,
                    "onCharacteristicChanged, characteristic: " +
                        characteristic.getUuid().toString().uppercase()
                )
                mCharacteristicChangedMap[characteristic] = true
                mOnCharacteristicChanged.value = true
            }
        }

    init {
        if (!isBLETransport()) {
            require(isBonded()) { "Trying to connect non BLE GATT on a not bonded device $mDevice" }
        }
        require(gattInstances.get(mDevice.address) == null) {
            "Trying to connect GATT on an already connected device $mDevice"
        }

        mGatt = mDevice.connectGatt(mContext, false, mCallback, mTransport)

        checkNotNull(mGatt) { "Failed to connect GATT on $mDevice" }
        gattInstances.put(mDevice.address, this)
    }

    public fun isConnected(): Boolean {
        return mConnectionState.value == BluetoothProfile.STATE_CONNECTED
    }

    public fun isDisconnected(): Boolean {
        return mConnectionState.value == BluetoothProfile.STATE_DISCONNECTED
    }

    public fun isBonded(): Boolean {
        return mDevice.getBondState() == BluetoothDevice.BOND_BONDED
    }

    public fun isBLETransport(): Boolean {
        return mTransport == BluetoothDevice.TRANSPORT_LE
    }

    public fun servicesDiscovered(): Boolean {
        return mServiceDiscovered.value
    }

    public suspend fun waitForOnCharacteristicChanged(
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        if (mOnCharacteristicChanged.value == false) {
            mOnCharacteristicChanged.first { it == true }
        }
        return mCharacteristicChangedMap[characteristic] == true
    }

    public suspend fun waitForState(newState: Int) {
        if (mConnectionState.value != newState) {
            mConnectionState.first { it == newState }
        }
    }

    public suspend fun waitForDiscoveryEnd() {
        if (mServiceDiscovered.value != true) {
            mServiceDiscovered.first { it == true }
        }
    }

    public suspend fun waitForValuesReadEnd() {
        if (mValuesRead.value < mGattInstanceValuesRead.size) {
            mValuesRead.first { it == mGattInstanceValuesRead.size }
        }
        mValuesRead.value = 0
    }

    public suspend fun waitForValuesRead() {
        if (mValuesRead.value < mGattInstanceValuesRead.size) {
            mValuesRead.first { it == mGattInstanceValuesRead.size }
        }
    }

    public suspend fun waitForWriteEnd() {
        if (mValueWrote.value != true) {
            mValueWrote.first { it == true }
        }
        mValueWrote.value = false
    }

    public suspend fun readCharacteristicBlocking(
        characteristic: BluetoothGattCharacteristic
    ): GattInstanceValueRead {
        // Init mGattInstanceValuesRead with characteristic values.
        mGattInstanceValuesRead =
            arrayListOf(
                GattInstanceValueRead(
                    characteristic.getUuid(),
                    characteristic.getInstanceId(),
                    ByteString.EMPTY,
                    AttStatusCode.UNKNOWN_ERROR
                )
            )
        if (mGatt.readCharacteristic(characteristic)) {
            waitForValuesReadEnd()
        }
        // This method read only one characteristic.
        return mGattInstanceValuesRead.get(0)
    }

    public suspend fun readCharacteristicUuidBlocking(
        uuid: UUID,
        startHandle: Int,
        endHandle: Int
    ): ArrayList<GattInstanceValueRead> {
        mGattInstanceValuesRead = arrayListOf()
        // Init mGattInstanceValuesRead with characteristics values.
        for (service: BluetoothGattService in mGatt.services.orEmpty()) {
            for (characteristic: BluetoothGattCharacteristic in service.characteristics) {
                if (
                    characteristic.getUuid() == uuid &&
                        characteristic.getInstanceId() >= startHandle &&
                        characteristic.getInstanceId() <= endHandle
                ) {
                    mGattInstanceValuesRead.add(
                        GattInstanceValueRead(
                            uuid,
                            characteristic.getInstanceId(),
                            ByteString.EMPTY,
                            AttStatusCode.UNKNOWN_ERROR
                        )
                    )
                    check(
                        mGatt.readUsingCharacteristicUuid(
                            uuid,
                            characteristic.getInstanceId(),
                            characteristic.getInstanceId()
                        )
                    )
                    waitForValuesRead()
                }
            }
        }
        // All needed characteristics are read.
        mValuesRead.value = 0

        // When PTS tests with wrong UUID, we return an empty GattInstanceValueRead
        // with UNKNOWN_ERROR so the MMI can confirm the fail. We also have to try
        // and read the characteristic anyway for the PTS to validate the test.
        if (mGattInstanceValuesRead.size == 0) {
            mGattInstanceValuesRead.add(
                GattInstanceValueRead(
                    uuid,
                    startHandle,
                    ByteString.EMPTY,
                    AttStatusCode.UNKNOWN_ERROR
                )
            )
            mGatt.readUsingCharacteristicUuid(uuid, startHandle, endHandle)
        }
        return mGattInstanceValuesRead
    }

    public suspend fun readDescriptorBlocking(
        descriptor: BluetoothGattDescriptor
    ): GattInstanceValueRead {
        // Init mGattInstanceValuesRead with descriptor values.
        mGattInstanceValuesRead =
            arrayListOf(
                GattInstanceValueRead(
                    descriptor.getUuid(),
                    descriptor.getInstanceId(),
                    ByteString.EMPTY,
                    AttStatusCode.UNKNOWN_ERROR
                )
            )
        if (mGatt.readDescriptor(descriptor)) {
            waitForValuesReadEnd()
        }
        // This method read only one descriptor.
        return mGattInstanceValuesRead.get(0)
    }

    public suspend fun writeCharacteristicBlocking(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): GattInstanceValueWrote {
        GattInstanceValueWrote(
            characteristic.getUuid(),
            characteristic.getInstanceId(),
            AttStatusCode.UNKNOWN_ERROR
        )
        if (
            mGatt.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        ) {
            waitForWriteEnd()
        }
        return mGattInstanceValueWrote
    }

    public suspend fun writeDescriptorBlocking(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): GattInstanceValueWrote {
        GattInstanceValueWrote(
            descriptor.getUuid(),
            descriptor.getInstanceId(),
            AttStatusCode.UNKNOWN_ERROR
        )
        if (mGatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS) {
            waitForWriteEnd()
        }
        return mGattInstanceValueWrote
    }

    public fun disconnectInstance() {
        require(isConnected()) { "Trying to disconnect an already disconnected device $mDevice" }
        mGatt.disconnect()
        gattInstances.remove(mDevice.address)
    }

    override fun toString(): String {
        return "GattInstance($mDevice)"
    }
}
