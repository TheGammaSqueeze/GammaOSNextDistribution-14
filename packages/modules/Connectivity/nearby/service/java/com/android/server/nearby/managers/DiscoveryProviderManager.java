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
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.nearby.DataElement;
import android.nearby.IScanListener;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.NearbyManager;
import android.nearby.PresenceScanFilter;
import android.nearby.ScanFilter;
import android.nearby.ScanRequest;
import android.nearby.aidl.IOffloadCallback;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.NearbyConfiguration;
import com.android.server.nearby.injector.Injector;
import com.android.server.nearby.managers.registration.DiscoveryRegistration;
import com.android.server.nearby.provider.AbstractDiscoveryProvider;
import com.android.server.nearby.provider.BleDiscoveryProvider;
import com.android.server.nearby.provider.ChreCommunication;
import com.android.server.nearby.provider.ChreDiscoveryProvider;
import com.android.server.nearby.util.identity.CallerIdentity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.annotation.concurrent.GuardedBy;

/** Manages all aspects of discovery providers. */
public class DiscoveryProviderManager extends
        ListenerMultiplexer<IScanListener, DiscoveryRegistration, MergedDiscoveryRequest> implements
        AbstractDiscoveryProvider.Listener,
        DiscoveryManager {

    protected final Object mLock = new Object();
    @VisibleForTesting
    @Nullable
    final ChreDiscoveryProvider mChreDiscoveryProvider;
    private final Context mContext;
    private final BleDiscoveryProvider mBleDiscoveryProvider;
    private final Injector mInjector;
    private final Executor mExecutor;
    private final NearbyConfiguration mNearbyConfiguration;

    public DiscoveryProviderManager(Context context, Injector injector) {
        Log.v(TAG, "DiscoveryProviderManager: ");
        mContext = context;
        mBleDiscoveryProvider = new BleDiscoveryProvider(mContext, injector);
        mExecutor = Executors.newSingleThreadExecutor();
        mChreDiscoveryProvider = new ChreDiscoveryProvider(mContext,
                new ChreCommunication(injector, mContext, mExecutor), mExecutor);
        mInjector = injector;
        mNearbyConfiguration = new NearbyConfiguration();
    }

    @VisibleForTesting
    DiscoveryProviderManager(Context context, Executor executor, Injector injector,
            BleDiscoveryProvider bleDiscoveryProvider,
            ChreDiscoveryProvider chreDiscoveryProvider) {
        mContext = context;
        mExecutor = executor;
        mInjector = injector;
        mBleDiscoveryProvider = bleDiscoveryProvider;
        mChreDiscoveryProvider = chreDiscoveryProvider;
        mNearbyConfiguration = new NearbyConfiguration();
    }

    private static boolean isChreOnly(Set<ScanFilter> scanFilters) {
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

    @Override
    public void onNearbyDeviceDiscovered(NearbyDeviceParcelable nearbyDevice) {
        synchronized (mMultiplexerLock) {
            Log.d(TAG, "Found device" + nearbyDevice);
            deliverToListeners(registration -> {
                try {
                    return registration.onNearbyDeviceDiscovered(nearbyDevice);
                } catch (Exception e) {
                    Log.w(TAG, "DiscoveryProviderManager failed to report callback.", e);
                    return null;
                }
            });
        }
    }

    @Override
    public void onError(int errorCode) {
        synchronized (mMultiplexerLock) {
            Log.e(TAG, "Error found during scanning.");
            deliverToListeners(registration -> {
                try {
                    return registration.reportError(errorCode);
                } catch (Exception e) {
                    Log.w(TAG, "DiscoveryProviderManager failed to report error.", e);
                    return null;
                }
            });
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
        DiscoveryRegistration registration = new DiscoveryRegistration(this, scanRequest, listener,
                mExecutor, callerIdentity, mMultiplexerLock, mInjector.getAppOpsManager());
        synchronized (mMultiplexerLock) {
            putRegistration(listener.asBinder(), registration);
            return NearbyManager.ScanStatus.SUCCESS;
        }
    }

    @Override
    public void onRegister() {
        Log.v(TAG, "Registering the DiscoveryProviderManager.");
        startProviders();
    }

    @Override
    public void onUnregister() {
        Log.v(TAG, "Unregistering the DiscoveryProviderManager.");
        stopProviders();
    }

    /**
     * Unregisters the listener in the manager and adjusts the scan mode if necessary afterwards.
     */
    public void unregisterScanListener(IScanListener listener) {
        Log.v(TAG, "Unregister scan listener");
        synchronized (mMultiplexerLock) {
            removeRegistration(listener.asBinder());
        }
        // TODO(b/221082271): updates the scan with reduced filters.
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
    Boolean startProviders() {
        synchronized (mMultiplexerLock) {
            if (!mMerged.getMediums().contains(MergedDiscoveryRequest.Medium.BLE)) {
                Log.w(TAG, "failed to start any provider because client disabled BLE");
                return false;
            }
            Set<ScanFilter> scanFilters = mMerged.getScanFilters();
            boolean chreOnly = isChreOnly(scanFilters);
            Boolean chreAvailable = mChreDiscoveryProvider.available();
            Log.v(TAG, "startProviders: chreOnly " + chreOnly + " chreAvailable " + chreAvailable);
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
                    Log.w(TAG,
                            "failed to start any provider because client wants CHRE only and CHRE"
                                    + " is not available");
                    return false;
                }
                startBleProvider(scanFilters);
                return true;
            }

            if (mMerged.getScanTypes().contains(SCAN_TYPE_NEARBY_PRESENCE)) {
                startChreProvider(scanFilters);
                return true;
            }

            startBleProvider(scanFilters);
            return true;
        }
    }

    @GuardedBy("mMultiplexerLock")
    private void startBleProvider(Set<ScanFilter> scanFilters) {
        if (!mBleDiscoveryProvider.getController().isStarted()) {
            Log.d(TAG, "DiscoveryProviderManager starts BLE scanning.");
            mBleDiscoveryProvider.getController().setListener(this);
            mBleDiscoveryProvider.getController().setProviderScanMode(mMerged.getScanMode());
            mBleDiscoveryProvider.getController().setProviderScanFilters(
                    new ArrayList<>(scanFilters));
            mBleDiscoveryProvider.getController().start();
        }
    }

    @VisibleForTesting
    @GuardedBy("mMultiplexerLock")
    void startChreProvider(Collection<ScanFilter> scanFilters) {
        Log.d(TAG, "DiscoveryProviderManager starts CHRE scanning. " + mMerged);
        mChreDiscoveryProvider.getController().setProviderScanFilters(new ArrayList<>(scanFilters));
        mChreDiscoveryProvider.getController().setProviderScanMode(mMerged.getScanMode());
        mChreDiscoveryProvider.getController().start();
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
            synchronized (mMultiplexerLock) {
                mBleDiscoveryProvider.getController().setProviderScanMode(mMerged.getScanMode());
            }
        } else {
            Log.d(TAG, "Skip invalidating BleDiscoveryProvider scan mode because the provider not "
                    + "started.");
        }
    }

    @Override
    public MergedDiscoveryRequest mergeRegistrations(
            @NonNull Collection<DiscoveryRegistration> registrations) {
        MergedDiscoveryRequest.Builder builder = new MergedDiscoveryRequest.Builder();
        int scanMode = ScanRequest.SCAN_MODE_NO_POWER;
        for (DiscoveryRegistration registration : registrations) {
            builder.addActions(registration.getActions());
            builder.addScanFilters(registration.getPresenceScanFilters());
            Log.d(TAG,
                    "mergeRegistrations: type is " + registration.getScanRequest().getScanType());
            builder.addScanType(registration.getScanRequest().getScanType());
            if (registration.getScanRequest().isBleEnabled()) {
                builder.addMedium(MergedDiscoveryRequest.Medium.BLE);
            }
            int requestScanMode = registration.getScanRequest().getScanMode();
            if (scanMode < requestScanMode) {
                scanMode = requestScanMode;
            }
        }
        builder.setScanMode(scanMode);
        return builder.build();
    }

    @Override
    public void onMergedRegistrationsUpdated() {
        invalidateProviderScanMode();
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
