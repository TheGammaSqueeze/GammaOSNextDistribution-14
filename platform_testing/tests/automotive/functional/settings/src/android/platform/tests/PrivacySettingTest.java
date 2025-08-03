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
import android.platform.helpers.IAutoPrivacySettingsHelper;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PrivacySettingTest {
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoPrivacySettingsHelper> mPrivacySettingsHelper;

    public PrivacySettingTest() throws Exception {
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mPrivacySettingsHelper = new HelperAccessor<>(IAutoPrivacySettingsHelper.class);
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
    }
    // This test needs a user logged in
    @Test
    public void testActivityControlUserLoggedIn() {
        mSettingHelper.get().openMenuWith("Data sharing with Google");
        mSettingHelper.get().openMenuWith("Activity Controls");
        assertTrue(
                "Manage activity controls is not displayed",
                mPrivacySettingsHelper.get().isManageActivityControlOpen());
    }

    // This test needs a user logged in
    @Test
    public void testAutofillServiceNoUserLoggedIn() {
        mSettingHelper.get().openMenuWith("Data sharing with Google");
        mSettingHelper.get().openMenuWith("Autofill service from Google");
        assertTrue(
                "Message to add Account is not displayed",
                mPrivacySettingsHelper.get().isAccountAddedAutofill());
    }

    // No account should be added for this test
    @Test
    public void testAutofillServiceUserLoggedIn() {
        mSettingHelper.get().openMenuWith("Data sharing with Google");
        mSettingHelper.get().openMenuWith("Autofill service from Google");
        assertFalse(
                "Message to add Account is displayed",
                mPrivacySettingsHelper.get().isAccountAddedAutofill());
    }
}
