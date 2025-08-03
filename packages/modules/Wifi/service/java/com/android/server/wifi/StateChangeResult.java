/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.util.Log;

/**
 * Stores supplicant state change information passed from WifiMonitor to
 * a state machine. ClientModeImpl, SupplicantStateTracker and WpsStateMachine
 * are example state machines that handle it.
 */
public class StateChangeResult {

    private static final String TAG = "StateChangeResult";
    StateChangeResult(int networkId, @NonNull WifiSsid wifiSsid, @NonNull String bssid,
            int frequencyMhz, SupplicantState state) {
        if (wifiSsid == null) {
            Log.wtf(TAG, "Null SSID provided");
            this.wifiSsid = WifiSsid.fromBytes(null);
        } else {
            this.wifiSsid = wifiSsid;
        }
        if (bssid == null) {
            Log.wtf(TAG, "Null BSSID provided");
            this.bssid = WifiManager.ALL_ZEROS_MAC_ADDRESS.toString();
        } else {
            this.bssid = bssid;
        }
        this.state = state;
        this.networkId = networkId;
        this.frequencyMhz = frequencyMhz;
    }

    public final int networkId;
    @NonNull public final WifiSsid wifiSsid;
    @NonNull public final String bssid;
    public final int frequencyMhz;
    public final SupplicantState state;

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(" ssid: ").append(wifiSsid);
        sb.append(" bssid: ").append(bssid);
        sb.append(" nid: ").append(networkId);
        sb.append(" frequencyMhz: ").append(frequencyMhz);
        sb.append(" state: ").append(state);
        return sb.toString();
    }
}
