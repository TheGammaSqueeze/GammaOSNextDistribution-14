/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.companion.cts.common

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.companion.DevicePresenceEvent.EVENT_BT_CONNECTED
import android.companion.DevicePresenceEvent.EVENT_BT_DISCONNECTED
import android.content.Intent
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import java.util.Collections.synchronizedMap
import java.util.Collections.synchronizedSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class CompanionService<T : CompanionService<T>>(
    private val instanceHolder: InstanceHolder<T>
) : CompanionDeviceService() {
    @Volatile var isBound: Boolean = false
        private set(isBound) {
            Log.d(TAG, "$this.isBound=$isBound")
            if (!isBound && !connectedDevices.isEmpty()) {
                error("Unbinding while there are connected devices")
            }
            field = isBound
        }

    var currentEvent: Int = -2

    val connectedDevices: Collection<AssociationInfo>
        get() = _connectedDevices.values

    val associationIdsForConnectedDevices: Collection<Int>
        get() = _connectedDevices.keys

    val connectedUuidDevices: MutableSet<ParcelUuid?> = synchronizedSet(mutableSetOf())

    val associationIdsForBtBondDevices: MutableSet<Int> = synchronizedSet(mutableSetOf())

    private val _connectedDevices: MutableMap<Int, AssociationInfo> =
            synchronizedMap(mutableMapOf())

    override fun onCreate() {
        Log.d(TAG, "$this.onCreate()")
        super.onCreate()
        instanceHolder.instance = this as T
    }

    override fun onBindCompanionDeviceService(intent: Intent) {
        Log.d(TAG, "$this.onBindCompanionDeviceService()")
        isBound = true
    }

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Log.d(TAG, "$this.onDevice_Appeared(), association=$associationInfo")
        _connectedDevices[associationInfo.id] = associationInfo

        super.onDeviceAppeared(associationInfo)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Log.d(TAG, "$this.onDevice_Disappeared(), association=$associationInfo")
        _connectedDevices.remove(associationInfo.id)

        super.onDeviceDisappeared(associationInfo)
    }

    override fun onDevicePresenceEvent(devicePresenceEvent: DevicePresenceEvent) {
        val event = devicePresenceEvent.event
        currentEvent = event
        Log.i("evanxinchen", "onDevicePresenceEvent: $currentEvent")

        if (devicePresenceEvent.uuid == null) {
            Log.i(
                TAG,
                "$this.onDevicePresenceEvent(), " +
                        "association id=${devicePresenceEvent.associationId}" + "event is: $event"
            )

            var associationId: Int = devicePresenceEvent.associationId
            if (event == EVENT_BT_CONNECTED) {
                associationIdsForBtBondDevices.add(associationId)
            } else if (event == EVENT_BT_DISCONNECTED) {
                associationIdsForBtBondDevices.remove(associationId)
                    ?: error("onDeviceDisconnected() has not been called for association with id " +
                            "${devicePresenceEvent.associationId}")
            }
        } else {
            val uuid: ParcelUuid? = devicePresenceEvent.uuid
            Log.i(TAG, "$this.onDeviceEvent(), ParcelUuid=$uuid event is: $event")
            if (event == EVENT_BT_CONNECTED) {
                connectedUuidDevices.add(uuid)
            } else if (event == EVENT_BT_DISCONNECTED) {
                if (!connectedUuidDevices.remove(uuid)) {
                    error(
                        "onDeviceEvent() with event " +
                                "$EVENT_BT_CONNECTED has not been called"
                    )
                }
            }
        }

        super.onDevicePresenceEvent(devicePresenceEvent)
    }

    // For now, we need to "post" a Runnable that sets isBound to false to the Main Thread's
    // Handler, because this may be called between invocations of
    // CompanionDeviceService.Stub.onDeviceAppeared() and the "real"
    // CompanionDeviceService.onDeviceAppeared(), which would cause an error() in isBound setter.
    override fun onUnbind(intent: Intent?) = super.onUnbind(intent)
            .also {
                Log.d(TAG, "$this.onUnbind()")
                Handler.getMain().post { isBound = false }
            }

    override fun onDestroy() {
        Log.d(TAG, "$this.onDestroy()")
        instanceHolder.instance = null
        super.onDestroy()
    }

    fun removeConnectedDevice(associationId: Int) {
        _connectedDevices.remove(associationId)
    }
}

sealed class InstanceHolder<T : CompanionService<T>> {
    // Need synchronization, because the setter will be called from the Main thread, while the
    // getter is expected to be called mostly from the instrumentation thread.
    var instance: T? = null
        @Synchronized internal set

        @Synchronized get

    val isBound: Boolean
        get() = instance?.isBound ?: false

    val connectedDevices: Collection<AssociationInfo>
        get() = instance?.connectedDevices ?: emptySet()

    val connectedBtBondDevices: Collection<AssociationInfo>
        get() = instance?.connectedDevices ?: emptySet()

    val connectedUuidBondDevices: Collection<ParcelUuid?>
        get() = instance?.connectedUuidDevices ?: emptySet()

    val associationIdsForConnectedDevices: Collection<Int>
        get() = instance?.associationIdsForConnectedDevices ?: emptySet()

    val associationIdsForBtBondDevices: Collection<Int>
        get() = instance?.associationIdsForBtBondDevices ?: emptySet()

    fun waitForBind(timeout: Duration = 1.seconds) {
        if (!waitFor(timeout) { isBound }) {
            throw AssertionError("Service hasn't been bound")
        }
    }

    fun waitForUnbind(timeout: Duration) {
        if (!waitFor(timeout) { !isBound }) {
            throw AssertionError("Service hasn't been unbound")
        }
    }

    fun waitAssociationToAppear(associationId: Int, timeout: Duration = 1.seconds) {
        val appeared = waitFor(timeout) {
            associationIdsForConnectedDevices.contains(associationId)
        }
        if (!appeared) {
            throw AssertionError("""Association with $associationId hasn't "appeared"""")
        }
    }

    fun waitAssociationToDisappear(associationId: Int, timeout: Duration = 1.seconds) {
        val gone = waitFor(timeout) {
            !associationIdsForConnectedDevices.contains(associationId)
        }
        if (!gone) throw AssertionError("""Association with $associationId hasn't "disappeared"""")
    }

    fun waitAssociationToBtConnect(associationId: Int, timeout: Duration = 1.seconds) {
        val appeared = waitFor(timeout) {
            associationIdsForBtBondDevices.contains(associationId)
        }
        if (!appeared) {
            throw AssertionError("""Association with$associationId hasn't "connected"""")
        }
    }

    fun waitAssociationToBtDisconnect(associationId: Int, timeout: Duration = 1.seconds) {
        val gone = waitFor(timeout) {
            !associationIdsForBtBondDevices.contains(associationId)
        }
        if (!gone) {
            throw AssertionError("""Association with $associationId hasn't "disconnected"""")
        }
    }

    fun waitDeviceUuidConnect(uuid: ParcelUuid, timeout: Duration = 1.seconds) {
        val appeared = waitFor(timeout) {
            connectedUuidBondDevices.contains(uuid)
        }
        if (!appeared) {
            throw AssertionError("""Uuid $uuid hasn't "connected"""")
        }
    }

    fun waitDeviceUuidDisconnect(uuid: ParcelUuid, timeout: Duration = 1.seconds) {
        val gone = waitFor(timeout) {
            !connectedUuidBondDevices.contains(uuid)
        }
        if (!gone) {
            throw AssertionError("""Uuid $uuid hasn't "disconnected"""")
        }
    }

    // This is a useful function to use to conveniently "forget" that a device is currently present.
    // Use to bypass the "unbinding while there are connected devices" for simulated devices.
    // (Don't worry! they would have removed themselves after 1 minute anyways!)
    fun forgetDevicePresence(associationId: Int) {
        instance?.removeConnectedDevice(associationId)
    }

    fun clearDeviceUuidPresence() {
        instance?.connectedUuidDevices?.clear()
    }

    fun getCurrentEvent(): Int? {
        return instance?.currentEvent
    }
}

class PrimaryCompanionService : CompanionService<PrimaryCompanionService>(Companion) {
    companion object : InstanceHolder<PrimaryCompanionService>()
}

class SecondaryCompanionService : CompanionService<SecondaryCompanionService>(Companion) {
    companion object : InstanceHolder<SecondaryCompanionService>()
}

class MissingPermissionCompanionService : CompanionService<
        MissingPermissionCompanionService>(Companion) {
    companion object : InstanceHolder<MissingPermissionCompanionService>()
}

class MissingIntentFilterActionCompanionService : CompanionService<
        MissingIntentFilterActionCompanionService>(Companion) {
    companion object : InstanceHolder<MissingIntentFilterActionCompanionService>()
}
