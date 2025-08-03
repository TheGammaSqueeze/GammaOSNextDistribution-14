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
import android.platform.helpers.IAutoDisplaySettingsHelper;
import android.platform.helpers.IAutoSettingHelper;
import android.platform.helpers.SettingsConstants;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import org.junit.Rule;
import org.junit.Test;

public class DisplaySettingTest {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

    private final HelperAccessor<IAutoSettingHelper> mSettingHelper;
    private HelperAccessor<IAutoDisplaySettingsHelper> mDisplaySettingsHelper;

    public DisplaySettingTest() {
        mSettingHelper = new HelperAccessor<>(IAutoSettingHelper.class);
        mDisplaySettingsHelper = new HelperAccessor<>(IAutoDisplaySettingsHelper.class);
    }

    @Test
    public void testBrightnessIncrease() {
        mSettingHelper.get().openSetting(SettingsConstants.DISPLAY_SETTINGS);
        assertTrue(
                "Display Setting did not open",
                mSettingHelper.get().checkMenuExists("Brightness level"));

        int lowBrightness = mSettingHelper.get().setBrightness(0.1f);

        // Increase the screen brightness
        int highBrightness = mSettingHelper.get().setBrightness(0.9f);

        // Verify that the screen brightness has changed.
        assertTrue(
                "Brightness was not increased (from "
                        + lowBrightness
                        + " to "
                        + highBrightness
                        + ")",
                lowBrightness < highBrightness);
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testAdaptiveBrightnessDefaultValue() {
        mSettingHelper.get().openSetting(SettingsConstants.DISPLAY_SETTINGS);
        assertTrue(
                "Display Setting did not open",
                mSettingHelper.get().checkMenuExists("Adaptive brightness"));

        // Verify that Adaptive Brightness is not enabled.
        assertFalse(
                "Adaptive Brightness was enabled, when it should be disabled by default.",
                mDisplaySettingsHelper.get().isAdaptiveBrightnessEnabled());
    }

    @Test
    public void testAdaptiveBrightnessToggle() {
        mSettingHelper.get().openSetting(SettingsConstants.DISPLAY_SETTINGS);
        assertTrue(
                "Display Setting did not open",
                mSettingHelper.get().checkMenuExists("Adaptive brightness"));

        // Verify that Adaptive Brightness can be toggled.
        boolean startSetting = mDisplaySettingsHelper.get().isAdaptiveBrightnessEnabled();
        mDisplaySettingsHelper.get().toggleAdaptiveBrightness();
        boolean endSetting = mDisplaySettingsHelper.get().isAdaptiveBrightnessEnabled();

        assertFalse(
                String.format(
                        "Adaptive Brightness value did not change after toggle;"
                                + "Value started at %b and ended at %b ",
                        startSetting, endSetting),
                startSetting == endSetting);
    }
}
