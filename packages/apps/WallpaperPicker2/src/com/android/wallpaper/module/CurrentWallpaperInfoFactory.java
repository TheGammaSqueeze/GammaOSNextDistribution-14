/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.WallpaperPreferences.PresentationMode;

/**
 * Interface for factories which construct {@link WallpaperInfo} objects representing the device's
 * currently set wallpapers.
 */
public interface CurrentWallpaperInfoFactory {

    /**
     * Constructs WallpaperInfo object(s) to represent the current home wallpaper and optionally the
     * current lock wallpaper if device is running Android N or later and lock wallpaper is explicitly
     * set.
     *
     * @param forceRefresh Whether the factory should ignore cached copies of the WallpaperInfo(s) and
     *                     presentation mode that represent the currently-set wallpaper.
     */
    void createCurrentWallpaperInfos(Context context, boolean forceRefresh,
            WallpaperInfoCallback callback);

    /**
     * Clears cached wallpaper copies to let refresher load the latest wallpaper.
     */
    void clearCurrentWallpaperInfos();

    /**
     * Interface which clients may implement to receive current wallpaper {@link WallpaperInfo}
     * objects constructed by the factory as well as the mode which describes their current
     * presentation (i.e., daily rotation or static).
     */
    interface WallpaperInfoCallback {
        void onWallpaperInfoCreated(WallpaperInfo homeWallpaper, @Nullable WallpaperInfo lockWallpaper,
                                    @PresentationMode int presentationMode);
    }
}
