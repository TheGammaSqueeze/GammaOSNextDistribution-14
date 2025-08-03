/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.csip;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.UUID;

/**
 * CSIP Set Coordinator role native interface
 */
public class CsipSetCoordinatorNativeInterface {
    private static final String TAG = "CsipSetCoordinatorNativeInterface";
    private static final boolean DBG = false;
    private BluetoothAdapter mAdapter;

    @GuardedBy("INSTANCE_LOCK")
    private static CsipSetCoordinatorNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private CsipSetCoordinatorNativeInterface() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mAdapter == null) {
            Log.wtf(TAG, "No Bluetooth Adapter Available");
        }
    }

    /**
     * Get singleton instance.
     */
    public static CsipSetCoordinatorNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new CsipSetCoordinatorNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(CsipSetCoordinatorNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    /**
     * Initializes the native interface.
     *
     * priorities to configure.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void init() {
        initNative();
    }

    /**
     * Cleanup the native interface.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void cleanup() {
        cleanupNative();
    }

    /**
     * Initiates CsipSetCoordinator connection to a remote device.
     *
     * @param device the remote device
     * @return true on success, otherwise false.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean connect(BluetoothDevice device) {
        return connectNative(getByteAddress(device));
    }

    /**
     * Disconnects CsipSetCoordinator from a remote device.
     *
     * @param device the remote device
     * @return true on success, otherwise false.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public boolean disconnect(BluetoothDevice device) {
        return disconnectNative(getByteAddress(device));
    }

    /**
     * Get the device by the address
     *
     * @return the device
     */
    @VisibleForTesting
    public BluetoothDevice getDevice(byte[] address) {
        return mAdapter.getRemoteDevice(address);
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        if (device == null) {
            return Utils.getBytesFromAddress("00:00:00:00:00:00");
        }
        return Utils.getBytesFromAddress(device.getAddress());
    }

    private void sendMessageToService(CsipSetCoordinatorStackEvent event) {
        CsipSetCoordinatorService service =
                CsipSetCoordinatorService.getCsipSetCoordinatorService();
        if (service != null) {
            service.messageFromNative(event);
        } else {
            Log.e(TAG, "Event ignored, service not available: " + event);
        }
    }

    // Callbacks from the native stack back into the Java framework.
    // All callbacks are routed via the Service which will disambiguate which
    // state machine the message should be routed to.

    /** Device connection state change */
    @VisibleForTesting
    public void onConnectionStateChanged(byte[] address, int state) {
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.device = getDevice(address);
        event.valueInt1 = state;

        if (DBG) {
            Log.d(TAG, "onConnectionStateChanged: " + event);
        }
        sendMessageToService(event);
    }

    /** Device availability */
    @VisibleForTesting
    public void onDeviceAvailable(
            byte[] address, int groupId, int groupSize, int rank, long uuidLsb, long uuidMsb) {
        UUID uuid = new UUID(uuidMsb, uuidLsb);
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_DEVICE_AVAILABLE);
        event.device = getDevice(address);
        event.valueInt1 = groupId;
        event.valueInt2 = groupSize;
        event.valueInt3 = rank;
        event.valueUuid1 = uuid;

        if (DBG) {
            Log.d(TAG, "onDeviceAvailable: " + event);
        }
        sendMessageToService(event);
    }

    // Callbacks from the native stack back into the Java framework.
    // All callbacks are routed via the Service which will disambiguate which
    // state machine the message should be routed to.

    /**
     * Set member available callback
     */
    @VisibleForTesting
    public void onSetMemberAvailable(byte[] address, int groupId) {
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_SET_MEMBER_AVAILABLE);
        event.device = getDevice(address);
        event.valueInt1 = groupId;
        if (DBG) {
            Log.d(TAG, "onSetMemberAvailable: " + event);
        }
        sendMessageToService(event);
    }

    /**
     * Group lock changed callback as a result of lock or unlock request or
     * autonomous event.
     *
     * @param groupId group identifier
     * @param locked whether group is locked or unlocked
     * @param status status of a requested lock/unlock
     */
    @VisibleForTesting
    public void onGroupLockChanged(int groupId, boolean locked, int status) {
        CsipSetCoordinatorStackEvent event = new CsipSetCoordinatorStackEvent(
                CsipSetCoordinatorStackEvent.EVENT_TYPE_GROUP_LOCK_CHANGED);
        event.valueInt1 = groupId;
        event.valueInt2 = status;
        event.valueBool1 = locked;
        if (DBG) {
            Log.d(TAG, "onGroupLockChanged: " + event);
        }
        sendMessageToService(event);
    }

    /**
     * Set lock on the group.
     * @param groupId  group identifier
     * @param lock  True for lock, false for unlock
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void groupLockSet(int groupId, boolean lock) {
        groupLockSetNative(groupId, lock);
    }

    // Native methods that call into the JNI interface
    private native void initNative();
    private native void cleanupNative();
    private native boolean connectNative(byte[] address);
    private native boolean disconnectNative(byte[] address);
    private native void groupLockSetNative(int groupId, boolean lock);
}
