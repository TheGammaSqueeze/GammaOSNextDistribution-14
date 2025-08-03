/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.text.TextUtils;

import androidx.annotation.StringRes;
import com.android.tv.settings.R;

import com.android.tv.settings.library.network.AccessPoint;
import com.android.wifitrackerlib.WifiEntry;

/** Helper class for Wifi configuration. */
class WifiUtils {
    @StringRes
    static int getConnectionStatus(WifiEntry wifiEntry) {
        if (wifiEntry.canSignIn()) {
            return R.string.wifi_captive_portal;
        } else if (wifiEntry.getConnectedState() == WifiEntry.CONNECTED_STATE_CONNECTED) {
            return wifiEntry.hasInternetAccess()
                    ? R.string.connected : R.string.wifi_no_internet;
        } else if (wifiEntry.shouldEditBeforeConnect()) {
            return R.string.wifi_bad_password;
        } else if (wifiEntry.isSaved()) {
            return R.string.wifi_saved;
        }
        return R.string.not_connected;
    }

    private WifiUtils() {}
}
