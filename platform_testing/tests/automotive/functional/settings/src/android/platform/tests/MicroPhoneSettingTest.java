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
import android.platform.helpers.IAutoFacetBarHelper;
import android.platform.helpers.IAutoPrivacySettingsHelper;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MicroPhoneSettingTest {
    private static final String USE_MICROPHONE_TXT = "Use microphone";
    private static final String MICROPHONE_OFF_TXT = "Microphone is off.";

    private HelperAccessor<IAutoFacetBarHelper> mFacetBarHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoPrivacySettingsHelper> mPrivacySettingsHelper;

    public MicroPhoneSettingTest() throws Exception {
        mFacetBarHelper = new HelperAccessor<>(IAutoFacetBarHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mPrivacySettingsHelper = new HelperAccessor<>(IAutoPrivacySettingsHelper.class);
    }


    @Before
    public void openPrivacySetting() {
        mSettingHelper.get().openSetting(SettingsConstants.PRIVACY_SETTINGS);
        assertTrue(
                "Privacy settings did not open",
                mSettingHelper.get().checkMenuExists("Microphone"));
        mSettingHelper.get().openMenuWith("MicroPhone");
        assertTrue(
                "MicroPhone settings did not open",
                mSettingHelper.get().checkMenuExists("Use microphone"));
    }

    @Test
    public void manageMicrophonePermissions() {
        mSettingHelper.get().openSetting(SettingsConstants.PRIVACY_SETTINGS);
        assertTrue(
                "Privacy settings did not open",
                mSettingHelper.get().checkMenuExists("Microphone"));
        mSettingHelper.get().openMenuWith("MicroPhone");
        mPrivacySettingsHelper.get().clickManageMicroPhonePermissions();
        assertTrue(
                "Microphone Permissions page is not displayed",
                mPrivacySettingsHelper.get().verifyMicrophoneManagePermissionsPage());
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
        mSettingHelper.get().openSetting(SettingsConstants.PRIVACY_SETTINGS);
        mSettingHelper.get().openMenuWith("MicroPhone");
        // MicroPhone is on by default
        if (!mPrivacySettingsHelper.get().isMicroPhoneOn()) {
            mPrivacySettingsHelper.get().turnOnOffMicroPhone(true);
        }
    }

    @Test
    public void testMicroPhoneToggleOff() {
        // turn off microphone
        mPrivacySettingsHelper.get().turnOnOffMicroPhone(false);
        assertFalse("MicroPhone is still on", mPrivacySettingsHelper.get().isMicroPhoneOn());
        assertFalse(
                "Recent apps is displayed",
                mSettingHelper.get().checkMenuExists("Recently accessed"));
        assertTrue(
                "Micro Phone button is not diplayed in the Status Bar",
                mPrivacySettingsHelper.get().isMutedMicChipPresentOnStatusBar());
    }

    @Test
    public void testMicroPhoneToggleOn() {
        // turn off microphone
        mPrivacySettingsHelper.get().turnOnOffMicroPhone(false);
        // turn on microphone
        mPrivacySettingsHelper.get().turnOnOffMicroPhone(true);
        assertTrue("MicroPhone is still off", mPrivacySettingsHelper.get().isMicroPhoneOn());
        assertTrue(
                "Recently accessed is not present",
                mSettingHelper.get().checkMenuExists("Recently accessed"));
        assertTrue(
                "No Recent apps is not present",
                mPrivacySettingsHelper.get().verifyNoRecentAppsPresent());
        assertFalse(
                "Muted Micro Phone button is still diplayed in the Status Bar",
                mPrivacySettingsHelper.get().isMutedMicChipPresentOnStatusBar());
    }

    @Test
    public void testMicroPhonePanelStatusBar() {
        // turn off microphone
        mPrivacySettingsHelper.get().turnOnOffMicroPhone(false);
        // open microphone panel
        mPrivacySettingsHelper.get().clickMicroPhoneStatusBar();
        assertTrue(
                "MicroPhone status not updated",
                mPrivacySettingsHelper.get().isMicroPhoneStatusMessageUpdated(MICROPHONE_OFF_TXT));
        assertTrue(
                "MicroPhone settings link is not present",
                mPrivacySettingsHelper.get().isMicroPhoneSettingsLinkPresent());
        assertTrue(
                "MicroPhone toggle not present in status bar",
                mPrivacySettingsHelper.get().isMicroPhoneTogglePresent());
    }

    @Test
    public void testMicroPhonePanelStatusBarFromHome() {
        // turn off microphone
        mPrivacySettingsHelper.get().turnOnOffMicroPhone(false);
        mFacetBarHelper.get().goToHomeScreen();
        // open microphone panel
        mPrivacySettingsHelper.get().clickMicroPhoneStatusBar();
        assertTrue(
                "MicroPhone settings link is not present",
                mPrivacySettingsHelper.get().isMicroPhoneSettingsLinkPresent());
        assertTrue(
                "MicroPhone toggle not present in status bar",
                mPrivacySettingsHelper.get().isMicroPhoneTogglePresent());
    }

    @Test
    public void testMicroPhonePanelSettingsLink() {
        // turn off microphone
        mPrivacySettingsHelper.get().turnOnOffMicroPhone(false);
        // open microphone panel
        mPrivacySettingsHelper.get().clickMicroPhoneStatusBar();
        // go to privacy settings
        mPrivacySettingsHelper.get().clickMicroPhoneSettingsLink();
        assertTrue(
                "Privacy settings did not open",
                mSettingHelper.get().checkMenuExists("App permissions"));
    }

    @Test
    public void testMicroPhonePanelToggle() {
        // turn off microphone
        mPrivacySettingsHelper.get().turnOnOffMicroPhone(false);
        mPrivacySettingsHelper.get().clickMicroPhoneStatusBar();
        // turn on microphone
        mPrivacySettingsHelper.get().clickMicroPhoneToggleStatusBar();
        assertTrue("MicroPhone is still off", mPrivacySettingsHelper.get().isMicroPhoneOn());
        assertFalse(
                "MicroPhone button not updated in status bar",
                mPrivacySettingsHelper.get().isMutedMicChipPresentWithMicPanel());
        assertTrue(
                "MicroPhone status not updated",
                mPrivacySettingsHelper.get().isMicroPhoneStatusMessageUpdated(USE_MICROPHONE_TXT));
        // turn off microphone
        mPrivacySettingsHelper.get().clickMicroPhoneToggleStatusBar();
        assertTrue(
                "MicroPhone button should be muted",
                mPrivacySettingsHelper.get().isMutedMicChipPresentWithMicPanel());
    }

    @Test
    public void testMicroPhoneButtonDimiss() {
        mPrivacySettingsHelper.get().turnOnOffMicroPhone(false);
        mPrivacySettingsHelper.get().clickMicroPhoneStatusBar();
        // turn microphone on
        mPrivacySettingsHelper.get().clickMicroPhoneToggleStatusBar();
        assertFalse(
                "Muted MicroPhone button is displayed in status bar",
                mPrivacySettingsHelper.get().isMutedMicChipPresentWithMicPanel());
        mFacetBarHelper.get().goToHomeScreen();
        assertFalse(
                "MicroPhone button is still displayed on status bar",
                mPrivacySettingsHelper.get().isMicChipPresentOnStatusBar());
    }
}
