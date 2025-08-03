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
import android.hardware.wifi.supplicant.V1_0.ISupplicantStaNetworkCallback;

import com.android.server.wifi.util.NativeUtil;

class SupplicantStaNetworkCallbackHidlImpl extends ISupplicantStaNetworkCallback.Stub {
    private final SupplicantStaNetworkHalHidlImpl mNetworkHal;

    /**
     * Current configured network's framework network id.
     */
    private final int mFrameworkNetworkId;
    /**
     * Current configured network's ssid.
     */
    private final String mSsid;

    private final String mIfaceName;

    private final Object mLock;

    private final WifiMonitor mWifiMonitor;

    SupplicantStaNetworkCallbackHidlImpl(
            @NonNull SupplicantStaNetworkHalHidlImpl networkHal,
            int frameworkNetworkId, String ssid,
            @NonNull String ifaceName, @NonNull Object lock, @NonNull WifiMonitor wifiMonitor) {
        mNetworkHal = networkHal;

        mFrameworkNetworkId = frameworkNetworkId;
        mSsid = ssid;

        mIfaceName = ifaceName;
        mLock = lock;
        mWifiMonitor = wifiMonitor;
    }

    @Override
    public void onNetworkEapSimGsmAuthRequest(
            ISupplicantStaNetworkCallback.NetworkRequestEapSimGsmAuthParams params) {
        synchronized (mLock) {
            mNetworkHal.logCallback("onNetworkEapSimGsmAuthRequest");
            String[] data = new String[params.rands.size()];
            int i = 0;
            for (byte[] rand : params.rands) {
                data[i++] = NativeUtil.hexStringFromByteArray(rand);
            }
            mWifiMonitor.broadcastNetworkGsmAuthRequestEvent(
                    mIfaceName, mFrameworkNetworkId, mSsid, data);
        }
    }

    @Override
    public void onNetworkEapSimUmtsAuthRequest(
            ISupplicantStaNetworkCallback.NetworkRequestEapSimUmtsAuthParams params) {
        synchronized (mLock) {
            mNetworkHal.logCallback("onNetworkEapSimUmtsAuthRequest");
            String randHex = NativeUtil.hexStringFromByteArray(params.rand);
            String autnHex = NativeUtil.hexStringFromByteArray(params.autn);
            String[] data = {randHex, autnHex};
            mWifiMonitor.broadcastNetworkUmtsAuthRequestEvent(
                    mIfaceName, mFrameworkNetworkId, mSsid, data);
        }
    }

    @Override
    public void onNetworkEapIdentityRequest() {
        synchronized (mLock) {
            mNetworkHal.logCallback("onNetworkEapIdentityRequest");
            mWifiMonitor.broadcastNetworkIdentityRequestEvent(
                    mIfaceName, mFrameworkNetworkId, mSsid);
        }
    }
}
