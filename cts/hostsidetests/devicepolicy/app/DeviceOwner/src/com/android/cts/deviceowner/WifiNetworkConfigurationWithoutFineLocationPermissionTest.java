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

package com.android.cts.deviceowner;

import static com.android.compatibility.common.util.WifiConfigCreator.SECURITY_TYPE_NONE;

import static com.google.common.truth.Truth.assertWithMessage;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.os.SystemClock;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class WifiNetworkConfigurationWithoutFineLocationPermissionTest extends BaseDeviceOwnerTest {
    private static final String TAG = "WifiNetworkConfigurationWithoutFineLocationPermissionTest";

    // Unique SSID to use for this test (max SSID length is 32)
    private static final String NETWORK_SSID = "com.android.cts.abcdefghijklmnop";
    private static final int INVALID_NETWORK_ID = -1;

    // Time duration to allow before assuming that a WiFi operation failed and ceasing to wait.
    private static final long UPDATE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // WiFi is supposed to be a prerequisite of CTS but sometimes it's not enabled
        // for some unknown reason. Check it here just in case.
        if (!mWifiManager.isWifiEnabled()) {
            Log.d(TAG, "Enabling wifi using shell");
            SystemUtil.runShellCommand("svc wifi enable");
            awaitWifiEnabled();
            Log.d(TAG, "Done: " + mWifiManager.isWifiEnabled());
        }
    }

    public void testAddAndRetrieveCallerConfiguredNetworks() throws Exception {
        assertWithMessage("wifi is enabled").that(mWifiManager.isWifiEnabled()).isTrue();
        assertWithMessage("permission status (denied=%s) for %s on user %s",
                PackageManager.PERMISSION_DENIED, Manifest.permission.ACCESS_FINE_LOCATION, mUserId)
                        .that(mContext.checkSelfPermission(
                                        Manifest.permission.ACCESS_FINE_LOCATION))
                        .isEqualTo(PackageManager.PERMISSION_DENIED);

        int netId = mWifiConfigCreator.addNetwork(NETWORK_SSID, /* hidden */ false,
                SECURITY_TYPE_NONE, /* password */ null);
        assertWithMessage("id of added network").that(netId).isNotEqualTo(INVALID_NETWORK_ID);

        try {
            List<WifiConfiguration> configs = mWifiManager.getCallerConfiguredNetworks();
            assertWithMessage("configured networks").that(configs).isNotEmpty();
            assertWithMessage("SSID of configured networks").that(configs.get(0).SSID)
                    .isEqualTo('"' + NETWORK_SSID + '"');
        } finally {
            Log.d(TAG, "Removing network " + netId);
            mWifiManager.removeNetwork(netId);
        }
    }

    private void awaitWifiEnabled()  {
        for (int probes = 0; probes * UPDATE_INTERVAL_MS <= UPDATE_TIMEOUT_MS; probes++) {
            if (probes != 0) {
                SystemClock.sleep(UPDATE_INTERVAL_MS);
            }
            if (mWifiManager.isWifiEnabled()) {
                return;
            }
        }
        fail("Waited too long for wifi enabled");
    }
}
