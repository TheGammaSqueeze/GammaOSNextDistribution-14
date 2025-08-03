/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.pan;

import static java.util.Objects.requireNonNull;

import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/** Provides Bluetooth Pan native interface for the Pan service */
public class PanNativeInterface {
    private static final String TAG = PanNativeInterface.class.getSimpleName();
    private PanService mPanService;

    @GuardedBy("INSTANCE_LOCK")
    private static PanNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private PanNativeInterface() {}

    /** Get singleton instance. */
    public static PanNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new PanNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(PanNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    void init(PanService panService) {
        mPanService = panService;
        initializeNative();
    }

    void cleanup() {
        cleanupNative();
    }

    boolean connect(byte[] identityAddress) {
        requireNonNull(identityAddress, "Identity address can not be null");
        return connectPanNative(
                identityAddress, BluetoothPan.LOCAL_PANU_ROLE, BluetoothPan.REMOTE_NAP_ROLE);
    }

    boolean disconnect(byte[] identityAddress) {
        requireNonNull(identityAddress, "Identity address can not be null");
        return disconnectPanNative(identityAddress);
    }

    /**********************************************************************************************/
    /*********************************** callbacks from native ************************************/
    /**********************************************************************************************/

    void onControlStateChanged(int localRole, int halState, int error, String ifname) {
        mPanService.onControlStateChanged(localRole, convertHalState(halState), error, ifname);
    }

    void onConnectStateChanged(
            byte[] address, int halState, int error, int localRole, int remoteRole) {
        mPanService.onConnectStateChanged(
                address, convertHalState(halState), error, localRole, remoteRole);
    }

    @VisibleForTesting
    static int convertHalState(int halState) {
        switch (halState) {
            case CONN_STATE_CONNECTED:
                return BluetoothProfile.STATE_CONNECTED;
            case CONN_STATE_CONNECTING:
                return BluetoothProfile.STATE_CONNECTING;
            case CONN_STATE_DISCONNECTED:
                return BluetoothProfile.STATE_DISCONNECTED;
            case CONN_STATE_DISCONNECTING:
                return BluetoothProfile.STATE_DISCONNECTING;
            default:
                Log.e(TAG, "Invalid pan connection state: " + halState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    /**********************************************************************************************/
    /******************************************* native *******************************************/
    /**********************************************************************************************/

    // Constants matching Hal header file bt_hh.h: bthh_connection_state_t

    @VisibleForTesting static final int CONN_STATE_CONNECTED = 0;

    @VisibleForTesting static final int CONN_STATE_CONNECTING = 1;

    @VisibleForTesting static final int CONN_STATE_DISCONNECTED = 2;

    @VisibleForTesting static final int CONN_STATE_DISCONNECTING = 3;

    private native void initializeNative();

    private native void cleanupNative();

    private native boolean connectPanNative(byte[] btAddress, int localRole, int remoteRole);

    private native boolean disconnectPanNative(byte[] btAddress);
}
