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
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.get
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/** This container view wraps {TabsPager}. This is used to control the touch area of {TabsPager}. */
class TabsPagerContainer
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {

    private var viewPager: ViewPager

    init {
        viewPager = TabsPager(context)

        val params =
            FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                .apply { gravity = Gravity.CENTER }

        viewPager.clipChildren = false
        viewPager.clipToPadding = false

        viewPager.layoutParams = params
        addView(viewPager)
    }

    // x-coordinate of the beginning of a gesture
    var touchDownX = 0

    // y-coordinate of the beginning of a gesture
    var touchDownY = 0

    // x-coordinate of the ending of a gesture
    var touchUpX = 0

    // x-coordinate of the ending of a gesture
    var touchUpY = 0
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val tab1 = viewPager.get(0)
        val tab2 = viewPager.get(1)
        val tab1Rect = tab1.getViewRect()
        val tab2Rect = tab2.getViewRect()
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.getRawX().toInt()
                touchDownY = ev.getRawY().toInt()

                // if the touch down event doesn't land in the visible rect of either tab buttons
                // then we ignore the entirety of the next gesture
                if (
                    !tab2Rect.contains(touchDownX, touchDownY) &&
                        !tab1Rect.contains(touchDownX, touchDownY)
                ) {
                    return false
                }
            }
            // Intercepting the touch event here indicates a button has been touched outiside
            // the bounds of the ViewPager.
            MotionEvent.ACTION_UP -> {

                // Verify whether the touch up action corresponds to actual tap as opposed to
                // another gesture
                if (isClick(touchDownX, touchDownY, ev)) {
                    if (tab2Rect.contains(touchDownX, touchDownY)) {
                        // the only time we need to actually scroll to a tab is when the tab which
                        // was clicked is not the currently centered tab
                        tab2.callOnClick()
                    }

                    if (tab1Rect.contains(touchDownX, touchDownY)) {
                        tab1.callOnClick()
                    }
                    return true
                }
            }
        }
        return viewPager.dispatchTouchEvent(ev)
    }

    // The maximum distance delta in finger position between where the finger first touches the
    // screen  and when it lifts from the screen to be considered a tap
    private val CLICK_TOLERANCE = ViewConfiguration.get(context).scaledTouchSlop

    /** Returns whether the given motion event is a tap */
    private fun isClick(downX: Int, downY: Int, ev: MotionEvent): Boolean {
        val deltaX = abs(downX - ev.getRawX().toInt())
        val deltaY = abs(downY - ev.getRawY().toInt())

        val isWithinTimeThreshold = gestureElapsedTime(ev) <= ViewConfiguration.getTapTimeout()
        val isWithinDistanceThreshold = deltaX <= CLICK_TOLERANCE && deltaY <= CLICK_TOLERANCE

        return isWithinTimeThreshold && isWithinDistanceThreshold
    }

    /** Returns the elapsed time since the touch gesture the given event is part of has begun. */
    private fun gestureElapsedTime(event: MotionEvent): Long {
        return event.eventTime - event.downTime
    }

    private fun View.getViewRect(): Rect {
        val returnRect = Rect()
        this.getGlobalVisibleRect(returnRect)
        return returnRect
    }

    fun getViewPager(): ViewPager {
        return viewPager
    }

    override fun performClick(): Boolean {
        // returning true to signify that the click was handled by a custom implementation
        return true
    }
}
