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

package com.android.car.qc.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.qc.R;

/**
 * Utility class used by {@link QCTileView} and {@link QCRowView}
 */
public class QCViewUtils {

    /**
     * Create a return a Quick Control toggle icon - used for tiles and action toggles.
     */
    public static Drawable getToggleIcon(@NonNull Context context, @Nullable Icon icon,
            boolean available) {
        Drawable defaultToggleBackground = context.getDrawable(R.drawable.qc_toggle_background);
        Drawable unavailableToggleBackground = context.getDrawable(
                R.drawable.qc_toggle_unavailable_background);
        int toggleForegroundIconInset = context.getResources()
                .getDimensionPixelSize(R.dimen.qc_toggle_foreground_icon_inset);

        Drawable background = available
                ? defaultToggleBackground.getConstantState().newDrawable().mutate()
                : unavailableToggleBackground.getConstantState().newDrawable().mutate();
        if (icon == null) {
            return background;
        }

        Drawable iconDrawable = icon.loadDrawable(context);
        if (iconDrawable == null) {
            return background;
        }

        if (!available) {
            int unavailableToggleIconTint = context.getColor(R.color.qc_toggle_unavailable_color);
            iconDrawable.setTint(unavailableToggleIconTint);
        } else {
            ColorStateList defaultToggleIconTint = context.getColorStateList(
                    R.color.qc_toggle_icon_fill_color);
            iconDrawable.setTintList(defaultToggleIconTint);
        }

        Drawable[] layers = {background, iconDrawable};
        LayerDrawable drawable = new LayerDrawable(layers);
        drawable.setLayerInsetRelative(/* index= */ 1, toggleForegroundIconInset,
                toggleForegroundIconInset, toggleForegroundIconInset,
                toggleForegroundIconInset);
        return drawable;
    }
}
