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

package com.android.bluetooth.gatt;

import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.PeriodicAdvertisingParameters;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/** Native interface for AdvertiseManager */
@VisibleForTesting
public class AdvertiseManagerNativeInterface {
    private static final String TAG = AdvertiseManagerNativeInterface.class.getSimpleName();

    @GuardedBy("INSTANCE_LOCK")
    private static AdvertiseManagerNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    private AdvertiseManager mManager;

    /** Get singleton instance. */
    public static AdvertiseManagerNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new AdvertiseManagerNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(AdvertiseManagerNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    void init(AdvertiseManager manager) {
        mManager = manager;
        initializeNative();
    }

    void cleanup() {
        cleanupNative();
        mManager = null;
    }

    void startAdvertisingSet(
            AdvertisingSetParameters parameters,
            byte[] advertiseDataBytes,
            byte[] scanResponseBytes,
            PeriodicAdvertisingParameters periodicParameters,
            byte[] periodicDataBytes,
            int duration,
            int maxExtAdvEvents,
            int cbId,
            int serverIf) {
        startAdvertisingSetNative(
                parameters,
                advertiseDataBytes,
                scanResponseBytes,
                periodicParameters,
                periodicDataBytes,
                duration,
                maxExtAdvEvents,
                cbId,
                serverIf);
    }

    void stopAdvertisingSet(int advertiserId) {
        stopAdvertisingSetNative(advertiserId);
    }

    void getOwnAddress(int advertiserId) {
        getOwnAddressNative(advertiserId);
    }

    void enableAdvertisingSet(int advertiserId, boolean enable, int duration, int maxExtAdvEvents) {
        enableAdvertisingSetNative(advertiserId, enable, duration, maxExtAdvEvents);
    }

    void setAdvertisingData(int advertiserId, byte[] advertiseDataBytes) {
        setAdvertisingDataNative(advertiserId, advertiseDataBytes);
    }

    void setScanResponseData(int advertiserId, byte[] advertiseDataBytes) {
        setScanResponseDataNative(advertiserId, advertiseDataBytes);
    }

    void setAdvertisingParameters(int advertiserId, AdvertisingSetParameters parameters) {
        setAdvertisingParametersNative(advertiserId, parameters);
    }

    void setPeriodicAdvertisingParameters(
            int advertiserId, PeriodicAdvertisingParameters parameters) {
        setPeriodicAdvertisingParametersNative(advertiserId, parameters);
    }

    void setPeriodicAdvertisingData(int advertiserId, byte[] advertiseDataBytes) {
        setPeriodicAdvertisingDataNative(advertiserId, advertiseDataBytes);
    }

    void setPeriodicAdvertisingEnable(int advertiserId, boolean enable) {
        setPeriodicAdvertisingEnableNative(advertiserId, enable);
    }

    void onAdvertisingSetStarted(int regId, int advertiserId, int txPower, int status)
            throws Exception {
        mManager.onAdvertisingSetStarted(regId, advertiserId, txPower, status);
    }

    void onOwnAddressRead(int advertiserId, int addressType, String address) throws Exception {
        mManager.onOwnAddressRead(advertiserId, addressType, address);
    }

    void onAdvertisingEnabled(int advertiserId, boolean enable, int status) throws Exception {
        mManager.onAdvertisingEnabled(advertiserId, enable, status);
    }

    void onAdvertisingDataSet(int advertiserId, int status) throws Exception {
        mManager.onAdvertisingDataSet(advertiserId, status);
    }

    void onScanResponseDataSet(int advertiserId, int status) throws Exception {
        mManager.onScanResponseDataSet(advertiserId, status);
    }

    void onAdvertisingParametersUpdated(int advertiserId, int txPower, int status)
            throws Exception {
        mManager.onAdvertisingParametersUpdated(advertiserId, txPower, status);
    }

    void onPeriodicAdvertisingParametersUpdated(int advertiserId, int status) throws Exception {
        mManager.onPeriodicAdvertisingParametersUpdated(advertiserId, status);
    }

    void onPeriodicAdvertisingDataSet(int advertiserId, int status) throws Exception {
        mManager.onPeriodicAdvertisingDataSet(advertiserId, status);
    }

    void onPeriodicAdvertisingEnabled(int advertiserId, boolean enable, int status)
            throws Exception {
        mManager.onPeriodicAdvertisingEnabled(advertiserId, enable, status);
    }

    private native void initializeNative();

    private native void cleanupNative();

    private native void startAdvertisingSetNative(
            AdvertisingSetParameters parameters,
            byte[] advertiseData,
            byte[] scanResponse,
            PeriodicAdvertisingParameters periodicParameters,
            byte[] periodicData,
            int duration,
            int maxExtAdvEvents,
            int regId,
            int serverIf);

    private native void stopAdvertisingSetNative(int advertiserId);

    private native void getOwnAddressNative(int advertiserId);

    private native void enableAdvertisingSetNative(
            int advertiserId, boolean enable, int duration, int maxExtAdvEvents);

    private native void setAdvertisingDataNative(int advertiserId, byte[] data);

    private native void setScanResponseDataNative(int advertiserId, byte[] data);

    private native void setAdvertisingParametersNative(
            int advertiserId, AdvertisingSetParameters parameters);

    private native void setPeriodicAdvertisingParametersNative(
            int advertiserId, PeriodicAdvertisingParameters parameters);

    private native void setPeriodicAdvertisingDataNative(int advertiserId, byte[] data);

    private native void setPeriodicAdvertisingEnableNative(int advertiserId, boolean enable);
}
