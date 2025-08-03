/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.platform.tests;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.platform.helpers.AutomotiveConfigConstants;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.IAutoStatusBarHelper;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NetworkPaletteTest {

    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoStatusBarHelper> mStatusBarHelper;
    private static final String HOTSPOT = AutomotiveConfigConstants.NETWORK_PALETTE_HOTSPOT;
    private static final String WIFI = AutomotiveConfigConstants.NETWORK_PALETTE_WIFI;

    public NetworkPaletteTest() {
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mStatusBarHelper = new HelperAccessor<>(IAutoStatusBarHelper.class);
    }

    @Before
    public void openNetworkPalette() {
        mStatusBarHelper.get().openNetworkPalette();
        assertTrue(
                "Network Palette is not open",
                mSettingHelper.get().checkMenuExists("Network & internet settings"));
    }

    @After
    public void goBackToHomeScreen() {
        mSettingHelper.get().exit();
    }

    @Test
    public void testHotspotName() {
        // check if hotspot is enabled
        if (!mStatusBarHelper.get().isNetworkSwitchEnabled(HOTSPOT)) {
            mStatusBarHelper.get().networkPaletteToggleOnOff(HOTSPOT);
        }
        assertTrue(
                "Hotspot Name and password is not displayed",
                mStatusBarHelper.get().isHotspotNameDisplayed());
        // Turn off the Hotspot
        mStatusBarHelper.get().networkPaletteToggleOnOff(HOTSPOT);
        assertFalse("Hotspot is enabled", mStatusBarHelper.get().isNetworkSwitchEnabled(HOTSPOT));
    }

    @Test
    public void testWifiName() {
        // check if wifi is enabled
        if (!mSettingHelper.get().isWifiOn()) {
            mStatusBarHelper.get().networkPaletteToggleOnOff(WIFI);
        }
        // Verifying Wifi is turned ON
        assertTrue("Wi-Fi is not enabled", mSettingHelper.get().isWifiOn());
        assertTrue("Wi-Fi Name is not displayed", mStatusBarHelper.get().isWifiNameDisplayed());
        // Turn off Wifi
        mStatusBarHelper.get().networkPaletteToggleOnOff(WIFI);
        assertFalse("Wi-Fi is enabled", mSettingHelper.get().isWifiOn());
    }

    @Test
    public void testWifiAndHotspotEnabledTogether() {
        // check if wifi and hostpot is enabled
        if (!mSettingHelper.get().isWifiOn()) {
            mStatusBarHelper.get().networkPaletteToggleOnOff(WIFI);
        }
        if (!mStatusBarHelper.get().isNetworkSwitchEnabled(HOTSPOT)) {
            mStatusBarHelper.get().networkPaletteToggleOnOff(HOTSPOT);
        }
        assertTrue("Wi-Fi is not enabled", mSettingHelper.get().isWifiOn());
        assertTrue(
                "Hotspot is not enabled", mStatusBarHelper.get().isNetworkSwitchEnabled(HOTSPOT));
    }

    @Test
    public void testNetworkAndinternetSettings() {
        mSettingHelper.get().openMenuWith("Network & internet settings");
        mStatusBarHelper.get().clickOnConnectedWifi();
        mStatusBarHelper.get().forgetWifi();
        openNetworkPalette();
        // check if wifi and hostpot is enabled
        if (!mSettingHelper.get().isWifiOn()) {
            mStatusBarHelper.get().networkPaletteToggleOnOff(WIFI);
        }
        if (!mStatusBarHelper.get().isNetworkSwitchEnabled(HOTSPOT)) {
            mStatusBarHelper.get().networkPaletteToggleOnOff(HOTSPOT);
        }
        assertTrue("Wi-Fi is not enabled", mSettingHelper.get().isWifiOn());
        assertTrue(
                "Hotspot is not enabled", mStatusBarHelper.get().isNetworkSwitchEnabled(HOTSPOT));
    }
}
