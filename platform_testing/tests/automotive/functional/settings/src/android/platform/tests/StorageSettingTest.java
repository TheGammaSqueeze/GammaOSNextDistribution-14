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

import android.platform.helpers.AutomotiveConfigConstants;
import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.IAutoSystemSettingsHelper;
import android.platform.helpers.IAutoUISettingsHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StorageSettingTest {
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoUISettingsHelper> mSettingsUIHelper;
    private HelperAccessor<IAutoSystemSettingsHelper> mSystemSettingsHelper;

    public StorageSettingTest() throws Exception {
        mSettingsUIHelper = new HelperAccessor<>(IAutoUISettingsHelper.class);
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mSystemSettingsHelper = new HelperAccessor<>(IAutoSystemSettingsHelper.class);
    }

    @Before
    public void openSystemStorageFacet() {
        mSettingHelper.get().openSetting(SettingsConstants.SYSTEM_SETTINGS);
        assertTrue(
                "System settings did not open",
                mSettingHelper.get().checkMenuExists("Languages & input"));
        mSystemSettingsHelper.get().openStorageMenu();
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
    }

    @Test
    public void testMusicAndAudio() {
        assertTrue(
                "Music and Audio and Usage are not Present",
                mSystemSettingsHelper
                        .get()
                        .verifyUsageinGB(AutomotiveConfigConstants.STORAGE_MUSIC_AUDIO_SETTINGS));
        assertTrue(
                "Music and Audio is not present",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.STORAGE_MUSIC_AUDIO_SETTINGS));
        mSettingsUIHelper
                .get()
                .openUIOptions(AutomotiveConfigConstants.STORAGE_MUSIC_AUDIO_SETTINGS);
        assertTrue(
                "Music and Audio is not open",
                mSettingHelper.get().checkMenuExists("Hide system apps"));
    }

    @Test
    public void testOtherApps() {
        assertTrue(
                "Other apps Usage is not Present",
                mSystemSettingsHelper
                        .get()
                        .verifyUsageinGB(AutomotiveConfigConstants.STORAGE_OTHER_APPS_SETTINGS));
        assertTrue(
                "Other apps is not present",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.STORAGE_OTHER_APPS_SETTINGS));
        mSettingsUIHelper
                .get()
                .openUIOptions(AutomotiveConfigConstants.STORAGE_OTHER_APPS_SETTINGS);
        assertTrue(
                "Other apps is not open", mSettingHelper.get().checkMenuExists("Hide system apps"));
    }

    @Test
    public void testFiles() {
        assertTrue(
                "Files Usage is not Present",
                mSystemSettingsHelper
                        .get()
                        .verifyUsageinGB(AutomotiveConfigConstants.STORAGE_FILES_SETTINGS));
        assertTrue(
                "Files Usage is not present",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.STORAGE_FILES_SETTINGS));
        mSettingsUIHelper.get().openUIOptions(AutomotiveConfigConstants.STORAGE_FILES_SETTINGS);
    }

    @Test
    public void testSystem() {
        assertTrue(
                "System Usage is not Present",
                mSystemSettingsHelper
                        .get()
                        .verifyUsageinGB(AutomotiveConfigConstants.STORAGE_SYSTEM_SETTINGS));
        assertTrue(
                "System is not present",
                mSettingsUIHelper
                        .get()
                        .hasUIElement(AutomotiveConfigConstants.STORAGE_SYSTEM_SETTINGS));
        mSettingsUIHelper.get().openUIOptions(AutomotiveConfigConstants.STORAGE_SYSTEM_SETTINGS);
    }
}
