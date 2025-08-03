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
package com.android.wallpaper.picker.preview.ui.util

import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import com.android.wallpaper.picker.preview.ui.util.CropSizeUtil.fitCropRectToLayoutDirection
import com.android.wallpaper.util.WallpaperCropUtils
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

object FullResImageViewUtil {

    private const val DEFAULT_WALLPAPER_MAX_ZOOM = 8f

    fun getScaleAndCenter(
        viewSize: Point,
        rawWallpaperSize: Point,
        displaySize: Point,
        cropRect: Rect?,
        isRtl: Boolean,
    ): ScaleAndCenter {
        // Determine minimum zoom to fit maximum visible area of wallpaper on crop surface.
        // defaultRawWallpaperRect represents a brand new wallpaper preview with no crop hints.
        val defaultRawWallpaperRect =
            WallpaperCropUtils.calculateVisibleRect(rawWallpaperSize, viewSize)
        val visibleRawWallpaperRect =
            cropRect?.let { fitCropRectToLayoutDirection(it, displaySize, isRtl) }
                ?: defaultRawWallpaperRect
        val centerPosition =
            PointF(
                visibleRawWallpaperRect.centerX().toFloat(),
                visibleRawWallpaperRect.centerY().toFloat()
            )
        val defaultWallpaperZoom =
            WallpaperCropUtils.calculateMinZoom(
                Point(defaultRawWallpaperRect.width(), defaultRawWallpaperRect.height()),
                viewSize
            )
        val visibleWallpaperZoom =
            WallpaperCropUtils.calculateMinZoom(
                Point(visibleRawWallpaperRect.width(), visibleRawWallpaperRect.height()),
                viewSize
            )

        return ScaleAndCenter(
            defaultWallpaperZoom,
            defaultWallpaperZoom.coerceAtLeast(DEFAULT_WALLPAPER_MAX_ZOOM),
            visibleWallpaperZoom,
            centerPosition,
        )
    }

    fun SubsamplingScaleImageView.getCropRect() = Rect().apply { visibleFileRect(this) }

    data class ScaleAndCenter(
        val minScale: Float,
        val maxScale: Float,
        val defaultScale: Float,
        val center: PointF
    )
}
