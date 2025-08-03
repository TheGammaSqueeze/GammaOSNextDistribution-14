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
import android.net.TetheringManager;
import android.net.Uri;
import android.net.wifi.WifiManager;

/**
 * Helper methods for hotspot related quick controls.
 */
public final class HotspotQCUtils {
    private HotspotQCUtils() {
    }

    /**
     * Returns whether or not hotspot is currently enabled.
     */
    public static boolean isHotspotEnabled(WifiManager wifiManager) {
        int state = wifiManager.getWifiApState();
        return state == WifiManager.WIFI_AP_STATE_ENABLED
                || state == WifiManager.WIFI_AP_STATE_ENABLING;
    }

    /**
     * Returns whether or not hotspot is currently busy.
     */
    public static boolean isHotspotBusy(WifiManager wifiManager) {
        int state = wifiManager.getWifiApState();
        return state == WifiManager.WIFI_AP_STATE_ENABLING
                || state == WifiManager.WIFI_AP_STATE_DISABLING;
    }

    /**
     * Helper method to get the default {@link TetheringManager.StartTetheringCallback} used by
     * hotspot quick controls.
     */
    public static TetheringManager.StartTetheringCallback getDefaultStartTetheringCallback(
            Context context, Uri uri) {
        return new TetheringManager.StartTetheringCallback() {
            @Override
            public void onTetheringFailed(final int result) {
                context.getContentResolver().notifyChange(uri, /* observer= */null);
            }
        };
    }
}
