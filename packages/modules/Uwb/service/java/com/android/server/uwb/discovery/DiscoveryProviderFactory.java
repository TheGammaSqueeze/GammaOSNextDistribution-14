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
package com.android.server.uwb.discovery;

import android.content.AttributionSource;
import android.content.Context;

import androidx.annotation.WorkerThread;

import com.android.server.uwb.discovery.DiscoveryAdvertiseProvider.DiscoveryAdvertiseCallback;
import com.android.server.uwb.discovery.DiscoveryScanProvider.DiscoveryScanCallback;
import com.android.server.uwb.discovery.ble.BleDiscoveryAdvertiseProvider;
import com.android.server.uwb.discovery.ble.BleDiscoveryScanProvider;
import com.android.server.uwb.discovery.info.DiscoveryInfo;

import java.util.concurrent.Executor;

/** Factory for creating DiscoveryProvider. */
@WorkerThread
public class DiscoveryProviderFactory {
    private DiscoveryProviderFactory() {}

    /**
     * Create a DiscoveryScanProvider.
     *
     * @param attributionSource Attribution Source.
     * @param context Context.
     * @param executor Executor.
     * @param discoveryInfo Info of the discovery request.
     * @param discoveryScanCallback callback for discovery scan events.
     */
    public static DiscoveryScanProvider createScanner(
            AttributionSource attributionSource,
            Context context,
            Executor executor,
            DiscoveryInfo discoveryInfo,
            DiscoveryScanCallback discoveryScanCallback)
            throws AssertionError {

        switch (discoveryInfo.transportType) {
            case BLE:
                return new BleDiscoveryScanProvider(
                        attributionSource,
                        context,
                        executor,
                        discoveryInfo.scanInfo.get(),
                        discoveryScanCallback);
        }
        return null;
    }

    /**
     * Create a DiscoveryAdvertiseProvider.
     *
     * @param attributionSource Attribution Source.
     * @param context Context.
     * @param executor Executor.
     * @param discoveryInfo Info of the discovery request.
     * @param discoveryAdvertiseCallback callback for discovery advertise events.
     */
    public static DiscoveryAdvertiseProvider createAdvertiser(
            AttributionSource attributionSource,
            Context context,
            Executor executor,
            DiscoveryInfo discoveryInfo,
            DiscoveryAdvertiseCallback discoveryAdvertiseCallback)
            throws AssertionError {

        switch (discoveryInfo.transportType) {
            case BLE:
                return new BleDiscoveryAdvertiseProvider(
                        attributionSource,
                        context,
                        executor,
                        discoveryInfo.advertiseInfo.get(),
                        discoveryAdvertiseCallback);
        }
        return null;
    }
}
