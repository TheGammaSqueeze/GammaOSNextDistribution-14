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
import android.platform.helpers.IAutoSettingsLocationHelper;
import android.platform.helpers.SettingsConstants;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsLocationTest {

    private HelperAccessor<IAutoSettingsLocationHelper> mSettingLocationHelper;
    private HelperAccessor<IAutoSettingHelper> mSettingHelper;

    public SettingsLocationTest() {
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mSettingLocationHelper = new HelperAccessor<>(IAutoSettingsLocationHelper.class);
    }

    @Before
    public void setup() {
        mSettingHelper.get().openSetting(SettingsConstants.LOCATION_SETTINGS);
        assertTrue(
                "Location settings did not open", mSettingHelper.get().checkMenuExists("Location"));
    }

    @Test
    public void testToVerifyToggleLocation() {
        mSettingLocationHelper.get().locationAccess();
        mSettingLocationHelper.get().isLocationOn();
        assertFalse("Location widget is displayed ", mSettingLocationHelper.get().hasMapsWidget());
        mSettingLocationHelper.get().toggleLocation(true);
        assertTrue(
                "Location widget is not displayed ", mSettingLocationHelper.get().hasMapsWidget());
        mSettingLocationHelper.get().toggleLocation(false);
        assertFalse("Location widget is displayed ", mSettingLocationHelper.get().hasMapsWidget());
    }

    @Test
    public void testToCheckRecentlyAccessedOption() {
        assertTrue(
                "Recently accessed option is not displayed ",
                mSettingLocationHelper.get().hasRecentlyAccessed());
    }
}
