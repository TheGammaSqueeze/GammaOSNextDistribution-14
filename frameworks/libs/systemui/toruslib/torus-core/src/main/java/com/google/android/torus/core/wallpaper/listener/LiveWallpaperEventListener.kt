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

package com.google.android.torus.core.wallpaper.listener

import android.app.WallpaperColors
import android.os.Bundle

/**
 * Interface that is used to implement specific wallpaper callbacks like offset change (user swipes
 * between home pages), when the preview state has changed or when the zoom state has changed.
 */
interface LiveWallpaperEventListener {
    companion object {
        const val WAKE_ACTION_LOCATION_X: String = "WAKE_ACTION_LOCATION_X"
        const val WAKE_ACTION_LOCATION_Y: String = "WAKE_ACTION_LOCATION_Y"
        const val SLEEP_ACTION_LOCATION_X: String = "SLEEP_ACTION_LOCATION_X"
        const val SLEEP_ACTION_LOCATION_Y: String = "SLEEP_ACTION_LOCATION_Y"
    }

    /**
     * Called when the wallpaper has been scrolled (usually when the user scroll between pages in
     * the home of the launcher). This only tracts the horizontal scroll.
     *
     * @param xOffset The current offset of the scroll. The value is normalize between [0,1].
     * @param xOffsetStep How is stepped the scroll. If you invert [xOffsetStep] you get the
     * number of pages in the scrolling area.
     */
    fun onOffsetChanged(xOffset: Float, xOffsetStep: Float)

    /**
     * Called when the zoom level of the wallpaper is changing.
     *
     * @param zoomLevel A value between 0 and 1 that tells how much the wallpaper should be zoomed
     * out: if 0, the wallpaper should be in normal state; if 1 the wallpaper should be zoomed out.
     */
    fun onZoomChanged(zoomLevel: Float)

    /**
     * Call when the wallpaper was set, and then is reapplied. This means that the wallpaper was
     * set and is being set again. This is useful to know if the wallpaper settings have to be
     * reapplied again (i.e. if the user enters the wallpaper picker and picks the same wallpaper,
     * changes the settings and sets the wallpaper again).
     */
    fun onWallpaperReapplied()

    /**
     * Called when the Wallpaper colors need to be computed you can create a [WallpaperColors]
     * instance using the [WallpaperColors.fromBitmap] function and passing a bitmap that
     * represents the wallpaper (i.e. the gallery thumbnail) or use the [WallpaperColors]
     * constructor and pass the primary, secondary and tertiary colors. This method is specially
     * important since the UI will change their colors based on what is returned here.
     *
     * @return The colors that represent the wallpaper; null if you want the System to take
     * care of the colors.
     */
    fun computeWallpaperColors(): WallpaperColors?

    /**
     * Called when the wallpaper receives the preview information (asynchronous call).
     *
     * @param extras the bundle of the preview information. The key "which_preview" can be used to
     * retrieve a string value (ex. main_preview_home) that specifies which preview the engine
     * is referring to.
     */
    fun onPreviewInfoReceived(extras: Bundle?) {}

    /**
     * Called when the device is activated from a sleep/AOD state.
     *
     * @param extras contains the location of the action that caused the wake event:
     * - [LiveWallpaperEventListener.WAKE_ACTION_LOCATION_X]: the X screen location (in Pixels). if
     * the value is not included or is -1, the X screen location is unknown.
     * - [LiveWallpaperEventListener.WAKE_ACTION_LOCATION_Y]: the Y screen location (in Pixels). if
     * the value is not included or is -1, the Y screen location is unknown.
     */
    fun onWake(extras: Bundle)

    /**
     * Called when the device enters a sleep/AOD state.
     *
     * @param extras contains the location of the action that caused the sleep event:
     * - [LiveWallpaperEventListener.SLEEP_ACTION_LOCATION_X]: the X screen location (in Pixels). if
     * the value is not included or is -1, the X screen location is unknown.
     * - [LiveWallpaperEventListener.SLEEP_ACTION_LOCATION_Y]: the Y screen location (in Pixels). if
     * the value is not included or is -1, the Y screen location is unknown.
     */
    fun onSleep(extras: Bundle)

    /**
     * Indicates whether the zoom animation should be handled in WindowManager. Preferred to be set
     * to true to avoid pressuring GPU.
     *
     * See [WallpaperService.shouldZoomOutWallpaper].
     */
    fun shouldZoomOutWallpaper() = false
}
