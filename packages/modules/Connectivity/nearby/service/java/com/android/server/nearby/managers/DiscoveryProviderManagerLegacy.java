/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.nearby.managers;

import static android.nearby.ScanRequest.SCAN_TYPE_NEARBY_PRESENCE;

import static com.android.server.nearby.NearbyService.TAG;

import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.nearby.DataElement;
import android.nearby.IScanListener;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.NearbyManager;
import android.nearby.PresenceScanFilter;
import android.nearby.ScanCallback;
import android.nearby.ScanFilter;
import android.nearby.ScanRequest;
import android.nearby.aidl.IOffloadCallback;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.NearbyConfiguration;
import com.android.server.nearby.injector.Injector;
import com.android.server.nearby.metrics.NearbyMetrics;
import com.android.server.nearby.presence.PresenceDiscoveryResult;
import com.android.server.nearby.provider.AbstractDiscoveryProvider;
import com.android.server.nearby.provider.BleDiscoveryProvider;
import com.android.server.nearby.provider.ChreCommunication;
import com.android.server.nearby.provider.ChreDiscoveryProvider;
import com.android.server.nearby.provider.PrivacyFilter;
import com.android.server.nearby.util.identity.CallerIdentity;
import com.android.server.nearby.util.permissions.DiscoveryPermissions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** Manages all aspects of discovery providers. */
public class DiscoveryProviderManagerLegacy implements AbstractDiscoveryProvider.Listener,
        DiscoveryManager {

    protected final Object mLock = new Object();
    @VisibleForTesting
    @Nullable
    final ChreDiscoveryProvider mChreDiscoveryProvider;
    private final Context mContext;
    private final BleDiscoveryProvider mBleDiscoveryProvider;
    private final Injector mInjector;
    private final NearbyConfiguration mNearbyConfiguration;
    @ScanRequest.ScanMode
    private int mScanMode;
    @GuardedBy("mLock")
    private final Map<IBinder, ScanListenerRecord> mScanTypeScanListenerRecordMap;

    public DiscoveryProviderManagerLegacy(Context context, Injector injector) {
        mContext = context;
        mBleDiscoveryProvider = new BleDiscoveryProvider(mContext, injector);
        Executor executor = Executors.newSingleThreadExecutor();
        mChreDiscoveryProvider =
                new ChreDiscoveryProvider(
                        mContext, new ChreCommunication(injector, mContext, executor), executor);
        mScanTypeScanListenerRecordMap = new HashMap<>();
        mInjector = injector;
        mNearbyConfiguration = new NearbyConfiguration();
        Log.v(TAG, "DiscoveryProviderManagerLegacy: ");
    }

    @VisibleForTesting
    DiscoveryProviderManagerLegacy(Context context, Injector injector,
            BleDiscoveryProvider bleDiscoveryProvider,
            ChreDiscoveryProvider chreDiscoveryProvider,
            Map<IBinder, ScanListenerRecord> scanTypeScanListenerRecordMap) {
        mContext = context;
        mInjector = injector;
        mBleDiscoveryProvider = bleDiscoveryProvider;
        mChreDiscoveryProvider = chreDiscoveryProvider;
        mScanTypeScanListenerRecordMap = scanTypeScanListenerRecordMap;
        mNearbyConfiguration = new NearbyConfiguration();
    }

    private static boolean isChreOnly(List<ScanFilter> scanFilters) {
        for (ScanFilter scanFilter : scanFilters) {
            List<DataElement> dataElements =
                    ((PresenceScanFilter) scanFilter).getExtendedProperties();
            for (DataElement dataElement : dataElements) {
                if (dataElement.getKey() != DataElement.DataType.SCAN_MODE) {
                    continue;
                }
                byte[] scanModeValue = dataElement.getValue();
                if (scanModeValue == null || scanModeValue.length == 0) {
                    break;
                }
                if (Byte.toUnsignedInt(scanModeValue[0]) == ScanRequest.SCAN_MODE_CHRE_ONLY) {
                    return true;
                }
            }

        }
        return false;
    }

    @VisibleForTesting
    static boolean presenceFilterMatches(
            NearbyDeviceParcelable device, List<ScanFilter> scanFilters) {
        if (scanFilters.isEmpty()) {
            return true;
        }
        PresenceDiscoveryResult discoveryResult = PresenceDiscoveryResult.fromDevice(device);
        for (ScanFilter scanFilter : scanFilters) {
            PresenceScanFilter presenceScanFilter = (PresenceScanFilter) scanFilter;
            if (discoveryResult.matches(presenceScanFilter)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onNearbyDeviceDiscovered(NearbyDeviceParcelable nearbyDevice) {
        synchronized (mLock) {
            AppOpsManager appOpsManager = Objects.requireNonNull(mInjector.getAppOpsManager());
            for (IBinder listenerBinder : mScanTypeScanListenerRecordMap.keySet()) {
                ScanListenerRecord record = mScanTypeScanListenerRecordMap.get(listenerBinder);
                if (record == null) {
                    Log.w(TAG, "DiscoveryProviderManagerLegacy cannot find the scan record.");
                    continue;
                }
                CallerIdentity callerIdentity = record.getCallerIdentity();
                if (!DiscoveryPermissions.noteDiscoveryResultDelivery(
                        appOpsManager, callerIdentity)) {
                    Log.w(TAG, "[DiscoveryProviderManagerLegacy] scan permission revoked "
                            + "- not forwarding results");
                    try {
                        record.getScanListener().onError(ScanCallback.ERROR_PERMISSION_DENIED);
                    } catch (RemoteException e) {
                        Log.w(TAG, "DiscoveryProviderManagerLegacy failed to report error.", e);
                    }
                    return;
                }

                if (nearbyDevice.getScanType() == SCAN_TYPE_NEARBY_PRESENCE) {
                    List<ScanFilter> presenceFilters =
                            record.getScanRequest().getScanFilters().stream()
                                    .filter(
                                            scanFilter ->
                                                    scanFilter.getType()
                                                            == SCAN_TYPE_NEARBY_PRESENCE)
                                    .collect(Collectors.toList());
                    if (!presenceFilterMatches(nearbyDevice, presenceFilters)) {
                        Log.d(TAG, "presence filter does not match for "
                                + "the scanned Presence Device");
                        continue;
                    }
                }
                try {
                    record.getScanListener()
                            .onDiscovered(
                                    PrivacyFilter.filter(
                                            record.getScanRequest().getScanType(), nearbyDevice));
                    NearbyMetrics.logScanDeviceDiscovered(
                            record.hashCode(), record.getScanRequest(), nearbyDevice);
                } catch (RemoteException e) {
                    Log.w(TAG, "DiscoveryProviderManagerLegacy failed to report onDiscovered.", e);
                }
            }
        }
    }

    @Override
    public void onError(int errorCode) {
        synchronized (mLock) {
            AppOpsManager appOpsManager = Objects.requireNonNull(mInjector.getAppOpsManager());
            for (IBinder listenerBinder : mScanTypeScanListenerRecordMap.keySet()) {
                ScanListenerRecord record = mScanTypeScanListenerRecordMap.get(listenerBinder);
                if (record == null) {
                    Log.w(TAG, "DiscoveryProviderManagerLegacy cannot find the scan record.");
                    continue;
                }
                CallerIdentity callerIdentity = record.getCallerIdentity();
                if (!DiscoveryPermissions.noteDiscoveryResultDelivery(
                        appOpsManager, callerIdentity)) {
                    Log.w(TAG, "[DiscoveryProviderManagerLegacy] scan permission revoked "
                            + "- not forwarding results");
                    try {
                        record.getScanListener().onError(ScanCallback.ERROR_PERMISSION_DENIED);
                    } catch (RemoteException e) {
                        Log.w(TAG, "DiscoveryProviderManagerLegacy failed to report error.", e);
                    }
                    return;
                }

                try {
                    record.getScanListener().onError(errorCode);
                } catch (RemoteException e) {
                    Log.w(TAG, "DiscoveryProviderManagerLegacy failed to report onError.", e);
                }
            }
        }
    }

    /** Called after boot completed. */
    public void init() {
        // Register BLE only scan when Bluetooth is turned off
        if (mNearbyConfiguration.enableBleInInit()) {
            setBleScanEnabled();
        }
        if (mInjector.getContextHubManager() != null) {
            mChreDiscoveryProvider.init();
        }
        mChreDiscoveryProvider.getController().setListener(this);
    }

    /**
     * Registers the listener in the manager and starts scan according to the requested scan mode.
     */
    @NearbyManager.ScanStatus
    public int registerScanListener(ScanRequest scanRequest, IScanListener listener,
            CallerIdentity callerIdentity) {
        synchronized (mLock) {
            ScanListenerDeathRecipient deathRecipient = (listener != null)
                    ? new ScanListenerDeathRecipient(listener) : null;
            IBinder listenerBinder = listener.asBinder();
            if (listenerBinder != null && deathRecipient != null) {
                try {
                    listenerBinder.linkToDeath(deathRecipient, 0);
                } catch (RemoteException e) {
                    throw new IllegalArgumentException("Can't link to scan listener's death");
                }
            }
            if (mScanTypeScanListenerRecordMap.containsKey(listener.asBinder())) {
                ScanRequest savedScanRequest =
                        mScanTypeScanListenerRecordMap.get(listenerBinder).getScanRequest();
                if (scanRequest.equals(savedScanRequest)) {
                    Log.d(TAG, "Already registered the scanRequest: " + scanRequest);
                    return NearbyManager.ScanStatus.SUCCESS;
                }
            }
            ScanListenerRecord scanListenerRecord =
                    new ScanListenerRecord(scanRequest, listener, callerIdentity, deathRecipient);

            mScanTypeScanListenerRecordMap.put(listenerBinder, scanListenerRecord);
            Boolean started = startProviders(scanRequest);
            if (started == null) {
                mScanTypeScanListenerRecordMap.remove(listenerBinder);
                return NearbyManager.ScanStatus.UNKNOWN;
            }
            if (!started) {
                mScanTypeScanListenerRecordMap.remove(listenerBinder);
                return NearbyManager.ScanStatus.ERROR;
            }
            NearbyMetrics.logScanStarted(scanListenerRecord.hashCode(), scanRequest);
            if (mScanMode < scanRequest.getScanMode()) {
                mScanMode = scanRequest.getScanMode();
                invalidateProviderScanMode();
            }
            return NearbyManager.ScanStatus.SUCCESS;
        }
    }

    /**
     * Unregisters the listener in the manager and adjusts the scan mode if necessary afterwards.
     */
    public void unregisterScanListener(IScanListener listener) {
        IBinder listenerBinder = listener.asBinder();
        synchronized (mLock) {
            if (!mScanTypeScanListenerRecordMap.containsKey(listenerBinder)) {
                Log.w(
                        TAG,
                        "Cannot unregister the scanRequest because the request is never "
                                + "registered.");
                return;
            }

            ScanListenerRecord removedRecord =
                    mScanTypeScanListenerRecordMap.remove(listenerBinder);
            ScanListenerDeathRecipient deathRecipient = removedRecord.getDeathRecipient();
            if (listenerBinder != null && deathRecipient != null) {
                listenerBinder.unlinkToDeath(removedRecord.getDeathRecipient(), 0);
            }
            Log.v(TAG, "DiscoveryProviderManagerLegacy unregistered scan listener.");
            NearbyMetrics.logScanStopped(removedRecord.hashCode(), removedRecord.getScanRequest());
            if (mScanTypeScanListenerRecordMap.isEmpty()) {
                Log.v(TAG, "DiscoveryProviderManagerLegacy stops provider because there is no "
                        + "scan listener registered.");
                stopProviders();
                return;
            }

            // TODO(b/221082271): updates the scan with reduced filters.

            // Removes current highest scan mode requested and sets the next highest scan mode.
            if (removedRecord.getScanRequest().getScanMode() == mScanMode) {
                Log.v(TAG, "DiscoveryProviderManagerLegacy starts to find the new highest "
                        + "scan mode because the highest scan mode listener was unregistered.");
                @ScanRequest.ScanMode int highestScanModeRequested = ScanRequest.SCAN_MODE_NO_POWER;
                // find the next highest scan mode;
                for (ScanListenerRecord record : mScanTypeScanListenerRecordMap.values()) {
                    @ScanRequest.ScanMode int scanMode = record.getScanRequest().getScanMode();
                    if (scanMode > highestScanModeRequested) {
                        highestScanModeRequested = scanMode;
                    }
                }
                if (mScanMode != highestScanModeRequested) {
                    mScanMode = highestScanModeRequested;
                    invalidateProviderScanMode();
                }
            }
        }
    }

    /**
     * Query offload capability in a device.
     */
    public void queryOffloadCapability(IOffloadCallback callback) {
        mChreDiscoveryProvider.queryOffloadCapability(callback);
    }

    /**
     * @return {@code null} when all providers are initializing
     * {@code false} when fail to start all the providers
     * {@code true} when any one of the provider starts successfully
     */
    @VisibleForTesting
    @Nullable
    Boolean startProviders(ScanRequest scanRequest) {
        if (!scanRequest.isBleEnabled()) {
            Log.w(TAG, "failed to start any provider because client disabled BLE");
            return false;
        }
        List<ScanFilter> scanFilters = getPresenceScanFilters();
        boolean chreOnly = isChreOnly(scanFilters);
        Boolean chreAvailable = mChreDiscoveryProvider.available();
        if (chreAvailable == null) {
            if (chreOnly) {
                Log.w(TAG, "client wants CHRE only and Nearby service is still querying CHRE"
                        + " status");
                return null;
            }
            startBleProvider(scanFilters);
            return true;
        }

        if (!chreAvailable) {
            if (chreOnly) {
                Log.w(TAG, "failed to start any provider because client wants CHRE only and CHRE"
                        + " is not available");
                return false;
            }
            startBleProvider(scanFilters);
            return true;
        }

        if (scanRequest.getScanType() == SCAN_TYPE_NEARBY_PRESENCE) {
            startChreProvider(scanFilters);
            return true;
        }

        startBleProvider(scanFilters);
        return true;
    }

    private void startBleProvider(List<ScanFilter> scanFilters) {
        if (!mBleDiscoveryProvider.getController().isStarted()) {
            Log.d(TAG, "DiscoveryProviderManagerLegacy starts BLE scanning.");
            mBleDiscoveryProvider.getController().setListener(this);
            mBleDiscoveryProvider.getController().setProviderScanMode(mScanMode);
            mBleDiscoveryProvider.getController().setProviderScanFilters(scanFilters);
            mBleDiscoveryProvider.getController().start();
        }
    }

    @VisibleForTesting
    void startChreProvider(List<ScanFilter> scanFilters) {
        Log.d(TAG, "DiscoveryProviderManagerLegacy starts CHRE scanning.");
        mChreDiscoveryProvider.getController().setProviderScanFilters(scanFilters);
        mChreDiscoveryProvider.getController().setProviderScanMode(mScanMode);
        mChreDiscoveryProvider.getController().start();
    }

    private List<ScanFilter> getPresenceScanFilters() {
        synchronized (mLock) {
            List<ScanFilter> scanFilters = new ArrayList();
            for (IBinder listenerBinder : mScanTypeScanListenerRecordMap.keySet()) {
                ScanListenerRecord record = mScanTypeScanListenerRecordMap.get(listenerBinder);
                List<ScanFilter> presenceFilters =
                        record.getScanRequest().getScanFilters().stream()
                                .filter(
                                        scanFilter ->
                                                scanFilter.getType() == SCAN_TYPE_NEARBY_PRESENCE)
                                .collect(Collectors.toList());
                scanFilters.addAll(presenceFilters);
            }
            return scanFilters;
        }
    }

    private void stopProviders() {
        stopBleProvider();
        stopChreProvider();
    }

    private void stopBleProvider() {
        mBleDiscoveryProvider.getController().stop();
    }

    @VisibleForTesting
    protected void stopChreProvider() {
        mChreDiscoveryProvider.getController().stop();
    }

    @VisibleForTesting
    void invalidateProviderScanMode() {
        if (mBleDiscoveryProvider.getController().isStarted()) {
            mBleDiscoveryProvider.getController().setProviderScanMode(mScanMode);
        } else {
            Log.d(
                    TAG,
                    "Skip invalidating BleDiscoveryProvider scan mode because the provider not "
                            + "started.");
        }
    }

    @VisibleForTesting
    static class ScanListenerRecord {

        private final ScanRequest mScanRequest;

        private final IScanListener mScanListener;

        private final CallerIdentity mCallerIdentity;

        private final ScanListenerDeathRecipient mDeathRecipient;

        ScanListenerRecord(ScanRequest scanRequest, IScanListener iScanListener,
                CallerIdentity callerIdentity, ScanListenerDeathRecipient deathRecipient) {
            mScanListener = iScanListener;
            mScanRequest = scanRequest;
            mCallerIdentity = callerIdentity;
            mDeathRecipient = deathRecipient;
        }

        IScanListener getScanListener() {
            return mScanListener;
        }

        ScanRequest getScanRequest() {
            return mScanRequest;
        }

        CallerIdentity getCallerIdentity() {
            return mCallerIdentity;
        }

        ScanListenerDeathRecipient getDeathRecipient() {
            return mDeathRecipient;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof ScanListenerRecord) {
                ScanListenerRecord otherScanListenerRecord = (ScanListenerRecord) other;
                return Objects.equals(mScanRequest, otherScanListenerRecord.mScanRequest)
                        && Objects.equals(mScanListener, otherScanListenerRecord.mScanListener);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mScanListener, mScanRequest);
        }
    }

    /**
     * Class to make listener unregister after the binder is dead.
     */
    public class ScanListenerDeathRecipient implements IBinder.DeathRecipient {
        public IScanListener listener;

        ScanListenerDeathRecipient(IScanListener listener) {
            this.listener = listener;
        }

        @Override
        public void binderDied() {
            Log.d(TAG, "Binder is dead - unregistering scan listener");
            unregisterScanListener(listener);
        }
    }

    /**
     * Registers Nearby service to Ble scan if Bluetooth is off. (Even when Bluetooth is off)
     * @return {@code true} when Nearby currently can scan through Bluetooth or Ble or successfully
     * registers Nearby service to Ble scan when Blutooth is off.
     */
    public boolean setBleScanEnabled() {
        BluetoothAdapter adapter = mInjector.getBluetoothAdapter();
        if (adapter == null) {
            Log.e(TAG, "BluetoothAdapter is null.");
            return false;
        }
        if (adapter.isEnabled() || adapter.isLeEnabled()) {
            return true;
        }
        if (!adapter.isBleScanAlwaysAvailable()) {
            Log.v(TAG, "Ble always on scan is disabled.");
            return false;
        }
        if (!adapter.enableBLE()) {
            Log.e(TAG, "Failed to register Ble scan.");
            return false;
        }
        return true;
    }
}
