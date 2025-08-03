/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wallpaper.picker.data

/**
 * Represents the model class that would be used for instantiating any type of wallpaper in the
 * picker. Any wallpaper should be of type LiveWallpaper or StaticWallpaperModel and depending on
 * the specific type of wallpaper, the individual fields could be null or not null.
 */
sealed class WallpaperModel {

    /**
     * All [WallpaperModel] data classes contain commonWallpaperData property which contains common
     * data amongst all [WallpaperModel] classes.
     */
    abstract val commonWallpaperData: CommonWallpaperData

    data class LiveWallpaperModel(
        override val commonWallpaperData: CommonWallpaperData,
        val liveWallpaperData: LiveWallpaperData,
        val creativeWallpaperData: CreativeWallpaperData?,
        val internalLiveWallpaperData: InternalLiveWallpaperData?
    ) : WallpaperModel()

    data class StaticWallpaperModel(
        override val commonWallpaperData: CommonWallpaperData,
        val staticWallpaperData: StaticWallpaperData,
        val imageWallpaperData: ImageWallpaperData?,
        val networkWallpaperData: NetworkWallpaperData?,
        val downloadableWallpaperData: DownloadableWallpaperData?,
    ) : WallpaperModel()
}
