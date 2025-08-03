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

package android.platform.uiautomator_helpers

import android.graphics.Rect
import android.platform.uiautomator_helpers.DeviceHelpers.context
import android.platform.uiautomator_helpers.DeviceHelpers.uiDevice
import android.platform.uiautomator_helpers.SwipeUtils.calculateStartEndPoint
import android.platform.uiautomator_helpers.TracingUtils.trace
import android.util.TypedValue
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * A fling utility that should be used instead of [UiObject2.fling] for more reliable flings.
 *
 * See [BetterSwipe] for more details on the problem of [UiObject2.fling].
 */
object BetterFling {
    private const val DEFAULT_FLING_MARGIN_DP = 30
    private const val DEFAULT_PERCENTAGE = 1.0f
    private val DEFAULT_FLING_DURATION = Duration.of(100, ChronoUnit.MILLIS)
    private val DEFAULT_WAIT_TIMEOUT = Duration.of(5, ChronoUnit.SECONDS)

    /**
     * Flings [percentage] of [rect] in the given [direction], with [marginDp] margins.
     *
     * Note that when direction is [Direction.DOWN], the scroll will be from the top to the bottom
     * (to scroll down).
     */
    @JvmStatic
    @JvmOverloads
    fun fling(
        rect: Rect,
        direction: Direction,
        duration: Duration = DEFAULT_FLING_DURATION,
        marginDp: Int = DEFAULT_FLING_MARGIN_DP,
        percentage: Float = DEFAULT_PERCENTAGE,
    ) {
        val (start, stop) =
            calculateStartEndPoint(rect, direction, percentage, marginDp.dpToPx().toInt())

        trace("Fling $start -> $stop") {
            uiDevice.performActionAndWait(
                { BetterSwipe.from(start).to(stop, duration).release() },
                Until.scrollFinished(Direction.reverse(direction)),
                DEFAULT_WAIT_TIMEOUT.toMillis()
            )
        }
    }

    private fun Number.dpToPx(): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            context.resources.displayMetrics,
        )
    }
}
