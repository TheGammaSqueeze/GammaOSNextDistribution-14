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

package com.android.bluetooth.le_scan;

import com.android.bluetooth.gatt.FilterParams;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * BLE Scan Native Interface to/from JNI.
 */
public class ScanNativeInterface {
    private static final String TAG = ScanNativeInterface.class.getSimpleName();

    private static ScanNativeInterface sInterface;
    private static final Object INSTANCE_LOCK = new Object();

    private CountDownLatch mLatch;

    private ScanNativeInterface() {}

    /**
     * This class is a singleton because native library should only be loaded once
     *
     * @return default instance
     */
    public static ScanNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInterface == null) {
                sInterface = new ScanNativeInterface();
            }
        }
        return sInterface;
    }

    /* Native methods */
    /************************** Regular scan related native methods **************************/
    private native void registerScannerNative(long appUuidLsb, long appUuidMsb);
    private native void unregisterScannerNative(int scannerId);
    private native void gattClientScanNative(boolean start);
    private native void gattSetScanParametersNative(int clientIf, int scanInterval,
            int scanWindow);
    /************************** Filter related native methods ********************************/
    private native void gattClientScanFilterAddNative(int clientId,
            ScanFilterQueue.Entry[] entries, int filterIndex);
    private native void gattClientScanFilterParamAddNative(FilterParams filtValue);
    // Note this effectively remove scan filters for ALL clients.
    private native void gattClientScanFilterParamClearAllNative(int clientIf);
    private native void gattClientScanFilterParamDeleteNative(int clientIf, int filtIndex);
    private native void gattClientScanFilterClearNative(int clientIf, int filterIndex);
    private native void gattClientScanFilterEnableNative(int clientIf, boolean enable);
    /************************** Batch related native methods *********************************/
    private native void gattClientConfigBatchScanStorageNative(int clientIf,
            int maxFullReportsPercent, int maxTruncatedReportsPercent,
            int notifyThresholdPercent);
    private native void gattClientStartBatchScanNative(int clientIf, int scanMode,
            int scanIntervalUnit, int scanWindowUnit, int addressType, int discardRule);
    private native void gattClientStopBatchScanNative(int clientIf);
    private native void gattClientReadScanReportsNative(int clientIf, int scanType);

    /**
     * Register BLE scanner
     */
    public void registerScanner(long appUuidLsb, long appUuidMsb) {
        registerScannerNative(appUuidLsb, appUuidMsb);
    }

    /**
     * Unregister BLE scanner
     */
    public void unregisterScanner(int scannerId) {
        unregisterScannerNative(scannerId);
    }

    /**
     * Enable/disable BLE scan
     */
    public void gattClientScan(boolean start) {
        gattClientScanNative(start);
    }

    /**
     * Configure BLE scan parameters
     */
    public void gattSetScanParameters(int clientIf, int scanInterval, int scanWindow) {
        gattSetScanParametersNative(clientIf, scanInterval, scanWindow);
    }

    /**
     * Add BLE scan filter
     */
    public void gattClientScanFilterAdd(int clientId, ScanFilterQueue.Entry[] entries,
            int filterIndex) {
        gattClientScanFilterAddNative(clientId, entries, filterIndex);
    }

    /**
     * Add BLE scan filter parameters
     */
    public void gattClientScanFilterParamAdd(FilterParams filtValue) {
        gattClientScanFilterParamAddNative(filtValue);
    }

    /**
     * Clear all BLE scan filter parameters
     */
    // Note this effectively remove scan filters for ALL clients.
    public void gattClientScanFilterParamClearAll(int clientIf) {
        gattClientScanFilterParamClearAllNative(clientIf);
    }

    /**
     * Delete BLE scan filter parameters
     */
    public void gattClientScanFilterParamDelete(int clientIf, int filtIndex) {
        gattClientScanFilterParamDeleteNative(clientIf, filtIndex);
    }

    /**
     * Clear BLE scan filter
     */
    public void gattClientScanFilterClear(int clientIf, int filterIndex) {
        gattClientScanFilterClearNative(clientIf, filterIndex);
    }

    /**
     * Enable/disable BLE scan filter
     */
    public void gattClientScanFilterEnable(int clientIf, boolean enable) {
        gattClientScanFilterEnableNative(clientIf, enable);
    }

    /**
     * Configure BLE batch scan storage
     */
    public void gattClientConfigBatchScanStorage(int clientIf,
            int maxFullReportsPercent, int maxTruncatedReportsPercent,
            int notifyThresholdPercent) {
        gattClientConfigBatchScanStorageNative(clientIf, maxFullReportsPercent,
                maxTruncatedReportsPercent, notifyThresholdPercent);
    }

    /**
     * Enable BLE batch scan with the parameters
     */
    public void gattClientStartBatchScan(int clientIf, int scanMode,
            int scanIntervalUnit, int scanWindowUnit, int addressType, int discardRule) {
        gattClientStartBatchScanNative(clientIf, scanMode, scanIntervalUnit, scanWindowUnit,
                addressType, discardRule);
    }

    /**
     * Disable BLE batch scan
     */
    public void gattClientStopBatchScan(int clientIf) {
        gattClientStopBatchScanNative(clientIf);
    }

    /**
     * Read BLE batch scan reports
     */
    public void gattClientReadScanReports(int clientIf, int scanType) {
        gattClientReadScanReportsNative(clientIf, scanType);
    }

    void callbackDone() {
        mLatch.countDown();
    }

    void resetCountDownLatch() {
        mLatch = new CountDownLatch(1);
    }

    // Returns true if mLatch reaches 0, false if timeout or interrupted.
    boolean waitForCallback(int timeoutMs) {
        try {
            return mLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }
}
