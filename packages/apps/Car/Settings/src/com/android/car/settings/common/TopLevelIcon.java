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

package com.android.car.settings.common;

import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_ICON_BACKGROUND_ARGB;
import static com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_ICON_BACKGROUND_HINT;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;

import androidx.annotation.ColorInt;
import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.internal.graphics.ColorUtils;
import com.android.settingslib.utils.ColorUtil;

/**
 * Top-level icon that can set background color.
 *
 * Adapted from {@link com.android.settingslib.widget.AdaptiveIcon}
 */
public class TopLevelIcon extends LayerDrawable {
    private static final Logger LOG = new Logger("TopLevelIcon");

    private static final int BACKGROUND_INDEX = 0;
    private static final int FOREGROUND_INDEX = 1;

    private Context mContext;
    private AdaptiveConstantState mAdaptiveConstantState;
    private boolean mAlwaysDefaultColor;
    @VisibleForTesting
    ColorStateList mBackgroundColorStateList;

    public TopLevelIcon(Context context, Drawable foreground) {
        this(context, foreground, R.dimen.top_level_foreground_icon_inset);
    }

    public TopLevelIcon(Context context, Drawable foreground, int insetResId) {
        super(new Drawable[]{
                new TopLevelIconShapeDrawable(context.getResources()),
                foreground
        });
        mContext = context;
        int insetPx = context.getResources().getDimensionPixelSize(insetResId);
        setLayerInset(FOREGROUND_INDEX , insetPx, insetPx, insetPx, insetPx);
        mAdaptiveConstantState = new AdaptiveConstantState(context, foreground);
        mAlwaysDefaultColor = context.getResources().getBoolean(
                R.bool.config_top_level_injection_background_always_use_default);
    }

    /**
     *  Sets background color based on injected metaData.
     */
    public void setBackgroundColor(Context context, Bundle metaData, String packageName) {
        try {
            if (metaData != null && packageName != null && !mAlwaysDefaultColor) {
                // Load from bg.argb first
                int bgColor = metaData.getInt(META_DATA_PREFERENCE_ICON_BACKGROUND_ARGB,
                        /* defaultValue= */ 0);
                // If not found, load from bg.hint
                if (bgColor == 0) {
                    int colorRes = metaData.getInt(META_DATA_PREFERENCE_ICON_BACKGROUND_HINT,
                            /* defaultValue= */ 0);
                    if (colorRes != 0) {
                        bgColor = context.getPackageManager()
                                .getResourcesForApplication(packageName)
                                .getColor(colorRes, /* theme= */ null);
                    }
                }
                // If a color was found, use it.
                if (bgColor != 0) {
                    setBackgroundColor(bgColor);
                    return;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            LOG.e("Failed to set background color for " + packageName);
        }
        setBackgroundColor(context.getColor(R.color.top_level_injected_default_background));
    }

    /**
     * Sets background color by {@code color}.
     */
    public void setBackgroundColor(@ColorInt int color) {
        mBackgroundColorStateList = createBackgroundColorStateList(color);
        getDrawable(BACKGROUND_INDEX).setTintList(mBackgroundColorStateList);
        mAdaptiveConstantState.mColor = color;
    }

    @Override
    public ConstantState getConstantState() {
        return mAdaptiveConstantState;
    }

    private ColorStateList createBackgroundColorStateList(@ColorInt int color) {
        return new ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_enabled}, // disabled state
                        new int[]{R.attr.state_ux_restricted}, // ux restricted state
                        new int[]{} // default state
                },
                new int[]{
                        getDisabledAlphaColor(color),
                        getDisabledAlphaColor(color),
                        color
                }
        );
    }

    @ColorInt
    private int getDisabledAlphaColor(@ColorInt int color) {
        return ColorUtils.setAlphaComponent(color,
                (int) (ColorUtil.getDisabledAlpha(mContext) * 255));
    }

    @VisibleForTesting
    static class AdaptiveConstantState extends ConstantState {
        Context mContext;
        Drawable mDrawable;
        int mColor;

        AdaptiveConstantState(Context context, Drawable drawable) {
            this.mContext = context;
            this.mDrawable = drawable;
        }

        @Override
        public Drawable newDrawable() {
            TopLevelIcon icon = new TopLevelIcon(mContext, mDrawable);
            icon.setBackgroundColor(mColor);

            return icon;
        }

        @Override
        public int getChangingConfigurations() {
            return 0;
        }
    }
}
