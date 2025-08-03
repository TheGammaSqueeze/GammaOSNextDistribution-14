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
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * Stores disconnect information passed from WifiMonitor.
 */
public class DisconnectEventInfo {
    @NonNull public final String ssid;
    @NonNull public final String bssid;
    public final int reasonCode;
    public final boolean locallyGenerated;

    private static final String TAG = "DisconnectEventInfo";

    public DisconnectEventInfo(@NonNull String ssid, @NonNull String bssid, int reasonCode,
            boolean locallyGenerated) {
        if (ssid == null) {
            Log.wtf(TAG, "Null SSID provided");
            this.ssid = WifiManager.UNKNOWN_SSID;
        } else {
            this.ssid = ssid;
        }
        if (bssid == null) {
            Log.wtf(TAG, "Null BSSID provided");
            this.bssid = WifiManager.ALL_ZEROS_MAC_ADDRESS.toString();
        } else {
            this.bssid = bssid;
        }
        this.reasonCode = reasonCode;
        this.locallyGenerated = locallyGenerated;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(" ssid: ").append(ssid);
        sb.append(" bssid: ").append(bssid);
        sb.append(" reasonCode: ").append(reasonCode);
        sb.append(" locallyGenerated: ").append(locallyGenerated);
        return sb.toString();
    }
}
