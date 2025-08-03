/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.google.android.utils.chre;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import org.junit.Assert;

import java.util.HexFormat;
import java.util.List;

/**
 * A class used to start BLE scans using Android APIs.
 * TODO(b/318864056): Use this class in the ContextHubBleTestExecutor
 */
public class BleHostClientUtil {
    /**
     * The advertisement type for service data.
     */
    public static final int CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16 = 0x16;

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothLeScanner mBluetoothLeScanner = null;

    /**
     * Callback for BLE scans.
     */
    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            // do nothing
        }

        @Override
        public void onScanFailed(int errorCode) {
            Assert.fail("Failed to start a BLE scan on the host");
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            // do nothing
        }
    };

    public BleHostClientUtil(Context context) {
        BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
        Assert.assertTrue(bluetoothManager != null);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter != null) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }
    }

    /**
     * Generates a BLE scan filter that filters only for the known Google beacons:
     * Google Eddystone and Nearby Fastpair.
     */
    private static List<ScanFilter> getDefaultScanFilterHost() {
        assertThat(CHRE_BLE_AD_TYPE_SERVICE_DATA_WITH_UUID_16)
                .isEqualTo(ScanRecord.DATA_TYPE_SERVICE_DATA_16_BIT);

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setAdvertisingDataTypeWithData(
                        ScanRecord.DATA_TYPE_SERVICE_DATA_16_BIT,
                        ByteString.copyFrom(HexFormat.of().parseHex("AAFE")).toByteArray(),
                        ByteString.copyFrom(HexFormat.of().parseHex("FFFF")).toByteArray())
                .build();
        ScanFilter scanFilter2 = new ScanFilter.Builder()
                .setAdvertisingDataTypeWithData(
                        ScanRecord.DATA_TYPE_SERVICE_DATA_16_BIT,
                        ByteString.copyFrom(HexFormat.of().parseHex("2CFE")).toByteArray(),
                        ByteString.copyFrom(HexFormat.of().parseHex("FFFF")).toByteArray())
                .build();

        return ImmutableList.of(scanFilter, scanFilter2);
    }

    /**
     * Returns true if BLE functionality is available.
     */
    public boolean isBleAvailable() {
        return mBluetoothAdapter != null;
    }

    /**
     * Starts a BLE scan. Callers should first confirm that BLE functionality exists
     * by calling isBleAvailable() before calling this function.
     */
    public void start() {
        ScanSettings setting = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build();
        mBluetoothLeScanner.startScan(getDefaultScanFilterHost(), setting, mScanCallback);
    }

    /**
     * Stops a BLE scan. Callers should first confirm that BLE functionality exists
     * by calling isBleAvailable() before calling this function.
     */
    public void stop() {
        mBluetoothLeScanner.stopScan(mScanCallback);
    }
}
