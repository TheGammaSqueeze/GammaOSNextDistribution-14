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

package com.android.wallpaper.picker.customization.domain.interactor

import android.graphics.Bitmap
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.module.logging.UserEventLogger.SetWallpaperEntryPoint
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Handles business logic for wallpaper-related use-cases. */
class WallpaperInteractor(
    private val repository: WallpaperRepository,
    /** Returns whether wallpaper picker should handle reload */
    val shouldHandleReload: () -> Boolean = { true },
) {
    val areRecentsAvailable: Boolean = repository.areRecentsAvailable
    val maxOptions = repository.maxOptions

    /** Returns a flow that is updated whenever the wallpaper has been updated */
    fun wallpaperUpdateEvents(screen: CustomizationSections.Screen): Flow<WallpaperModel?> {
        return when (screen) {
            CustomizationSections.Screen.LOCK_SCREEN ->
                previews(WallpaperDestination.LOCK, 1).map { recentWallpapers ->
                    if (recentWallpapers.isEmpty()) null else recentWallpapers[0]
                }
            CustomizationSections.Screen.HOME_SCREEN ->
                previews(WallpaperDestination.HOME, 1).map { recentWallpapers ->
                    if (recentWallpapers.isEmpty()) null else recentWallpapers[0]
                }
        }
    }

    /** Returns the ID of the currently-selected wallpaper. */
    fun selectedWallpaperId(
        destination: WallpaperDestination,
    ): StateFlow<String> {
        return repository.selectedWallpaperId(destination = destination)
    }

    /**
     * Returns the ID of the wallpaper that is in the process of becoming the selected wallpaper or
     * `null` if no such transaction is currently taking place.
     */
    fun selectingWallpaperId(
        destination: WallpaperDestination,
    ): Flow<String?> {
        return repository.selectingWallpaperId.map { it[destination] }
    }

    /** This is true when a wallpaper is selected but not yet set to the System. */
    fun isSelectingWallpaper(
        destination: WallpaperDestination,
    ): Flow<Boolean> {
        return selectingWallpaperId(destination).distinctUntilChanged().map { it != null }
    }

    /**
     * Lists the [maxResults] most recent wallpapers.
     *
     * The first one is the most recent (current) wallpaper.
     */
    fun previews(
        destination: WallpaperDestination,
        maxResults: Int,
    ): Flow<List<WallpaperModel>> {
        return repository
            .recentWallpapers(
                destination = destination,
                limit = maxResults,
            )
            .map { previews ->
                if (previews.size > maxResults) {
                    previews.subList(0, maxResults)
                } else {
                    previews
                }
            }
    }

    /** Sets the wallpaper to the one with the given ID. */
    suspend fun setRecentWallpaper(
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        destination: WallpaperDestination,
        wallpaperId: String,
    ) {
        repository.setRecentWallpaper(
            setWallpaperEntryPoint = setWallpaperEntryPoint,
            destination = destination,
            wallpaperId = wallpaperId,
        )
    }

    /** Returns a thumbnail for the wallpaper with the given ID and destination. */
    suspend fun loadThumbnail(
        wallpaperId: String,
        lastUpdatedTimestamp: Long,
        destination: WallpaperDestination
    ): Bitmap? {
        return repository.loadThumbnail(
            wallpaperId = wallpaperId,
            lastUpdatedTimestamp = lastUpdatedTimestamp,
            destination = destination
        )
    }
}
