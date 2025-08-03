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
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextParams;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.android.server.uwb.discovery.DiscoveryAdvertiseProvider;
import com.android.server.uwb.discovery.DiscoveryAdvertiseProvider.DiscoveryAdvertiseCallback;
import com.android.server.uwb.discovery.info.AdvertiseInfo;
import com.android.server.uwb.discovery.info.VendorSpecificData;

import java.util.concurrent.Executor;

/** Class for UWB discovery advertise provider using BLE. */
@WorkerThread
public class BleDiscoveryAdvertiseProvider extends DiscoveryAdvertiseProvider {
    private static final String TAG = "BleDiscoveryAdvertiseProvider";

    private final Context mContext;
    private final Executor mExecutor;
    private AdvertiseInfo mAdvertiseInfo;
    private DiscoveryAdvertiseCallback mDiscoveryAdvertiseCallback;
    private BluetoothManager mBluetoothManager;

    private AdvertisingSetCallback mAdvertisingSetCallback =
            new AdvertisingSetCallback() {
                @Override
                public void onAdvertisingSetStarted(
                        AdvertisingSet advertisingSet, int txPower, int status) {
                    Log.i(
                            TAG,
                            "onAdvertisingSetStarted(): txPower:"
                                    + txPower
                                    + " , status: "
                                    + status);
                    mExecutor.execute(() -> processAdvertiseFailed(status));
                }
            };

    public BleDiscoveryAdvertiseProvider(
            AttributionSource attributionSource,
            Context context,
            Executor executor,
            AdvertiseInfo advertiseInfo,
            DiscoveryAdvertiseCallback discoveryAdvertiseCallback) {
        mExecutor = executor;
        mAdvertiseInfo = advertiseInfo;
        mDiscoveryAdvertiseCallback = discoveryAdvertiseCallback;

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
        BluetoothLeAdvertiser advertiser = getBleAdvertiser();
        if (advertiser == null) {
            Log.w(TAG, "startAdvertise failed due to BluetoothLeAdvertiser is null.");
            return false;
        }

        try {
            advertiser.startAdvertisingSet(
                    getAdvertisingSetParameters(),
                    getAdvertiseData(),
                    getScanResponse(),
                    /* periodicParameters=*/ null,
                    /* periodicData=*/ null,
                    mAdvertisingSetCallback);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "startAdvertise failed.", e);
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
        BluetoothLeAdvertiser advertiser = getBleAdvertiser();
        if (advertiser == null) {
            Log.w(TAG, "stopAdvertise failed due to BluetoothLeAdvertiser is null.");
            return false;
        }

        advertiser.stopAdvertisingSet(mAdvertisingSetCallback);
        mStarted = false;
        return true;
    }

    @Nullable
    private BluetoothLeAdvertiser getBleAdvertiser() {
        BluetoothAdapter adapter = mBluetoothManager.getAdapter();
        if (adapter == null) {
            return null;
        }
        return adapter.getBluetoothLeAdvertiser();
    }

    /**
     * Checek advertising status and notify mDiscoveryAdvertiseCallback if error.
     *
     * @param status advertise status code.
     */
    private void processAdvertiseFailed(int status) {
        if (status == AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED
                || status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
            return;
        }
        mDiscoveryAdvertiseCallback.onDiscoveryFailed(status);
    }

    private AdvertisingSetParameters getAdvertisingSetParameters() {
        if (mAdvertiseInfo.advertisingSetParameters != null) {
            AdvertisingSetParameters parameters = mAdvertiseInfo.advertisingSetParameters;
            return new AdvertisingSetParameters.Builder()
                    .setConnectable(true)
                    .setIncludeTxPower(parameters.includeTxPower())
                    .setInterval(parameters.getInterval())
                    .setLegacyMode(parameters.isLegacy())
                    .setPrimaryPhy(parameters.getPrimaryPhy())
                    .setScannable(parameters.isScannable())
                    .setSecondaryPhy(parameters.getSecondaryPhy())
                    .setTxPowerLevel(parameters.getTxPowerLevel())
                    .build();
        }

        return new AdvertisingSetParameters.Builder().setConnectable(true).build();
    }

    private AdvertiseData getAdvertiseData() {
        return new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(UuidConstants.FIRA_CP_PARCEL_UUID)
                .build();
    }

    private AdvertiseData getScanResponse() {
        AdvertiseData.Builder scanResponseBuilder =
                new AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .setIncludeTxPowerLevel(false)
                        .addServiceUuid(UuidConstants.FIRA_CP_PARCEL_UUID)
                        .addServiceData(
                                UuidConstants.FIRA_CP_PARCEL_UUID,
                                DiscoveryAdvertisement.toBytes(
                                        mAdvertiseInfo.discoveryAdvertisement,
                                        /*includeVendorSpecificData=*/ false));

        VendorSpecificData[] vendorSpecificData =
                mAdvertiseInfo.discoveryAdvertisement.vendorSpecificData;
        if (vendorSpecificData != null && vendorSpecificData.length > 0) {
            for (VendorSpecificData entry : vendorSpecificData) {
                scanResponseBuilder.addManufacturerData(entry.vendorId, entry.vendorData);
            }
        }

        return scanResponseBuilder.build();
    }
}
