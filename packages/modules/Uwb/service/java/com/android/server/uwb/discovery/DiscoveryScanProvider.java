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

import android.bluetooth.le.ScanResult;

import androidx.annotation.WorkerThread;

import com.android.server.uwb.discovery.ble.DiscoveryAdvertisement;

/** Abstract class for Discovery Scan Provider */
@WorkerThread
public abstract class DiscoveryScanProvider extends DiscoveryProvider {

    /** Holds information about the discovery request. */
    public static class DiscoveryResult {

        // BLE discovery result
        public ScanResult scanResult;
        public DiscoveryAdvertisement discoveryAdvertisement;

        public DiscoveryResult(
                ScanResult scanResult, DiscoveryAdvertisement discoveryAdvertisement) {
            this.scanResult = scanResult;
            this.discoveryAdvertisement = discoveryAdvertisement;
        }
    }

    /** Callback for listening to discovery events. */
    @WorkerThread
    public interface DiscoveryScanCallback {
        /**
         * Called when device is discovered.
         *
         * @param result provide the info on discovered device.
         */
        void onDiscovered(DiscoveryResult result);
        /**
         * Called when discovery failed.
         *
         * @param errorCode discovery failure error code.
         */
        void onDiscoveryFailed(int errorCode);
    }
}
