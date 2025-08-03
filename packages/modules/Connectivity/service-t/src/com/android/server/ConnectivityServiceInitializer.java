/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.thread.ThreadNetworkManager;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;
import com.android.networkstack.apishim.ConstantsShim;
import com.android.server.connectivity.ConnectivityNativeService;
import com.android.server.ethernet.EthernetService;
import com.android.server.ethernet.EthernetServiceImpl;
import com.android.server.nearby.NearbyService;
import com.android.server.thread.ThreadNetworkService;

/**
 * Connectivity service initializer for core networking. This is called by system server to create
 * a new instance of connectivity services.
 */
public final class ConnectivityServiceInitializer extends SystemService {
    private static final String TAG = ConnectivityServiceInitializer.class.getSimpleName();
    private final ConnectivityNativeService mConnectivityNative;
    private final ConnectivityService mConnectivity;
    private final IpSecService mIpSecService;
    private final NsdService mNsdService;
    private final NearbyService mNearbyService;
    private final EthernetServiceImpl mEthernetServiceImpl;
    private final ThreadNetworkService mThreadNetworkService;

    public ConnectivityServiceInitializer(Context context) {
        super(context);
        // Load JNI libraries used by ConnectivityService and its dependencies
        System.loadLibrary("service-connectivity");
        mEthernetServiceImpl = createEthernetService(context);
        mConnectivity = new ConnectivityService(context);
        mIpSecService = createIpSecService(context);
        mConnectivityNative = createConnectivityNativeService(context);
        mNsdService = createNsdService(context);
        mNearbyService = createNearbyService(context);
        mThreadNetworkService = createThreadNetworkService(context);
    }

    @Override
    public void onStart() {
        if (mEthernetServiceImpl != null) {
            Log.i(TAG, "Registering " + Context.ETHERNET_SERVICE);
            publishBinderService(Context.ETHERNET_SERVICE, mEthernetServiceImpl,
                    /* allowIsolated= */ false);
        }

        Log.i(TAG, "Registering " + Context.CONNECTIVITY_SERVICE);
        publishBinderService(Context.CONNECTIVITY_SERVICE, mConnectivity,
                /* allowIsolated= */ false);

        if (mIpSecService != null) {
            Log.i(TAG, "Registering " + Context.IPSEC_SERVICE);
            publishBinderService(Context.IPSEC_SERVICE, mIpSecService, /* allowIsolated= */ false);
        }

        if (mConnectivityNative != null) {
            Log.i(TAG, "Registering " + ConnectivityNativeService.SERVICE_NAME);
            publishBinderService(ConnectivityNativeService.SERVICE_NAME, mConnectivityNative,
                    /* allowIsolated= */ false);
        }

        if (mNsdService != null) {
            Log.i(TAG, "Registering " + Context.NSD_SERVICE);
            publishBinderService(Context.NSD_SERVICE, mNsdService, /* allowIsolated= */ false);
        }

        if (mNearbyService != null) {
            Log.i(TAG, "Registering " + ConstantsShim.NEARBY_SERVICE);
            publishBinderService(ConstantsShim.NEARBY_SERVICE, mNearbyService,
                    /* allowIsolated= */ false);
        }

        if (mThreadNetworkService != null) {
            Log.i(TAG, "Registering " + ThreadNetworkManager.SERVICE_NAME);
            publishBinderService(ThreadNetworkManager.SERVICE_NAME, mThreadNetworkService,
                    /* allowIsolated= */ false);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (mNearbyService != null) {
            mNearbyService.onBootPhase(phase);
        }

        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY && mEthernetServiceImpl != null) {
            mEthernetServiceImpl.start();
        }

        if (mThreadNetworkService != null) {
            mThreadNetworkService.onBootPhase(phase);
        }
    }

    /**
     * Return IpSecService instance, or null if current SDK is lower than T.
     */
    private IpSecService createIpSecService(final Context context) {
        if (!SdkLevel.isAtLeastT()) return null;

        return new IpSecService(context);
    }

    /**
     * Return ConnectivityNativeService instance, or null if current SDK is lower than T.
     */
    private ConnectivityNativeService createConnectivityNativeService(final Context context) {
        if (!SdkLevel.isAtLeastT()) return null;
        try {
            return new ConnectivityNativeService(context);
        } catch (UnsupportedOperationException e) {
            Log.d(TAG, "Unable to get ConnectivityNative service", e);
            return null;
        }
    }

    /** Return NsdService instance or null if current SDK is lower than T */
    private NsdService createNsdService(final Context context) {
        if (!SdkLevel.isAtLeastT()) return null;

        return NsdService.create(context);
    }

    /** Return Nearby service instance or null if current SDK is lower than T */
    private NearbyService createNearbyService(final Context context) {
        if (!SdkLevel.isAtLeastT()) return null;
        try {
            return new NearbyService(context);
        } catch (UnsupportedOperationException e) {
            // Nearby is not yet supported in all branches
            // TODO: remove catch clause when it is available.
            Log.i(TAG, "Skipping unsupported service " + ConstantsShim.NEARBY_SERVICE);
            return null;
        }
    }

    /**
     * Return EthernetServiceImpl instance or null if current SDK is lower than T or Ethernet
     * service isn't necessary.
     */
    private EthernetServiceImpl createEthernetService(final Context context) {
        if (!SdkLevel.isAtLeastT() || !mConnectivity.deviceSupportsEthernet(context)) {
            return null;
        }
        return EthernetService.create(context);
    }

    /**
     * Returns Thread network service instance if supported.
     * Thread is supported if all of below are satisfied:
     * 1. the FEATURE_THREAD_NETWORK is available
     * 2. the SDK level is V+, or SDK level is U and the device is a TV
     */
    @Nullable
    private ThreadNetworkService createThreadNetworkService(final Context context) {
        final PackageManager pm = context.getPackageManager();
        if (!pm.hasSystemFeature(ThreadNetworkManager.FEATURE_NAME)) {
            return null;
        }
        if (!SdkLevel.isAtLeastU()) {
            return null;
        }
        if (!SdkLevel.isAtLeastV() && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return null;
        }
        return new ThreadNetworkService(context);
    }
}
