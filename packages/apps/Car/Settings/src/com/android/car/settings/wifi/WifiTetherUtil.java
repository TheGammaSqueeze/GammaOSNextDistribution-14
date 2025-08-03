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

package com.android.car.settings.wifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.text.TextUtils;

import com.android.car.settings.R;
import com.android.internal.util.ConcurrentUtils;
import com.android.settingslib.wifi.WifiUtils;

/**
 * Collection of helper methods for Wi-Fi tethering.
 */
public class WifiTetherUtil {
    private WifiTetherUtil() {
    }

    /**
     * Helper method to enable tethering with {@link TetheringManager.StartTetheringCallback}
     * on success or failure.
     */
    public static void startTethering(TetheringManager tetheringManager,
            TetheringManager.StartTetheringCallback callback) {
        tetheringManager.startTethering(ConnectivityManager.TETHERING_WIFI,
                ConcurrentUtils.DIRECT_EXECUTOR, callback);
    }

    /**
     * Helper method to disable tethering.
     */
    public static void stopTethering(TetheringManager tetheringManager) {
        tetheringManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
    }

    /** Returns the subtitle to be shown for hotspot action preferences.
     * There are three different states that can be shown:
     * - If tethering is disabled, return the off string.
     * - If tethering is enabled but no devices are connected, return the ssid + password string.
     * - If tethering is enabled and devices are connected, return the devices connected string.
     */
    public static String getHotspotSubtitle(Context context, SoftApConfiguration softApConfig,
            boolean hotspotEnabled, int connectedDevices) {
        if (!hotspotEnabled) {
            return context.getString(R.string.wifi_hotspot_state_off);
        }
        if (connectedDevices > 0) {
            return WifiUtils.getWifiTetherSummaryForConnectedDevices(context, connectedDevices);
        }
        String subtitle = softApConfig.getSsid();
        if (TextUtils.isEmpty(subtitle)) {
            // If there currently is no SSID to show, use a default "On" string
            return context.getString(R.string.car_ui_preference_switch_on);
        }
        String password = getHotspotPassword(softApConfig);
        if (!TextUtils.isEmpty(password)) {
            subtitle += " / " + password;
        }
        return subtitle;
    }

    private static String getHotspotPassword(SoftApConfiguration softApConfig) {
        if (softApConfig.getSecurityType() == SoftApConfiguration.SECURITY_TYPE_OPEN) {
            return null;
        }
        return softApConfig.getPassphrase();
    }
}
