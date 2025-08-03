package android.platform.uiautomator_helpers

import android.graphics.Point
import android.graphics.Rect
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Direction.DOWN
import androidx.test.uiautomator.Direction.LEFT
import androidx.test.uiautomator.Direction.RIGHT
import androidx.test.uiautomator.Direction.UP

/** Common utils to perform swipes. */
internal object SwipeUtils {

    /**
     * Calculates start and end point taking into consideration first [marginPx], then [percent].
     */
    fun calculateStartEndPoint(
        rawBound: Rect,
        direction: Direction,
        percent: Float = 1.0f,
        marginPx: Int = 0
    ): Pair<Point, Point> {
        val bounds = Rect(rawBound)
        bounds.inset(marginPx, marginPx)
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        return when (direction) {
            LEFT -> {
                Point(bounds.right, centerY) to
                    Point(bounds.right - (bounds.width() * percent).toInt(), centerY)
            }
            RIGHT -> {
                Point(bounds.left, centerY) to
                    Point(bounds.left + (bounds.width() * percent).toInt(), centerY)
            }
            UP -> {
                Point(centerX, bounds.bottom) to
                    Point(centerX, bounds.bottom - (bounds.height() * percent).toInt())
            }
            DOWN -> {
                Point(centerX, bounds.top) to
                    Point(centerX, bounds.top + (bounds.height() * percent).toInt())
            }
        }
    }
}
