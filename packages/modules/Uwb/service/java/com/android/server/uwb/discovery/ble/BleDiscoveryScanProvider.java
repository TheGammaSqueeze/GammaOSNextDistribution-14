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
package com.android.server.uwb.discovery.ble;

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextParams;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.android.server.uwb.discovery.DiscoveryScanProvider;
import com.android.server.uwb.discovery.DiscoveryScanProvider.DiscoveryResult;
import com.android.server.uwb.discovery.DiscoveryScanProvider.DiscoveryScanCallback;
import com.android.server.uwb.discovery.info.ScanInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Class for UWB discovery scan provider using BLE. */
@WorkerThread
public class BleDiscoveryScanProvider extends DiscoveryScanProvider {
    private static final String TAG = "BleDiscoveryScanProvider";

    private final Context mContext;
    private final Executor mExecutor;
    private ScanInfo mScanInfo;
    private DiscoveryScanCallback mDiscoveryScanCallback;
    private BluetoothManager mBluetoothManager;

    private ScanCallback mScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                        mExecutor.execute(() -> processScanResult(result));
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.w(TAG, "BLE Scan failed with error code " + errorCode);
                    mExecutor.execute(() -> processScanFailed(errorCode));
                }
            };

    public BleDiscoveryScanProvider(
            AttributionSource attributionSource,
            Context context,
            Executor executor,
            ScanInfo scanInfo,
            DiscoveryScanCallback discoveryScanCallback) {
        mExecutor = executor;
        mScanInfo = scanInfo;
        mDiscoveryScanCallback = discoveryScanCallback;

        mContext =
                context.createContext(
                        new ContextParams.Builder()
                                .setNextAttributionSource(attributionSource)
                                .build());
        mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
    }

    @Override
    public boolean start() {
        if (!super.start()) {
            return false;
        }
        BluetoothLeScanner scanner = getBleScanner();
        if (scanner == null) {
            Log.w(TAG, "startScan failed due to BluetoothLeScanner is null.");
            return false;
        }

        try {
            scanner.startScan(getScanFilters(), getScanSettings(), mScanCallback);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "startScan failed.", e);
            return false;
        }

        mStarted = true;
        return true;
    }

    @Override
    public boolean stop() {
        if (!super.stop()) {
            return false;
        }
        BluetoothLeScanner scanner = getBleScanner();
        if (scanner == null) {
            Log.w(TAG, "stopScan failed due to BluetoothLeScanner is null.");
            return false;
        }

        scanner.stopScan(mScanCallback);
        mStarted = false;
        return true;
    }

    @Nullable
    private BluetoothLeScanner getBleScanner() {
        BluetoothAdapter adapter = mBluetoothManager.getAdapter();
        if (adapter == null) {
            return null;
        }
        return adapter.getBluetoothLeScanner();
    }

    /**
     * Process the ScanResult according to FiRa BLE OOB 1.0 Spec and notify mDiscoveryScanCallback.
     *
     * @param scanResult scan result of the BLE Scan.
     */
    private void processScanResult(ScanResult scanResult) {
        ScanRecord record = scanResult.getScanRecord();
        if (record == null) {
            Log.w(TAG, "Ignoring scan result. Empty ScanRecord");
            return;
        }

        byte[] serviceData = record.getServiceData(UuidConstants.FIRA_CP_PARCEL_UUID);
        if (serviceData == null) {
            Log.w(TAG, "Ignoring scan result. Empty ServiceData");
            return;
        }

        DiscoveryAdvertisement adv =
                DiscoveryAdvertisement.fromBytes(serviceData, record.getManufacturerSpecificData());

        if (adv == null) {
            Log.w(TAG, "Ignoring scan result. Invalid DiscoveryAdvertisement");
            return;
        }

        int rssi = scanResult.getRssi();
        int rssiThreshold = adv.uwbIndicationData.bluetoothRssiThresholdDbm;
        // rssiThreshold of value -128 has specific meaning of “connect immediately regardless RSSI
        // value measured" as defined by FiRa BLE OOB 1.0 Spec.
        if (rssiThreshold != -128 && rssi < rssiThreshold) {
            Log.w(TAG, "Ignoring scan result. RSSI below threshold (" + rssi + "<" + rssiThreshold);
            return;
        }

        DiscoveryResult discoveryResult = new DiscoveryResult(scanResult, adv);
        mDiscoveryScanCallback.onDiscovered(discoveryResult);
    }

    /**
     * Process and notify mDiscoveryScanCallback of scanning failures.
     *
     * @param errorCode scan failed error code.
     */
    private void processScanFailed(int errorCode) {
        mDiscoveryScanCallback.onDiscoveryFailed(errorCode);
    }

    private List<ScanFilter> getScanFilters() {
        List<ScanFilter> scanFilterList = new ArrayList<ScanFilter>();
        if (mScanInfo != null && mScanInfo.scanFilters != null) {
            scanFilterList = mScanInfo.scanFilters;
        }
        // Add scan filter for FiRa Connector Primary Service UUID.
        scanFilterList.add(
                new ScanFilter.Builder().setServiceUuid(UuidConstants.FIRA_CP_PARCEL_UUID).build());

        return scanFilterList;
    }

    private ScanSettings getScanSettings() {
        if (mScanInfo != null && mScanInfo.scanSettings != null) {
            return mScanInfo.scanSettings;
        }

        return new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
    }
}
