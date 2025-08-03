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

import android.tools.FloatFormatter
import android.tools.withCache
import kotlin.math.abs

/**
 * Wrapper for FloatRectProto (frameworks/native/services/surfaceflinger/layerproto/layers.proto)
 *
 * This class is used by flicker and Winscope
 */
class RectF
private constructor(val left: Float, val top: Float, val right: Float, val bottom: Float) :
    DataType() {
    val height: Float
        get() = bottom - top
    val width: Float
        get() = right - left

    /** Returns true if the rectangle is empty (left >= right or top >= bottom) */
    override val isEmpty: Boolean
        get() = width <= 0f || height <= 0f

    /**
     * Returns a [Rect] version fo this rectangle.
     *
     * All fractional parts are rounded to 0
     */
    fun toRect(): Rect {
        return Rect.from(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    /**
     * Returns true iff the specified rectangle r is inside or equal to this rectangle. An empty
     * rectangle never contains another rectangle.
     *
     * @param r The rectangle being tested for containment.
     * @return true iff the specified rectangle r is inside or equal to this
     *
     * ```
     *              rectangle
     * ```
     */
    operator fun contains(r: RectF): Boolean {
        // check for empty first
        return this.left < this.right &&
            this.top < this.bottom && // now check for containment
            left <= r.left &&
            top <= r.top &&
            right >= r.right &&
            bottom >= r.bottom
    }

    fun containsWithThreshold(r: RectF, threshold: Float = 0.01f): Boolean {
        // check for empty first
        return this.left < this.right &&
            this.top < this.bottom && // now check for containment
            (left <= r.left || abs(left - r.left) < threshold) &&
            (top <= r.top || abs(top - r.top) < threshold) &&
            (right >= r.right || abs(right - r.right) < threshold) &&
            (bottom >= r.bottom || abs(bottom - r.bottom) < threshold)
    }

    /**
     * Returns a [RectF] where the dimensions don't exceed those of [crop]
     *
     * @param crop The crop that should be applied to this layer
     */
    fun crop(crop: RectF): RectF {
        val newLeft = maxOf(left, crop.left)
        val newTop = maxOf(top, crop.top)
        val newRight = minOf(right, crop.right)
        val newBottom = minOf(bottom, crop.bottom)
        return from(newLeft, newTop, newRight, newBottom)
    }

    /**
     * If the rectangle specified by left,top,right,bottom intersects this rectangle, return true
     * and set this rectangle to that intersection, otherwise return false and do not change this
     * rectangle. No check is performed to see if either rectangle is empty. Note: To just test for
     * intersection, use intersects()
     *
     * @param left The left side of the rectangle being intersected with this rectangle
     * @param top The top of the rectangle being intersected with this rectangle
     * @param right The right side of the rectangle being intersected with this rectangle.
     * @param bottom The bottom of the rectangle being intersected with this rectangle.
     * @return A rectangle with the intersection coordinates
     */
    fun intersection(left: Float, top: Float, right: Float, bottom: Float): RectF {
        if (this.left < right && left < this.right && this.top <= bottom && top <= this.bottom) {
            var intersectionLeft = this.left
            var intersectionTop = this.top
            var intersectionRight = this.right
            var intersectionBottom = this.bottom

            if (this.left < left) {
                intersectionLeft = left
            }
            if (this.top < top) {
                intersectionTop = top
            }
            if (this.right > right) {
                intersectionRight = right
            }
            if (this.bottom > bottom) {
                intersectionBottom = bottom
            }
            return from(intersectionLeft, intersectionTop, intersectionRight, intersectionBottom)
        }
        return EMPTY
    }

    /**
     * If the specified rectangle intersects this rectangle, return true and set this rectangle to
     * that intersection, otherwise return false and do not change this rectangle. No check is
     * performed to see if either rectangle is empty. To just test for intersection, use
     * intersects()
     *
     * @param r The rectangle being intersected with this rectangle.
     * @return A rectangle with the intersection coordinates
     */
    fun intersection(r: RectF): RectF = intersection(r.left, r.top, r.right, r.bottom)

    override fun doPrintValue(): String {
        val left = FloatFormatter.format(left)
        val top = FloatFormatter.format(top)
        val right = FloatFormatter.format(right)
        val bottom = FloatFormatter.format(bottom)
        return "($left, $top) - ($right, $bottom)"
    }

    companion object {
        val EMPTY: RectF
            get() = withCache { RectF(left = 0f, top = 0f, right = 0f, bottom = 0f) }

        fun from(left: Float, top: Float, right: Float, bottom: Float): RectF = withCache {
            RectF(left, top, right, bottom)
        }
    }
}
