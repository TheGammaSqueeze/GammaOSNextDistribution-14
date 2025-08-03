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

package com.android.wallpaper.picker

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.children
import com.android.wallpaper.util.ScreenSizeCalculator

/**
 * [FrameLayout] that sizes its children using a fixed aspect ratio that is the same as that of the
 * display.
 */
class DisplayAspectRatioFrameLayout(
    context: Context,
    attrs: AttributeSet?,
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val screenAspectRatio = ScreenSizeCalculator.getInstance().getScreenAspectRatio(context)
        // We're always forcing the width based on the height. This will only work if the
        // DisplayAspectRatioFrameLayout is allowed to stretch to fill its parent (for example if
        // the parent is a vertical LinearLayout and the DisplayAspectRatioFrameLayout has a height
        // if 0 and a weight of 1.
        // However we make sure that the width of the children never exceeds the width of the parent
        //
        // If you need to use this class to force the height dimension based on the width instead,
        // you will need to flip the logic below.
        children.forEach { child ->
            val childWidth =
                (child.measuredHeight / screenAspectRatio).toInt().coerceAtMost(measuredWidth)
            child.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(
                    if (childWidth < measuredWidth) {
                        child.measuredHeight
                    } else {
                        (childWidth * screenAspectRatio).toInt()
                    },
                    MeasureSpec.EXACTLY,
                ),
            )
        }
    }
}
