/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.server.bluetooth

import android.bluetooth.BluetoothProfile
import android.bluetooth.IBluetooth
import android.bluetooth.IBluetoothCallback
import android.content.AttributionSource
import android.os.IBinder
import android.os.RemoteException
import com.android.modules.utils.SynchronousResultReceiver
import com.android.server.bluetooth.BluetoothManagerService.timeToLog
import java.time.Duration
import java.util.concurrent.TimeoutException

val SYNC_TIMEOUT = Duration.ofSeconds(3)

class AdapterBinder(rawBinder: IBinder) {
    private val TAG = "AdapterBinder"
    val adapterBinder: IBluetooth = IBluetooth.Stub.asInterface(rawBinder)
    val createdAt = System.currentTimeMillis()

    override fun toString(): String =
        "[Binder=" + adapterBinder.hashCode() + ", createdAt=" + timeToLog(createdAt) + "]"

    @Throws(RemoteException::class, TimeoutException::class)
    fun disable(source: AttributionSource): Boolean {
        val recv: SynchronousResultReceiver<Boolean> = SynchronousResultReceiver.get()
        adapterBinder.disable(source, recv)
        return recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(false)
    }

    @Throws(RemoteException::class, TimeoutException::class)
    fun enable(quietMode: Boolean, source: AttributionSource): Boolean {
        val recv: SynchronousResultReceiver<Boolean> = SynchronousResultReceiver.get()
        adapterBinder.enable(quietMode, source, recv)
        return recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(false)
    }

    @Throws(RemoteException::class, TimeoutException::class)
    fun getAddress(source: AttributionSource): String? {
        val recv: SynchronousResultReceiver<String> = SynchronousResultReceiver.get()
        adapterBinder.getAddress(source, recv)
        return recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(null)
    }

    @Throws(RemoteException::class, TimeoutException::class)
    fun getName(source: AttributionSource): String? {
        val recv: SynchronousResultReceiver<String> = SynchronousResultReceiver.get()
        adapterBinder.getName(source, recv)
        return recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(null)
    }

    @Throws(RemoteException::class, TimeoutException::class)
    fun stopBle(source: AttributionSource) {
        val recv: SynchronousResultReceiver<Any> = SynchronousResultReceiver.get()
        adapterBinder.stopBle(source, recv)
        recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(null)
    }

    @Throws(RemoteException::class, TimeoutException::class)
    fun startBrEdr(source: AttributionSource) {
        val recv: SynchronousResultReceiver<Any> = SynchronousResultReceiver.get()
        adapterBinder.startBrEdr(source, recv)
        recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(null)
    }

    @Throws(RemoteException::class, TimeoutException::class)
    fun registerCallback(callback: IBluetoothCallback, source: AttributionSource) {
        val recv: SynchronousResultReceiver<Any> = SynchronousResultReceiver.get()
        adapterBinder.registerCallback(callback, source, recv)
        recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(null)
    }

    @Throws(RemoteException::class, TimeoutException::class)
    fun unregisterCallback(callback: IBluetoothCallback, source: AttributionSource) {
        val recv: SynchronousResultReceiver<Any> = SynchronousResultReceiver.get()
        adapterBinder.unregisterCallback(callback, source, recv)
        recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(null)
    }

    @Throws(RemoteException::class, TimeoutException::class)
    fun getSupportedProfiles(source: AttributionSource): MutableList<Int> {
        val supportedProfiles = ArrayList<Int>()
        val recv: SynchronousResultReceiver<Long> = SynchronousResultReceiver.get()
        adapterBinder.getSupportedProfiles(source, recv)
        val supportedProfilesBitMask = recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(0L)
        for (i in 0..BluetoothProfile.MAX_PROFILE_ID) {
            if (supportedProfilesBitMask and (1 shl i).toLong() != 0L) {
                supportedProfiles.add(i)
            }
        }
        return supportedProfiles
    }

    @Throws(RemoteException::class)
    fun setForegroundUserId(userId: Int, source: AttributionSource) {
        adapterBinder.setForegroundUserId(userId, source)
    }

    @Throws(RemoteException::class, TimeoutException::class)
    fun unregAllGattClient(source: AttributionSource) {
        val recv: SynchronousResultReceiver<Any> = SynchronousResultReceiver.get()
        adapterBinder.unregAllGattClient(source, recv)
        recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(null)
    }

    fun isMediaProfileConnected(source: AttributionSource): Boolean {
        try {
            val recv: SynchronousResultReceiver<Boolean> = SynchronousResultReceiver.get()
            adapterBinder.isMediaProfileConnected(source, recv)
            return recv.awaitResultNoInterrupt(SYNC_TIMEOUT).getValue(false)
        } catch (ex: Exception) {
            when (ex) {
                is RemoteException,
                is TimeoutException -> {
                    Log.e(TAG, "Error when calling isMediaProfileConnected", ex)
                }
                else -> throw ex
            }
            return false
        }
    }
}
