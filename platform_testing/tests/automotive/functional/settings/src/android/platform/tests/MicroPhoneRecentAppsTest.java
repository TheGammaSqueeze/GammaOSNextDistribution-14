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

import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoAppGridHelper;
import android.platform.helpers.IAutoPrivacySettingsHelper;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MicroPhoneRecentAppsTest {
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoAppGridHelper> mAppGridHelper;
    private HelperAccessor<IAutoPrivacySettingsHelper> mPrivacySettingsHelper;

    private static final String APP = "Google Assistant";
    private static final String APP_TXT = "Google Assistant is using the mic";

    public MicroPhoneRecentAppsTest() throws Exception {
        mAppGridHelper = new HelperAccessor<>(IAutoAppGridHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mPrivacySettingsHelper = new HelperAccessor<>(IAutoPrivacySettingsHelper.class);
    }

    @Before
    public void openPrivacySetting() {
        mAppGridHelper.get().open();
        mAppGridHelper.get().openApp(APP);
    }

    @Before
    public void exit() {
        mPrivacySettingsHelper.get().exit();
    }

    @Test
    public void testRecentlyAccessedApps() {
        mSettingHelper.get().openSetting(SettingsConstants.PRIVACY_SETTINGS);
        mSettingHelper.get().openMenuWith("MicroPhone");
        assertTrue(
                "Recent App time stamp is not displayed in microphone settings page",
                mPrivacySettingsHelper.get().isRecentAppDisplayedWithStamp(APP));
    }

    @Test
    public void testViewAllLink() {
        mSettingHelper.get().openSetting(SettingsConstants.PRIVACY_SETTINGS);
        mSettingHelper.get().openMenuWith("MicroPhone");
        mPrivacySettingsHelper.get().clickViewAllLink();
        assertTrue(
                "Recent App time stamp is not displayed in view all page",
                mPrivacySettingsHelper.get().isRecentAppDisplayedWithStamp(APP));
    }

    @Test
    public void testMicroPhonePanelUpdatedWithCurrentAppUsage() {
        mPrivacySettingsHelper.get().clickUnMutedMicroPhoneStatusBar();
        assertTrue(
                "Current App usage is not displayed in the panel",
                mPrivacySettingsHelper.get().isMicroPhoneStatusMessageUpdated(APP_TXT));
    }
}
