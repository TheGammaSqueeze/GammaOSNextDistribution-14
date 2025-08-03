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
package com.android.wallpaper.module

/** Checks the status of the home/lock screen wallpaper in the device. */
interface WallpaperStatusChecker {
    /** Returns whether a static home wallpaper is set to the device. */
    fun isHomeStaticWallpaperSet(): Boolean

    /**
     * Returns an explicit lock wallpaper is set to the device independently (i.e.,independently
     * shown under the keyguard and separate from the wallpaper shown under the user's launcher).
     */
    fun isLockWallpaperSet(): Boolean
}
