/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.customization.model.color;

import android.app.WallpaperColors;

import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import com.android.customization.model.CustomizationManager.OptionsFetchedListener;

/**
 * Interface for a class that can retrieve Colors from the system.
 */
public interface ColorOptionsProvider {

    /**
     * Extra setting indicating the source of the color overlays (it can be one of
     * COLOR_SOURCE_PRESET, COLOR_SOURCE_HOME or COLOR_SOURCE_LOCK)
     */
    String OVERLAY_COLOR_SOURCE = "android.theme.customization.color_source";

    /**
     * Extra setting indicating the style of the color overlays (it can be one of
     * {@link com.android.systemui.monet.Style}).
     */
    String OVERLAY_THEME_STYLE = "android.theme.customization.theme_style";

    /**
     * Users selected color option, its value starts from 1 (which means first option).
     */
    String OVERLAY_COLOR_INDEX = "android.theme.customization.color_index";

    /**
     * Users selected color from both home and lock screen.
     * Example value: 0 means home or lock screen, 1 means both.
     */
    String OVERLAY_COLOR_BOTH = "android.theme.customization.color_both";

    String COLOR_SOURCE_PRESET = "preset";
    String COLOR_SOURCE_HOME = "home_wallpaper";
    String COLOR_SOURCE_LOCK = "lock_wallpaper";

    @StringDef({COLOR_SOURCE_PRESET, COLOR_SOURCE_HOME, COLOR_SOURCE_LOCK})
    @interface ColorSource{}


    /**
     * Returns whether themes are available in the current setup.
     */
    boolean isAvailable();

    /**
     * Retrieve the available themes.
     * @param callback called when the themes have been retrieved (or immediately if cached)
     * @param reload whether to reload themes if they're cached.
     * @param homeWallpaperColors to get seed colors from
     * @param lockWallpaperColors WallpaperColors from the lockscreen wallpaper to get seeds from,
     *                            if different than homeWallpaperColors
     */
    void fetch(OptionsFetchedListener<ColorOption> callback, boolean reload,
            @Nullable WallpaperColors homeWallpaperColors,
            @Nullable WallpaperColors lockWallpaperColors
    );
}
