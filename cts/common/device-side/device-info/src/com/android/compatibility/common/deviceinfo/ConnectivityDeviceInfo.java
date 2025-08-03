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
package com.android.compatibility.common.deviceinfo;

import android.content.pm.PackageManager;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import android.os.Build;

import android.util.Log;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Device connectivity info collector.
 */
public final class ConnectivityDeviceInfo extends DeviceInfo {

    private static final String LOG_TAG = "ConnectivityDeviceInfo";

    private static final int WIFI_STANDARDS[] = {
        ScanResult.WIFI_STANDARD_UNKNOWN,
        ScanResult.WIFI_STANDARD_LEGACY,
        ScanResult.WIFI_STANDARD_11N,
        ScanResult.WIFI_STANDARD_11AC,
        ScanResult.WIFI_STANDARD_11AX,
        ScanResult.WIFI_STANDARD_11AD,
        ScanResult.WIFI_STANDARD_11BE
    };

    private static String wifiStandardToString(int standard) {
        switch (standard) {
        case ScanResult.WIFI_STANDARD_UNKNOWN:
            return "unknown";
        case ScanResult.WIFI_STANDARD_LEGACY:
            return "legacy";
        case ScanResult.WIFI_STANDARD_11N:
            return "11n";
        case ScanResult.WIFI_STANDARD_11AC:
            return "11ac";
        case ScanResult.WIFI_STANDARD_11AX:
            return "11ax";
        case ScanResult.WIFI_STANDARD_11AD:
            return "11ad";
        case ScanResult.WIFI_STANDARD_11BE:
            return "11be";
        }
        return "";
    }

    private boolean hasWifi() {
        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        if (hasWifi()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    collectWifiStandards(store);
                } catch (IOException e) {
                    Log.w(LOG_TAG, "Failed to collect WiFi standards", e);
                }
            }
        }
    }

    private void collectWifiStandards(DeviceInfoStore store) throws IOException {
      final WifiManager wifiManager = getContext().getSystemService(WifiManager.class);
      List<String> wifiStandards = new ArrayList<String>();
      for (int standard: WIFI_STANDARDS) {
          if (wifiManager.isWifiStandardSupported(standard)) {
              wifiStandards.add(wifiStandardToString(standard));
          }
      }
      store.addListResult("wifi_standards", wifiStandards);
    }
}
