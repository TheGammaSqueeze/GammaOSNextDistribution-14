/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.car.kitchensink.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BluetoothDeviceTypeChecker {
    private static final ParcelUuid IAP_UUID =
            ParcelUuid.fromString("00000000-deca-fade-deca-deafdecacafe");
    private static final int MAX_SECONDS_TO_BLOCK = 10;
    private static final String TAG = "BluetoothDeviceTypeChecker";

    private final Context mContext;
    private final boolean mAllowBlocking;
    private final Object mLock = new Object();

    CompletableFuture<ParcelUuid[]> mDeviceUUidsFuture;
    BluetoothDevice mBluetoothDevice;

    /**
     * BluetoothDeviceTypeChecker
     * Class designed to fetch and check UUID records for matches based on either cached or live
     * records.  Live records are fetched if allowBlocking is enabled and there is nothing in the
     * cache.  Paired devices should always have records in the cache if the BluetoothAdapter is on.
     *
     * @param context The context on which to receive updates if live records are necessary
     * @param allowBlocking If cached SDP records are not available allow methods to block in a
     *                     best effort of acquiring them.
     */
    public BluetoothDeviceTypeChecker(Context context, boolean allowBlocking) {
        mContext = context;
        if (mContext != null) {
            mAllowBlocking = allowBlocking;
        } else {
            mAllowBlocking = false;
        }
    }

    /**
     * isIapDevice
     * Check if device is indicating support for iAP
     * @param device
     * @return
     */
    public boolean isIapDevice(BluetoothDevice device) {
        return deviceContainsUuid(device, IAP_UUID);
    }

    /**
     * deviceContainsUuid
     * Check if device contains a specific UUID record
     * @param device to perform a lookup on
     * @param uuid to check in the records
     * @return
     */
    public boolean deviceContainsUuid(BluetoothDevice device, ParcelUuid uuid) {
        if (device == null) return false;
        if (uuid == null) return false;

        synchronized (mLock) {
            mBluetoothDevice = device;
            ParcelUuid[] uuidsArray = device.getUuids();
            if (mAllowBlocking && (uuidsArray == null || uuidsArray.length == 0)) {
                uuidsArray = blockingFetchUuids(device);
            }
            if (uuidsArray == null || uuidsArray.length == 0) {
                return false;
            }
            for (int i = 0; i < uuidsArray.length; i++) {
                if (uuid.equals(uuidsArray[i])) {
                    return true;
                }
            }
            return false;
        }
    }

    /*
    Perform a blocking fetch of the UUIDs on specified BluetoothDevice
     */
    private ParcelUuid[] blockingFetchUuids(BluetoothDevice device) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_UUID);
        mContext.registerReceiver(mUuidReceiver, filter);
        mDeviceUUidsFuture = new CompletableFuture<>();
        if (!device.fetchUuidsWithSdp()) {
            Log.w(TAG, "fetching UUIDs failed.");
            mContext.unregisterReceiver(mUuidReceiver);
            return new ParcelUuid[0];
        }
        try {
            return mDeviceUUidsFuture.get(MAX_SECONDS_TO_BLOCK, TimeUnit.SECONDS);
        } catch (Exception e) {
            mContext.unregisterReceiver(mUuidReceiver);
            return new ParcelUuid[0];
        }
    }

    /*
    Broadcast receiver on which to receive updates to Bluetooth UUID records.
     */
    private BroadcastReceiver mUuidReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (mBluetoothDevice.equals(device)
                    && BluetoothDevice.ACTION_UUID.equals(intent.getAction())) {
                mDeviceUUidsFuture.complete(device.getUuids());
                mContext.unregisterReceiver(this);
            }
        }
    };
}
