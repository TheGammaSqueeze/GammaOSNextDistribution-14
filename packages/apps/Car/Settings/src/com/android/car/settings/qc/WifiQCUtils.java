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

package com.android.car.settings.qc;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import androidx.annotation.DrawableRes;

import com.android.car.settings.R;
import com.android.car.settings.wifi.WifiUtil;

/**
 * Helper methods for Wifi-related quick controls.
 */
public class WifiQCUtils {
    private WifiQCUtils() {
    }

    /**
     * Returns the subtitle string based on the current wifi state.
     */
    public static String getSubtitle(Context context, WifiManager wifiManager) {
        int wifiState = wifiManager.getWifiState();
        int stringId = WifiUtil.getStateDesc(wifiState);
        if (stringId != 0) {
            return context.getString(stringId);
        }
        if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
            String wifiName;
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo.isPasspointAp() || wifiInfo.isOsuAp()) {
                wifiName = wifiInfo.getPasspointProviderFriendlyName();
                if (TextUtils.isEmpty(wifiName)) {
                    wifiName = wifiInfo.getSSID();
                }
            } else {
                wifiName = wifiInfo.getSSID();
            }

            if (wifiName == null || wifiName.equals(WifiManager.UNKNOWN_SSID)) {
                return context.getString(R.string.wifi_disconnected);
            }
            return WifiInfo.sanitizeSsid(wifiName);
        }
        return context.getString(R.string.wifi_disabled);
    }

    /**
     * Returns the icon resource for the current wifi state.
     */
    @DrawableRes
    public static int getIcon(WifiManager wifiManager) {
        if (!wifiManager.isWifiEnabled()) {
            return R.drawable.ic_qc_wifi_disabled;
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo.getNetworkId() == -1) {
            return R.drawable.ic_qc_wifi_disconnected;
        }
        int rssi = wifiInfo.getRssi();
        if (rssi == WifiInfo.INVALID_RSSI) {
            return R.drawable.ic_qc_wifi_disconnected;
        }
        int level = wifiManager.calculateSignalLevel(rssi);
        switch (level) {
            case 0:
                return R.drawable.ic_qc_wifi_level_0;
            case 1:
                return R.drawable.ic_qc_wifi_level_1;
            case 2:
                return R.drawable.ic_qc_wifi_level_2;
            case 3:
                return R.drawable.ic_qc_wifi_level_3;
            case 4:
            default:
                return R.drawable.ic_qc_wifi_level_4;
        }
    }

    /**
     * Returns if the wifi is changing state between enabled and disabled.
     */
    public static boolean isWifiBusy(WifiManager wifiManager) {
        int state = wifiManager.getWifiState();
        return state == WifiManager.WIFI_STATE_ENABLING
            || state == WifiManager.WIFI_STATE_DISABLING;
    }

}
