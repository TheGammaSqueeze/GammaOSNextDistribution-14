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

package com.android.tv.settings.connectivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.annotation.Nullable;

import java.util.Optional;

/**
 * Provides information about the currently connected network synchronously.
 */
public class ActiveNetworkProvider {
    private final ConnectivityManager mConnectivityManager;
    private final Optional<WifiManager> mWifiManagerOptional;
    @Nullable private NetworkInfo mNetworkInfo;

    ActiveNetworkProvider(Context context) {
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mWifiManagerOptional = Optional.ofNullable(context.getSystemService(WifiManager.class));
        mNetworkInfo = null;
    }

    void updateActiveNetwork() {
        mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
    }

    public boolean isConnected() {
        return mNetworkInfo != null
                && (mNetworkInfo.isConnected() || mNetworkInfo.isConnectedOrConnecting());
    }

    public boolean isTypeCellular() {
        return isConnected() && mNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE;
    }

    public boolean isTypeEthernet() {
        return isConnected() && mNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET;
    }

    public boolean isTypeWifi() {
        return isConnected() && mNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    public boolean isWifiEnabled() {
        return mWifiManagerOptional.map(WifiManager::isWifiEnabled).orElse(false);
    }

    @Nullable
    public String getSsid() {
        if (!isTypeWifi()) {
            return null;
        }
        return mWifiManagerOptional
                .map(WifiManager::getConnectionInfo)
                .map(WifiInfo::getSSID)
                .map(ActiveNetworkProvider::sanitizeSsid)
                .orElse(null);
    }

    private static String sanitizeSsid(@Nullable String string) {
        return removeDoubleQuotes(string);
    }

    private static String removeDoubleQuotes(@Nullable String string) {
        if (string == null) return null;
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }
}
