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

package com.android.bluetooth.bass_client;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;

import java.util.List;

/**
 * Helper class to mock {@link BluetoothLeScanner} which is final.
 */
public class BluetoothLeScannerWrapper {

    BluetoothLeScanner mBluetoothLeScanner;

    BluetoothLeScannerWrapper(BluetoothLeScanner scanner) {
        mBluetoothLeScanner = scanner;
    }

    /**
     * Starts Bluetooth LE scanning
     */
    public void startScan(List<ScanFilter> filters, ScanSettings settings,
            final ScanCallback callback) {
        mBluetoothLeScanner.startScan(filters, settings, callback);
    }

    /**
     * Stops Bluetooth LE scanning
     */
    public void stopScan(ScanCallback callback) {
        mBluetoothLeScanner.stopScan(callback);
    }
}
