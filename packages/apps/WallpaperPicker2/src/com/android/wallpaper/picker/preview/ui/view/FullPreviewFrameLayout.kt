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
package com.android.wallpaper.picker.preview.ui.view

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.widget.FrameLayout
import com.android.wallpaper.picker.preview.ui.util.CropSizeUtil.findMaxRectWithRatioIn

/**
 * A [FrameLayout] that scales to largest possible on current display with the size preserving
 * target display aspect ratio.
 *
 * Acts as [FrameLayout] if the current and target display size were not set by the time of
 * [onMeasure].
 */
class FullPreviewFrameLayout(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    // Current display size represents the size of the currently used display. There is only one
    // size for handheld and tablet devices, but there are 2 sizes for foldable devices.
    private var currentDisplaySize: Point? = null

    // Target display size represents the size of the display that a wallpaper aims to be set to.
    private var targetDisplaySize: Point? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val currentSize = currentDisplaySize
        val targetSize = targetDisplaySize

        if (currentSize == null || targetSize == null) {
            setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val maxRect = targetSize.findMaxRectWithRatioIn(currentSize)
        val width = maxRect.x.toInt()
        val height = maxRect.y.toInt()
        setMeasuredDimension(width, height)
        measureChildren(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
    }

    /**
     * Sets the target display size and the current display size.
     *
     * The view size is maxed out within current display size while preserving the aspect ratio of
     * the target display size. On a single display device the current display size is always the
     * target display size.
     *
     * @param currentSize current display size used as the max bound of this view.
     * @param targetSize target display size to get and preserve it's aspect ratio.
     */
    fun setCurrentAndTargetDisplaySize(currentSize: Point, targetSize: Point) {
        currentDisplaySize = currentSize
        targetDisplaySize = targetSize
    }
}
