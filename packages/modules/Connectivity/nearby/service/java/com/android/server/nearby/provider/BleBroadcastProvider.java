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

package com.android.server.nearby.provider;

import static com.android.server.nearby.NearbyService.TAG;
import static com.android.server.nearby.presence.PresenceConstants.PRESENCE_UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.nearby.BroadcastCallback;
import android.nearby.BroadcastRequest;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.injector.Injector;

import java.util.concurrent.Executor;

/**
 * A provider for Bluetooth Low Energy advertisement.
 */
public class BleBroadcastProvider extends AdvertiseCallback {

    /**
     * Listener for Broadcast status changes.
     */
    public interface BroadcastListener {
        void onStatusChanged(int status);
    }

    private final Injector mInjector;
    private final Executor mExecutor;

    private BroadcastListener mBroadcastListener;
    private boolean mIsAdvertising;
    @VisibleForTesting
    AdvertisingSetCallback mAdvertisingSetCallback;
    public BleBroadcastProvider(Injector injector, Executor executor) {
        mInjector = injector;
        mExecutor = executor;
        mAdvertisingSetCallback = getAdvertisingSetCallback();
    }

    /**
     * Starts to broadcast with given bytes.
     */
    public void start(@BroadcastRequest.BroadcastVersion int version, byte[] advertisementPackets,
            BroadcastListener listener) {
        if (mIsAdvertising) {
            stop();
        }
        boolean advertiseStarted = false;
        BluetoothAdapter adapter = mInjector.getBluetoothAdapter();
        if (adapter != null) {
            BluetoothLeAdvertiser bluetoothLeAdvertiser =
                    mInjector.getBluetoothAdapter().getBluetoothLeAdvertiser();
            if (bluetoothLeAdvertiser != null) {
                advertiseStarted = true;
                AdvertiseData advertiseData =
                        new AdvertiseData.Builder()
                                .addServiceData(PRESENCE_UUID, advertisementPackets).build();
                try {
                    mBroadcastListener = listener;
                    switch (version) {
                        case BroadcastRequest.PRESENCE_VERSION_V0:
                            bluetoothLeAdvertiser.startAdvertising(getAdvertiseSettings(),
                                    advertiseData, this);
                            Log.v(TAG, "Start to broadcast V0 advertisement.");
                            break;
                        case BroadcastRequest.PRESENCE_VERSION_V1:
                            if (adapter.isLeExtendedAdvertisingSupported()) {
                                bluetoothLeAdvertiser.startAdvertisingSet(
                                        getAdvertisingSetParameters(),
                                        advertiseData,
                                        null, null, null, mAdvertisingSetCallback);
                                Log.v(TAG, "Start to broadcast V1 advertisement.");
                            } else {
                                Log.w(TAG, "Failed to start advertising set because the chipset"
                                        + " does not supports LE Extended Advertising feature.");
                                advertiseStarted = false;
                            }
                            break;
                        default:
                            Log.w(TAG, "Failed to start advertising set because the advertisement"
                                    + " is wrong.");
                            advertiseStarted = false;
                    }
                } catch (NullPointerException | IllegalStateException | SecurityException
                    | IllegalArgumentException e) {
                    Log.w(TAG, "Failed to start advertising.", e);
                    advertiseStarted = false;
                }
            }
        }
        if (!advertiseStarted) {
            listener.onStatusChanged(BroadcastCallback.STATUS_FAILURE);
        }
    }

    /**
     * Stops current advertisement.
     */
    public void stop() {
        if (mIsAdvertising) {
            BluetoothAdapter adapter = mInjector.getBluetoothAdapter();
            if (adapter != null) {
                BluetoothLeAdvertiser bluetoothLeAdvertiser =
                        mInjector.getBluetoothAdapter().getBluetoothLeAdvertiser();
                if (bluetoothLeAdvertiser != null) {
                    bluetoothLeAdvertiser.stopAdvertising(this);
                    bluetoothLeAdvertiser.stopAdvertisingSet(mAdvertisingSetCallback);
                }
            }
            mBroadcastListener = null;
            mIsAdvertising = false;
        }
    }

    @Override
    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
        mExecutor.execute(() -> {
            if (mBroadcastListener != null) {
                mBroadcastListener.onStatusChanged(BroadcastCallback.STATUS_OK);
            }
            mIsAdvertising = true;
        });
    }

    @Override
    public void onStartFailure(int errorCode) {
        if (mBroadcastListener != null) {
            mBroadcastListener.onStatusChanged(BroadcastCallback.STATUS_FAILURE);
        }
    }

    private static AdvertiseSettings getAdvertiseSettings() {
        return new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build();
    }

    private static AdvertisingSetParameters getAdvertisingSetParameters() {
        return new AdvertisingSetParameters.Builder()
                .setInterval(AdvertisingSetParameters.INTERVAL_MEDIUM)
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                .setIncludeTxPower(true)
                .setConnectable(true)
                .build();
    }

    private AdvertisingSetCallback getAdvertisingSetCallback() {
        return new AdvertisingSetCallback() {
            @Override
            public void onAdvertisingSetStarted(AdvertisingSet advertisingSet,
                    int txPower, int status) {
                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                    if (mBroadcastListener != null) {
                        mBroadcastListener.onStatusChanged(BroadcastCallback.STATUS_OK);
                    }
                    mIsAdvertising = true;
                } else {
                    Log.e(TAG, "Starts advertising failed in status " + status);
                    if (mBroadcastListener != null) {
                        mBroadcastListener.onStatusChanged(BroadcastCallback.STATUS_FAILURE);
                    }
                }
            }
        };
    }
}
