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

package com.android.wallpaper.util.converter

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.android.wallpaper.model.CreativeWallpaperInfo
import com.android.wallpaper.model.CurrentWallpaperInfo
import com.android.wallpaper.model.ImageWallpaperInfo
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.picker.data.ColorInfo
import com.android.wallpaper.picker.data.CommonWallpaperData
import com.android.wallpaper.picker.data.CreativeWallpaperData
import com.android.wallpaper.picker.data.Destination
import com.android.wallpaper.picker.data.ImageWallpaperData
import com.android.wallpaper.picker.data.LiveWallpaperData
import com.android.wallpaper.picker.data.WallpaperId
import com.android.wallpaper.picker.data.WallpaperModel

/** This class creates an instance of [WallpaperModel] from a given [WallpaperInfo] object. */
interface WallpaperModelFactory {

    fun getWallpaperModel(context: Context, wallpaperInfo: WallpaperInfo): WallpaperModel

    companion object {

        const val STATIC_WALLPAPER_PACKAGE = "StaticWallpaperPackage"
        const val STATIC_WALLPAPER_CLASS = "StaticWallpaperClass"

        private const val TAG = "WallpaperModelFactory"
        private const val UNKNOWN_COLLECTION_ID = "unknown_collection_id"

        fun WallpaperInfo.getCommonWallpaperData(context: Context): CommonWallpaperData {
            var wallpaperDestination = Destination.NOT_APPLIED
            if (this is CurrentWallpaperInfo) {
                wallpaperDestination =
                    when (wallpaperManagerFlag) {
                        WallpaperManager.FLAG_SYSTEM -> Destination.APPLIED_TO_SYSTEM
                        WallpaperManager.FLAG_LOCK -> Destination.APPLIED_TO_LOCK
                        WallpaperManager.FLAG_LOCK and WallpaperManager.FLAG_SYSTEM ->
                            Destination.APPLIED_TO_SYSTEM_LOCK
                        else -> {
                            Log.w(
                                TAG,
                                "Invalid value for wallpaperManagerFlag: $wallpaperManagerFlag"
                            )
                            Destination.NOT_APPLIED
                        }
                    }
            }

            // componentName is a valid value for liveWallpapers, for other types of wallpapers
            // (which are static) we can have a constant value
            val componentName =
                if (this is LiveWallpaperInfo) {
                    wallpaperComponent.component
                } else {
                    ComponentName(STATIC_WALLPAPER_PACKAGE, STATIC_WALLPAPER_CLASS)
                }

            val wallpaperId =
                WallpaperId(
                    componentName = componentName,
                    uniqueId =
                        if (this is ImageWallpaperInfo && getWallpaperId() == null)
                            "${uri.hashCode()}"
                        else wallpaperId,
                    // TODO(b/308800470): Figure out the use of collection ID
                    collectionId = getCollectionId(context) ?: UNKNOWN_COLLECTION_ID,
                )

            val colorInfoOfWallpaper =
                ColorInfo(colorInfo.wallpaperColors, colorInfo.placeholderColor)

            return CommonWallpaperData(
                id = wallpaperId,
                title = getTitle(context),
                attributions = getAttributions(context),
                exploreActionUrl = getActionUrl(context),
                thumbAsset = getThumbAsset(context),
                placeholderColorInfo = colorInfoOfWallpaper,
                destination = wallpaperDestination,
            )
        }

        fun LiveWallpaperInfo.getLiveWallpaperData(context: Context): LiveWallpaperData {
            val groupNameOfWallpaper = (this as? CreativeWallpaperInfo)?.groupName ?: ""
            val wallpaperManager = WallpaperManager.getInstance(context)
            val currentHomeWallpaper =
                wallpaperManager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)
            val currentLockWallpaper = wallpaperManager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)
            return LiveWallpaperData(
                groupName = groupNameOfWallpaper,
                systemWallpaperInfo = info,
                isTitleVisible = isVisibleTitle,
                isApplied = isApplied(currentHomeWallpaper, currentLockWallpaper),
                effectNames = effectNames,
            )
        }

        fun CreativeWallpaperInfo.getCreativeWallpaperData(): CreativeWallpaperData {
            return CreativeWallpaperData(
                configPreviewUri = configPreviewUri,
                cleanPreviewUri = cleanPreviewUri,
                deleteUri = deleteUri,
                thumbnailUri = thumbnailUri,
                shareUri = shareUri,
                author = author ?: "",
                description = description ?: "",
                contentDescription = contentDescription,
                isCurrent = isCurrent,
            )
        }

        fun ImageWallpaperInfo.getImageWallpaperData(): ImageWallpaperData {
            return ImageWallpaperData(uri)
        }
    }
}
