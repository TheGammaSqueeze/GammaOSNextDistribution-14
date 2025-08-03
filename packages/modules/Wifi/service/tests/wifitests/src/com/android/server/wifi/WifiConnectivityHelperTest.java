/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.net.wifi.WifiManager.WIFI_FEATURE_CONTROL_ROAMING;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

/**
 * Unit tests for {@link com.android.server.wifi.WifiConnectivityHelper}.
 */
@SmallTest
public class WifiConnectivityHelperTest extends WifiBaseTest {
    /** Sets up test. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        setupWifiNative();

        mWifiConnectivityHelper = new WifiConnectivityHelper(mWifiInjector);
    }

    /** Cleans up test. */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    private WifiConnectivityHelper mWifiConnectivityHelper;
    @Mock private WifiInjector mWifiInjector;
    @Mock private ActiveModeWarden mActiveModeWarden;
    @Mock private ClientModeManager mClientModeManager;
    @Captor ArgumentCaptor<WifiNative.RoamingConfig> mRoamingConfigCaptor;
    private int mFeatureSetValue;
    private static final String TAG = "WifiConnectivityHelperTest";
    private static final int MAX_BSSID_BLOCKLIST_SIZE = 16;
    private static final int MAX_SSID_ALLOWLIST_SIZE = 8;

    private void setupWifiNative() {
        when(mWifiInjector.getActiveModeWarden()).thenReturn(mActiveModeWarden);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mClientModeManager);

        // Return firmware roaming feature as supported by default.
        when(mClientModeManager.getSupportedFeatures()).thenReturn(WIFI_FEATURE_CONTROL_ROAMING);

        WifiNative.RoamingCapabilities roamCap = new WifiNative.RoamingCapabilities();
        roamCap.maxBlocklistSize = MAX_BSSID_BLOCKLIST_SIZE;
        roamCap.maxAllowlistSize = MAX_SSID_ALLOWLIST_SIZE;
        when(mClientModeManager.getRoamingCapabilities()).thenReturn(roamCap);

        when(mClientModeManager.configureRoaming(isNotNull())).thenReturn(true);
    }

    private ArrayList<String> buildBssidBlocklist(int size) {
        ArrayList<String> bssidBlocklist = new ArrayList<String>();

        for (int i = 0; i < size; i++) {
            StringBuilder bssid = new StringBuilder("11:22:33:44:55:66");
            bssid.setCharAt(16, (char) ('0' + i));
            bssidBlocklist.add(bssid.toString());
        }

        return bssidBlocklist;
    }

    private ArrayList<String> buildSsidAllowlist(int size) {
        ArrayList<String> ssidAllowlist = new ArrayList<String>();

        for (int i = 0; i < size; i++) {
            StringBuilder ssid = new StringBuilder("\"Test_Ap_0\"");
            ssid.setCharAt(9, (char) ('0' + i));
            ssidAllowlist.add(ssid.toString());
        }

        return ssidAllowlist;
    }

    /**
     * When WifiNative has WIFI_FEATURE_CONTROL_ROAMING set, verify that
     * WifiConnectivityHelper#isFirmwareRoamingSupported returns true.
     */
    @Test
    public void returnFirmwareRoamingSupported() {
        //By default WifiNative has WIFI_FEATURE_CONTROL_ROAMING set in its feature set.
        assertTrue(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        assertTrue(mWifiConnectivityHelper.isFirmwareRoamingSupported());
    }

    /**
     * When WifiNative doesn't have WIFI_FEATURE_CONTROL_ROAMING set, verify that
     * WifiConnectivityHelper#isFirmwareRoamingSupported returns false.
     */
    @Test
    public void returnFirmwareRoamingNotSupported() {
        when(mClientModeManager.getSupportedFeatures()).thenReturn(~WIFI_FEATURE_CONTROL_ROAMING);
        assertTrue(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        assertFalse(mWifiConnectivityHelper.isFirmwareRoamingSupported());
    }

    /**
     * Verify that correct firmware roaming capability values are returned if querying
     * WifiNative for roaming capability succeeded.
     */
    @Test
    public void verifyFirmwareRoamingCapabilityWithSuccessfulNativeCall() {
        assertTrue(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        assertTrue(mWifiConnectivityHelper.isFirmwareRoamingSupported());
        assertEquals(MAX_BSSID_BLOCKLIST_SIZE, mWifiConnectivityHelper.getMaxNumBlocklistBssid());
        assertEquals(MAX_SSID_ALLOWLIST_SIZE, mWifiConnectivityHelper.getMaxNumAllowlistSsid());
    }

    /**
     * Verify that firmware roaming is set to not supported if WifiNative returned firmware roaming
     * is supported but failed to return roaming capabilities. Firmware roaming capability values
     * should be reset to INVALID_LIST_SIZE.
     */
    @Test
    public void verifyFirmwareRoamingCapabilityWithFailureNativeCall() {
        when(mClientModeManager.getRoamingCapabilities()).thenReturn(null);
        assertFalse(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        assertFalse(mWifiConnectivityHelper.isFirmwareRoamingSupported());
        assertEquals(WifiConnectivityHelper.INVALID_LIST_SIZE,
                mWifiConnectivityHelper.getMaxNumBlocklistBssid());
        assertEquals(WifiConnectivityHelper.INVALID_LIST_SIZE,
                mWifiConnectivityHelper.getMaxNumAllowlistSsid());
    }

    /**
     * Verify that firmware roaming is set to not supported if WifiNative returned firmware roaming
     * is supported but returned invalid max BSSID blocklist size. Firmware roaming capability
     * values should be reset to INVALID_LIST_SIZE.
     */
    @Test
    public void verifyFirmwareRoamingCapabilityWithInvalidMaxBssidBlocklistSize() {
        WifiNative.RoamingCapabilities roamCap = new WifiNative.RoamingCapabilities();
        roamCap.maxBlocklistSize = -5;
        roamCap.maxAllowlistSize = MAX_SSID_ALLOWLIST_SIZE;
        when(mClientModeManager.getRoamingCapabilities()).thenReturn(roamCap);

        assertFalse(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        assertFalse(mWifiConnectivityHelper.isFirmwareRoamingSupported());
        assertEquals(WifiConnectivityHelper.INVALID_LIST_SIZE,
                mWifiConnectivityHelper.getMaxNumBlocklistBssid());
        assertEquals(WifiConnectivityHelper.INVALID_LIST_SIZE,
                mWifiConnectivityHelper.getMaxNumAllowlistSsid());
    }

    /**
     * Verify that firmware roaming is set to not supported if WifiNative returned firmware roaming
     * is supported but returned invalid max SSID allowlist size. Firmware roaming capability values
     * should be reset to INVALID_LIST_SIZE.
     */
    @Test
    public void verifyFirmwareRoamingCapabilityWithInvalidMaxSsidAllowlistSize() {
        WifiNative.RoamingCapabilities roamCap = new WifiNative.RoamingCapabilities();
        roamCap.maxBlocklistSize = MAX_BSSID_BLOCKLIST_SIZE;
        roamCap.maxAllowlistSize = -2;
        when(mClientModeManager.getRoamingCapabilities()).thenReturn(roamCap);

        assertFalse(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        assertFalse(mWifiConnectivityHelper.isFirmwareRoamingSupported());
        assertEquals(WifiConnectivityHelper.INVALID_LIST_SIZE,
                mWifiConnectivityHelper.getMaxNumBlocklistBssid());
        assertEquals(WifiConnectivityHelper.INVALID_LIST_SIZE,
                mWifiConnectivityHelper.getMaxNumAllowlistSsid());
    }

    /**
     * Verify that correct size BSSID blocklist and SSID allowlist are accepted.
     */
    @Test
    public void verifySetFirmwareRoamingConfigurationWithGoodInput() {
        assertTrue(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        ArrayList<String> blocklist = buildBssidBlocklist(MAX_BSSID_BLOCKLIST_SIZE);
        ArrayList<String> allowlist = buildSsidAllowlist(MAX_SSID_ALLOWLIST_SIZE);
        assertTrue(mWifiConnectivityHelper.setFirmwareRoamingConfiguration(blocklist, allowlist));

        blocklist = buildBssidBlocklist(MAX_BSSID_BLOCKLIST_SIZE - 2);
        allowlist = buildSsidAllowlist(MAX_SSID_ALLOWLIST_SIZE - 3);
        assertTrue(mWifiConnectivityHelper.setFirmwareRoamingConfiguration(blocklist, allowlist));
    }

    /**
     * Verify that null BSSID blocklist or SSID allowlist is rejected.
     */
    @Test
    public void verifySetFirmwareRoamingConfigurationWithNullInput() {
        assertTrue(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        ArrayList<String> blocklist = buildBssidBlocklist(MAX_BSSID_BLOCKLIST_SIZE);
        ArrayList<String> allowlist = buildSsidAllowlist(MAX_SSID_ALLOWLIST_SIZE);
        assertFalse(mWifiConnectivityHelper.setFirmwareRoamingConfiguration(null, allowlist));
        assertFalse(mWifiConnectivityHelper.setFirmwareRoamingConfiguration(blocklist, null));
    }

    /**
     * Verify that incorrect size BSSID blocklist is rejected.
     */
    @Test
    public void verifySetFirmwareRoamingConfigurationWithIncorrectBlocklist() {
        assertTrue(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        ArrayList<String> blocklist = buildBssidBlocklist(MAX_BSSID_BLOCKLIST_SIZE + 1);
        ArrayList<String> allowlist = buildSsidAllowlist(MAX_SSID_ALLOWLIST_SIZE);
        assertFalse(mWifiConnectivityHelper.setFirmwareRoamingConfiguration(blocklist, allowlist));
    }

    /**
     * Verify that incorrect size SSID allowlist is rejected.
     */
    @Test
    public void verifySetFirmwareRoamingConfigurationWithIncorrectAllowlist() {
        assertTrue(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        ArrayList<String> blocklist = buildBssidBlocklist(MAX_BSSID_BLOCKLIST_SIZE);
        ArrayList<String> allowlist = buildSsidAllowlist(MAX_SSID_ALLOWLIST_SIZE + 1);
        assertFalse(mWifiConnectivityHelper.setFirmwareRoamingConfiguration(blocklist, allowlist));
    }

    /**
     * Verify that empty BSSID blocklist and SSID allowlist are sent to WifiNative
     * to reset the firmware roaming configuration.
     */
    @Test
    public void verifySetFirmwareRoamingConfigurationWithEmptyBlocklistAndAllowlist() {
        assertTrue(mWifiConnectivityHelper.getFirmwareRoamingInfo());
        ArrayList<String> blocklist = buildBssidBlocklist(0);
        ArrayList<String> allowlist = buildSsidAllowlist(0);
        assertTrue(mWifiConnectivityHelper.setFirmwareRoamingConfiguration(blocklist, allowlist));
        verify(mClientModeManager).configureRoaming(mRoamingConfigCaptor.capture());
        assertEquals(0, mRoamingConfigCaptor.getValue().blocklistBssids.size());
        assertEquals(0, mRoamingConfigCaptor.getValue().allowlistSsids.size());
    }
}
