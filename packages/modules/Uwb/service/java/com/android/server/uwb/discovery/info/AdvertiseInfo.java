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
package com.android.server.uwb.discovery.info;

import android.bluetooth.le.AdvertisingSetParameters;

import com.android.server.uwb.discovery.ble.DiscoveryAdvertisement;

/**
 * Holds information about the discovery advertise request.
 */
public class AdvertiseInfo {

    public AdvertiseInfo(
            AdvertisingSetParameters advertisingSetParameters,
            DiscoveryAdvertisement discoveryAdvertisement) {
        this.advertisingSetParameters = advertisingSetParameters;
        this.discoveryAdvertisement = discoveryAdvertisement;
    }

    /** BLE advertise parameters */
    public final AdvertisingSetParameters advertisingSetParameters;

    public final DiscoveryAdvertisement discoveryAdvertisement;
}
