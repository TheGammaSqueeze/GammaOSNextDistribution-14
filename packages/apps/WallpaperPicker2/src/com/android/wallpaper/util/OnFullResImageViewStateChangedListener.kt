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
 */
package com.android.wallpaper.util

import android.graphics.PointF
import android.os.Handler
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

/**
 * Listens to the state change of [SubsamplingScaleImageView]. This also calls a "debounced" center
 * changed event which only fires a [DEBOUNCE_THRESHOLD_MILLIS] time after the last center changed
 * event from consecutive center changed events.
 */
abstract class OnFullResImageViewStateChangedListener :
    SubsamplingScaleImageView.OnStateChangedListener {
    companion object {
        private const val DEBOUNCE_THRESHOLD_MILLIS: Long = 100
    }

    private val mHandler = Handler()

    /**
     * Fires a [DEBOUNCE_THRESHOLD_MILLIS] time after the last center changed event from consecutive
     * center changed events.
     */
    abstract fun onDebouncedCenterChanged(newCenter: PointF?, origin: Int)

    /**
     * When center changed, any undone delayed calls will be removed. And another delayed call for
     * the "debounced" center changed event is scheduled.
     */
    override fun onCenterChanged(newCenter: PointF, origin: Int) {
        mHandler.removeCallbacksAndMessages(null)
        mHandler.postDelayed(
            { onDebouncedCenterChanged(newCenter, origin) },
            DEBOUNCE_THRESHOLD_MILLIS
        )
    }

    /** Do nothing intended. */
    override fun onScaleChanged(newScale: Float, origin: Int) {}
}
