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

package com.android.wallpaper.util.converter

import android.content.Context
import com.android.wallpaper.model.CreativeWallpaperInfo
import com.android.wallpaper.model.ImageWallpaperInfo
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.picker.data.StaticWallpaperData
import com.android.wallpaper.picker.data.WallpaperModel
import com.android.wallpaper.util.converter.WallpaperModelFactory.Companion.getCommonWallpaperData
import com.android.wallpaper.util.converter.WallpaperModelFactory.Companion.getCreativeWallpaperData
import com.android.wallpaper.util.converter.WallpaperModelFactory.Companion.getImageWallpaperData
import com.android.wallpaper.util.converter.WallpaperModelFactory.Companion.getLiveWallpaperData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultWallpaperModelFactory @Inject constructor() : WallpaperModelFactory {

    override fun getWallpaperModel(context: Context, wallpaperInfo: WallpaperInfo): WallpaperModel {
        return if (wallpaperInfo is LiveWallpaperInfo) {
            WallpaperModel.LiveWallpaperModel(
                commonWallpaperData = wallpaperInfo.getCommonWallpaperData(context),
                liveWallpaperData = wallpaperInfo.getLiveWallpaperData(context),
                creativeWallpaperData =
                    (wallpaperInfo as? CreativeWallpaperInfo)?.getCreativeWallpaperData(),
                internalLiveWallpaperData = null,
            )
        } else {
            WallpaperModel.StaticWallpaperModel(
                commonWallpaperData = wallpaperInfo.getCommonWallpaperData(context),
                staticWallpaperData =
                    StaticWallpaperData(
                        asset = wallpaperInfo.getAsset(context),
                        cropHints = wallpaperInfo.wallpaperCropHints
                    ),
                imageWallpaperData =
                    (wallpaperInfo as? ImageWallpaperInfo)?.getImageWallpaperData(),
                networkWallpaperData = null,
                downloadableWallpaperData = null,
            )
        }
    }
}
