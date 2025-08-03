/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.wallpaper.util

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.DisplayInfo
import android.view.Surface.ROTATION_270
import android.view.Surface.ROTATION_90
import com.android.systemui.shared.recents.utilities.Utilities
import kotlin.math.min

/**
 * Utility class to provide methods to find and obtain information about displays via {@link
 * DisplayManager}
 *
 * Always pass [Context] or [Display] for the current display, instead of using the context in this
 * class, which is fine for stateless info.
 */
class DisplayUtils(private val context: Context) {
    companion object {
        private const val TAG = "DisplayUtils"
        private val ROTATION_HORIZONTAL_HINGE = setOf(ROTATION_90, ROTATION_270)
        private const val TABLET_MIN_DPS = 600f // See Sysui's Utilities.TABLET_MIN_DPS
    }

    private val displayManager: DisplayManager by lazy {
        context.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    fun hasMultiInternalDisplays(): Boolean {
        return getInternalDisplays().size > 1
    }

    /**
     * Returns the internal {@link Display} with the largest area to be used to calculate wallpaper
     * size and cropping.
     */
    fun getWallpaperDisplay(): Display {
        val internalDisplays = getInternalDisplays()
        return internalDisplays.maxWithOrNull { a, b -> getRealArea(a) - getRealArea(b) }
            ?: internalDisplays[0]
    }

    /**
     * Checks if the device only has one display or unfolded screen in horizontal hinge orientation.
     */
    fun isSingleDisplayOrUnfoldedHorizontalHinge(activity: Activity): Boolean {
        return !hasMultiInternalDisplays() || isUnfoldedHorizontalHinge(activity)
    }

    /**
     * Checks if the device is a foldable and it's unfolded and in horizontal hinge orientation
     * (portrait).
     */
    fun isUnfoldedHorizontalHinge(activity: Activity): Boolean {
        return activity.display?.rotation in ROTATION_HORIZONTAL_HINGE &&
            isOnWallpaperDisplay(activity) &&
            hasMultiInternalDisplays()
    }

    fun getMaxDisplaysDimension(): Point {
        val dimen = Point()
        getInternalDisplays().let { displays ->
            dimen.x = displays.maxOf { getRealSize(it).x }
            dimen.y = displays.maxOf { getRealSize(it).y }
        }
        return dimen
    }

    /**
     * This flag returns true if the display is:
     * 1. a large screen device display, e.g. tablet
     * 2. an unfolded display from a foldable device
     *
     * This flag returns false the display is:
     * 1. a handheld device display
     * 2. a folded display from a foldable device
     */
    fun isLargeScreenOrUnfoldedDisplay(activity: Activity): Boolean {
        // Note that a foldable is a large screen device if the largest display is large screen.
        // Ths flag is true if it is a large screen device, e.g. tablet, or a foldable device.
        val isLargeScreenOrFoldable = isLargeScreenDevice()
        // For a single display device, this flag is always true.
        // For a multi-display device, it is only true when the current display is the largest
        // display. For the case of foldable, it is true when the display is the unfolded one, and
        // false when it is folded.
        val isSingleDisplayOrUnfolded = isOnWallpaperDisplay(activity)
        return isLargeScreenOrFoldable && isSingleDisplayOrUnfolded
    }

    /**
     * Returns true if this device's screen (or largest screen in case of multiple screen devices)
     * is considered a "Large screen"
     */
    fun isLargeScreenDevice(): Boolean {
        // We need to use MaxDisplay's dimensions because if we're in embedded mode, our window
        // will only be the size of the embedded Activity.
        val maxDisplaysDimension = getRealSize(getWallpaperDisplay())
        val smallestWidth = min(maxDisplaysDimension.x, maxDisplaysDimension.y)
        return Utilities.dpiFromPx(
            smallestWidth.toFloat(),
            context.resources.configuration.densityDpi
        ) >= TABLET_MIN_DPS
    }

    /**
     * Returns `true` if the current display is the wallpaper display on a multi-display device.
     *
     * On a multi-display device the wallpaper display is the largest display while on a single
     * display device the only display is both the wallpaper display and the current display.
     *
     * For single display device, this is always true.
     */
    fun isOnWallpaperDisplay(activity: Activity): Boolean {
        return activity.display?.uniqueId == getWallpaperDisplay().uniqueId
    }

    /** Gets the real width and height of the display. */
    fun getRealSize(display: Display): Point {
        val displayInfo = DisplayInfo()
        display.getDisplayInfo(displayInfo)
        return Point(displayInfo.logicalWidth, displayInfo.logicalHeight)
    }

    /**
     * Returns the smallest display on a device
     *
     * For foldable devices, this method will return the outer display or the primary display when
     * the device is folded. This is always the smallest display in foldable devices.
     */
    fun getSmallerDisplay(): Display {
        val internalDisplays = getInternalDisplays()
        val largestDisplay = getWallpaperDisplay()
        val smallestDisplay = internalDisplays.firstOrNull() { it != largestDisplay }
        return smallestDisplay ?: largestDisplay
    }

    private fun getRealArea(display: Display): Int {
        val displayInfo = DisplayInfo()
        display.getDisplayInfo(displayInfo)
        return displayInfo.logicalHeight * displayInfo.logicalWidth
    }

    private fun getInternalDisplays(): List<Display> {
        val allDisplays: Array<out Display> =
            displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)
        if (allDisplays.isEmpty()) {
            Log.e(TAG, "No displays found on context ${context.applicationContext}")
            throw RuntimeException("No displays found!")
        }
        return allDisplays.filter { it.type == Display.TYPE_INTERNAL }
    }

    fun getInternalDisplaySizes(): List<Point> {
        return getInternalDisplays().map { getRealSize(it) }
    }
}
