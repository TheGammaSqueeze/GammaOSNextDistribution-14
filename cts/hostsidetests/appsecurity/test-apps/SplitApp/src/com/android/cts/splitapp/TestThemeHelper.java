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

package com.android.cts.splitapp;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;

/**
 * A helper class to retrieve theme values of Theme_Base and Theme_Warm and Theme_Rose.
 */
public class TestThemeHelper {

    public static final String THEME_WARM =
            "com.android.cts.splitapp.feature_warm:style/Theme_Warm";
    public static final String THEME_ROSE =
            "com.android.cts.splitapp.feature_rose:style/Theme_Rose";

    public enum ThemeColors {
        BASE,
        BASE_LT,
        WARM,
        WARM_LT,
        ROSE,
        ROSE_LT
    };

    private static final int COLOR_BLUE = 0xFF0000FF;
    private static final int COLOR_TEAL = 0xFF008080;
    private static final int COLOR_AQUA = 0xFF00FFFF;
    private static final int COLOR_BLUE_LT = 0xFFADD8E6;
    private static final int COLOR_TEAL_LT = 0xFFE0F0F0;
    private static final int COLOR_AQUA_LT = 0xFFE0FFFF;
    private static final int COLOR_RED = 0xFFFF0000;
    private static final int COLOR_YELLOW = 0xFFFFFF00;
    private static final int COLOR_RED_LT = 0xFFFFCCCB;
    private static final int COLOR_ORANGE_LT = 0xFFFED8B1;
    private static final int COLOR_PINK = 0xFFFFC0CB;
    private static final int COLOR_RUBY = 0xFFCC0080;
    private static final int COLOR_PINK_LT = 0xFFFFB6C1;
    private static final int COLOR_ROSE_LT = 0xFFFF66CC;

    private static final int[] THEME_BASE_COLORS = {COLOR_BLUE, COLOR_TEAL, COLOR_AQUA};
    private static final int[] THEME_BASE_LT_COLORS = {COLOR_BLUE_LT, COLOR_TEAL_LT, COLOR_AQUA_LT};
    private static final int[] THEME_WARM_COLORS = {COLOR_RED, COLOR_TEAL, COLOR_YELLOW};
    private static final int[] THEME_WARM_LT_COLORS =
            {COLOR_RED_LT, COLOR_ORANGE_LT, COLOR_AQUA_LT};
    private static final int[] THEME_ROSE_COLORS = {COLOR_PINK, COLOR_TEAL, COLOR_RUBY};
    private static final int[] THEME_ROSE_LT_COLORS = {COLOR_PINK_LT, COLOR_ROSE_LT, COLOR_AQUA_LT};

    /** {@link com.android.cts.splitapp.R.attr.customColor} */
    private final int mCustomColor;

    /** {#link android.R.attr.colorBackground} */
    private final int mColorBackground;

    /** {#link android.R.attr.navigationBarColor} */
    private final int mNavigationBarColor;

    /** {#link android.R.attr.statusBarColor} */
    private final int mStatusBarColor;

    /** {#link android.R.attr.windowBackground} */
    private final int mWindowBackground;

    public TestThemeHelper(Context context, int themeResId) {
        final Resources.Theme theme = new ContextThemeWrapper(context, themeResId).getTheme();
        mCustomColor = getColor(theme, R.attr.customColor);
        mColorBackground = getColor(theme, android.R.attr.colorBackground);
        mNavigationBarColor = getColor(theme, android.R.attr.navigationBarColor);
        mStatusBarColor = getColor(theme, android.R.attr.statusBarColor);
        mWindowBackground = getDrawableColor(theme, android.R.attr.windowBackground);
    }

    public void assertThemeValues(ThemeColors themeColors) {
        final int[] colors = getThemeColors(themeColors);
        assertThat(themeColors).isNotNull();
        assertThat(mCustomColor).isEqualTo(colors[0]);
        assertThat(mNavigationBarColor).isEqualTo(colors[1]);
        assertThat(mStatusBarColor).isEqualTo(colors[2]);
        assertThat(mWindowBackground).isEqualTo(mCustomColor);
    }

    private int[] getThemeColors(ThemeColors themeColors) {
        switch (themeColors) {
            case BASE: return THEME_BASE_COLORS;
            case BASE_LT: return THEME_BASE_LT_COLORS;
            case WARM: return THEME_WARM_COLORS;
            case WARM_LT: return THEME_WARM_LT_COLORS;
            case ROSE: return THEME_ROSE_COLORS;
            case ROSE_LT: return THEME_ROSE_LT_COLORS;
            default:
                break;
        }
        return null;
    }

    public void assertThemeApplied(Activity activity) {
        assertLayoutBGColor(activity, mCustomColor);

        final Window window = activity.getWindow();
        assertThat(window.getStatusBarColor()).isEqualTo(mStatusBarColor);
        assertThat(window.getNavigationBarColor()).isEqualTo(mNavigationBarColor);
        assertDrawableColor(window.getDecorView().getBackground(), mWindowBackground);

        assertTextViewBGColor(activity);
    }

    private int getColor(Resources.Theme theme, int resourceId) {
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
            fail("Can't get drawable color");
        }
        return ((ColorDrawable) color).getColor();
    }

    private void assertLayoutBGColor(Activity activity, int expected) {
        final LinearLayout layout = activity.findViewById(R.id.content);
        final Drawable background = layout.getBackground();
        assertDrawableColor(background, expected);
    }

    private void assertDrawableColor(Drawable drawable, int expected) {
        int color = 0;
        if (drawable instanceof ColorDrawable) {
            color = ((ColorDrawable) drawable).getColor();
        } else {
            fail("Can't get drawable color");
        }
        assertThat(color).isEqualTo(expected);
    }

    private void assertTextViewBGColor(Activity activity) {
        final View view = activity.findViewById(R.id.text);
        final Drawable background = view.getBackground();
        assertDrawableColor(background, mColorBackground);
    }
}
