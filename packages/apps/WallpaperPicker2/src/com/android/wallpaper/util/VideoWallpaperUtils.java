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
package com.android.wallpaper.util;

import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;

/**
 * Workarounds for dealing with issues displaying video wallpaper previews until better solutions
 * are found.
 *
 * See b/268066031.
 */
public class VideoWallpaperUtils {

    /**
     * Transition time for fade-in animation.
     */
    public static final int TRANSITION_MILLIS = 250;

    /**
     * Returns true if the is a video wallpaper that requires the fade-in workaround.
     */
    public static boolean needsFadeIn(WallpaperInfo info) {
        return info instanceof LiveWallpaperInfo;
    }
}
