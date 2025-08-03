/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.net.wifi.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.MloLink;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telephony.SubscriptionManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.wifi.flags.Flags;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get WifiManager in instant app mode")
public class WifiInfoTest extends WifiJUnit4TestBase{
    private static Context sContext;

    private static boolean sShouldRunTest = false;

    private static class MySync {
        int expectedState = STATE_NULL;
    }

    private static WifiManager sWifiManager;
    private static WifiLock sWifiLock;
    private static MySync sMySync;

    private static final int STATE_NULL = 0;
    private static final int STATE_WIFI_CHANGING = 1;
    private static final int STATE_WIFI_CHANGED = 2;

    private static final String TEST_SSID = "Test123";
    private static final String TEST_BSSID = "12:12:12:12:12:12";
    private static final int TEST_RSSI = -60;
    private static final int TEST_NETWORK_ID = 5;
    private static final int TEST_NETWORK_ID2 = 6;

    private static final String TAG = "WifiInfoTest";
    private static final int TIMEOUT_MSEC = 6000;
    private static final int WAIT_MSEC = 60;
    private static final int DURATION = 10000;
    private static final int WIFI_CONNECT_TIMEOUT_MILLIS = 30_000;
    private static final BroadcastReceiver RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                synchronized (sMySync) {
                    sMySync.expectedState = STATE_WIFI_CHANGED;
                    sMySync.notify();
                }
            }
        }
    };

    @BeforeClass
    public static void setUpClass() throws Exception {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!WifiFeature.isWifiSupported(sContext)) {
            // skip the test if WiFi is not supported
            return;
        }
        sShouldRunTest = true;
        sMySync = new MySync();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

        sContext.registerReceiver(RECEIVER, intentFilter);
        sWifiManager = sContext.getSystemService(WifiManager.class);
        assertThat(sWifiManager).isNotNull();
        sWifiLock = sWifiManager.createWifiLock(TAG);
        sWifiLock.acquire();

        // enable Wifi
        if (!sWifiManager.isWifiEnabled()) setWifiEnabled(true);
        PollingCheck.check("Wifi not enabled", DURATION, () -> sWifiManager.isWifiEnabled());

        sMySync.expectedState = STATE_NULL;
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!sShouldRunTest) {
            return;
        }
        sWifiLock.release();
        sContext.unregisterReceiver(RECEIVER);
        if (!sWifiManager.isWifiEnabled()) setWifiEnabled(true);
    }

    @Before
    public void setUp() throws Exception {
        assumeTrue(sShouldRunTest);
    }

    private static void setWifiEnabled(boolean enable) throws Exception {
        synchronized (sMySync) {
            sMySync.expectedState = STATE_WIFI_CHANGING;
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> sWifiManager.setWifiEnabled(enable));
            long timeout = System.currentTimeMillis() + TIMEOUT_MSEC;
            while (System.currentTimeMillis() < timeout
                    && sMySync.expectedState == STATE_WIFI_CHANGING)
                sMySync.wait(WAIT_MSEC);
        }
    }

    @Test
    public void testWifiInfoProperties() throws Exception {

        // ensure Wifi is connected
        ShellIdentityUtils.invokeWithShellPermissions(() -> sWifiManager.reconnect());
        PollingCheck.check(
                "Wifi not connected - Please ensure there is a saved network in range of this "
                        + "device",
                WIFI_CONNECT_TIMEOUT_MILLIS,
                () -> sWifiManager.getConnectionInfo().getNetworkId() != -1);

        // this test case should in Wifi environment
        WifiInfo wifiInfo = sWifiManager.getConnectionInfo();

        testWifiInfoPropertiesWhileConnected(wifiInfo);

        setWifiEnabled(false);

        PollingCheck.check("getNetworkId not -1", 20000,
                () -> sWifiManager.getConnectionInfo().getNetworkId() == -1);

        PollingCheck.check("getWifiState not disabled", 20000,
                () -> sWifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLED);
    }

    private void testWifiInfoPropertiesWhileConnected(WifiInfo wifiInfo) {
        assertThat(wifiInfo).isNotNull();
        assertThat(wifiInfo.toString()).isNotNull();
        SupplicantState.isValidState(wifiInfo.getSupplicantState());
        WifiInfo.getDetailedStateOf(SupplicantState.DISCONNECTED);
        String ssid = wifiInfo.getSSID();
        if (!ssid.startsWith("0x") && !ssid.equals(WifiManager.UNKNOWN_SSID)) {
            // Non-hex string should be quoted
            assertThat(ssid).startsWith("\"");
            assertThat(ssid).endsWith("\"");
        }

        assertThat(wifiInfo.getBSSID()).isNotNull();
        assertThat(wifiInfo.getFrequency()).isGreaterThan(0);
        assertThat(wifiInfo.getMacAddress()).isNotNull();

        wifiInfo.getRssi();
        wifiInfo.getIpAddress();
        wifiInfo.getHiddenSSID();
        wifiInfo.getScore();
        wifiInfo.isApTidToLinkMappingNegotiationSupported();

        // null for saved networks
        assertThat(wifiInfo.getRequestingPackageName()).isNull();
        assertThat(wifiInfo.getPasspointFqdn()).isNull();
        assertThat(wifiInfo.getPasspointProviderFriendlyName()).isNull();

        // false for saved networks
        assertThat(wifiInfo.isEphemeral()).isFalse();
        assertThat(wifiInfo.isOsuAp()).isFalse();
        assertThat(wifiInfo.isPasspointAp()).isFalse();

        assertThat(wifiInfo.getWifiStandard()).isAnyOf(
                ScanResult.WIFI_STANDARD_UNKNOWN,
                ScanResult.WIFI_STANDARD_LEGACY,
                ScanResult.WIFI_STANDARD_11N,
                ScanResult.WIFI_STANDARD_11AC,
                ScanResult.WIFI_STANDARD_11AX
        );

        assertThat(wifiInfo.getLostTxPacketsPerSecond()).isAtLeast(0.0);
        assertThat(wifiInfo.getRetriedTxPacketsPerSecond()).isAtLeast(0.0);
        assertThat(wifiInfo.getSuccessfulRxPacketsPerSecond()).isAtLeast(0.0);
        assertThat(wifiInfo.getSuccessfulTxPacketsPerSecond()).isAtLeast(0.0);

        // Can be -1 if link speed is unknown
        assertThat(wifiInfo.getLinkSpeed()).isAtLeast(-1);
        assertThat(wifiInfo.getTxLinkSpeedMbps()).isAtLeast(-1);
        assertThat(wifiInfo.getRxLinkSpeedMbps()).isAtLeast(-1);
        assertThat(wifiInfo.getMaxSupportedTxLinkSpeedMbps()).isAtLeast(-1);
        assertThat(wifiInfo.getMaxSupportedRxLinkSpeedMbps()).isAtLeast(-1);
        assertThat(wifiInfo.getCurrentSecurityType()).isNotEqualTo(
                    WifiInfo.SECURITY_TYPE_UNKNOWN);
    }

    /**
     * Test that the WifiInfo Builder returns the same values that was set, and that
     * calling build multiple times returns different instances.
     */
    @Test
    public void testWifiInfoBuilder() {
        WifiInfo.Builder builder = new WifiInfo.Builder()
                .setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8))
                .setBssid(TEST_BSSID)
                .setRssi(TEST_RSSI)
                .setNetworkId(TEST_NETWORK_ID);

        WifiInfo info1 = builder.build();

        assertThat(info1.getSSID()).isEqualTo("\"" + TEST_SSID + "\"");
        assertThat(info1.getBSSID()).isEqualTo(TEST_BSSID);
        assertThat(info1.getRssi()).isEqualTo(TEST_RSSI);
        assertThat(info1.getNetworkId()).isEqualTo(TEST_NETWORK_ID);
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
            assertThat(info1.getSubscriptionId())
                    .isEqualTo(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            assertFalse(info1.isOemPaid());
            assertFalse(info1.isOemPrivate());
            assertFalse(info1.isCarrierMerged());
        }
        assertFalse(info1.isApTidToLinkMappingNegotiationSupported());

        WifiInfo info2 = builder
                .setNetworkId(TEST_NETWORK_ID2)
                .build();

        // different instances
        assertThat(info1).isNotSameInstanceAs(info2);

        // assert that info1 didn't change
        assertThat(info1.getSSID()).isEqualTo("\"" + TEST_SSID + "\"");
        assertThat(info1.getBSSID()).isEqualTo(TEST_BSSID);
        assertThat(info1.getRssi()).isEqualTo(TEST_RSSI);
        assertThat(info1.getNetworkId()).isEqualTo(TEST_NETWORK_ID);

        // assert that info2 changed
        assertThat(info2.getSSID()).isEqualTo("\"" + TEST_SSID + "\"");
        assertThat(info2.getBSSID()).isEqualTo(TEST_BSSID);
        assertThat(info2.getRssi()).isEqualTo(TEST_RSSI);
        assertThat(info2.getNetworkId()).isEqualTo(TEST_NETWORK_ID2);
        assertFalse(info1.isApTidToLinkMappingNegotiationSupported());
    }

    /**
     * Test that setCurrentSecurityType and getCurrentSecurityType work as expected
     */
    @Test
    public void testWifiInfoCurrentSecurityType() {
        WifiInfo.Builder builder = new WifiInfo.Builder()
                .setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8))
                .setBssid(TEST_BSSID)
                .setRssi(TEST_RSSI)
                .setNetworkId(TEST_NETWORK_ID);

        WifiInfo info = builder.build();
        assertEquals(WifiInfo.SECURITY_TYPE_UNKNOWN, info.getCurrentSecurityType());

        builder.setCurrentSecurityType(WifiInfo.SECURITY_TYPE_SAE);
        info = builder.build();
        assertEquals(WifiInfo.SECURITY_TYPE_SAE, info.getCurrentSecurityType());
    }

    /**
     * Test MLO Attributes (WiFi-7)
     */
    @Test
    public void testWifiInfoMloAttributes() {
        // Verify that MLO Attributes are initialzed correctly
        WifiInfo.Builder builder = new WifiInfo.Builder()
                .setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8))
                .setBssid(TEST_BSSID)
                .setRssi(TEST_RSSI)
                .setNetworkId(TEST_NETWORK_ID);

        WifiInfo wifiInfo = builder.build();

        assertNull(wifiInfo.getApMldMacAddress());
        assertEquals(MloLink.INVALID_MLO_LINK_ID, wifiInfo.getApMloLinkId());
        assertNotNull(wifiInfo.getAffiliatedMloLinks());
        assertTrue(wifiInfo.getAffiliatedMloLinks().isEmpty());
        assertNotNull(wifiInfo.getAssociatedMloLinks());
        assertTrue(wifiInfo.getAssociatedMloLinks().isEmpty());
        assertFalse(wifiInfo.isApTidToLinkMappingNegotiationSupported());
    }

    /**
     * Test that vendor data can be properly set and retrieved.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @Test
    public void testWifiInfoSetAndGetVendorData() {
        WifiInfo.Builder builder = new WifiInfo.Builder()
                .setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8))
                .setBssid(TEST_BSSID)
                .setRssi(TEST_RSSI)
                .setNetworkId(TEST_NETWORK_ID);
        WifiInfo wifiInfo = builder.build();
        assertTrue(wifiInfo.getVendorData().isEmpty()); // default value

        OuiKeyedData ouiKeyedData =
                new OuiKeyedData.Builder(0x00aabbcc, new PersistableBundle()).build();
        List<OuiKeyedData> vendorData = Arrays.asList(ouiKeyedData);
        wifiInfo.setVendorData(vendorData);
        assertTrue(vendorData.equals(wifiInfo.getVendorData()));
    }

    /**
     * Verify getPasspointUniqueId can be called.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_V_WIFI_API)
    @Test
    public void testGetPasspointUniqueId() {
        WifiInfo.Builder builder = new WifiInfo.Builder()
                .setSsid(TEST_SSID.getBytes(StandardCharsets.UTF_8))
                .setBssid(TEST_BSSID)
                .setRssi(TEST_RSSI)
                .setNetworkId(TEST_NETWORK_ID);
        WifiInfo wifiInfo = builder.build();
        assertNull(wifiInfo.getPasspointUniqueId());
    }

}
