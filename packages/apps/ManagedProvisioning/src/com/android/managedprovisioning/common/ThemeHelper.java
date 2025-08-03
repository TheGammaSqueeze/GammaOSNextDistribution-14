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

import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;

import static com.android.managedprovisioning.provisioning.Constants.FLAG_ENABLE_LIGHT_DARK_MODE;

import static com.google.android.setupdesign.util.ThemeHelper.trySetDynamicColor;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.webkit.WebSettings;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.R;
import com.google.android.setupdesign.util.ThemeResolver;

/**
 * Helper with utility methods to manage the ManagedProvisioning theme and night mode.
 */
public class ThemeHelper {
    private static final String SYSTEM_PROPERTY_SETUPWIZARD_THEME =
            SystemProperties.get("setupwizard.theme");

    private final NightModeChecker mNightModeChecker;
    private final SetupWizardBridge mSetupWizardBridge;
    private final AnimationDynamicColorsHelper mAnimationDynamicColorsHelper;

    public ThemeHelper(NightModeChecker nightModeChecker, SetupWizardBridge setupWizardBridge) {
        mNightModeChecker = requireNonNull(nightModeChecker);
        mSetupWizardBridge = requireNonNull(setupWizardBridge);
        // TODO(b/190182035): Tidy up tests after adding dependency injection support
        mAnimationDynamicColorsHelper = new AnimationDynamicColorsHelper();
    }

    /**
     * Infers the correct theme resource id.
     */
    public int inferThemeResId(Context context, Intent intent) {
        requireNonNull(context);
        requireNonNull(intent);
        String themeName = getDefaultThemeName(context, intent);
        int defaultTheme = mSetupWizardBridge.isSetupWizardDayNightEnabled(context)
                    ? R.style.SudThemeGlifV4_DayNight
                    : R.style.SudThemeGlifV4_Light;
        return mSetupWizardBridge
                .resolveTheme(defaultTheme, themeName, shouldSuppressDayNight(context));
    }

    /**
     * Sets up theme-specific colors. Must be called after {@link
     * #inferThemeResId(Context, Intent)}.
     */
    public void setupDynamicColors(Context context) {
        requireNonNull(context);
        trySetDynamicColor(context);
    }

    /**
     * Returns the appropriate day or night mode, depending on the setup wizard flags.
     *
     * @return {@link AppCompatDelegate#MODE_NIGHT_YES} or {@link AppCompatDelegate#MODE_NIGHT_NO}
     */
    public int getDefaultNightMode(Context context, Intent intent) {
        requireNonNull(context);
        if (TextUtils.isEmpty(getProvidedTheme(intent))) {
            return isSystemNightMode(context)
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (shouldSuppressDayNight(context)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (isSystemNightMode(context)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_NO;
    }

    /**
     * Forces the web pages shown by the {@link android.webkit.WebView} which has the
     * supplied {@code webSettings} to have the appropriate day/night mode depending
     * on the app theme.
     */
    public void applyWebSettingsDayNight(Context context, WebSettings webSettings, Intent intent) {
        requireNonNull(context);
        requireNonNull(webSettings);
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            return;
        }
        WebSettingsCompat.setForceDark(webSettings, getForceDarkMode(context, intent));
    }

    /**
     * Updates the relevant animation with theme-specific colors.
     * <p>If the supplied {@link LottieAnimationView} does not have a loaded {@link
     * LottieComposition}, it asynchronously waits for it to load and then applies the colors.
     */
    public void setupAnimationDynamicColors(
            Context context, LottieAnimationView lottieAnimationView, Intent intent) {
        mAnimationDynamicColorsHelper.setupAnimationDynamicColors(
                new LottieAnimationWrapper(lottieAnimationView),
                getDefaultNightMode(context, intent));
    }

    private int getForceDarkMode(Context context, Intent intent) {
        if (getDefaultNightMode(context, intent) == AppCompatDelegate.MODE_NIGHT_YES) {
            return WebSettingsCompat.FORCE_DARK_ON;
        } else {
            return WebSettingsCompat.FORCE_DARK_OFF;
        }
    }

    private boolean shouldSuppressDayNight(Context context) {
        if (!FLAG_ENABLE_LIGHT_DARK_MODE) {
            return true;
        }
        return !mSetupWizardBridge.isSetupWizardDayNightEnabled(context);
    }

    private boolean isSystemNightMode(Context context) {
        return mNightModeChecker.isSystemNightMode(context);
    }

    private String getDefaultThemeName(Context context, Intent intent) {
        String theme = getProvidedTheme(intent);
        if (TextUtils.isEmpty(theme)) {
            if (isSystemNightMode(context)) {
                theme = com.google.android.setupdesign.util.ThemeHelper.THEME_GLIF_V4;
            } else {
                theme = com.google.android.setupdesign.util.ThemeHelper.THEME_GLIF_V4_LIGHT;
            }
        }
        return theme;
    }

    private String getProvidedTheme(Intent intent) {
        String theme = intent.getStringExtra(WizardManagerHelper.EXTRA_THEME);
        if (TextUtils.isEmpty(theme)) {
            return mSetupWizardBridge.getSystemPropertySetupWizardTheme();
        }
        return theme;
    }

    interface SetupWizardBridge {
        boolean isSetupWizardDayNightEnabled(Context context);

        String getSystemPropertySetupWizardTheme();

        int resolveTheme(int defaultTheme, String themeName, boolean suppressDayNight);
    }

    interface NightModeChecker {
        boolean isSystemNightMode(Context context);
    }

    /**
     * Default implementation of {@link NightModeChecker}.
     */
    public static class DefaultNightModeChecker implements NightModeChecker {
        @Override
        public boolean isSystemNightMode(Context context) {
            return (context.getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK)
                    == UI_MODE_NIGHT_YES;
        }
    }

    /**
     * Default implementation of {@link SetupWizardBridge}.
     */
    public static class DefaultSetupWizardBridge implements SetupWizardBridge {
        @Override
        public boolean isSetupWizardDayNightEnabled(Context context) {
            return com.google.android.setupdesign.util.ThemeHelper
                    .isSetupWizardDayNightEnabled(context);
        }

        @Override
        public String getSystemPropertySetupWizardTheme() {
            return SYSTEM_PROPERTY_SETUPWIZARD_THEME;
        }

        @Override
        public int resolveTheme(int defaultTheme, String themeName, boolean suppressDayNight) {
            ThemeResolver themeResolver = new ThemeResolver.Builder(ThemeResolver.getDefault())
                    .setDefaultTheme(defaultTheme)
                    .setUseDayNight(true)
                    .build();
            return themeResolver.resolve(
                    themeName,
                    suppressDayNight);
        }
    }
}
