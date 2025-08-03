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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.net.MacAddress;

import java.util.Objects;

/**
 * Store authentication failure information passed from WifiMonitor.
 */
public class AuthenticationFailureEventInfo {
    @NonNull public final String ssid;
    @NonNull public final MacAddress bssid;
    public final int reasonCode;
    public final int errorCode;

    public AuthenticationFailureEventInfo(@NonNull String ssid, @NonNull MacAddress bssid,
            int reasonCode, int errorCode) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.reasonCode = reasonCode;
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" ssid: ").append(ssid);
        sb.append(" bssid: ").append(bssid);
        sb.append(" reasonCode: ").append(reasonCode);
        sb.append(" errorCode: ").append(errorCode);
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(ssid, bssid, reasonCode, errorCode);
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) return true;
        if (!(that instanceof AuthenticationFailureEventInfo)) return false;

        AuthenticationFailureEventInfo thatAuthenticationFailureEventInfo =
                (AuthenticationFailureEventInfo) that;
        return (reasonCode == thatAuthenticationFailureEventInfo.reasonCode
                && errorCode == thatAuthenticationFailureEventInfo.errorCode
                && Objects.equals(ssid, thatAuthenticationFailureEventInfo.ssid)
                && Objects.equals(bssid, thatAuthenticationFailureEventInfo.bssid));
    }
}
