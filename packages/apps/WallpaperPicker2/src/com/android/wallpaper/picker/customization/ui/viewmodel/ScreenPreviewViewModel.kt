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
 *
 */

package com.android.wallpaper.picker.customization.ui.viewmodel

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.os.Bundle
import com.android.wallpaper.R
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.module.CustomizationSections.Screen
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.customization.shared.model.WallpaperModel
import com.android.wallpaper.util.PreviewUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Models the UI state for a preview of the home screen or lock screen. */
open class ScreenPreviewViewModel(
    val previewUtils: PreviewUtils,
    private val initialExtrasProvider: () -> Bundle? = { null },
    private val wallpaperInfoProvider: suspend (forceReload: Boolean) -> WallpaperInfo?,
    private val onWallpaperColorChanged: (WallpaperColors?) -> Unit = {},
    private val wallpaperInteractor: WallpaperInteractor,
    val screen: Screen,
) {

    val previewContentDescription: Int =
        when (screen) {
            Screen.HOME_SCREEN -> R.string.home_wallpaper_preview_card_content_description
            Screen.LOCK_SCREEN -> R.string.lock_wallpaper_preview_card_content_description
        }

    /** Returns whether wallpaper picker should handle reload */
    fun shouldReloadWallpaper(): Flow<Boolean> {
        // Setting the lock screen to the same wp as the home screen doesn't trigger a UI update,
        // so fix that here for now
        // TODO(b/281730113) Remove this once better solution is ready.
        return wallpaperUpdateEvents().map { thisWallpaper ->
            val otherWallpaper = wallpaperUpdateEvents(otherScreen()).first()
            true
        }
    }

    fun wallpaperThumbnail(): Flow<Bitmap?> {
        return wallpaperUpdateEvents().filterNotNull().map { wallpaper ->
            wallpaperInteractor.loadThumbnail(
                wallpaper.wallpaperId,
                wallpaper.lastUpdated,
                getWallpaperDestination()
            )
        }
    }

    private fun otherScreen(): Screen {
        return if (screen == Screen.LOCK_SCREEN) Screen.HOME_SCREEN else Screen.LOCK_SCREEN
    }

    /** Returns a flow that is updated whenever the wallpaper has been updated */
    private fun wallpaperUpdateEvents(
        s: CustomizationSections.Screen = screen
    ): Flow<WallpaperModel?> {
        return wallpaperInteractor.wallpaperUpdateEvents(s)
    }

    open fun workspaceUpdateEvents(): Flow<Boolean>? = null

    fun getInitialExtras(): Bundle? {
        return initialExtrasProvider.invoke()
    }

    /**
     * Returns the current wallpaper's WallpaperInfo
     *
     * @param forceReload if true, any cached values will be ignored and current wallpaper info will
     *   be reloaded
     */
    suspend fun getWallpaperInfo(forceReload: Boolean = false): WallpaperInfo? {
        return wallpaperInfoProvider.invoke(forceReload)
    }

    fun onWallpaperColorsChanged(colors: WallpaperColors?) {
        onWallpaperColorChanged.invoke(colors)
    }

    open val isLoading: Flow<Boolean> =
        wallpaperInteractor.isSelectingWallpaper(
            destination = getWallpaperDestination(),
        )

    private fun getWallpaperDestination() =
        if (screen == Screen.LOCK_SCREEN) {
            WallpaperDestination.LOCK
        } else {
            WallpaperDestination.HOME
        }
}
