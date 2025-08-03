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

package com.android.server.nearby.presence;

import static com.android.server.nearby.NearbyService.TAG;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nearby.DataElement;
import android.nearby.NearbyDevice;
import android.nearby.NearbyManager;
import android.nearby.PresenceDevice;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;
import android.nearby.ScanCallback;
import android.nearby.ScanRequest;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/** PresenceManager is the class initiated in nearby service to handle presence related work. */
public class PresenceManager {

    final Context mContext;
    private final IntentFilter mIntentFilter;

    @VisibleForTesting
    final ScanCallback mScanCallback =
            new ScanCallback() {
                @Override
                public void onDiscovered(@NonNull NearbyDevice device) {
                    Log.i(TAG, "[PresenceManager] discovered Device.");
                    PresenceDevice presenceDevice = (PresenceDevice) device;
                    List<DataElement> dataElements = presenceDevice.getExtendedProperties();
                    for (DataElement dataElement : dataElements) {
                        Log.i(TAG, "[PresenceManager] Data Element key "
                                + dataElement.getKey());
                        Log.i(TAG, "[PresenceManager] Data Element value "
                                + Arrays.toString(dataElement.getValue()));
                    }
                }

                @Override
                public void onUpdated(@NonNull NearbyDevice device) {}

                @Override
                public void onLost(@NonNull NearbyDevice device) {}

                @Override
                public void onError(int errorCode) {
                    Log.w(TAG, "[PresenceManager] Scan error is " + errorCode);
                }
            };

    private final BroadcastReceiver mScreenBroadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    NearbyManager manager = getNearbyManager();
                    if (manager == null) {
                        Log.e(TAG, "Nearby Manager is null");
                        return;
                    }
                    if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                        Log.d(TAG, "PresenceManager Start scan.");
                        PublicCredential publicCredential =
                                new PublicCredential.Builder(new byte[]{1}, new byte[]{1},
                                        new byte[]{1}, new byte[]{1}, new byte[]{1}).build();
                        PresenceScanFilter presenceScanFilter =
                                new PresenceScanFilter.Builder()
                                        .setMaxPathLoss(3)
                                        .addCredential(publicCredential)
                                        .addPresenceAction(1)
                                        .addExtendedProperty(new DataElement(
                                                DataElement.DataType.ACCOUNT_KEY_DATA,
                                                new byte[16]))
                                        .build();
                        ScanRequest scanRequest =
                                new ScanRequest.Builder()
                                        .setScanType(ScanRequest.SCAN_TYPE_NEARBY_PRESENCE)
                                        .addScanFilter(presenceScanFilter)
                                        .build();
                        Log.d(
                                TAG,
                                String.format(
                                        Locale.getDefault(),
                                        "[PresenceManager] Start Presence scan with request: %s",
                                        scanRequest.toString()));
                        manager.startScan(
                                scanRequest, Executors.newSingleThreadExecutor(), mScanCallback);
                    } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                        Log.d(TAG, "PresenceManager Stop scan.");
                        manager.stopScan(mScanCallback);
                    }
                }
            };

    public PresenceManager(Context context) {
        mContext = context;
        mIntentFilter = new IntentFilter();
    }

    /** Null when the Nearby Service is not available. */
    @Nullable
    private NearbyManager getNearbyManager() {
        return (NearbyManager)
                mContext.getApplicationContext()
                        .getSystemService(Context.NEARBY_SERVICE);
    }

    /** Function called when nearby service start. */
    public void initiate() {
        mIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
        mIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mScreenBroadcastReceiver, mIntentFilter);
    }
}
