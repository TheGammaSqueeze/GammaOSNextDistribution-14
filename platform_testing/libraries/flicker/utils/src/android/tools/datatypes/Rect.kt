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

package android.tools.datatypes

import android.tools.withCache

/**
 * Wrapper for RectProto
 *
 * ```
 *     - frameworks/native/services/surfaceflinger/layerproto/common.proto and
 *     - frameworks/base/core/proto/android/graphics/rect.proto
 * ```
 *
 * This class is used by flicker and Winscope
 */
open class Rect(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0) :
    DataType() {
    val height: Int = bottom - top
    val width: Int = right - left
    fun centerX(): Int = (left + right) / 2
    fun centerY(): Int = (top + bottom) / 2

    /** Returns true if the rectangle is empty (left >= right or top >= bottom) */
    override val isEmpty: Boolean = width <= 0 || height <= 0

    /** Returns a [RectF] version fo this rectangle. */
    fun toRectF(): RectF {
        return RectF.from(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    override fun doPrintValue() = "($left, $top) - ($right, $bottom)"

    /**
     * Returns true iff the specified rectangle r is inside or equal to this rectangle. An empty
     * rectangle never contains another rectangle.
     *
     * @param rect The rectangle being tested for containment.
     * @return true iff the specified rectangle r is inside or equal to this
     *
     * ```
     *              rectangle
     * ```
     */
    operator fun contains(rect: Rect): Boolean {
        val thisRect = toRectF()
        val otherRect = rect.toRectF()
        return thisRect.contains(otherRect)
    }

    /**
     * Returns a [Rect] where the dimensions don't exceed those of [crop]
     *
     * @param crop The crop that should be applied to this layer
     */
    fun crop(crop: Rect): Rect {
        val newLeft = maxOf(left, crop.left)
        val newTop = maxOf(top, crop.top)
        val newRight = minOf(right, crop.right)
        val newBottom = minOf(bottom, crop.bottom)
        return from(newLeft, newTop, newRight, newBottom)
    }

    /**
     * Returns true if: fLeft <= x < fRight && fTop <= y < fBottom. Returns false if SkIRect is
     * empty.
     *
     * Considers input to describe constructed SkIRect: (x, y, x + 1, y + 1) and returns true if
     * constructed area is completely enclosed by SkIRect area.
     *
     * @param x test SkIPoint x-coordinate @param y test SkIPoint y-coordinate @return true if (x,
     *   y) is inside SkIRect
     */
    fun contains(x: Int, y: Int): Boolean {
        return x in left until right && y in top until bottom
    }

    /**
     * If the specified rectangle intersects this rectangle, return true and set this rectangle to
     * that intersection, otherwise return false and do not change this rectangle. No check is
     * performed to see if either rectangle is empty. To just test for intersection, use
     * intersects()
     *
     * @param rect The rectangle being intersected with this rectangle.
     * @return A rectangle with the intersection coordinates
     */
    fun intersection(rect: Rect): Rect {
        val thisRect = toRectF()
        val otherRect = rect.toRectF()
        return thisRect.intersection(otherRect).toRect()
    }

    fun clone(): Rect {
        return from(left, top, right, bottom)
    }

    companion object {
        val EMPTY: Rect
            get() = withCache { Rect() }

        fun from(left: Int, top: Int, right: Int, bottom: Int): Rect = withCache {
            Rect(left, top, right, bottom)
        }

        internal fun withoutCache(left: Int, top: Int, right: Int, bottom: Int): Rect =
            Rect(left, top, right, bottom)
    }
}
