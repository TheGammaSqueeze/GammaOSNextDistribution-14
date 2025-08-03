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

package com.android.compatibility.common.util;

import static android.net.wifi.WifiManager.EXTRA_WIFI_STATE;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ProxyInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A simple activity to create and manage wifi configurations.
 */
public class WifiConfigCreator {
    public static final String ACTION_CREATE_WIFI_CONFIG =
            "com.android.compatibility.common.util.CREATE_WIFI_CONFIG";
    public static final String ACTION_UPDATE_WIFI_CONFIG =
            "com.android.compatibility.common.util.UPDATE_WIFI_CONFIG";
    public static final String ACTION_REMOVE_WIFI_CONFIG =
            "com.android.compatibility.common.util.REMOVE_WIFI_CONFIG";
    public static final String EXTRA_NETID = "extra-netid";
    public static final String EXTRA_SSID = "extra-ssid";
    public static final String EXTRA_SECURITY_TYPE = "extra-security-type";
    public static final String EXTRA_PASSWORD = "extra-password";

    public static final int SECURITY_TYPE_NONE = 1;
    public static final int SECURITY_TYPE_WPA = 2;
    public static final int SECURITY_TYPE_WEP = 3;

    private static final String TAG = "WifiConfigCreator";

    private static final long ENABLE_WIFI_WAIT_SEC = 10L;

    private final Context mContext;
    private final WifiManager mWifiManager;
    private WifiManager mCurrentUserWifiManager;

    public WifiConfigCreator(Context context) {
        this(context, context.getApplicationContext().getSystemService(WifiManager.class));
    }

    public WifiConfigCreator(Context context, WifiManager wifiManager) {
        mContext = context;
        mWifiManager = wifiManager;
        mCurrentUserWifiManager = mContext.getSystemService(WifiManager.class);
        Log.d(TAG, "WifiConfigCreator: user=" + Process.myUserHandle() + ", ctx=" + context
                + ", mgr=" + mWifiManager + ", currentUserMgr=" + mCurrentUserWifiManager);
    }

    @Override
    public String toString() {
        return "WifiConfigCreator[mWifiManager=" + mWifiManager
                + ",mCurrentUserWifiManager=" + mCurrentUserWifiManager + "]";
    }

    /**
     * Adds a new WiFi network.
     * @return network id or -1 in case of error
     */
    public int addNetwork(String ssid, boolean hidden, int securityType,
            String password) throws InterruptedException, SecurityException {
        checkAndEnableWifi();

        WifiConfiguration wifiConf = createConfig(ssid, hidden, securityType, password);

        Log.i(TAG, "Adding SSID " + ssid + " using " + mWifiManager);
        int netId = mWifiManager.addNetwork(wifiConf);

        if (netId != -1) {
            Log.i(TAG, "Added SSID '" + ssid + "': netId = " + netId + "; enabling it");
            mWifiManager.enableNetwork(netId, true);
            Log.i(TAG, "SSID '" + ssid + "' enabled!");
        } else {
            Log.w(TAG, "Unable to add SSID '" + ssid + "': netId = " + netId);
        }
        return netId;
    }

    /**
     * Adds a new wifiConfiguration with OPEN security type, and the given pacProxy
     * verifies that the proxy is added by getting the configuration back, and checking it.
     * @return returns the PAC proxy URL after adding the network and getting it from WifiManager
     * @throws IllegalStateException if any of the WifiManager operations fail
     */
    public String addHttpProxyNetworkVerifyAndRemove(String ssid, String pacProxyUrl)
            throws IllegalStateException {
        int netId = -1;
        String retrievedPacProxyUrl;

        try {
            netId = addNetworkWithProxy(ssid, pacProxyUrl);
            WifiConfiguration conf = getWifiConfigurationBySsid(ssid);
            retrievedPacProxyUrl = getPacProxyUrl(conf);

            Log.d(TAG, "calling removeNetwork(" + netId + ")");
            if (!mWifiManager.removeNetwork(netId)) {
                throw new IllegalStateException("Failed to remove WifiConfiguration: " + ssid);
            }
        } finally {
            mWifiManager.removeNetwork(netId);
        }
        return retrievedPacProxyUrl;
    }

    private String getPacProxyUrl(WifiConfiguration conf) {
        return Optional.of(conf)
                .map(WifiConfiguration::getHttpProxy)
                .map(ProxyInfo::getPacFileUrl)
                .map(Object::toString)
                .orElse(null);
    }

    private int addNetworkWithProxy(String ssid, String pacProxyUrl) {
        WifiConfiguration conf = createConfig(ssid, false, SECURITY_TYPE_NONE, null);

        if (pacProxyUrl != null) {
            conf.setHttpProxy(ProxyInfo.buildPacProxy(Uri.parse(pacProxyUrl)));
        }

        Log.d(TAG, "addNetworkWithProxy(ssid=" + ssid + ", pacProxyUrl=" + pacProxyUrl);
        int netId = mWifiManager.addNetwork(conf);
        Log.d(TAG, "added: netId=" + netId);
        if (netId == -1) {
            throw new IllegalStateException("Failed to addNetwork: " + ssid);
        }
        return netId;
    }

    private WifiConfiguration getWifiConfigurationBySsid(String ssid) {
        WifiConfiguration wifiConfiguration = null;
        String expectedSsid = wrapInQuotes(ssid);
        List<WifiConfiguration> configuredNetworks = getConfiguredNetworksWithLogging();
        for (WifiConfiguration w : configuredNetworks) {
            if (w.SSID.equals(expectedSsid)) {
                wifiConfiguration = w;
                break;
            }
            Log.v(TAG, "skipping " + w.SSID);
        }
        if (wifiConfiguration == null) {
            throw new IllegalStateException("Failed to get WifiConfiguration for: " + ssid);
        }
        return wifiConfiguration;
    }

    /**
     * Updates a new WiFi network.
     * @return network id (may differ from original) or -1 in case of error
     */
    public int updateNetwork(WifiConfiguration wifiConf, String ssid, boolean hidden,
            int securityType, String password) throws InterruptedException, SecurityException {
        checkAndEnableWifi();
        if (wifiConf == null) {
            return -1;
        }

        WifiConfiguration conf = createConfig(ssid, hidden, securityType, password);
        conf.networkId = wifiConf.networkId;

        int newNetId = mWifiManager.updateNetwork(conf);

        if (newNetId != -1) {
            Log.v(TAG, "calling saveConfiguration()");
            mWifiManager.saveConfiguration();
            Log.v(TAG, "calling enableNetwork(" + newNetId + ")");
            mWifiManager.enableNetwork(newNetId, true);
            Log.v(TAG, "enabled");
        } else {
            Log.w(TAG, "Unable to update SSID '" + ssid + "': netId = " + newNetId);
        }
        return newNetId;
    }

    /**
     * Updates a new WiFi network.
     * @return network id (may differ from original) or -1 in case of error
     */
    public int updateNetwork(int netId, String ssid, boolean hidden,
            int securityType, String password) throws InterruptedException, SecurityException {
        Log.d(TAG, "updateNetwork(): netId= " + netId + ", ssid=" + ssid + ", hidden=" + hidden);
        checkAndEnableWifi();

        WifiConfiguration wifiConf = null;
        List<WifiConfiguration> configs = getConfiguredNetworksWithLogging();
        for (WifiConfiguration config : configs) {
            if (config.networkId == netId) {
                wifiConf = config;
                break;
            }
            Log.v(TAG, "skipping " + config.networkId);
        }
        return updateNetwork(wifiConf, ssid, hidden, securityType, password);
    }

    public boolean removeNetwork(int netId) {
        Log.v(TAG, "calling removeNetwork(" + netId + ")");
        boolean removed = mWifiManager.removeNetwork(netId);
        Log.v(TAG, "removed: " + removed);
        return removed;
    }

    /**
     * Creates a WifiConfiguration set up according to given parameters
     * @param ssid SSID of the network
     * @param hidden Is SSID not broadcast?
     * @param securityType One of {@link #SECURITY_TYPE_NONE}, {@link #SECURITY_TYPE_WPA} or
     *                     {@link #SECURITY_TYPE_WEP}
     * @param password Password for WPA or WEP
     * @return Created configuration object
     */
    private WifiConfiguration createConfig(String ssid, boolean hidden, int securityType,
            String password) {
        WifiConfiguration wifiConf = new WifiConfiguration();
        if (!TextUtils.isEmpty(ssid)) {
            wifiConf.SSID = wrapInQuotes(ssid);
        }
        wifiConf.status = WifiConfiguration.Status.ENABLED;
        wifiConf.hiddenSSID = hidden;
        switch (securityType) {
            case SECURITY_TYPE_NONE:
                wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                break;
            case SECURITY_TYPE_WPA:
                updateForWPAConfiguration(wifiConf, password);
                break;
            case SECURITY_TYPE_WEP:
                updateForWEPConfiguration(wifiConf, password);
                break;
        }
        return wifiConf;
    }

    private void updateForWPAConfiguration(WifiConfiguration wifiConf, String wifiPassword) {
        wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        if (!TextUtils.isEmpty(wifiPassword)) {
            wifiConf.preSharedKey = wrapInQuotes(wifiPassword);
        }
    }

    private void updateForWEPConfiguration(WifiConfiguration wifiConf, String password) {
        wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
        if (!TextUtils.isEmpty(password)) {
            int length = password.length();
            if ((length == 10 || length == 26
                    || length == 58) && password.matches("[0-9A-Fa-f]*")) {
                wifiConf.wepKeys[0] = password;
            } else {
                wifiConf.wepKeys[0] = wrapInQuotes(password);
            }
            wifiConf.wepTxKeyIndex = 0;
        }
    }

    private void checkAndEnableWifi() throws InterruptedException {
        final CountDownLatch enabledLatch = new CountDownLatch(1);

        // Register a change receiver first to pick up events between isEnabled and setEnabled
        final BroadcastReceiver watcher = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received intent " + intent.getAction());
                if (intent.getIntExtra(EXTRA_WIFI_STATE, -1) == WIFI_STATE_ENABLED) {
                    enabledLatch.countDown();
                }
            }
        };

        mContext.registerReceiver(watcher, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
        try {
            // In case wifi is not already enabled, wait for it to come up
            if (!mWifiManager.isWifiEnabled()) {
                Log.v(TAG, "Calling setWifiEnabled(true)");
                mWifiManager.setWifiEnabled(true);
                Log.v(TAG, "enabled");
                enabledLatch.await(ENABLE_WIFI_WAIT_SEC, TimeUnit.SECONDS);
            }
        } finally {
            mContext.unregisterReceiver(watcher);
        }
    }

    private String wrapInQuotes(String ssid) {
        return '"' + ssid + '"';
    }

    private List<WifiConfiguration> getConfiguredNetworksWithLogging() {
        Log.d(TAG, "calling getConfiguredNetworks() using " + mCurrentUserWifiManager);
        // Must use a the WifiManager of the current user to list networks, as
        // getConfiguredNetworks() would return empty on systems using headless system
        // mode as that method "Return a list of all the networks configured for the current
        // foreground user", and the system user is running in the background in this case.
        List<WifiConfiguration> configuredNetworks = mCurrentUserWifiManager
                .getConfiguredNetworks();
        Log.d(TAG, "Got " + configuredNetworks.size() + " networks: "
                + configuredNetworks.stream().map((c) -> c.SSID + "/" + c.networkId)
                        .collect(Collectors.toList()));
        return configuredNetworks;
    }
}

