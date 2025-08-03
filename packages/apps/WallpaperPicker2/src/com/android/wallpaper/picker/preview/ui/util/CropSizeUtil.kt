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
import kotlin.math.abs

/** Util for calculating cropping related size. */
object CropSizeUtil {

    /**
     * Finds largest possible rectangle in [Rect] with the same aspect ratio in the target
     * rectangle.
     *
     * See [findMaxRectWithRatioIn].
     */
    fun Rect.findMaxRectWithRatioIn(rect: Rect): PointF =
        Point(width(), height()).findMaxRectWithRatioIn(Point(rect.width(), rect.height()))

    /**
     * Finds largest possible rectangle in [Point] with the same aspect ratio in the target
     * rectangle.
     *
     * Calling rectangle will scale up or down to match the size of the target rectangle, the final
     * rectangle size is the largest but not exceeding the size of target rectangle.
     */
    fun Point.findMaxRectWithRatioIn(point: Point): PointF {
        val ratio =
            if (x <= point.x && y <= point.y) {
                // Target rect is containing the calling rect
                if (abs(x - point.x) <= abs(y - point.y)) {
                    point.x.toFloat() / x
                } else {
                    point.y.toFloat() / y
                }
            } else if (x > point.x && y > point.y) {
                // Calling rect is containing the target rect
                if (abs(x - point.x) >= abs(y - point.y)) {
                    point.x.toFloat() / x
                } else {
                    point.y.toFloat() / y
                }
            } else {
                // Target rect and calling rect overlap
                if (x > point.x) {
                    point.x.toFloat() / x
                } else {
                    point.y.toFloat() / y
                }
            }

        return PointF(x * ratio, y * ratio)
    }

    fun fitCropRectToLayoutDirection(
        cropRect: Rect,
        displaySize: Point,
        isRtl: Boolean,
    ): Rect {
        val parallax = cropRect.width() - displaySize.x * cropRect.height() / displaySize.y
        return Rect(cropRect).apply { if (isRtl) left += parallax else right -= parallax }
    }
}
