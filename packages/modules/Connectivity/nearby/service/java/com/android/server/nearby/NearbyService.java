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

package com.android.server.nearby;

import static com.android.server.SystemService.PHASE_BOOT_COMPLETED;
import static com.android.server.SystemService.PHASE_SYSTEM_SERVICES_READY;

import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.location.ContextHubManager;
import android.nearby.BroadcastRequestParcelable;
import android.nearby.IBroadcastListener;
import android.nearby.INearbyManager;
import android.nearby.IScanListener;
import android.nearby.NearbyManager;
import android.nearby.PoweredOffFindingEphemeralId;
import android.nearby.ScanRequest;
import android.nearby.aidl.IOffloadCallback;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.injector.Injector;
import com.android.server.nearby.managers.BluetoothFinderManager;
import com.android.server.nearby.managers.BroadcastProviderManager;
import com.android.server.nearby.managers.DiscoveryManager;
import com.android.server.nearby.managers.DiscoveryProviderManager;
import com.android.server.nearby.managers.DiscoveryProviderManagerLegacy;
import com.android.server.nearby.presence.PresenceManager;
import com.android.server.nearby.util.identity.CallerIdentity;
import com.android.server.nearby.util.permissions.BroadcastPermissions;
import com.android.server.nearby.util.permissions.DiscoveryPermissions;

import java.util.List;

/** Service implementing nearby functionality. */
public class NearbyService extends INearbyManager.Stub {
    public static final String TAG = "NearbyService";
    // Sets to true to start BLE scan from PresenceManager for manual testing.
    public static final Boolean MANUAL_TEST = false;

    private final Context mContext;
    private final PresenceManager mPresenceManager;
    private final NearbyConfiguration mNearbyConfiguration;
    private Injector mInjector;
    private final BroadcastReceiver mBluetoothReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int state =
                            intent.getIntExtra(
                                    BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_ON) {
                        if (mInjector != null && mInjector instanceof SystemInjector) {
                            // Have to do this logic in listener. Even during PHASE_BOOT_COMPLETED
                            // phase, BluetoothAdapter is not null, the BleScanner is null.
                            Log.v(TAG, "Initiating BluetoothAdapter when Bluetooth is turned on.");
                            ((SystemInjector) mInjector).initializeBluetoothAdapter();
                        }
                    }
                }
            };
    private final DiscoveryManager mDiscoveryProviderManager;
    private final BroadcastProviderManager mBroadcastProviderManager;
    private final BluetoothFinderManager mBluetoothFinderManager;

    public NearbyService(Context context) {
        mContext = context;
        mInjector = new SystemInjector(context);
        mBroadcastProviderManager = new BroadcastProviderManager(context, mInjector);
        mPresenceManager = new PresenceManager(context);
        mNearbyConfiguration = new NearbyConfiguration();
        mDiscoveryProviderManager =
                mNearbyConfiguration.refactorDiscoveryManager()
                        ? new DiscoveryProviderManager(context, mInjector)
                        : new DiscoveryProviderManagerLegacy(context, mInjector);
        mBluetoothFinderManager = new BluetoothFinderManager();
    }

    @VisibleForTesting
    void setInjector(Injector injector) {
        this.mInjector = injector;
    }

    @Override
    @NearbyManager.ScanStatus
    public int registerScanListener(ScanRequest scanRequest, IScanListener listener,
            String packageName, @Nullable String attributionTag) {
        // Permissions check
        enforceBluetoothPrivilegedPermission(mContext);
        CallerIdentity identity = CallerIdentity.fromBinder(mContext, packageName, attributionTag);
        DiscoveryPermissions.enforceDiscoveryPermission(mContext, identity);

        return mDiscoveryProviderManager.registerScanListener(scanRequest, listener, identity);
    }

    @Override
    public void unregisterScanListener(IScanListener listener, String packageName,
            @Nullable String attributionTag) {
        // Permissions check
        enforceBluetoothPrivilegedPermission(mContext);
        CallerIdentity identity = CallerIdentity.fromBinder(mContext, packageName, attributionTag);
        DiscoveryPermissions.enforceDiscoveryPermission(mContext, identity);

        mDiscoveryProviderManager.unregisterScanListener(listener);
    }

    @Override
    public void startBroadcast(BroadcastRequestParcelable broadcastRequestParcelable,
            IBroadcastListener listener, String packageName, @Nullable String attributionTag) {
        // Permissions check
        enforceBluetoothPrivilegedPermission(mContext);
        BroadcastPermissions.enforceBroadcastPermission(
                mContext, CallerIdentity.fromBinder(mContext, packageName, attributionTag));

        mBroadcastProviderManager.startBroadcast(
                broadcastRequestParcelable.getBroadcastRequest(), listener);
    }

    @Override
    public void stopBroadcast(IBroadcastListener listener, String packageName,
            @Nullable String attributionTag) {
        // Permissions check
        enforceBluetoothPrivilegedPermission(mContext);
        CallerIdentity identity = CallerIdentity.fromBinder(mContext, packageName, attributionTag);
        BroadcastPermissions.enforceBroadcastPermission(mContext, identity);

        mBroadcastProviderManager.stopBroadcast(listener);
    }

    @Override
    public void queryOffloadCapability(IOffloadCallback callback) {
        mDiscoveryProviderManager.queryOffloadCapability(callback);
    }

    @Override
    public void setPoweredOffFindingEphemeralIds(List<PoweredOffFindingEphemeralId> eids) {
        // Permissions check
        enforceBluetoothPrivilegedPermission(mContext);

        mBluetoothFinderManager.sendEids(eids);
    }

    @Override
    public void setPoweredOffModeEnabled(boolean enabled) {
        // Permissions check
        enforceBluetoothPrivilegedPermission(mContext);

        mBluetoothFinderManager.setPoweredOffFinderMode(enabled);
    }

    @Override
    public boolean getPoweredOffModeEnabled() {
        // Permissions check
        enforceBluetoothPrivilegedPermission(mContext);

        return mBluetoothFinderManager.getPoweredOffFinderMode();
    }

    /**
     * Called by the service initializer.
     *
     * <p>{@see com.android.server.SystemService#onBootPhase}.
     */
    public void onBootPhase(int phase) {
        switch (phase) {
            case PHASE_SYSTEM_SERVICES_READY:
                if (mInjector instanceof SystemInjector) {
                    ((SystemInjector) mInjector).initializeAppOpsManager();
                }
                break;
            case PHASE_BOOT_COMPLETED:
                // mInjector needs to be initialized before mProviderManager.
                if (mInjector instanceof SystemInjector) {
                    // The nearby service must be functioning after this boot phase.
                    ((SystemInjector) mInjector).initializeBluetoothAdapter();
                    // Initialize ContextManager for CHRE scan.
                    ((SystemInjector) mInjector).initializeContextHubManager();
                }
                mDiscoveryProviderManager.init();
                mContext.registerReceiver(
                        mBluetoothReceiver,
                        new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
                // Only enable for manual Presence test on device.
                if (MANUAL_TEST) {
                    mPresenceManager.initiate();
                }
                break;
        }
    }

    /**
     * If the calling process of has not been granted
     * {@link android.Manifest.permission.BLUETOOTH_PRIVILEGED} permission,
     * throw a {@link SecurityException}.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_PRIVILEGED)
    private void enforceBluetoothPrivilegedPermission(Context context) {
        if (!mNearbyConfiguration.isTestAppSupported()) {
            context.enforceCallingOrSelfPermission(
                    android.Manifest.permission.BLUETOOTH_PRIVILEGED,
                    "Need BLUETOOTH PRIVILEGED permission");
        }
    }

    private static final class SystemInjector implements Injector {
        private final Context mContext;
        @Nullable private BluetoothAdapter mBluetoothAdapter;
        @Nullable private ContextHubManager mContextHubManager;
        @Nullable private AppOpsManager mAppOpsManager;

        SystemInjector(Context context) {
            mContext = context;
        }

        @Override
        @Nullable
        public BluetoothAdapter getBluetoothAdapter() {
            return mBluetoothAdapter;
        }

        @Override
        @Nullable
        public ContextHubManager getContextHubManager() {
            return mContextHubManager;
        }

        @Override
        @Nullable
        public AppOpsManager getAppOpsManager() {
            return mAppOpsManager;
        }

        synchronized void initializeBluetoothAdapter() {
            if (mBluetoothAdapter != null) {
                return;
            }
            BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
            if (manager == null) {
                return;
            }
            mBluetoothAdapter = manager.getAdapter();
        }

        synchronized void initializeContextHubManager() {
            if (mContextHubManager != null) {
                return;
            }
            if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CONTEXT_HUB)) {
                mContextHubManager = mContext.getSystemService(ContextHubManager.class);
            }
        }

        synchronized void initializeAppOpsManager() {
            if (mAppOpsManager != null) {
                return;
            }
            mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        }
    }
}
