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
import android.hardware.wifi.supplicant.V1_4.ISupplicantStaNetworkCallback;

class SupplicantStaNetworkCallbackHidlV1_4Impl extends ISupplicantStaNetworkCallback.Stub {
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

    private final WifiMonitor mWifiMonitor;

    private final Object mLock;

    private SupplicantStaNetworkHalHidlImpl.SupplicantStaNetworkHalCallback mCallbackV10;

    SupplicantStaNetworkCallbackHidlV1_4Impl(
            @NonNull SupplicantStaNetworkHalHidlImpl networkHal,
            int frameworkNetworkId, @NonNull String ssid,
            @NonNull String ifaceName, @NonNull Object lock, @NonNull WifiMonitor wifiMonitor) {
        mNetworkHal = networkHal;

        mFrameworkNetworkId = frameworkNetworkId;
        mSsid = ssid;

        mIfaceName = ifaceName;
        mLock = lock;
        mWifiMonitor = wifiMonitor;

        mCallbackV10 = mNetworkHal.new SupplicantStaNetworkHalCallback(
                frameworkNetworkId, ssid);
    }

    @Override
    public void onNetworkEapSimGsmAuthRequest(
            ISupplicantStaNetworkCallback.NetworkRequestEapSimGsmAuthParams params) {
        mCallbackV10.onNetworkEapSimGsmAuthRequest(params);
    }

    @Override
    public void onNetworkEapSimUmtsAuthRequest(
            ISupplicantStaNetworkCallback.NetworkRequestEapSimUmtsAuthParams params) {
        mCallbackV10.onNetworkEapSimUmtsAuthRequest(params);
    }

    @Override
    public void onNetworkEapIdentityRequest() {
        mCallbackV10.onNetworkEapIdentityRequest();
    }

    @Override
    public void onTransitionDisable(int indicationBits) {
        synchronized (mLock) {
            mNetworkHal.logCallback("onTransitionDisable");
            int frameworkBits = 0;
            if (0 != (indicationBits & TransitionDisableIndication.USE_WPA3_PERSONAL)) {
                frameworkBits |= WifiMonitor.TDI_USE_WPA3_PERSONAL;
            }
            if (0 != (indicationBits & TransitionDisableIndication.USE_SAE_PK)) {
                frameworkBits |= WifiMonitor.TDI_USE_SAE_PK;
            }
            if (0 != (indicationBits & TransitionDisableIndication.USE_WPA3_ENTERPRISE)) {
                frameworkBits |= WifiMonitor.TDI_USE_WPA3_ENTERPRISE;
            }
            if (0 != (indicationBits & TransitionDisableIndication.USE_ENHANCED_OPEN)) {
                frameworkBits |= WifiMonitor.TDI_USE_ENHANCED_OPEN;
            }
            if (0 == frameworkBits) return;

            mWifiMonitor.broadcastTransitionDisableEvent(
                    mIfaceName, mFrameworkNetworkId, frameworkBits);
        }
    }
}
