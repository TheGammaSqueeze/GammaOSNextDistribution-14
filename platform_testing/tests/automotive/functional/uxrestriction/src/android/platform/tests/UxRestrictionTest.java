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
import android.platform.helpers.IAutoAppGridHelper;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.IAutoVehicleHardKeysHelper;
import android.platform.helpers.IAutoVehicleHardKeysHelper.DrivingState;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UxRestrictionTest {
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoAppGridHelper> mAppGridHelper;
    private HelperAccessor<IAutoVehicleHardKeysHelper> mHardKeysHelper;

    private static final int SPEED_TWENTY = 20;
    private static final int SPEED_ZERO = 0;

    public UxRestrictionTest() throws Exception {
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mAppGridHelper = new HelperAccessor<>(IAutoAppGridHelper.class);
        mHardKeysHelper = new HelperAccessor<>(IAutoVehicleHardKeysHelper.class);
    }

    @Before
    public void enableDrivingMode() {
        mHardKeysHelper.get().setDrivingState(DrivingState.MOVING);
        mHardKeysHelper.get().setSpeed(SPEED_TWENTY);
    }

    @After
    public void disableDrivingMode() {
        mSettingHelper.get().goBackToSettingsScreen();
        mHardKeysHelper.get().setSpeed(SPEED_ZERO);
        mHardKeysHelper.get().setDrivingState(DrivingState.PARKED);
    }

    @Test
    public void testRestrictedSoundSettings() {
        mSettingHelper.get().openSetting(SettingsConstants.SOUND_SETTINGS);
        String currentTitle = mSettingHelper.get().getPageTitleText();
        assertTrue(
                "Sound setting did not open",
                mSettingHelper.get().checkMenuExists("In-call volume"));
        mSettingHelper.get().openMenuWith("In-call Volume");
        String newTitle = mSettingHelper.get().getPageTitleText();
        assertTrue("Phone ringtone setting is not disabled", currentTitle.equals(newTitle));
    }

    @Test
    public void testRestrictedNetworkSettings() {
        mSettingHelper.get().openSetting(SettingsConstants.NETWORK_AND_INTERNET_SETTINGS);
        assertTrue(
                "Network and Internet settings did not open",
                mSettingHelper.get().checkMenuExists("Hotspot"));
        Boolean currentHotspotState = mSettingHelper.get().isHotspotOn();
        mSettingHelper.get().toggleHotspot();
        Boolean newHotspotState = mSettingHelper.get().isHotspotOn();
        assertFalse("Hotspot is not working", currentHotspotState.equals(newHotspotState));
        mSettingHelper.get().toggleHotspot();
    }

    @Test
    public void testRestrictedBluetoothSettings() {
        mSettingHelper.get().openSetting(SettingsConstants.BLUETOOTH_SETTINGS);
        assertTrue(
                "Bluetooth Setting did not open",
                mSettingHelper.get().checkMenuExists("Pair new device"));
        String currentTitle = mSettingHelper.get().getPageTitleText();
        mSettingHelper.get().openMenuWith("Pair new device");
        String newTitle = mSettingHelper.get().getPageTitleText();
        assertTrue("Bluetooth setting is not disabled", currentTitle.equals(newTitle));
    }

    @Test
    public void testRestrictedAppSettings() {
        mSettingHelper.get().openFullSettings();
        String currentTitle = mSettingHelper.get().getPageTitleText();
        mSettingHelper.get().openSetting(SettingsConstants.APPS_SETTINGS);
        assertFalse("Apps is not disabled", mSettingHelper.get().checkMenuExists("View all"));
        String newTitle = mSettingHelper.get().getPageTitleText();
        assertTrue("Apps & notification settings is not disabled", currentTitle.equals(newTitle));
    }

    @Test
    public void testRestrictedProfilesAndAccountsSettings() {
        mSettingHelper.get().openFullSettings();
        String currentTitle = mSettingHelper.get().getPageTitleText();
        mSettingHelper.get().openSetting(SettingsConstants.PROFILE_ACCOUNT_SETTINGS);
        assertFalse(
                "Profiles and accounts settings is not disabled",
                mSettingHelper.get().checkMenuExists("Add a profile"));
        String newTitle = mSettingHelper.get().getPageTitleText();
        assertTrue("Profiles and accounts settings is not disabled", currentTitle.equals(newTitle));
    }

    @Test
    public void testRestrictedSecuritySettings() {
        mSettingHelper.get().openFullSettings();
        String currentTitle = mSettingHelper.get().getPageTitleText();
        mSettingHelper.get().openSetting(SettingsConstants.SECURITY_SETTINGS);
        assertFalse(
                "Security settings is not disabled",
                mSettingHelper.get().checkMenuExists("Profile lock"));
        String newTitle = mSettingHelper.get().getPageTitleText();
        assertTrue("Security settings is not disabled", currentTitle.equals(newTitle));
    }

    @Test
    public void testRestrictedSystemSettings() {
        mSettingHelper.get().openFullSettings();
        String currentTitle = mSettingHelper.get().getPageTitleText();
        mSettingHelper.get().openSetting(SettingsConstants.SYSTEM_SETTINGS);
        assertFalse(
                "System settings is not disabled",
                mSettingHelper.get().checkMenuExists("Languages & input"));
        String newTitle = mSettingHelper.get().getPageTitleText();
        assertTrue("System settings is not disabled", currentTitle.equals(newTitle));
    }
}
