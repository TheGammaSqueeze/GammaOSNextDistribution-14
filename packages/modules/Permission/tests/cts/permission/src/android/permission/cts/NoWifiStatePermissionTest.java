/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.permission.cts;

import static android.content.pm.PackageManager.FEATURE_WIFI;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verify WifiManager related methods without specific Wifi state permissions.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot access the WifiManager")
@SmallTest
public class NoWifiStatePermissionTest {
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private static final int TEST_NET_ID = 1;
    private static final WifiConfiguration TEST_WIFI_CONFIGURATION = new WifiConfiguration();
    private WifiManager mWifiManager;

    @Before
    public void setUp() {
        boolean hasWifi = sContext.getPackageManager().hasSystemFeature(FEATURE_WIFI);
        assumeTrue(hasWifi);

        mWifiManager = (WifiManager) sContext.getSystemService(Context.WIFI_SERVICE);
        assertNotNull(mWifiManager);
    }

    /**
     * Verify that WifiManager#getWifiState() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#ACCESS_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testGetWifiState() {
        mWifiManager.getWifiState();
    }

    /**
     * Verify that WifiManager#getConfiguredNetworks() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#ACCESS_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testGetConfiguredNetworks() {
        mWifiManager.getConfiguredNetworks();
    }

    /**
     * Verify that WifiManager#getConnectionInfo() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#ACCESS_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testGetConnectionInfo() {
        mWifiManager.getConnectionInfo();
    }

    /**
     * Verify that WifiManager#getScanResults() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#ACCESS_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testGetScanResults() {
        mWifiManager.getScanResults();
    }

    /**
     * Verify that WifiManager#getDhcpInfo() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#ACCESS_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testGetDhcpInfo() {
        mWifiManager.getDhcpInfo();
    }

    /**
     * Verify that WifiManager#disconnect() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testDisconnect() {
        mWifiManager.disconnect();
    }

    /**
     * Verify that WifiManager#reconnect() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testReconnect() {
        mWifiManager.reconnect();
    }

    /**
     * Verify that WifiManager#reassociate() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testReassociate() {
        mWifiManager.reassociate();
    }

    /**
     * Verify that WifiManager#addNetwork() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testAddNetwork() {
        mWifiManager.addNetwork(TEST_WIFI_CONFIGURATION);
    }

    /**
     * Verify that WifiManager#updateNetwork() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testUpdateNetwork() {
        TEST_WIFI_CONFIGURATION.networkId = 2;
        mWifiManager.updateNetwork(TEST_WIFI_CONFIGURATION);
    }
    /**
     * Verify that WifiManager#removeNetwork() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testRemoveNetwork() {
        mWifiManager.removeNetwork(TEST_NET_ID);
    }

    /**
     * Verify that WifiManager#enableNetwork() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testEnableNetwork() {
        mWifiManager.enableNetwork(TEST_NET_ID, false);
    }

    /**
     * Verify that WifiManager#disableNetwork() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testDisableNetwork() {
        mWifiManager.disableNetwork(TEST_NET_ID);
    }

    /**
     * Verify that WifiManager#pingSupplicant() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testPingSupplicant() {
        mWifiManager.pingSupplicant();
    }

    /**
     * Verify that WifiManager#startScan() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testStartScan() {
        mWifiManager.startScan();
    }

    /**
     * Verify that WifiManager#setWifiEnabled() requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#CHANGE_WIFI_STATE}.
     */
    @Test(expected = SecurityException.class)
    public void testSetWifiEnabled() {
        mWifiManager.setWifiEnabled(true);
    }
}
