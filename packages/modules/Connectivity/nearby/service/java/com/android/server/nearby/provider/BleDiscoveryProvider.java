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

package com.android.server.nearby.provider;

import static android.nearby.ScanCallback.ERROR_UNKNOWN;

import static com.android.server.nearby.NearbyService.TAG;
import static com.android.server.nearby.presence.PresenceConstants.PRESENCE_UUID;

import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.nearby.DataElement;
import android.nearby.NearbyDevice;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.PresenceDevice;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;
import android.nearby.ScanRequest;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.nearby.injector.Injector;
import com.android.server.nearby.presence.ExtendedAdvertisement;
import com.android.server.nearby.util.ArrayUtils;
import com.android.server.nearby.util.ForegroundThread;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Discovery provider that uses Bluetooth Low Energy to do scanning.
 */
public class BleDiscoveryProvider extends AbstractDiscoveryProvider {

    // Don't block the thread as it may be used by other services.
    private static final Executor NEARBY_EXECUTOR = ForegroundThread.getExecutor();
    private final Injector mInjector;
    private final Object mLock = new Object();
    // Null when the filters are never set
    @VisibleForTesting
    @GuardedBy("mLock")
    @Nullable
    private List<android.nearby.ScanFilter> mScanFilters;
    private android.bluetooth.le.ScanCallback mScanCallback =
            new android.bluetooth.le.ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult scanResult) {
                    NearbyDeviceParcelable.Builder builder = new NearbyDeviceParcelable.Builder();
                    String bleAddress = scanResult.getDevice().getAddress();
                    builder.setDeviceId(bleAddress.hashCode())
                            .setMedium(NearbyDevice.Medium.BLE)
                            .setRssi(scanResult.getRssi())
                            .setTxPower(scanResult.getTxPower())
                            .setBluetoothAddress(bleAddress);

                    ScanRecord record = scanResult.getScanRecord();
                    if (record != null) {
                        String deviceName = record.getDeviceName();
                        if (deviceName != null) {
                            builder.setName(record.getDeviceName());
                        }
                        Map<ParcelUuid, byte[]> serviceDataMap = record.getServiceData();
                        if (serviceDataMap != null) {
                            byte[] presenceData = serviceDataMap.get(PRESENCE_UUID);
                            if (presenceData != null) {
                                setPresenceDevice(presenceData, builder, deviceName,
                                        scanResult.getRssi());
                            }
                        }
                    }
                    mExecutor.execute(() -> mListener.onNearbyDeviceDiscovered(builder.build()));
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.w(TAG, "BLE 5.0 Scan failed with error code " + errorCode);
                    mExecutor.execute(() -> mListener.onError(ERROR_UNKNOWN));
                }
            };

    public BleDiscoveryProvider(Context context, Injector injector) {
        super(context, NEARBY_EXECUTOR);
        mInjector = injector;
    }

    private static PresenceDevice getPresenceDevice(ExtendedAdvertisement advertisement,
            String deviceName, int rssi) {
        // TODO(238458326): After implementing encryption, use real data.
        byte[] secretIdBytes = new byte[0];
        PresenceDevice.Builder builder =
                new PresenceDevice.Builder(
                        String.valueOf(advertisement.hashCode()),
                        advertisement.getSalt(),
                        secretIdBytes,
                        advertisement.getIdentity())
                        .addMedium(NearbyDevice.Medium.BLE)
                        .setName(deviceName)
                        .setRssi(rssi);
        for (DataElement dataElement : advertisement.getDataElements()) {
            builder.addExtendedProperty(dataElement);
        }
        return builder.build();
    }

    private static List<ScanFilter> getScanFilters() {
        List<ScanFilter> scanFilterList = new ArrayList<>();
        scanFilterList.add(
                new ScanFilter.Builder()
                        .setServiceData(PRESENCE_UUID, new byte[]{0}, new byte[]{0})
                        .build());
        return scanFilterList;
    }

    private boolean isBleAvailable() {
        BluetoothAdapter adapter = mInjector.getBluetoothAdapter();
        if (adapter == null) {
            return false;
        }

        return adapter.getBluetoothLeScanner() != null;
    }

    @Nullable
    private BluetoothLeScanner getBleScanner() {
        BluetoothAdapter adapter = mInjector.getBluetoothAdapter();
        if (adapter == null) {
            return null;
        }
        return adapter.getBluetoothLeScanner();
    }

    @Override
    protected void onStart() {
        if (isBleAvailable()) {
            Log.d(TAG, "BleDiscoveryProvider started");
            startScan(getScanFilters(), getScanSettings(/* legacy= */ false), mScanCallback);
            return;
        }
        Log.w(TAG, "Cannot start BleDiscoveryProvider because Ble is not available.");
        mController.stop();
    }

    @Override
    protected void onStop() {
        BluetoothLeScanner bluetoothLeScanner = getBleScanner();
        if (bluetoothLeScanner == null) {
            Log.w(TAG, "BleDiscoveryProvider failed to stop BLE scanning "
                    + "because BluetoothLeScanner is null.");
            return;
        }
        Log.v(TAG, "Ble scan stopped.");
        bluetoothLeScanner.stopScan(mScanCallback);
        synchronized (mLock) {
            if (mScanFilters != null) {
                mScanFilters = null;
            }
        }
    }

    @Override
    protected void invalidateScanMode() {
        onStop();
        onStart();
    }

    @Override
    protected void onSetScanFilters(List<android.nearby.ScanFilter> filters) {
        synchronized (mLock) {
            mScanFilters = filters == null ? null : List.copyOf(filters);
        }
    }

    @VisibleForTesting
    protected List<android.nearby.ScanFilter> getFiltersLocked() {
        synchronized (mLock) {
            return mScanFilters == null ? null : List.copyOf(mScanFilters);
        }
    }

    private void startScan(
            List<ScanFilter> scanFilters, ScanSettings scanSettings,
            android.bluetooth.le.ScanCallback scanCallback) {
        try {
            BluetoothLeScanner bluetoothLeScanner = getBleScanner();
            if (bluetoothLeScanner == null) {
                Log.w(TAG, "BleDiscoveryProvider failed to start BLE scanning "
                        + "because BluetoothLeScanner is null.");
                return;
            }
            bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback);
        } catch (NullPointerException | IllegalStateException | SecurityException e) {
            // NullPointerException:
            //   - Commonly, on Blackberry devices. b/73299795
            //   - Rarely, on other devices. b/75285249
            // IllegalStateException:
            // Caused if we call BluetoothLeScanner.startScan() after Bluetooth has turned off.
            // SecurityException:
            // refer to b/177380884
            Log.w(TAG, "BleDiscoveryProvider failed to start BLE scanning.", e);
        }
    }

    private ScanSettings getScanSettings(boolean legacy) {
        int bleScanMode = ScanSettings.SCAN_MODE_LOW_POWER;
        switch (mController.getProviderScanMode()) {
            case ScanRequest.SCAN_MODE_LOW_LATENCY:
                bleScanMode = ScanSettings.SCAN_MODE_LOW_LATENCY;
                break;
            case ScanRequest.SCAN_MODE_BALANCED:
                bleScanMode = ScanSettings.SCAN_MODE_BALANCED;
                break;
            case ScanRequest.SCAN_MODE_LOW_POWER:
                bleScanMode = ScanSettings.SCAN_MODE_LOW_POWER;
                break;
            case ScanRequest.SCAN_MODE_NO_POWER:
                bleScanMode = ScanSettings.SCAN_MODE_OPPORTUNISTIC;
                break;
        }
        return new ScanSettings.Builder().setScanMode(bleScanMode).setLegacy(legacy).build();
    }

    @VisibleForTesting
    ScanCallback getScanCallback() {
        return mScanCallback;
    }

    private void setPresenceDevice(byte[] data, NearbyDeviceParcelable.Builder builder,
            String deviceName, int rssi) {
        synchronized (mLock) {
            if (mScanFilters == null) {
                return;
            }
            for (android.nearby.ScanFilter scanFilter : mScanFilters) {
                if (scanFilter instanceof PresenceScanFilter) {
                    // Iterate all possible authenticity key and identity combinations to decrypt
                    // advertisement
                    PresenceScanFilter presenceFilter = (PresenceScanFilter) scanFilter;
                    for (PublicCredential credential : presenceFilter.getCredentials()) {
                        ExtendedAdvertisement advertisement =
                                ExtendedAdvertisement.fromBytes(data, credential);
                        if (advertisement == null) {
                            continue;
                        }
                        builder.setPresenceDevice(getPresenceDevice(advertisement, deviceName,
                                rssi));
                        builder.setEncryptionKeyTag(credential.getEncryptedMetadataKeyTag());
                        if (!ArrayUtils.isEmpty(credential.getSecretId())) {
                            builder.setDeviceId(Arrays.hashCode(credential.getSecretId()));
                        }
                        return;
                    }
                }
            }
        }
    }
}
