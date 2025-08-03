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
 *
 */

package com.android.wallpaper.picker.customization.shared.model

import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.app.WallpaperManager.SetWallpaperFlags
import com.android.wallpaper.module.WallpaperPersister.DEST_BOTH
import com.android.wallpaper.module.WallpaperPersister.DEST_HOME_SCREEN
import com.android.wallpaper.module.WallpaperPersister.DEST_LOCK_SCREEN
import com.android.wallpaper.module.WallpaperPersister.Destination

/** Enumerates all known wallpaper destinations. */
enum class WallpaperDestination {
    /** Both [HOME] and [LOCK] destinations. */
    BOTH,
    /** The home screen wallpaper. */
    HOME,
    /** The lock screen wallpaper. */
    LOCK;

    companion object {
        fun fromFlags(@SetWallpaperFlags flags: Int): WallpaperDestination {
            return when (flags) {
                FLAG_SYSTEM or FLAG_LOCK -> BOTH
                FLAG_SYSTEM -> HOME
                FLAG_LOCK -> LOCK
                else -> throw IllegalArgumentException("Bad @SetWallpaperFlags value $flags")
            }
        }

        @Destination
        fun WallpaperDestination.toDestinationInt(): Int {
            return when (this) {
                BOTH -> DEST_BOTH
                HOME -> DEST_HOME_SCREEN
                LOCK -> DEST_LOCK_SCREEN
            }
        }
    }
}
