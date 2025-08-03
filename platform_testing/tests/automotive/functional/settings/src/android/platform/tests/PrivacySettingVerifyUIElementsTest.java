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
import android.platform.helpers.IAutoUISettingsHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PrivacySettingVerifyUIElementsTest {
    private HelperAccessor<IAutoUISettingsHelper> mSettingsUIHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;

    public PrivacySettingVerifyUIElementsTest() throws Exception {
        mSettingsUIHelper = new HelperAccessor<>(IAutoUISettingsHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
    }

    @Before
    public void openPrivacySetting() {
        mSettingHelper.get().openSetting(SettingsConstants.PRIVACY_SETTINGS);
        assertTrue(
                "Privacy settings did not open",
                mSettingHelper.get().checkMenuExists("Microphone"));
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
        mSettingHelper.get().exit();
    }

    @Test
    public void testVerifyMicrophoneUIElement() {
        assertTrue(
                "Microphone Option is not displayed.",
                mSettingsUIHelper.get().hasUIElement(AutomotiveConfigConstants.MICROPHONE));
        mSettingsUIHelper.get().openUIOptions(AutomotiveConfigConstants.MICROPHONE);
        assertTrue(
                "Microphone settings did not open",
                mSettingHelper.get().checkMenuExists("Use microphone"));
        String currentTitle = mSettingHelper.get().getPageTitleText();
        mSettingsUIHelper.get().pressBackButton();
        String newTitle = mSettingHelper.get().getPageTitleText();
        assertFalse("Back button is not working", currentTitle.equals(newTitle));
    }

    @Test
    public void testVerifyLocationUIElement() {
        assertTrue(
                "Location Option is not displayed.",
                mSettingsUIHelper.get().hasUIElement(AutomotiveConfigConstants.LOCATION));
        mSettingsUIHelper.get().openUIOptions(AutomotiveConfigConstants.LOCATION);
        assertTrue(
                "Location settings did not open",
                mSettingHelper.get().checkMenuExists("Location access"));
        String currentTitle = mSettingHelper.get().getPageTitleText();
        mSettingsUIHelper.get().pressBackButton();
        String newTitle = mSettingHelper.get().getPageTitleText();
        assertFalse("Back button is not working", currentTitle.equals(newTitle));
    }

    @Test
    public void testVerifyAppPermissionsUIElement() {
        assertTrue(
                "App permissions Option is not displayed.",
                mSettingsUIHelper.get().hasUIElement(AutomotiveConfigConstants.APP_PERMISSION));
        mSettingsUIHelper.get().openUIOptions(AutomotiveConfigConstants.APP_PERMISSION);
        assertTrue(
                "App permissions settings did not open",
                mSettingHelper.get().checkMenuExists("Privacy dashboard"));
        String currentTitle = mSettingHelper.get().getPageTitleText();
        mSettingsUIHelper.get().pressBackButton();
        String newTitle = mSettingHelper.get().getPageTitleText();
        assertFalse("Back button is not working", currentTitle.equals(newTitle));
    }

    @Test
    public void testVerifyInfotainmentSystemDataUIElement() {
        assertTrue(
                "Infotainment system data Option is not displayed.",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.INFOTAINMENT_SYSTEM_DATA));
        mSettingsUIHelper.get().openUIOptions(AutomotiveConfigConstants.INFOTAINMENT_SYSTEM_DATA);
        assertTrue(
                "Infotainment system data settings did not open",
                mSettingHelper.get().checkMenuExists("Delete your profile"));
        String currentTitle = mSettingHelper.get().getPageTitleText();
        mSettingsUIHelper.get().pressBackButton();
        String newTitle = mSettingHelper.get().getPageTitleText();
        assertFalse("Back button is not working", currentTitle.equals(newTitle));
    }

    @Test
    public void testVerifyDataSharingGoogleUIElement() {
        assertTrue(
                "Data Sharing with Google Option is not displayed.",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.DATA_SHARING_WITH_GOOGLE));
        mSettingsUIHelper.get().openUIOptions(AutomotiveConfigConstants.DATA_SHARING_WITH_GOOGLE);
        assertTrue(
                "Data Sharing with Google settings did not open",
                mSettingHelper.get().checkMenuExists("Send feedback to Google"));
    }
}
