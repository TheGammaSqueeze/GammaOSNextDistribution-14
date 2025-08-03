package android.platform.uiautomator_helpers

import android.graphics.Rect
import android.platform.uiautomator_helpers.DeviceHelpers.uiDevice
import android.platform.uiautomator_helpers.SwipeUtils.calculateStartEndPoint
import android.platform.uiautomator_helpers.TracingUtils.trace
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.time.Duration

/**
 * A scroll utility that should be used instead of [UiObject2.scroll] for more reliable scrolls.
 *
 * See [BetterSwipe] for more details on the problem of [UiObject2.scroll].
 */
object BetterScroll {
    private const val DEFAULT_PERCENTAGE = 0.8f
    private val DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(1)

    /**
     * Scrolls [percentage] of [rect] in the given [direction].
     *
     * Note that when direction is [Direction.DOWN], the scroll will be from the top to the bottom
     * (to scroll down).
     */
    @JvmStatic
    @JvmOverloads
    fun scroll(
        rect: Rect,
        direction: Direction,
        percentage: Float = DEFAULT_PERCENTAGE,
    ) {
        val (start, stop) = calculateStartEndPoint(rect, direction, percentage)

        trace("Scrolling $start -> $stop") {
            uiDevice.performActionAndWait(
                { BetterSwipe.from(start).to(stop).pause().release() },
                Until.scrollFinished(Direction.reverse(direction)),
                DEFAULT_WAIT_TIMEOUT.toMillis()
            )
        }
    }
}
