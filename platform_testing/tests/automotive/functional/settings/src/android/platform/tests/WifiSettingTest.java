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

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WifiSettingTest {
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;

    public WifiSettingTest() throws Exception {
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }


    @Before
    public void openNetworkSetting() {
        mSettingHelper.get().openSetting(SettingsConstants.NETWORK_AND_INTERNET_SETTINGS);
        assertTrue(
                "Network and Internet settings did not open",
                mSettingHelper.get().checkMenuExists("Hotspot"));
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
    }

    @Test
    public void testWifiSettings() {
        mSettingHelper.get().turnOnOffWifi(false);
        assertFalse(mSettingHelper.get().isWifiOn());
        mSettingHelper.get().turnOnOffWifi(true);
        assertTrue(mSettingHelper.get().isWifiOn());
    }

    @Test
    public void testTurnOnOffHotspot() {
        mSettingHelper.get().turnOnOffHotspot(true);
        assertTrue(mSettingHelper.get().isHotspotOn());
        mSettingHelper.get().turnOnOffHotspot(false);
    }
}
