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

package com.android.wallpaper.picker.preview.data.util

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.android.wallpaper.picker.data.WallpaperModel
import com.android.wallpaper.picker.preview.shared.model.LiveWallpaperDownloadResultModel

/**
 * Handles the download process of a downloadable wallpaper. This downloader should be aware of the
 * Activity's lifecycle.
 */
interface LiveWallpaperDownloader {

    /**
     * Initializes the downloadable service. This needs to be called when [Activity.onCreate] and
     * before calling [downloadWallpaper].
     */
    fun initiateDownloadableService(
        activity: Activity,
        wallpaperData: WallpaperModel.StaticWallpaperModel,
        intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>,
    )

    /**
     * Clean up the underlying downloadable service. This needs to be called when
     * [Activity.onDestroy].
     */
    fun cleanup()

    suspend fun downloadWallpaper(): LiveWallpaperDownloadResultModel?
}
