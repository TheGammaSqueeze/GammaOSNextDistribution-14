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

package com.android.server.wifi.hal;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

/**
 * Wrapper around a WifiP2pIface.
 * May be initialized using a HIDL or AIDL WifiP2pIface.
 */
public class WifiP2pIface implements WifiHal.WifiInterface {
    private static final String TAG = "WifiP2pIface";
    private final IWifiP2pIface mWifiP2pIface;

    public WifiP2pIface(@NonNull android.hardware.wifi.V1_0.IWifiP2pIface p2pIface) {
        mWifiP2pIface = createWifiP2pIfaceHidlImplMockable(p2pIface);
    }

    public WifiP2pIface(@NonNull android.hardware.wifi.IWifiP2pIface p2pIface) {
        mWifiP2pIface = createWifiP2pIfaceAidlImplMockable(p2pIface);
    }

    protected WifiP2pIfaceHidlImpl createWifiP2pIfaceHidlImplMockable(
            android.hardware.wifi.V1_0.IWifiP2pIface p2pIface) {
        return new WifiP2pIfaceHidlImpl(p2pIface);
    }

    protected WifiP2pIfaceAidlImpl createWifiP2pIfaceAidlImplMockable(
            android.hardware.wifi.IWifiP2pIface p2pIface) {
        return new WifiP2pIfaceAidlImpl(p2pIface);
    }

    private void handleNullIface(String methodStr) {
        Log.wtf(TAG, "Cannot call " + methodStr + " because mWifiP2pIface is null");
    }

    /**
     * See comments for {@link IWifiP2pIface#getName()}
     */
    @Override
    @Nullable
    public String getName() {
        final String methodStr = "getName";
        if (mWifiP2pIface == null) {
            handleNullIface(methodStr);
            return null;
        }
        return mWifiP2pIface.getName();
    }
}
