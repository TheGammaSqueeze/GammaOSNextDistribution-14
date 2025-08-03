/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.isolatedsplitapp;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.ContextThemeWrapper;

/**
 * A helper class to retrieve theme values of Theme_Base, Theme_Feature_A, Theme_Feature_B or
 * Theme_Feature_C.
 */
public class TestTheme {

    public static final String THEME_FEATURE_A =
            "com.android.cts.isolatedsplitapp.feature_a:style/Theme_Feature_A";
    public static final String THEME_FEATURE_B =
            "com.android.cts.isolatedsplitapp.feature_b:style/Theme_Feature_B";
    public static final String THEME_FEATURE_C =
            "com.android.cts.isolatedsplitapp.feature_c:style/Theme_Feature_C";

    public static final int COLOR_BLACK = 0xFF000000;
    public static final int COLOR_RED = 0xFFFF0000;
    public static final int COLOR_GREEN = 0xFF00FF00;
    public static final int COLOR_BLUE = 0xFF0000FF;
    public static final int COLOR_GRAY = 0xFF888888;
    public static final int COLOR_LTGRAY = 0xFF444444;
    public static final int COLOR_DKGRAY = 0xFFCCCCCC;
    public static final int COLOR_YELLOW = 0xFFFFFF00;
    public static final int COLOR_PURPLE = 0xFF800080;
    public static final int COLOR_PINK = 0xFFFFC0CB;
    public static final int COLOR_ORANGE = 0xFFFFA500;
    public static final int COLOR_LINEN = 0xFFFAF0E6;
    public static final int COLOR_MINTCREAM = 0xFFF5FFFA;
    public static final int COLOR_CYAN = 0xFF00FFFF;
    public static final int COLOR_MAGENTA = 0xFFFF00FF;
    public static final int COLOR_MAROON = 0xFF800000;
    public static final int COLOR_NAVY = 0xFF000080;
    public static final int COLOR_OLIVE = 0xFF808000;

    private static final String ATTR_THEME_PRIMARY_COLOR =
            "com.android.cts.isolatedsplitapp.feature_a:attr/themePrimaryColor";
    private static final String ATTR_THEME_SECONDARY_COLOR =
            "com.android.cts.isolatedsplitapp.feature_b:attr/themeSecondaryColor";
    private static final String ATTR_THEME_TERTIARY_COLOR =
            "com.android.cts.isolatedsplitapp.feature_c:attr/themeTertiaryColor";

    /** {@link R.attr.themeName} */
    public String mName;

    /** {@link R.attr.themeBaseColor} */
    public int mBaseColor;

    /** {@link #ATTR_THEME_PRIMARY_COLOR} */
    public int mPrimaryColor;

    /** {@link #ATTR_THEME_SECONDARY_COLOR} */
    public int mSecondaryColor;

    /** {@link #ATTR_THEME_TERTIARY_COLOR} */
    public int mTertiaryColor;

    /** {#link android.R.attr.colorBackground} */
    public int mColorBackground;

    /** {#link android.R.attr.navigationBarColor} */
    public int mNavigationBarColor;

    /** {#link android.R.attr.statusBarColor} */
    public int mStatusBarColor;

    /** {#link android.R.attr.windowBackground} */
    public int mWindowBackground;

    public TestTheme(Context context, String nameOfIdentifier) {
        setTheme(context, nameOfIdentifier);
    }

    public TestTheme(Context context, int themeId) {
        setTheme(context, themeId);
    }

    public void assertThemeBaseValues() {
        assertThat(mName).isEqualTo("Base Theme");
        assertThat(mBaseColor).isEqualTo(COLOR_BLACK);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_RED);
        assertThat(mStatusBarColor).isEqualTo(COLOR_GREEN);
        assertThat(mWindowBackground).isEqualTo(mBaseColor);
    }

    public void assertThemeBaseValues_pl() {
        assertThat(mName).isEqualTo("Base Theme Polish");
        assertThat(mBaseColor).isEqualTo(COLOR_GREEN);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_BLACK);
        assertThat(mStatusBarColor).isEqualTo(COLOR_RED);
        assertThat(mWindowBackground).isEqualTo(mBaseColor);
    }

    public void assertThemeFeatureAValues() {
        assertThat(mName).isEqualTo("Feature A Theme");
        assertThat(mBaseColor).isEqualTo(COLOR_BLUE);
        assertThat(mPrimaryColor).isEqualTo(COLOR_GRAY);
        assertThat(mColorBackground).isEqualTo(COLOR_LTGRAY);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_DKGRAY);
        assertThat(mStatusBarColor).isEqualTo(COLOR_YELLOW);
        assertThat(mWindowBackground).isEqualTo(mPrimaryColor);
    }

    public void assertThemeFeatureAValues_pl() {
        assertThat(mName).isEqualTo("Feature A Theme Polish");
        assertThat(mBaseColor).isEqualTo(COLOR_YELLOW);
        assertThat(mPrimaryColor).isEqualTo(COLOR_DKGRAY);
        assertThat(mColorBackground).isEqualTo(COLOR_GRAY);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_LTGRAY);
        assertThat(mStatusBarColor).isEqualTo(COLOR_BLUE);
        assertThat(mWindowBackground).isEqualTo(mPrimaryColor);
    }

    public void assertThemeFeatureAValuesDiffRev() {
        assertThat(mName).isEqualTo("Feature A Theme Diff Revision");
        assertThat(mBaseColor).isEqualTo(COLOR_YELLOW);
        assertThat(mPrimaryColor).isEqualTo(COLOR_BLUE);
        assertThat(mColorBackground).isEqualTo(COLOR_GRAY);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_LTGRAY);
        assertThat(mStatusBarColor).isEqualTo(COLOR_DKGRAY);
        assertThat(mWindowBackground).isEqualTo(mPrimaryColor);
    }

    public void assertThemeFeatureBValues() {
        assertThat(mName).isEqualTo("Feature B Theme");
        assertThat(mBaseColor).isEqualTo(COLOR_PURPLE);
        assertThat(mSecondaryColor).isEqualTo(COLOR_PINK);
        assertThat(mColorBackground).isEqualTo(COLOR_ORANGE);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_LINEN);
        assertThat(mStatusBarColor).isEqualTo(COLOR_MINTCREAM);
        assertThat(mWindowBackground).isEqualTo(mSecondaryColor);
    }

    public void assertThemeFeatureBValues_pl() {
        assertThat(mName).isEqualTo("Feature B Theme Polish");
        assertThat(mBaseColor).isEqualTo(COLOR_MINTCREAM);
        assertThat(mSecondaryColor).isEqualTo(COLOR_LINEN);
        assertThat(mColorBackground).isEqualTo(COLOR_PINK);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_ORANGE);
        assertThat(mStatusBarColor).isEqualTo(COLOR_PURPLE);
        assertThat(mWindowBackground).isEqualTo(mSecondaryColor);
    }

    public void assertThemeFeatureCValues() {
        assertThat(mName).isEqualTo("Feature C Theme");
        assertThat(mBaseColor).isEqualTo(COLOR_CYAN);
        assertThat(mTertiaryColor).isEqualTo(COLOR_MAGENTA);
        assertThat(mColorBackground).isEqualTo(COLOR_MAROON);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_NAVY);
        assertThat(mStatusBarColor).isEqualTo(COLOR_OLIVE);
        assertThat(mWindowBackground).isEqualTo(mTertiaryColor);
    }

    public void assertThemeFeatureCValues_pl() {
        assertThat(mName).isEqualTo("Feature C Theme Polish");
        assertThat(mBaseColor).isEqualTo(COLOR_OLIVE);
        assertThat(mTertiaryColor).isEqualTo(COLOR_NAVY);
        assertThat(mColorBackground).isEqualTo(COLOR_MAGENTA);
        assertThat(mNavigationBarColor).isEqualTo(COLOR_MAROON);
        assertThat(mStatusBarColor).isEqualTo(COLOR_CYAN);
        assertThat(mWindowBackground).isEqualTo(mTertiaryColor);
    }

    private void setTheme(Context context, String nameOfIdentifier) {
        final int themeId = resolveResourceId(context , nameOfIdentifier);
        if (themeId == 0) {
            throw new IllegalArgumentException("Failed to a resource identifier for the "
                    + nameOfIdentifier);
        }
        setTheme(context, themeId);
    }

    private void setTheme(Context context, int themeId) {
        final Resources.Theme theme = new ContextThemeWrapper(context, themeId).getTheme();
        mName = getString(theme, R.attr.themeName);
        mBaseColor = getColor(theme, R.attr.themeBaseColor);
        mPrimaryColor = getColor(theme, resolveResourceId(context, ATTR_THEME_PRIMARY_COLOR));
        mSecondaryColor = getColor(theme, resolveResourceId(context, ATTR_THEME_SECONDARY_COLOR));
        mTertiaryColor = getColor(theme, resolveResourceId(context, ATTR_THEME_TERTIARY_COLOR));
        mColorBackground = getColor(theme, android.R.attr.colorBackground);
        mNavigationBarColor = getColor(theme, android.R.attr.navigationBarColor);
        mStatusBarColor = getColor(theme, android.R.attr.statusBarColor);
        mWindowBackground = getDrawableColor(theme, android.R.attr.windowBackground);
    }

    private int resolveResourceId(Context context, String nameOfIdentifier) {
        return context.getResources().getIdentifier(nameOfIdentifier, null, null);
    }

    private String getString(Resources.Theme theme, int resourceId) {
        final TypedArray ta = theme.obtainStyledAttributes(new int[] {resourceId});
        final String string = ta.getString(0);
        ta.recycle();
        return string;
    }

    private int getColor(Resources.Theme theme, int resourceId) {
        if (resourceId == 0) {
            return 0;
        }
        final TypedArray ta = theme.obtainStyledAttributes(new int[] {resourceId});
        final int color = ta.getColor(0, 0);
        ta.recycle();
        return color;
    }

    private int getDrawableColor(Resources.Theme theme, int resourceId) {
        final TypedArray ta = theme.obtainStyledAttributes(new int[] {resourceId});
        final Drawable color = ta.getDrawable(0);
        ta.recycle();
        if (!(color instanceof ColorDrawable)) {
            return 0;
        }
        return ((ColorDrawable) color).getColor();
    }
}
