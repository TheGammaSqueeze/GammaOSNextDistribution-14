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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.net.wifi.WifiSsid;

import java.util.BitSet;

/** Data class to hold information for a {@link WifiMonitor#NETWORK_CONNECTION_EVENT}. */
public class NetworkConnectionEventInfo {
    public final int networkId;
    @NonNull
    public final WifiSsid wifiSsid;
    @NonNull
    public final String bssid;
    public final boolean isFilsConnection;
    public final BitSet keyMgmtMask;

    public NetworkConnectionEventInfo(int networkId, WifiSsid wifiSsid, String bssid,
            boolean isFilsConnection, BitSet keyMgmtMask) {
        this.networkId = networkId;
        this.wifiSsid = wifiSsid;
        this.bssid = bssid;
        this.isFilsConnection = isFilsConnection;
        this.keyMgmtMask = keyMgmtMask;
    }

    @Override
    public String toString() {
        return "NetworkConnectionEventInfo{"
                + "networkId=" + networkId
                + ", wifiSsid=" + wifiSsid
                + ", bssid='" + bssid + '\''
                + ", isFilsConnection=" + isFilsConnection
                + ", keyMgmtMask=" + keyMgmtMask
                + '}';
    }
}
