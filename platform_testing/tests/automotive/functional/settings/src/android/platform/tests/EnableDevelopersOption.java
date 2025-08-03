/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.IAutoSystemSettingsHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EnableDevelopersOption {
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoSystemSettingsHelper> mSystemSettingsHelper;

    public EnableDevelopersOption() throws Exception {
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mSystemSettingsHelper = new HelperAccessor<>(IAutoSystemSettingsHelper.class);
    }

    @Test
    public void testToVerifyDeveloperOptions() throws Exception {
        mSettingHelper.get().openSetting(SettingsConstants.SYSTEM_SETTINGS);
        mSystemSettingsHelper.get().enterDeveloperMode();
        assertTrue(
                "Developer options are not displayed",
                mSystemSettingsHelper.get().hasDeveloperOptions());
        mSystemSettingsHelper.get().openDeveloperOptions();
    }

    @After
    public void goBackToSettingsScreen() {
        mSettingHelper.get().goBackToSettingsScreen();
    }
}
