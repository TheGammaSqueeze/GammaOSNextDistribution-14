/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.b2b.WifiRoamingModeManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.wifi.b2b.WifiRoamingModeManager}.
 */
@SmallTest
public class WifiRoamingModeManagerTest extends WifiBaseTest {
    private static final String TEST_SSID = "\"test_ssid\"";
    private static final String CURRENT_SSID = "\"current_ssid\"";
    private static final String WIFI_IFACE_NAME = "wlan0";
    private static final int DEFAULT_ROAMING_MODE = WifiManager.ROAMING_MODE_NORMAL;
    private static final int TEST_ROAMING_MODE = WifiManager.ROAMING_MODE_AGGRESSIVE;
    @Mock ActiveModeWarden mActiveModeWarden;
    @Mock ClientModeManager mClientModeManager;
    @Mock WifiInfo mWifiInfo;
    @Mock WifiNative mWifiNative;
    @Mock WifiRoamingConfigStore mWifiRoamingConfigStore;

    WifiRoamingModeManager mWifiRoamingModeManager;
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mWifiRoamingModeManager = new WifiRoamingModeManager(mWifiNative,
                mActiveModeWarden, mWifiRoamingConfigStore);
        when(mActiveModeWarden.getConnectionInfo()).thenReturn(mWifiInfo);
        when(mWifiInfo.getSSID()).thenReturn(CURRENT_SSID);
        when(mActiveModeWarden.getPrimaryClientModeManager()).thenReturn(mClientModeManager);
        when(mClientModeManager.getInterfaceName()).thenReturn(WIFI_IFACE_NAME);
    }

    @Test
    public void testSetPerSsidRoamingMode() {
        assumeTrue(SdkLevel.isAtLeastV());
        when(mWifiRoamingConfigStore.getRoamingMode(CURRENT_SSID)).thenReturn(
                TEST_ROAMING_MODE);
        mWifiRoamingModeManager.setPerSsidRoamingMode(WifiSsid.fromString(CURRENT_SSID),
                TEST_ROAMING_MODE, false);
        verify(mWifiRoamingConfigStore).addRoamingMode(CURRENT_SSID, TEST_ROAMING_MODE, false);
        verify(mActiveModeWarden).getConnectionInfo();
        // set roaming mode when current network is the updated network
        verify(mWifiNative).setRoamingMode(WIFI_IFACE_NAME, TEST_ROAMING_MODE);
    }

    @Test
    public void testRemovePerSsidRoamingMode() {
        assumeTrue(SdkLevel.isAtLeastV());
        mWifiRoamingModeManager.removePerSsidRoamingMode(WifiSsid.fromString(TEST_SSID), false);
        verify(mWifiRoamingConfigStore).removeRoamingMode(TEST_SSID, false);
        verify(mActiveModeWarden).getConnectionInfo();
        // do not set roaming mode when current network is not the updated network
        verify(mWifiNative, never()).setRoamingMode(anyString(), anyInt());
    }

    @Test
    public void testGetPerSsidRoamingMode() {
        assumeTrue(SdkLevel.isAtLeastV());
        mWifiRoamingModeManager.getPerSsidRoamingModes(false);
        verify(mWifiRoamingConfigStore).getPerSsidRoamingModes(false);
        verify(mActiveModeWarden, never()).getConnectionInfo();
    }

    @Test
    public void testApplyWifiRoamingMode() {
        when(mWifiRoamingConfigStore.getRoamingMode(TEST_SSID)).thenReturn(
                DEFAULT_ROAMING_MODE);
        mWifiRoamingModeManager.applyWifiRoamingMode(WIFI_IFACE_NAME, TEST_SSID);
        verify(mWifiNative).setRoamingMode(WIFI_IFACE_NAME, DEFAULT_ROAMING_MODE);
    }
}
