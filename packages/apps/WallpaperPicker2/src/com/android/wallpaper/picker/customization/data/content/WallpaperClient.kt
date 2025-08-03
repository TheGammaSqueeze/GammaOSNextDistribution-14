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

package com.android.wallpaper.picker.customization.data.content

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.android.wallpaper.module.logging.UserEventLogger.SetWallpaperEntryPoint
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.LiveWallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.StaticWallpaperModel
import com.android.wallpaper.picker.preview.shared.model.FullPreviewCropModel
import java.io.InputStream
import kotlinx.coroutines.flow.Flow

/** Defines interface for classes that can interact with the Wallpaper API. */
interface WallpaperClient {

    /** Lists the most recent wallpapers. The first one is the most recent (current) wallpaper. */
    fun recentWallpapers(
        destination: WallpaperDestination,
        limit: Int,
    ): Flow<List<WallpaperModel>>

    /**
     * Asynchronously sets a static wallpaper.
     *
     * @param setWallpaperEntryPoint The entry point where we set the wallpaper from.
     * @param destination The screen to set the wallpaper on.
     * @param wallpaperModel The wallpaper model of the wallpaper.
     * @param bitmap The bitmap of the static wallpaper. Note that the bitmap should be the
     *   original, full-size bitmap.
     * @param wallpaperSize raw wallpaper size
     * @param fullPreviewCropModels full preview crop info for each dimension that user has cropped
     */
    suspend fun setStaticWallpaper(
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        destination: WallpaperDestination,
        wallpaperModel: StaticWallpaperModel,
        inputStream: InputStream?,
        bitmap: Bitmap,
        wallpaperSize: Point,
        fullPreviewCropModels: Map<Point, FullPreviewCropModel>?,
    )

    /**
     * Asynchronously sets a live wallpaper.
     *
     * @param setWallpaperEntryPoint The entry point where we set the wallpaper from.
     * @param destination The screen to set the wallpaper on.
     * @param wallpaperModel The wallpaper model of the wallpaper.
     */
    suspend fun setLiveWallpaper(
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        destination: WallpaperDestination,
        wallpaperModel: LiveWallpaperModel,
    )

    /**
     * Asynchronously sets a recent wallpaper selected from the wallpaper quick switcher. The recent
     * wallpaper must have a wallpaper ID.
     *
     * @param setWallpaperEntryPoint The entry point where we set the wallpaper from.
     * @param destination The screen to set the wallpaper on.
     * @param wallpaperId The ID of the wallpaper to set.
     * @param onDone A callback to invoke when setting is done.
     */
    suspend fun setRecentWallpaper(
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        destination: WallpaperDestination,
        wallpaperId: String,
        onDone: () -> Unit,
    )

    /** Returns a thumbnail for the wallpaper with the given ID and destination. */
    suspend fun loadThumbnail(wallpaperId: String, destination: WallpaperDestination): Bitmap?

    /** Returns whether the recent wallpapers provider is available. */
    fun areRecentsAvailable(): Boolean

    fun getCurrentCropHints(
        displaySizes: List<Point>,
        @WallpaperManager.SetWallpaperFlags which: Int
    ): Map<Point, Rect>?

    /** Returns the wallpaper colors for preview a bitmap with a set of crop hints */
    suspend fun getWallpaperColors(bitmap: Bitmap, cropHints: Map<Point, Rect>?): WallpaperColors?
}
