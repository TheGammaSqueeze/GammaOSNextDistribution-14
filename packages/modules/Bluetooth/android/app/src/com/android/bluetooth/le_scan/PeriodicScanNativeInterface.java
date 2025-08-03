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

package com.android.bluetooth.le_scan;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.android.bluetooth.gatt.GattServiceConfig;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/** NativeInterface for PeriodicScanManager */
public class PeriodicScanNativeInterface {
    private static final String TAG = PeriodicScanNativeInterface.class.getSimpleName();
    private static final boolean DBG = GattServiceConfig.DBG;

    private static final int PA_SOURCE_LOCAL = 1;
    private static final int PA_SOURCE_REMOTE = 2;

    private PeriodicScanManager mManager;

    @GuardedBy("INSTANCE_LOCK")
    private static PeriodicScanNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private PeriodicScanNativeInterface() {}

    static PeriodicScanNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new PeriodicScanNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(PeriodicScanNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    void init(PeriodicScanManager manager) {
        mManager = manager;
        initializeNative();
    }

    void cleanup() {
        cleanupNative();
    }

    void startSync(int sid, String address, int skip, int timeout, int regId) {
        startSyncNative(sid, address, skip, timeout, regId);
    }

    void stopSync(int syncHandle) {
        stopSyncNative(syncHandle);
    }

    void cancelSync(int sid, String address) {
        cancelSyncNative(sid, address);
    }

    void syncTransfer(BluetoothDevice bda, int serviceData, int syncHandle) {
        syncTransferNative(PA_SOURCE_REMOTE, bda.getAddress(), serviceData, syncHandle);
    }

    void transferSetInfo(BluetoothDevice bda, int serviceData, int advHandle) {
        transferSetInfoNative(PA_SOURCE_LOCAL, bda.getAddress(), serviceData, advHandle);
    }

    /**********************************************************************************************/
    /*********************************** callbacks from native ************************************/
    /**********************************************************************************************/

    void onSyncStarted(
            int regId,
            int syncHandle,
            int sid,
            int addressType,
            String address,
            int phy,
            int interval,
            int status)
            throws Exception {
        if (DBG) {
            Log.d(
                    TAG,
                    "onSyncStarted(): "
                            + (" regId=" + regId)
                            + (" syncHandle=" + syncHandle)
                            + (" status=" + status));
        }
        mManager.onSyncStarted(regId, syncHandle, sid, addressType, address, phy, interval, status);
    }

    void onSyncReport(int syncHandle, int txPower, int rssi, int dataStatus, byte[] data)
            throws Exception {
        if (DBG) {
            Log.d(TAG, "onSyncReport(): syncHandle=" + syncHandle);
        }
        mManager.onSyncReport(syncHandle, txPower, rssi, dataStatus, data);
    }

    void onSyncLost(int syncHandle) throws Exception {
        if (DBG) {
            Log.d(TAG, "onSyncLost(): syncHandle=" + syncHandle);
        }
        mManager.onSyncLost(syncHandle);
    }

    void onSyncTransferredCallback(int paSource, int status, String bda) {
        if (DBG) {
            Log.d(TAG, "onSyncTransferredCallback()");
        }
        mManager.onSyncTransferredCallback(paSource, status, bda);
    }

    void onBigInfoReport(int syncHandle, boolean encrypted) throws Exception {
        if (DBG) {
            Log.d(
                    TAG,
                    "onBigInfoReport():"
                            + (" syncHandle=" + syncHandle)
                            + (" encrypted=" + encrypted));
        }
        mManager.onBigInfoReport(syncHandle, encrypted);
    }

    /**********************************************************************************************/
    /******************************************* native *******************************************/
    /**********************************************************************************************/

    private native void initializeNative();

    private native void cleanupNative();

    private native void startSyncNative(int sid, String address, int skip, int timeout, int regId);

    private native void stopSyncNative(int syncHandle);

    private native void cancelSyncNative(int sid, String address);

    private native void syncTransferNative(
            int paSource, String address, int serviceData, int syncHandle);

    private native void transferSetInfoNative(
            int paSource, String address, int serviceData, int advHandle);
}
