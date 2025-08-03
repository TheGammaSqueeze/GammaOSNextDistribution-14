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

package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothQualityReport;
import android.bluetooth.BluetoothStatusCodes;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/** Native interface to BQR */
public class BluetoothQualityReportNativeInterface {
    private static final String TAG = "BluetoothQualityReportNativeInterface";

    @GuardedBy("INSTANCE_LOCK")
    private static BluetoothQualityReportNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private BluetoothQualityReportNativeInterface() {}

    /** Get singleton instance. */
    public static BluetoothQualityReportNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new BluetoothQualityReportNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    static void setInstance(BluetoothQualityReportNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    /**
     * Initializes the native interface.
     *
     * <p>priorities to configure.
     */
    public void init() {
        initNative();
    }

    /** Cleanup the native interface. */
    public void cleanup() {
        cleanupNative();
    }

    /**
     * Callback from the native stack back into the Java framework.
     */
    private void bqrDeliver(
            byte[] remoteAddr, int lmpVer, int lmpSubVer, int manufacturerId, byte[] bqrRawData) {
        BluetoothClass remoteBtClass = null;
        BluetoothDevice device = null;
        String remoteName = null;

        String remoteAddress = Utils.getAddressStringFromByte(remoteAddr);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (remoteAddress != null && adapter != null) {
            device = adapter.getRemoteDevice(remoteAddress);
            if (device == null) {
                Log.e(TAG, "bqrDeliver failed: device is null");
                return;
            }
            remoteName = device.getName();
            remoteBtClass = device.getBluetoothClass();
        } else {
            Log.e(TAG, "bqrDeliver failed: "
                    + (remoteAddress == null ? "remoteAddress is null" : "adapter is null"));
            return;
        }

        BluetoothQualityReport bqr;
        try {
            bqr =
                    new BluetoothQualityReport.Builder(bqrRawData)
                            .setRemoteAddress(remoteAddress)
                            .setLmpVersion(lmpVer)
                            .setLmpSubVersion(lmpSubVer)
                            .setManufacturerId(manufacturerId)
                            .setRemoteName(remoteName)
                            .setBluetoothClass(remoteBtClass)
                            .build();
            Log.i(TAG, bqr.toString());
        } catch (Exception e) {
            Log.e(TAG, "bqrDeliver failed: failed to create BluetotQualityReport", e);
            return;
        }

        try {
            AdapterService adapterService = AdapterService.getAdapterService();
            if (adapterService == null) {
                Log.e(TAG, "bqrDeliver failed: adapterService is null");
                return;
            }
            int status = adapterService.bluetoothQualityReportReadyCallback(device, bqr);
            if (status != BluetoothStatusCodes.SUCCESS) {
                Log.e(TAG, "bluetoothQualityReportReadyCallback failed, status: " + status);
            }
        } catch (Exception e) {
            Log.e(TAG, "bqrDeliver failed: bluetoothQualityReportReadyCallback error", e);
            return;
        }
    }

    // Native methods that call into the JNI interface
    private native void initNative();

    private native void cleanupNative();
}
