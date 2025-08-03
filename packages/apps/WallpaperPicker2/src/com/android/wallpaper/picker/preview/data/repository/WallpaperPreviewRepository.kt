/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.wallpaper.picker.preview.data.repository

import com.android.wallpaper.module.WallpaperPreferences
import com.android.wallpaper.picker.data.WallpaperModel
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import com.android.wallpaper.picker.preview.data.util.LiveWallpaperDownloader
import com.android.wallpaper.picker.preview.shared.model.LiveWallpaperDownloadResultCode.SUCCESS
import com.android.wallpaper.picker.preview.shared.model.LiveWallpaperDownloadResultModel
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** This repository class manages the [WallpaperModel] for the preview screen */
@ActivityRetainedScoped
class WallpaperPreviewRepository
@Inject
constructor(
    private val liveWallpaperDownloader: LiveWallpaperDownloader,
    private val preferences: WallpaperPreferences,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
) {
    /** This [WallpaperModel] represents the current selected wallpaper */
    private val _wallpaperModel = MutableStateFlow<WallpaperModel?>(null)
    val wallpaperModel: StateFlow<WallpaperModel?> = _wallpaperModel.asStateFlow()

    fun setWallpaperModel(wallpaperModel: WallpaperModel) {
        _wallpaperModel.value = wallpaperModel
    }

    private val _hasTooltipBeenShown: MutableStateFlow<Boolean> =
        MutableStateFlow(preferences.getHasPreviewTooltipBeenShown())
    val hasTooltipBeenShown: StateFlow<Boolean> = _hasTooltipBeenShown.asStateFlow()

    fun dismissTooltip() {
        _hasTooltipBeenShown.value = true
        preferences.setHasPreviewTooltipBeenShown(true)
    }

    suspend fun downloadWallpaper(): LiveWallpaperDownloadResultModel? =
        withContext(bgDispatcher) {
            val result = liveWallpaperDownloader.downloadWallpaper()
            if (result?.code == SUCCESS && result.wallpaperModel != null) {
                // If download success, update repo's WallpaperModel to render the live wallpaper.
                _wallpaperModel.value = result.wallpaperModel
                result
            } else {
                result
            }
        }
}
