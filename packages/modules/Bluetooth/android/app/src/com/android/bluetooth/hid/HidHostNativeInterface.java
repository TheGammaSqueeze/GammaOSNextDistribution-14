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

package com.android.bluetooth.hid;

import android.bluetooth.BluetoothProfile;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/** Provides Bluetooth Hid Host profile, as a service in the Bluetooth application. */
public class HidHostNativeInterface {
    private static final String TAG = HidHostNativeInterface.class.getSimpleName();
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private HidHostService mHidHostService;

    @GuardedBy("INSTANCE_LOCK")
    private static HidHostNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    static HidHostNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new HidHostNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(HidHostNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    void init(HidHostService service) {
        mHidHostService = service;
        initializeNative();
    }

    void cleanup() {
        cleanupNative();
    }

    boolean connectHid(byte[] address) {
        return connectHidNative(address);
    }

    boolean disconnectHid(byte[] address, boolean reconnectAllowed) {
        return disconnectHidNative(address, reconnectAllowed);
    }

    boolean getProtocolMode(byte[] address) {
        return getProtocolModeNative(address);
    }

    boolean virtualUnPlug(byte[] address) {
        return virtualUnPlugNative(address);
    }

    boolean setProtocolMode(byte[] address, byte protocolMode) {
        return setProtocolModeNative(address, protocolMode);
    }

    boolean getReport(byte[] address, byte reportType, byte reportId, int bufferSize) {
        return getReportNative(address, reportType, reportId, bufferSize);
    }

    boolean setReport(byte[] address, byte reportType, String report) {
        return setReportNative(address, reportType, report);
    }

    boolean sendData(byte[] address, String report) {
        return sendDataNative(address, report);
    }

    boolean setIdleTime(byte[] address, byte idleTime) {
        return setIdleTimeNative(address, idleTime);
    }

    boolean getIdleTime(byte[] address) {
        return getIdleTimeNative(address);
    }

    private static int convertHalState(int halState) {
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
                Log.e(TAG, "bad hid connection state: " + halState);
                return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    /**********************************************************************************************/
    /*********************************** callbacks from native ************************************/
    /**********************************************************************************************/

    private void onConnectStateChanged(byte[] address, int state) {
        if (DBG) Log.d(TAG, "onConnectStateChanged: state=" + state);
        mHidHostService.onConnectStateChanged(address, convertHalState(state));
    }

    private void onGetProtocolMode(byte[] address, int mode) {
        if (DBG) Log.d(TAG, "onGetProtocolMode()");
        mHidHostService.onGetProtocolMode(address, mode);
    }

    private void onGetReport(byte[] address, byte[] report, int rptSize) {
        if (DBG) Log.d(TAG, "onGetReport()");
        mHidHostService.onGetReport(address, report, rptSize);
    }

    private void onHandshake(byte[] address, int status) {
        if (DBG) Log.d(TAG, "onHandshake: status=" + status);
        mHidHostService.onHandshake(address, status);
    }

    private void onVirtualUnplug(byte[] address, int status) {
        if (DBG) Log.d(TAG, "onVirtualUnplug: status=" + status);
        mHidHostService.onVirtualUnplug(address, status);
    }

    private void onGetIdleTime(byte[] address, int idleTime) {
        if (DBG) Log.d(TAG, "onGetIdleTime()");
        mHidHostService.onGetIdleTime(address, idleTime);
    }

    /**********************************************************************************************/
    /******************************************* native *******************************************/
    /**********************************************************************************************/

    // Constants matching Hal header file bt_hh.h
    // bthh_connection_state_t
    private static final int CONN_STATE_CONNECTED = 0;

    private static final int CONN_STATE_CONNECTING = 1;
    private static final int CONN_STATE_DISCONNECTED = 2;
    private static final int CONN_STATE_DISCONNECTING = 3;

    private native void initializeNative();

    private native void cleanupNative();

    private native boolean connectHidNative(byte[] btAddress);

    private native boolean disconnectHidNative(byte[] btAddress, boolean reconnectAllowed);

    private native boolean getProtocolModeNative(byte[] btAddress);

    private native boolean virtualUnPlugNative(byte[] btAddress);

    private native boolean setProtocolModeNative(byte[] btAddress, byte protocolMode);

    private native boolean getReportNative(
            byte[] btAddress, byte reportType, byte reportId, int bufferSize);

    private native boolean setReportNative(byte[] btAddress, byte reportType, String report);

    private native boolean sendDataNative(byte[] btAddress, String report);

    private native boolean setIdleTimeNative(byte[] btAddress, byte idleTime);

    private native boolean getIdleTimeNative(byte[] btAddress);
}
