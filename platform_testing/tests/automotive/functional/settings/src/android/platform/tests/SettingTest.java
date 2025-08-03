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
import android.platform.helpers.IAutoAppInfoSettingsHelper;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingTest {
    private static final int DAY_MODE_VALUE = 0;
    private HelperAccessor<IAutoAppInfoSettingsHelper> mAppInfoSettingsHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;

    public SettingTest() throws Exception {
        mAppInfoSettingsHelper = new HelperAccessor<>(IAutoAppInfoSettingsHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }


    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
    }

    @Test
    public void testDisplaySettings() {
        mSettingHelper.get().openSetting(SettingsConstants.DISPLAY_SETTINGS);
        assertTrue(
                "Display Setting did not open",
                mSettingHelper.get().checkMenuExists("Brightness level"));
    }

    @Test
    public void testSoundSettings() {
        mSettingHelper.get().openSetting(SettingsConstants.SOUND_SETTINGS);
        assertTrue(
                "Sound setting did not open",
                mSettingHelper.get().checkMenuExists("In-call volume"));
    }

    @Test
    public void testAppinfoSettings() {
        mSettingHelper.get().openSetting(SettingsConstants.APPS_SETTINGS);
        assertTrue(
                "Apps setting did not open",
                mSettingHelper.get().checkMenuExists("Recently opened"));
        mAppInfoSettingsHelper.get().showAllApps();
    }

    @Test
    public void testAccountsSettings() {
        mSettingHelper.get().openSetting(SettingsConstants.PROFILE_ACCOUNT_SETTINGS);
        assertTrue(
                "Profiles and accounts settings did not open",
                mSettingHelper.get().checkMenuExists("Add a profile"));
    }

    @Test
    public void testSystemSettings() {
        mSettingHelper.get().openSetting(SettingsConstants.SYSTEM_SETTINGS);
        assertTrue(
                "System settings did not open",
                mSettingHelper.get().checkMenuExists("Languages & input"));
    }

    @Test
    public void testBluetoothSettings() {
        mSettingHelper.get().openSetting(SettingsConstants.BLUETOOTH_SETTINGS);
        assertTrue(
                "Bluetooth Setting did not open",
                mSettingHelper.get().checkMenuExists("Pair new device"));
        mSettingHelper.get().turnOnOffBluetooth(false);
        assertFalse(mSettingHelper.get().isBluetoothOn());
        mSettingHelper.get().turnOnOffBluetooth(true);
    }
}
