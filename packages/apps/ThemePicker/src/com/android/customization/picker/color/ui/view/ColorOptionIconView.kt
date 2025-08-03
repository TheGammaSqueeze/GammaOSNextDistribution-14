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
package com.android.customization.picker.color.ui.view

import android.annotation.ColorInt
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Draw a color option icon, which is a quadrant circle that can show at most 4 different colors.
 */
class ColorOptionIconView(
    context: Context,
    attrs: AttributeSet,
) : View(context, attrs) {

    private val paint = Paint().apply { style = Paint.Style.FILL }

    private val oval = RectF()

    private var color0 = DEFAULT_PLACEHOLDER_COLOR
    private var color1 = DEFAULT_PLACEHOLDER_COLOR
    private var color2 = DEFAULT_PLACEHOLDER_COLOR
    private var color3 = DEFAULT_PLACEHOLDER_COLOR

    private var w = 0
    private var h = 0

    /**
     * @param color0 the color in the top left quadrant
     * @param color1 the color in the top right quadrant
     * @param color2 the color in the bottom left quadrant
     * @param color3 the color in the bottom right quadrant
     */
    fun bindColor(
        @ColorInt color0: Int,
        @ColorInt color1: Int,
        @ColorInt color2: Int,
        @ColorInt color3: Int,
    ) {
        this.color0 = color0
        this.color1 = color1
        this.color2 = color2
        this.color3 = color3
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        this.w = w
        this.h = h
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The w and h need to be an even number to avoid tiny pixel-level gaps between the pies
        w = w.roundDownToEven()
        h = h.roundDownToEven()

        val width = w.toFloat()
        val height = h.toFloat()

        oval.set(0f, 0f, width, height)
        canvas.apply {
            paint.color = color3
            drawArc(
                oval,
                0f,
                90f,
                true,
                paint,
            )
            paint.color = color2
            drawArc(
                oval,
                90f,
                90f,
                true,
                paint,
            )
            paint.color = color0
            drawArc(
                oval,
                180f,
                90f,
                true,
                paint,
            )
            paint.color = color1
            drawArc(
                oval,
                270f,
                90f,
                true,
                paint,
            )
        }
    }

    companion object {
        const val DEFAULT_PLACEHOLDER_COLOR = Color.BLACK

        fun Int.roundDownToEven(): Int {
            return if (this % 2 == 0) this else this - 1
        }
    }
}
