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
package com.android.wallpaper.picker.preview.ui.fragment.smallpreview.views

import android.content.Context
import android.util.AttributeSet
import androidx.viewpager.widget.ViewPager

/** A view pager whose height and width are determined by the maximum height/width of children */
class TabsPager
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null /* attrs */) : ViewPager(context, attrs) {
    /** This measures the view pager to have the height and width of the largest item */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var measureSpecWidth = widthMeasureSpec
        var measureSpecHeight = heightMeasureSpec
        var maxChildHeight = 0
        var maxChildWidth = 0
        for (i in 0 until childCount) {
            val view = getChildAt(i)
            view.measure(
                measureSpecWidth,
                MeasureSpec.makeMeasureSpec(0 /* size */, MeasureSpec.UNSPECIFIED)
            )
            val childHeight = view.measuredHeight
            val childWidth = view.measuredWidth
            if (childHeight > maxChildHeight) {
                maxChildHeight = childHeight
            }
            if (childWidth > maxChildWidth) {
                maxChildWidth = childWidth
            }
        }
        if (maxChildHeight != 0) {
            measureSpecHeight = MeasureSpec.makeMeasureSpec(maxChildHeight, MeasureSpec.EXACTLY)
        }
        measureSpecWidth = MeasureSpec.makeMeasureSpec(maxChildWidth, MeasureSpec.EXACTLY)
        super.onMeasure(measureSpecWidth, measureSpecHeight)
    }
}
