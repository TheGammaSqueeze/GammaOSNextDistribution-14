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

package com.android.server.wifi;

import android.annotation.NonNull;

/**
 * Used to respond to calls to ClientMode interface when ClientModeImpl is not up
 * i.e. in scan only mode.
 */
public class ScanOnlyModeImpl implements ClientModeDefaults {

    private final long mId;
    private final WifiNative mWifiNative;
    private final String mIfaceName;

    public ScanOnlyModeImpl(long id, @NonNull WifiNative wifiNative, @NonNull String ifaceName) {
        mId = id;
        mWifiNative = wifiNative;
        mIfaceName = ifaceName;
    }

    @Override
    public long getId() {
        return mId;
    }

    @Override
    public long getSupportedFeatures() {
        return mWifiNative.getSupportedFeatureSet(mIfaceName);
    }

    @Override
    public boolean isWifiStandardSupported(int standard) {
        return mWifiNative.isWifiStandardSupported(mIfaceName, standard);
    }

    @Override
    public boolean setCountryCode(String countryCode) {
        return mWifiNative.setChipCountryCode(countryCode);
    }

    @Override
    public String toString() {
        return "ScanOnlyModeImpl{"
                + "id=" + mId
                + " iface=" + mIfaceName
                + '}';
    }
}
