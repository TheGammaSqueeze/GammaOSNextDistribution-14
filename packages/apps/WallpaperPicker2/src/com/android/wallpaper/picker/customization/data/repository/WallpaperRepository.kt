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

package com.android.wallpaper.picker.customization.data.repository

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.LruCache
import com.android.wallpaper.module.WallpaperPreferences
import com.android.wallpaper.module.logging.UserEventLogger.SetWallpaperEntryPoint
import com.android.wallpaper.picker.customization.data.content.WallpaperClient
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.LiveWallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.StaticWallpaperModel
import com.android.wallpaper.picker.preview.shared.model.FullPreviewCropModel
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Encapsulates access to wallpaper-related data. */
class WallpaperRepository(
    private val scope: CoroutineScope,
    private val client: WallpaperClient,
    private val wallpaperPreferences: WallpaperPreferences,
    private val backgroundDispatcher: CoroutineDispatcher,
) {
    val maxOptions = MAX_OPTIONS

    private val thumbnailCache = LruCache<String, Bitmap>(maxOptions)

    /** The ID of the currently-selected wallpaper. */
    fun selectedWallpaperId(
        destination: WallpaperDestination,
    ): StateFlow<String> {
        return client
            .recentWallpapers(destination = destination, limit = 1)
            .map { previews -> currentWallpaperKey(destination, previews) }
            .flowOn(backgroundDispatcher)
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = currentWallpaperKey(destination, null)
            )
    }

    private fun currentWallpaperKey(
        destination: WallpaperDestination,
        previews: List<WallpaperModel>?,
    ): String {
        val key =
            when (destination) {
                WallpaperDestination.HOME -> wallpaperPreferences.getHomeWallpaperRecentsKey()
                WallpaperDestination.LOCK -> wallpaperPreferences.getLockWallpaperRecentsKey()
                else -> error("Unsupported destination")
            }
        return key ?: previews?.firstOrNull()?.wallpaperId ?: DEFAULT_KEY
    }

    val areRecentsAvailable: Boolean by lazy { client.areRecentsAvailable() }

    private val _selectingWallpaperId =
        MutableStateFlow<Map<WallpaperDestination, String?>>(emptyMap())
    /**
     * The ID of the wallpaper that is in the process of becoming the selected wallpaper or `null`
     * if no such transaction is currently taking place.
     */
    val selectingWallpaperId: StateFlow<Map<WallpaperDestination, String?>> =
        _selectingWallpaperId.asStateFlow()

    /** Lists the most recent wallpapers. The first one is the most recent (current) wallpaper. */
    fun recentWallpapers(
        destination: WallpaperDestination,
        limit: Int,
    ): Flow<List<WallpaperModel>> {
        return client
            .recentWallpapers(destination = destination, limit = limit)
            .flowOn(backgroundDispatcher)
    }

    /** Returns a thumbnail for the wallpaper with the given ID and destination. */
    suspend fun loadThumbnail(
        wallpaperId: String,
        lastUpdatedTimestamp: Long,
        destination: WallpaperDestination
    ): Bitmap? {
        val cacheKey = "$wallpaperId-$lastUpdatedTimestamp"
        return thumbnailCache[cacheKey]
            ?: withContext(backgroundDispatcher) {
                val thumbnail = client.loadThumbnail(wallpaperId, destination)
                if (thumbnail != null) {
                    thumbnailCache.put(cacheKey, thumbnail)
                }
                thumbnail
            }
    }

    suspend fun setStaticWallpaper(
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        destination: WallpaperDestination,
        wallpaperModel: StaticWallpaperModel,
        inputStream: InputStream?,
        bitmap: Bitmap,
        wallpaperSize: Point,
        fullPreviewCropModels: Map<Point, FullPreviewCropModel>? = null,
    ) {
        // TODO(b/303317694): provide set wallpaper status as flow
        withContext(backgroundDispatcher) {
            client.setStaticWallpaper(
                setWallpaperEntryPoint,
                destination,
                wallpaperModel,
                inputStream,
                bitmap,
                wallpaperSize,
                fullPreviewCropModels,
            )
        }
    }

    suspend fun setLiveWallpaper(
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        destination: WallpaperDestination,
        wallpaperModel: LiveWallpaperModel,
    ) {
        withContext(backgroundDispatcher) {
            client.setLiveWallpaper(
                setWallpaperEntryPoint,
                destination,
                wallpaperModel,
            )
        }
    }

    /** Sets the wallpaper to the one with the given ID. */
    suspend fun setRecentWallpaper(
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        destination: WallpaperDestination,
        wallpaperId: String,
    ) {
        _selectingWallpaperId.value =
            _selectingWallpaperId.value.toMutableMap().apply { this[destination] = wallpaperId }
        withContext(backgroundDispatcher) {
            client.setRecentWallpaper(
                setWallpaperEntryPoint = setWallpaperEntryPoint,
                destination = destination,
                wallpaperId = wallpaperId,
            ) {
                _selectingWallpaperId.value =
                    _selectingWallpaperId.value.toMutableMap().apply { this[destination] = null }
            }
        }
    }

    suspend fun getWallpaperColors(bitmap: Bitmap, cropHints: Map<Point, Rect>?): WallpaperColors? =
        withContext(backgroundDispatcher) { client.getWallpaperColors(bitmap, cropHints) }

    companion object {
        const val DEFAULT_KEY = "default_missing_key"
        /** The maximum number of options to show, including the currently-selected one. */
        private const val MAX_OPTIONS = 5
    }
}
