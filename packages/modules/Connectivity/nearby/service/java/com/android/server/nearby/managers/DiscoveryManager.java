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

import android.nearby.IScanListener;
import android.nearby.NearbyManager;
import android.nearby.ScanRequest;
import android.nearby.aidl.IOffloadCallback;

import com.android.server.nearby.util.identity.CallerIdentity;

/**
 * Interface added for flagging DiscoveryProviderManager refactor. After the
 * nearby_refactor_discovery_manager flag is fully rolled out, this can be deleted.
 */
public interface DiscoveryManager {

    /**
     * Registers the listener in the manager and starts scan according to the requested scan mode.
     */
    @NearbyManager.ScanStatus
    int registerScanListener(ScanRequest scanRequest, IScanListener listener,
            CallerIdentity callerIdentity);

    /**
     * Unregisters the listener in the manager and adjusts the scan mode if necessary afterwards.
     */
    void unregisterScanListener(IScanListener listener);

    /** Query offload capability in a device. */
    void queryOffloadCapability(IOffloadCallback callback);

    /** Called after boot completed. */
    void init();
}
