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

package com.android.cts.verifier.presence.ble;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;


/** Scans BLE beacons. */
public class BleScanner {
    private static final String TAG = BleScanner.class.getName();
    private final BluetoothLeScanner bluetoothLeScanner =
            BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();


    private BleScanListener bleScanListener;

    private final ScanCallback scanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult scanResult) {
                    super.onScanResult(callbackType, scanResult);
                    if (scanResult == null) {
                        Log.i(TAG, "Empty scan result");
                        return;
                    }

                    BluetoothDevice bluetoothDevice = scanResult.getDevice();
                    String macAddress = bluetoothDevice.getAddress();
                    String deviceName = bluetoothDevice.getName();

                    byte[] data = scanResult.getScanRecord().getServiceData(Const.PARCEL_UUID);
                    if (data == null || data.length == 0) {
                        return;
                    }
                    if (data.length != BleAdvertisingPacket.ADVERTISING_PACKET_LENGTH) {
                        Log.i(TAG, "Advertising packet is not the correct length");
                        return;
                    }
                    BleAdvertisingPacket packet = BleAdvertisingPacket.fromBytes(data);
                    List<ParcelUuid> uuids = scanResult.getScanRecord().getServiceUuids();
                    if (bleScanListener == null) {
                        return;
                    }
                    bleScanListener.onBleScanResult(
                            uuids,
                            macAddress,
                            deviceName,
                            packet.getReferenceDeviceName(),
                            packet.getRandomDeviceId(),
                            packet.getRssiMedianFromReferenceDevice(),
                            scanResult.getRssi());
                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                    Log.w(TAG, "BLE discovery onScanFailed: " + errorCode);
                }
            };

    ScanSettings scanSettings =
            new ScanSettings.Builder()
                    .setLegacy(
                            false) // must set to false to support extended BLE 5.0 advertisements
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

    public BleScanner() {}

    public void startScanning(BleScanListener bleScanListener) {
        if (bleScanListener != null) {
            Log.i(TAG, "Active previous scan, stopping");
            stopScanning();
        }
        Log.i(TAG, "startScanning");

        bluetoothLeScanner.startScan(Collections.singletonList(
                        new ScanFilter.Builder().setServiceData(Const.PARCEL_UUID, new byte[0],
                                new byte[0]).build()),
                scanSettings,
                scanCallback);
        this.bleScanListener = bleScanListener;
    }

    public void stopScanning() {
        Log.i(TAG, "stopScanning");
        bluetoothLeScanner.stopScan(scanCallback);
        this.bleScanListener = null;
    }

    /** Listens to BLE scans. */
    public interface BleScanListener {
        void onBleScanResult(
                List<ParcelUuid> uuids,
                String macAddress,
                @Nullable String deviceName,
                String referenceDeviceName,
                byte randomDeviceId,
                byte rssiMedian,
                int rawRssi);
    }
}
