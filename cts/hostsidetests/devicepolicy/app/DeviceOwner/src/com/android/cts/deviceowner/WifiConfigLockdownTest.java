/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.compatibility.common.util.WifiConfigCreator.ACTION_CREATE_WIFI_CONFIG;
import static com.android.compatibility.common.util.WifiConfigCreator.ACTION_REMOVE_WIFI_CONFIG;
import static com.android.compatibility.common.util.WifiConfigCreator.ACTION_UPDATE_WIFI_CONFIG;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_NETID;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_PASSWORD;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_SECURITY_TYPE;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_SSID;
import static com.android.compatibility.common.util.WifiConfigCreator.SECURITY_TYPE_NONE;
import static com.android.compatibility.common.util.WifiConfigCreator.SECURITY_TYPE_WPA;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Testing WiFi configuration lockdown by Device Owner
 */
public final class WifiConfigLockdownTest extends BaseDeviceOwnerTest {
    private static final String TAG = "WifiConfigLockdownTest";
    private static final String ORIGINAL_DEVICE_OWNER_SSID = "DOCTSTest";
    private static final String CHANGED_DEVICE_OWNER_SSID = "DOChangedCTSTest";
    private static final String ORIGINAL_REGULAR_SSID = "RegularCTSTest";
    private static final String CHANGED_REGULAR_SSID = "RegularChangedCTSTest";
    private static final String ORIGINAL_PASSWORD = "originalpassword";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevicePolicyManager.setGlobalSetting(getWho(),
                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, "1");
        mWifiConfigCreator.addNetwork(ORIGINAL_DEVICE_OWNER_SSID, true, SECURITY_TYPE_WPA,
                ORIGINAL_PASSWORD);

        Log.d(TAG, "setUp: user=" + Process.myUserHandle() + ", creator=" + mWifiConfigCreator
                + ", dpm=" + mDevicePolicyManager + ", wifiMgr=" + mWifiManager
                + ", mCurrentUserWifiManager= " + mCurrentUserWifiManager);
        logConfigs("setup()", getConfiguredNetworks());

        startRegularActivity(ACTION_CREATE_WIFI_CONFIG, -1, ORIGINAL_REGULAR_SSID,
                SECURITY_TYPE_WPA, ORIGINAL_PASSWORD);
    }

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.setGlobalSetting(getWho(),
                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, "0");
        List<WifiConfiguration> configs = getConfiguredNetworks();
        logConfigs("tearDown()", configs);
        for (WifiConfiguration config : configs) {
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID) ||
                    areMatchingSsids(CHANGED_DEVICE_OWNER_SSID, config.SSID) ||
                    areMatchingSsids(ORIGINAL_REGULAR_SSID, config.SSID) ||
                    areMatchingSsids(CHANGED_REGULAR_SSID, config.SSID)) {
                Log.d(TAG, "Removing " + config.networkId);
                mWifiManager.removeNetwork(config.networkId);
            }
        }
        super.tearDown();
    }

    public void testDeviceOwnerCanUpdateConfig() throws Exception {
        List<WifiConfiguration> configs = getConfiguredNetworks();
        logConfigs("testDeviceOwnerCanUpdateConfig()", configs);
        int updateCount = 0;
        for (WifiConfiguration config : configs) {
            Log.d(TAG, "testDeviceOwnerCanUpdateConfig(): testing " + config.SSID);
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)) {
                int netId = mWifiConfigCreator.updateNetwork(config,
                        CHANGED_DEVICE_OWNER_SSID, true, SECURITY_TYPE_NONE, null);
                Log.d(TAG, "netid after updateNetwork(REGULAR_SSID):" + netId);
                assertWithMessage("netid after updateNetwork(%s, DO_SSID)", config.SSID)
                        .that(netId).isNotEqualTo(-1);
                ++updateCount;
            }
            if (areMatchingSsids(ORIGINAL_REGULAR_SSID, config.SSID)) {
                int netId = mWifiConfigCreator.updateNetwork(config,
                        CHANGED_REGULAR_SSID, true, SECURITY_TYPE_NONE, null);
                Log.d(TAG, "netid after updateNetwork(REGULAR_SSID):" + netId);
                assertWithMessage("netid after updateNetwork(%s, REGULAR_SSID)", config.SSID)
                        .that(netId).isNotEqualTo(-1);
                ++updateCount;
            }
        }
        // There might be auto-upgrade configs returned.
        assertWithMessage("number of updated configs (the DO created one and the regular one)")
                .that(updateCount).isAtLeast(2);
    }

    public void testDeviceOwnerCanRemoveConfig() throws Exception {
        List<WifiConfiguration> configs = getConfiguredNetworks();
        logConfigs("testDeviceOwnerCanRemoveConfig()", configs);
        int removeCount = 0;
        for (WifiConfiguration config : configs) {
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)
                    || areMatchingSsids(ORIGINAL_REGULAR_SSID, config.SSID)) {
                // On some devices a wpa3-sae configuration is auto-created for every wpa2
                // config, and they are auto-removed when the corresponding config is removed.
                // Recheck every config against the latest list of wifi configurations and skip
                // those which is already auto-removed.
                Log.d(TAG, "Checking if SSID " + config.SSID + " / id " + config.networkId
                        + " should be removed");
                if (getConfiguredNetworks().stream()
                        .noneMatch(c -> c.networkId == config.networkId)) {
                    Log.d(TAG, "Skipping it");
                    continue;
                }
                Log.d(TAG, "Removing using " + mWifiManager);
                assertWithMessage("removeNetwork(%s)", config.networkId)
                        .that(mWifiManager.removeNetwork(config.networkId)).isTrue();
                ++removeCount;
            }
        }
        logConfigs("After removing " + removeCount, configs);
        assertWithMessage("number of removed configs (the DO created one and the regular one)")
                .that(removeCount).isEqualTo(2);
    }

    public void testRegularAppCannotUpdateDeviceOwnerConfig() throws Exception {
        List<WifiConfiguration> configs = getConfiguredNetworks();
        logConfigs("testRegularAppCannotUpdateDeviceOwnerConfig()", configs);
        int updateCount = 0;
        for (WifiConfiguration config : configs) {
            Log.d(TAG, "testRegularAppCannotUpdateDeviceOwnerConfig(): testing " + config.SSID);
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)) {
                startRegularActivity(ACTION_UPDATE_WIFI_CONFIG, config.networkId,
                        CHANGED_DEVICE_OWNER_SSID, SECURITY_TYPE_NONE, null);
                ++updateCount;
            }
        }
        // There might be auto-upgrade configs returned.
        assertWithMessage("number of updated configs (the DO created one)")
                .that(updateCount).isAtLeast(1);

        // Assert nothing has changed
        configs = getConfiguredNetworks();
        int notChangedCount = 0;
        for (WifiConfiguration config : configs) {
            Log.d(TAG, "testRegularAppCannotUpdateDeviceOwnerConfig(): testing " + config.SSID);
            assertWithMessage("matching ssids for %s / %s", CHANGED_DEVICE_OWNER_SSID, config.SSID)
                    .that(areMatchingSsids(CHANGED_DEVICE_OWNER_SSID, config.SSID)).isFalse();
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)) {
                ++notChangedCount;
            }
        }
        // There might be auto-upgrade configs returned.
        assertWithMessage("number of unchanged configs").that(notChangedCount).isAtLeast(1);
    }

    public void testRegularAppCannotRemoveDeviceOwnerConfig() throws Exception {
        List<WifiConfiguration> configs = getConfiguredNetworks();
        logConfigs("testRegularAppCannotUpdateDeviceOwnerConfig()", configs);
        int removeCount = 0;
        for (WifiConfiguration config : configs) {
            Log.d(TAG, "testRegularAppCannotRemoveDeviceOwnerConfig(): testing " + config.SSID);
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)) {
                startRegularActivity(ACTION_REMOVE_WIFI_CONFIG, config.networkId,
                        null, SECURITY_TYPE_NONE, null);
                ++removeCount;
            }
        }

        // There might be auto-upgrade configs returned.
        assertWithMessage("number of removed configs (the DO created one)")
                .that(removeCount).isAtLeast(1);

        // Assert nothing has changed
        configs = getConfiguredNetworks();
        int notChangedCount = 0;
        for (WifiConfiguration config : configs) {
            Log.d(TAG, "testRegularAppCannotRemoveDeviceOwnerConfig(): testing " + config.SSID);
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)) {
                ++notChangedCount;
            }
        }
        // There might be auto-upgrade configs returned.
        assertWithMessage("number of unchanged configs").that(notChangedCount).isAtLeast(1);
    }

    private void startRegularActivity(String action, int netId, String ssid, int securityType,
            String password) throws InterruptedException {
        Intent createRegularConfig = new Intent(action);
        createRegularConfig.putExtra(EXTRA_NETID, netId);
        createRegularConfig.putExtra(EXTRA_SSID, ssid);
        createRegularConfig.putExtra(EXTRA_SECURITY_TYPE, securityType);
        createRegularConfig.putExtra(EXTRA_PASSWORD, password);
        createRegularConfig.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.d(TAG, "Starting " + action  + " on user " + mContext.getUserId());
        mContext.startActivity(createRegularConfig);

        // Give some time for the other app to finish the action
        Log.d(TAG, "Sleeping 5s");
        Thread.sleep(5000);
    }

    private boolean areMatchingSsids(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return false;
        }
        return s1.replace("\"", "").equals(s2.replace("\"", ""));
    }

    private void logConfigs(String prefix, List<WifiConfiguration> configs) {
        if (configs == null) {
            Log.d(TAG, prefix + ": null configs");
            return;
        }
        Log.d(TAG, prefix + ": " + configs.size() + " configs: "
                + configs.stream().map((c) -> c.SSID + "/" + c.networkId)
                        .collect(Collectors.toList()));
    }
}
