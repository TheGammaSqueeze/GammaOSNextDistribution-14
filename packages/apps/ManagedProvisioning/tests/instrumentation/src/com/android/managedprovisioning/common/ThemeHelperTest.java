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

package com.android.managedprovisioning.common;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

import static com.android.managedprovisioning.provisioning.Constants.FLAG_ENABLE_LIGHT_DARK_MODE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;

@SmallTest
public class ThemeHelperTest {
    private static final String THEME_TEST_VALUE = "glif_v3_light";
    private static final Intent INTENT_WITH_THEME =
            new Intent().putExtra(WizardManagerHelper.EXTRA_THEME, THEME_TEST_VALUE);
    private static final Intent INTENT_NO_THEME = new Intent();

    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private boolean mInitialFeatureFlagValue;
    private ThemeHelper.SetupWizardBridge mSetupWizardBridge;

    @Before
    public void setUp() {
        mInitialFeatureFlagValue = FLAG_ENABLE_LIGHT_DARK_MODE;
    }

    @After
    public void tearDown() {
        FLAG_ENABLE_LIGHT_DARK_MODE = mInitialFeatureFlagValue;
    }

    @Test
    public void inferThemeResId_themeProvidedAsExtra_suwDayNightEnabled_themesMatch() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper = createThemeHelperWithDayNightEnabled(true);
        Intent intent = new Intent()
                .putExtra(WizardManagerHelper.EXTRA_THEME, THEME_TEST_VALUE);
        int expectedResId = mSetupWizardBridge.resolveTheme(
                R.style.SudThemeGlifV4_DayNight,
                THEME_TEST_VALUE,
                /* suppressDayNight= */ false);

        assertThat(themeHelper.inferThemeResId(mContext, intent)).isEqualTo(expectedResId);
    }

    @Test
    public void inferThemeResId_themeProvidedAsExtra_suwDayNightDisabled_themesMatch() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper = createThemeHelperWithDayModeValues();
        Intent intent = new Intent()
                .putExtra(WizardManagerHelper.EXTRA_THEME, THEME_TEST_VALUE);
        int expectedResId = mSetupWizardBridge.resolveTheme(
                R.style.SudThemeGlifV4_Light,
                THEME_TEST_VALUE,
                /* suppressDayNight= */ true);

        assertThat(themeHelper.inferThemeResId(mContext, intent)).isEqualTo(expectedResId);
    }

    @Test
    public void
            inferThemeResId_themeProvidedAsExtra_suwDayNightEnabled_flagDisabled_themesMatch() {
        FLAG_ENABLE_LIGHT_DARK_MODE = false;
        ThemeHelper themeHelper = createThemeHelperWithDayNightEnabled(true);
        Intent intent = new Intent()
                .putExtra(WizardManagerHelper.EXTRA_THEME, THEME_TEST_VALUE);
        int expectedResId = mSetupWizardBridge.resolveTheme(
                R.style.SudThemeGlifV4_DayNight,
                THEME_TEST_VALUE,
                /* suppressDayNight= */ true);

        assertThat(themeHelper.inferThemeResId(mContext, intent)).isEqualTo(expectedResId);
    }

    @Test
    public void
            inferThemeResId_themeProvidedAsExtra_suwDayNightDisabled_flagDisabled_themesMatch() {
        FLAG_ENABLE_LIGHT_DARK_MODE = false;
        ThemeHelper themeHelper = createThemeHelperWithDayModeValues();
        Intent intent = new Intent()
                .putExtra(WizardManagerHelper.EXTRA_THEME, THEME_TEST_VALUE);
        int expectedResId = mSetupWizardBridge.resolveTheme(
                R.style.SudThemeGlifV4_Light,
                THEME_TEST_VALUE,
                /* suppressDayNight= */ true);

        assertThat(themeHelper.inferThemeResId(mContext, intent)).isEqualTo(expectedResId);
    }

    @Test
    public void inferThemeResId_systemPropertyThemeUsed_suwDayNightEnabled_themesMatch() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper =
                createThemeHelperWithDayNightEnabledAndSystemPropertyTheme(
                        true, THEME_TEST_VALUE);
        int expectedResId = mSetupWizardBridge.resolveTheme(
                R.style.SudThemeGlifV4_DayNight,
                THEME_TEST_VALUE,
                /* suppressDayNight= */ false);

        assertThat(themeHelper.inferThemeResId(mContext, new Intent())).isEqualTo(expectedResId);
    }

    @Test
    public void inferThemeResId_glifLightThemeUsed_suwDayNightEnabled_themesMatch() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper =
                createThemeHelperWithDayNightEnabled(true);
        int expectedResId = mSetupWizardBridge.resolveTheme(
                R.style.SudThemeGlifV4_DayNight,
                com.google.android.setupdesign.util.ThemeHelper.THEME_GLIF_V4,
                /* suppressDayNight= */ false);

        assertThat(themeHelper.inferThemeResId(mContext, new Intent())).isEqualTo(expectedResId);
    }

    @Test
    public void inferThemeResId_suppressDayNight_systemNightMode_noSetupWizard_isDarkMode() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper =
                createThemeHelperWithDayNightAndSystemNightModeEnabled(
                        /* setupWizardDayNightEnabled= */ false,
                        /* isSystemNightMode= */ true);
        int expectedResId = mSetupWizardBridge.resolveTheme(
                R.style.SudThemeGlifV4_Light,
                com.google.android.setupdesign.util.ThemeHelper.THEME_GLIF_V4,
                /* suppressDayNight= */ true);

        assertThat(themeHelper.inferThemeResId(mContext, new Intent())).isEqualTo(expectedResId);
    }

    @Test
    public void inferThemeResId_suppressDayNight_systemNightMode_noSetupWizard_isLightMode() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper =
                createThemeHelperWithDayNightAndSystemNightModeEnabled(
                        /* setupWizardDayNightEnabled= */ false,
                        /* isSystemNightMode= */ false);
        int expectedResId = mSetupWizardBridge.resolveTheme(
                R.style.SudThemeGlifV4_Light,
                com.google.android.setupdesign.util.ThemeHelper.THEME_GLIF_V4_LIGHT,
                /* suppressDayNight= */ true);

        assertThat(themeHelper.inferThemeResId(mContext, new Intent())).isEqualTo(expectedResId);
    }

    @Test
    public void getDefaultNightMode_returnsYes() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper = createThemeHelperForNightMode();
        assertThat(themeHelper.getDefaultNightMode(mContext, INTENT_WITH_THEME))
                .isEqualTo(MODE_NIGHT_YES);
    }

    @Test
    public void getDefaultNightMode_featureFlagDisabled_returnsNo() {
        FLAG_ENABLE_LIGHT_DARK_MODE = false;
        ThemeHelper themeHelper = createThemeHelperForNightMode();

        assertThat(themeHelper.getDefaultNightMode(mContext, INTENT_WITH_THEME))
                .isEqualTo(MODE_NIGHT_NO);
    }

    @Test
    public void getDefaultNightMode_setupWizardDayNightDisabled_returnsNo() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper = createThemeHelperWithDayNightEnabled(false);

        assertThat(themeHelper.getDefaultNightMode(mContext, INTENT_WITH_THEME))
                .isEqualTo(MODE_NIGHT_NO);
    }

    @Test
    public void getDefaultNightMode_systemNightModeFalse_returnsNo() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper = createThemeHelperWithSystemNightModeEnabled(false);

        assertThat(themeHelper.getDefaultNightMode(mContext, INTENT_WITH_THEME))
                .isEqualTo(MODE_NIGHT_NO);
    }

    @Test
    public void getDefaultNightMode_systemNightModeFalse_noThemeExtra_returnsNo() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper = createThemeHelperWithSystemNightModeEnabled(false);

        assertThat(themeHelper.getDefaultNightMode(mContext, INTENT_NO_THEME))
                .isEqualTo(MODE_NIGHT_NO);
    }

    @Test
    public void getDefaultNightMode_systemNightModeTrue_noThemeExtra_returnsYes() {
        FLAG_ENABLE_LIGHT_DARK_MODE = true;
        ThemeHelper themeHelper = createThemeHelperWithSystemNightModeEnabled(true);

        assertThat(themeHelper.getDefaultNightMode(mContext, INTENT_NO_THEME))
                .isEqualTo(MODE_NIGHT_YES);
    }

    private ThemeHelper createThemeHelperForNightMode() {
        return createThemeHelper(
                /* setupWizardDayNightEnabled= */ true,
                /* systemPropertySetupWizardTheme= */ null,
                /* isSystemNightMode= */ true);
    }

    private ThemeHelper createThemeHelperWithDayNightEnabled(boolean setupWizardDayNightEnabled) {
        return createThemeHelper(
                setupWizardDayNightEnabled,
                /* systemPropertySetupWizardTheme= */ null,
                /* isSystemNightMode= */ true);
    }

    private ThemeHelper createThemeHelperWithDayNightAndSystemNightModeEnabled(
            boolean setupWizardDayNightEnabled,
            boolean isSystemNightMode) {
        return createThemeHelper(
                setupWizardDayNightEnabled,
                /* systemPropertySetupWizardTheme= */ null,
                /* isSystemNightMode= */ isSystemNightMode);
    }

    private ThemeHelper createThemeHelperWithSystemNightModeEnabled(boolean isSystemNightMode) {
        return createThemeHelper(
                /* setupWizardDayNightEnabled= */ false,
                /* systemPropertySetupWizardTheme= */ null,
                isSystemNightMode);
    }

    private ThemeHelper createThemeHelperWithDayModeValues() {
        return createThemeHelper(
                /* setupWizardDayNightEnabled= */ false,
                /* systemPropertySetupWizardTheme= */ null,
                /* isSystemNightMode= */ false);
    }

    private ThemeHelper createThemeHelperWithDayNightEnabledAndSystemPropertyTheme(
            boolean setupWizardDayNightEnabled, String systemPropertySetupWizardTheme) {
        return createThemeHelper(
                setupWizardDayNightEnabled,
                systemPropertySetupWizardTheme,
                /* isSystemNightMode= */ false);
    }

    private ThemeHelper createThemeHelper(
            boolean setupWizardDayNightEnabled,
            String systemPropertySetupWizardTheme,
            boolean isSystemNightMode) {
        mSetupWizardBridge = new ThemeHelper.SetupWizardBridge() {
            @Override
            public boolean isSetupWizardDayNightEnabled(Context context) {
                return setupWizardDayNightEnabled;
            }

            @Override
            public String getSystemPropertySetupWizardTheme() {
                return systemPropertySetupWizardTheme;
            }

            @Override
            public int resolveTheme(int defaultTheme, String themeName, boolean suppressDayNight) {
                return Objects.hash(defaultTheme, themeName, suppressDayNight);
            }
        };
        return new ThemeHelper(context -> isSystemNightMode, mSetupWizardBridge);
    }
}
