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

package com.android.server.wifi.scanner;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.IWifiScannerListener;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.os.Handler;
import android.os.Process;
import android.os.WorkSource;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.List;

/**
 * WifiScanner manager local system service interface.
 *
 * @hide Only for use within the system server.
 */
public abstract class WifiScannerInternal {

    /**
     * Local scan listener
     */
    public static class ScanListener extends IWifiScannerListener.Stub {
        private static final String TAG = "WifiScannerInternal";
        private final WifiScanner.ScanListener mScanListener;
        private final Handler mHandler;

        /**
         * Local scan listener constructor
         * @param scanListener WifiScanner listener
         * @param handler handler for the listener
         */
        public ScanListener(WifiScanner.ScanListener scanListener, Handler handler) {
            mScanListener = scanListener;
            mHandler = handler;
        }

        /**
         * Get the WifiScanner listener
         * @hide
         */
        @VisibleForTesting
        public WifiScanner.ScanListener getWifiScannerListener() {
            return mScanListener;
        }

        @Override
        public void onSuccess() {
            mHandler.post(() -> {
                mScanListener.onSuccess();
            });
        }

        @Override
        public void onFailure(int reason, String description) {
            mHandler.post(() -> {
                mScanListener.onFailure(reason, description);
            });
        }

        @Override
        public void onResults(WifiScanner.ScanData[] scanDatas) {
            mHandler.post(() -> {
                mScanListener.onResults(scanDatas);
            });
        }

        @Override
        public void onFullResult(ScanResult fullScanResult) {
            mHandler.post(() -> {
                mScanListener.onFullResult(fullScanResult);
            });
        }

        @Override
        public void onSingleScanCompleted() {
            // Internal scan listener doesn't need to handle this.
        }

        @Override
        public void onPnoNetworkFound(ScanResult[] scanResult) {
            if (!(mScanListener instanceof WifiScanner.PnoScanListener)) {
                Log.wtf(TAG, "Listener is not a PnoScanListener!");
                return;
            }
            WifiScanner.PnoScanListener pnoScanListener =
                    (WifiScanner.PnoScanListener) mScanListener;
            mHandler.post(() -> {
                pnoScanListener.onPnoNetworkFound(scanResult);
            });
        }
    }

    /**
     * Enable/Disable wifi scanning.
     *
     * @param enable set true to enable scanning, false to disable all types of scanning.
     */
    public void setScanningEnabled(boolean enable) {
    }

    /**
     * Register a listener that will receive results from all single scans.
     * @param listener specifies the object to report events to.
     */
    public void registerScanListener(@NonNull ScanListener listener) {
    }

    /**
     * Start a single scan.
     * @param settings Wifi single scan setting
     * @param listener listener to the scan
     */
    public void startScan(WifiScanner.ScanSettings settings, ScanListener listener) {
        startScan(settings, listener, new WorkSource(Process.WIFI_UID));
    }

    /**
     * Start a single scan.
     * @param settings Wifi single scan setting
     * @param listener listener to the scan
     * @param workSource WorkSource to blame for power usage
     */
    public void startScan(WifiScanner.ScanSettings settings, ScanListener listener,
            @Nullable WorkSource workSource) {
    }

    /**
     * Stop a single scan.
     * @param listener listener to the scan
     */
    public void stopScan(ScanListener listener) {
    }

    /**
     * Start a PNO scan.
     * @param scanSettings Wifi single scan setting
     * @param pnoSettings Wifi pno scan setting
     * @param listener listener to the scan
     */
    public void startPnoScan(WifiScanner.ScanSettings scanSettings,
            WifiScanner.PnoSettings pnoSettings,
            ScanListener listener) {
    }

    /**
     * Stop a pno scan.
     * @param listener listener to the scan
     */
    public void stopPnoScan(ScanListener listener) {
    }

    /**
     * Get single scan results.
     * @return the list of scan results
     */
    public List<ScanResult> getSingleScanResults() {
        return Collections.emptyList();
    }

}
