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

package com.android.wallpaper.picker

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import com.android.wallpaper.util.ScreenSizeCalculator

/**
 * [LinearLayout] that sizes its children using a fixed aspect ratio that is the same as that of the
 * display, and can lay out multiple children horizontally with margin
 */
class DisplayAspectRatioLinearLayout(
    context: Context,
    attrs: AttributeSet?,
) : LinearLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val screenAspectRatio = ScreenSizeCalculator.getInstance().getScreenAspectRatio(context)
        val parentWidth = this.measuredWidth
        val parentHeight = this.measuredHeight
        val itemSpacingPx = ITEM_SPACING_DP.toPx(context.resources.displayMetrics.density)
        val (childWidth, childHeight) =
            if (orientation == HORIZONTAL) {
                val availableWidth =
                    parentWidth - paddingStart - paddingEnd - (childCount - 1) * itemSpacingPx
                val availableHeight = parentHeight - paddingTop - paddingBottom
                var width = availableWidth / childCount
                var height = (width * screenAspectRatio).toInt()
                if (height > availableHeight) {
                    height = availableHeight
                    width = (height / screenAspectRatio).toInt()
                }
                width to height
            } else {
                val availableWidth = parentWidth - paddingStart - paddingEnd
                val availableHeight =
                    parentHeight - paddingTop - paddingBottom - (childCount - 1) * itemSpacingPx
                var height = availableHeight / childCount
                var width = (height / screenAspectRatio).toInt()
                if (width > availableWidth) {
                    width = availableWidth
                    height = (width * screenAspectRatio).toInt()
                }
                width to height
            }

        val itemSpacingHalfPx = ITEM_SPACING_DP_HALF.toPx(context.resources.displayMetrics.density)
        children.forEachIndexed { index, child ->
            val addSpacingToStart = index > 0
            val addSpacingToEnd = index < (childCount - 1)
            if (orientation == HORIZONTAL) {
                child.updateLayoutParams<MarginLayoutParams> {
                    if (addSpacingToStart) this.marginStart = itemSpacingHalfPx
                    if (addSpacingToEnd) this.marginEnd = itemSpacingHalfPx
                }
            } else {
                child.updateLayoutParams<MarginLayoutParams> {
                    if (addSpacingToStart) this.topMargin = itemSpacingHalfPx
                    if (addSpacingToEnd) this.bottomMargin = itemSpacingHalfPx
                }
            }

            child.measure(
                MeasureSpec.makeMeasureSpec(
                    childWidth,
                    MeasureSpec.EXACTLY,
                ),
                MeasureSpec.makeMeasureSpec(
                    childHeight,
                    MeasureSpec.EXACTLY,
                ),
            )
        }
    }

    private fun Int.toPx(density: Float): Int {
        return (this * density).toInt()
    }

    companion object {
        private const val ITEM_SPACING_DP = 12
        private const val ITEM_SPACING_DP_HALF = ITEM_SPACING_DP / 2
    }
}
