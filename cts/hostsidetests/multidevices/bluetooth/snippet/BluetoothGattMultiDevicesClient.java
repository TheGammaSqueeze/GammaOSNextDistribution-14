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

package com.google.snippet.bluetooth;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class BluetoothGattMultiDevicesClient {
    private static final String TAG = "BluetoothGattMultiDevicesClient";

    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;

    private CountDownLatch mConnectionBlocker = null;
    private CountDownLatch mServicesDiscovered = null;

    private static final int CALLBACK_TIMEOUT_SEC = 60;

    private BluetoothDevice mServer;

    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(
                        BluetoothGatt device, int status, int newState) {
                    Log.i(TAG, "onConnectionStateChange: newState=" + newState);
                    if (newState == BluetoothProfile.STATE_CONNECTED
                            && mConnectionBlocker != null) {
                        Log.v(TAG, "Connected");
                        mConnectionBlocker.countDown();
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    mServicesDiscovered.countDown();
                }
            };

    public BluetoothGattMultiDevicesClient(Context context, BluetoothManager manager) {
        mContext = context;
        mBluetoothAdapter = manager.getAdapter();
    }

    public boolean connect(String uuid) {
        // Scan for the peer
        var serverFoundBlocker = new CountDownLatch(1);
        var scanner = mBluetoothAdapter.getBluetoothLeScanner();
        var callback =
                new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        var uuids = result.getScanRecord().getServiceUuids();
                        Log.v(TAG, "Found uuids " + uuids);
                        if (uuids != null
                                && uuids.contains(new ParcelUuid(UUID.fromString(uuid)))) {
                            mServer = result.getDevice();
                            serverFoundBlocker.countDown();
                        }
                    }
                };
        scanner.startScan(null, new ScanSettings.Builder().setLegacy(false).build(), callback);
        boolean timeout = false;
        try {
            timeout = !serverFoundBlocker.await(CALLBACK_TIMEOUT_SEC, SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "", e);
            timeout = true;
        }
        scanner.stopScan(callback);
        if (timeout) {
            Log.e(TAG, "Did not discover server");
            return false;
        }

        // Connect to the peer
        mConnectionBlocker = new CountDownLatch(1);
        mBluetoothGatt = mServer.connectGatt(mContext, false, mGattCallback, TRANSPORT_LE);
        timeout = false;
        try {
            timeout = !mConnectionBlocker.await(CALLBACK_TIMEOUT_SEC, SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "", e);
            timeout = true;
        }
        if (timeout) {
            Log.e(TAG, "Did not connect to server");
            return false;
        }

        return true;
    }

    public boolean containsService(String uuid) {
        mServicesDiscovered = new CountDownLatch(1);
        mBluetoothGatt.discoverServices();
        try {
            mServicesDiscovered.await(CALLBACK_TIMEOUT_SEC, SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "", e);
            return false;
        }

        return mBluetoothGatt.getService(UUID.fromString(uuid)) != null;
    }
}
