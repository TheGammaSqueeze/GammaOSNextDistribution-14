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
package com.android.wallpaper.picker

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

/**
 * This nested scroll view will detect horizontal touch movements and stop vertical scrolls when a
 * horizontal touch movement is detected.
 */
class HorizontalTouchMovementAwareNestedScrollView(context: Context, attrs: AttributeSet?) :
    NestedScrollView(context, attrs) {

    private var startXPosition = 0f
    private var startYPosition = 0f
    private var isHorizontalTouchMovement = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startXPosition = event.x
                startYPosition = event.y
                isHorizontalTouchMovement = false
            }
            MotionEvent.ACTION_MOVE -> {
                val xMoveDistance = abs(event.x - startXPosition)
                val yMoveDistance = abs(event.y - startYPosition)
                if (
                    !isHorizontalTouchMovement &&
                        xMoveDistance > yMoveDistance &&
                        xMoveDistance > ViewConfiguration.get(context).scaledTouchSlop
                ) {
                    isHorizontalTouchMovement = true
                }
            }
            else -> {}
        }
        return if (isHorizontalTouchMovement) {
            // We only want to intercept the touch event when the touch moves more vertically than
            // horizontally. So we return false.
            false
        } else {
            super.onInterceptTouchEvent(event)
        }
    }
}
