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

import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.UUID;

public final class BluetoothGattMultiDevicesServer {
    private static final String TAG = "BluetoothGattMultiDevicesServer";

    private Context mContext;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    public BluetoothGattMultiDevicesServer(Context context, BluetoothManager manager) {
        mContext = context;
        mBluetoothManager = manager;
        mBluetoothAdapter = manager.getAdapter();
    }

    public BluetoothGattServer createGattServer(String uuid) {
        var bluetoothGattServer =
                mBluetoothManager.openGattServer(mContext, new BluetoothGattServerCallback() {});
        var service = new BluetoothGattService(UUID.fromString(uuid), SERVICE_TYPE_PRIMARY);
        bluetoothGattServer.addService(service);
        return bluetoothGattServer;
    }

    public void createAndAdvertiseServer(String uuid) {
        createGattServer(uuid);

        var bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        var params = new AdvertisingSetParameters.Builder().setConnectable(true).build();
        var data =
                new AdvertiseData.Builder()
                        .addServiceUuid(new ParcelUuid(UUID.fromString(uuid)))
                        .build();

        bluetoothLeAdvertiser.startAdvertisingSet(
                params, data, null, null, null, new AdvertisingSetCallback() {});
    }

    public void createAndAdvertiseIsolatedServer(String uuid) {
        var gattServer = createGattServer(uuid);

        var bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        var params =
                new AdvertisingSetParameters.Builder()
                        .setConnectable(true)
                        .setOwnAddressType(
                                AdvertisingSetParameters.ADDRESS_TYPE_RANDOM_NON_RESOLVABLE)
                        .build();
        var data =
                new AdvertiseData.Builder()
                        .addServiceUuid(new ParcelUuid(UUID.fromString(uuid)))
                        .build();

        bluetoothLeAdvertiser.startAdvertisingSet(
                params,
                data,
                null,
                null,
                null,
                0,
                0,
                gattServer,
                new AdvertisingSetCallback() {},
                new Handler(Looper.getMainLooper()));
    }
}
