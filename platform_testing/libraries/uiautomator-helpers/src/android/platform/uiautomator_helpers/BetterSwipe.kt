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
package android.platform.uiautomator_helpers

import android.animation.TimeInterpolator
import android.graphics.Point
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.os.SystemClock.sleep
import android.platform.uiautomator_helpers.DeviceHelpers.context
import android.platform.uiautomator_helpers.TracingUtils.trace
import android.platform.uiautomator_helpers.WaitUtils.ensureThat
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.TOOL_TYPE_FINGER
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.temporal.ChronoUnit.MILLIS
import java.util.concurrent.atomic.AtomicInteger

private val DEFAULT_DURATION: Duration = Duration.of(500, MILLIS)
private val PAUSE_DURATION: Duration = Duration.of(250, MILLIS)

/**
 * Allows fine control of swipes on the screen.
 *
 * Guarantees that all touches are dispatched, as opposed to [UiDevice] APIs, that might lose
 * touches in case of high load.
 *
 * It is possible to perform operation before the swipe finishes. Timestamp of touch events are set
 * according to initial time and duration.
 *
 * Example usage:
 * ```
 * val swipe = BetterSwipe.from(startPoint).to(intermediatePoint)
 *
 * assertThat(someUiState).isTrue();
 *
 * swipe.to(anotherPoint).release()
 * ```
 */
object BetterSwipe {

    private val lastPointerId = AtomicInteger(0)

    /** Starts a swipe from [start] at the current time. */
    @JvmStatic fun from(start: PointF) = Swipe(start)

    /** Starts a swipe from [start] at the current time. */
    @JvmStatic fun from(start: Point) = Swipe(PointF(start.x.toFloat(), start.y.toFloat()))

    class Swipe internal constructor(start: PointF) {

        private val downTime = SystemClock.uptimeMillis()
        private val pointerId = lastPointerId.incrementAndGet()
        private var lastPoint: PointF = start
        private var lastTime: Long = downTime
        private var released = false

        init {
            log("Touch $pointerId started at $start")
            sendPointer(currentTime = downTime, action = MotionEvent.ACTION_DOWN, point = start)
        }

        /**
         * Swipes from the current point to [end] in [duration] using [interpolator] for the gesture
         * speed. Pass [FLING_GESTURE_INTERPOLATOR] for a fling-like gesture that may leave the
         * surface moving by inertia. Don't use it to drag objects to a precisely specified
         * position. [PRECISE_GESTURE_INTERPOLATOR] will result in a precise drag-like gesture not
         * triggering inertia.
         */
        @JvmOverloads
        fun to(
            end: PointF,
            duration: Duration = DEFAULT_DURATION,
            interpolator: TimeInterpolator = FLING_GESTURE_INTERPOLATOR
        ): Swipe {
            throwIfReleased()
            val stepTime = calculateStepTime()
            log(
                "Swiping from $lastPoint to $end in $duration " +
                    "(step time: ${stepTime.toMillis()}ms)" +
                    "using ${interpolator.javaClass.simpleName}"
            )
            lastTime =
                movePointer(duration = duration, from = lastPoint, to = end, interpolator, stepTime)
            lastPoint = end
            return this
        }

        /**
         * Swipes from the current point to [end] in [duration] using [interpolator] for the gesture
         * speed. Pass [FLING_GESTURE_INTERPOLATOR] for a fling-like gesture that may leave the
         * surface moving by inertia. Don't use it to drag objects to a precisely specified
         * position. [PRECISE_GESTURE_INTERPOLATOR] will result in a precise drag-like gesture not
         * triggering inertia.
         */
        @JvmOverloads
        fun to(
            end: Point,
            duration: Duration = DEFAULT_DURATION,
            interpolator: TimeInterpolator = FLING_GESTURE_INTERPOLATOR
        ): Swipe {
            return to(PointF(end.x.toFloat(), end.y.toFloat()), duration, interpolator)
        }

        /** Sends the last point, simulating a finger pause. */
        fun pause(): Swipe {
            return to(PointF(lastPoint.x, lastPoint.y), PAUSE_DURATION)
        }

        /** Moves the pointer up, finishing the swipe. Further calls will result in an exception. */
        @JvmOverloads
        fun release(sync: Boolean = true) {
            throwIfReleased()
            log("Touch $pointerId released at $lastPoint")
            sendPointer(
                currentTime = lastTime,
                action = MotionEvent.ACTION_UP,
                point = lastPoint,
                sync = sync
            )
            lastPointerId.decrementAndGet()
            released = true
        }

        /** Moves the pointer by [delta], sending the event at [currentTime]. */
        internal fun moveBy(delta: PointF, currentTime: Long, sync: Boolean) {
            val targetPoint = PointF(lastPoint.x + delta.x, lastPoint.y + delta.y)
            sendPointer(currentTime, MotionEvent.ACTION_MOVE, targetPoint, sync)
            lastTime = currentTime
            lastPoint = targetPoint
        }

        private fun throwIfReleased() {
            check(!released) { "Trying to perform a swipe operation after pointer released" }
        }

        private fun sendPointer(
            currentTime: Long,
            action: Int,
            point: PointF,
            sync: Boolean = true
        ) {
            val event = getMotionEvent(downTime, currentTime, action, point, pointerId)

            try {
                trySendMotionEvent(event, sync)
            } finally {
                event.recycle()
            }
        }

        private fun trySendMotionEvent(event: MotionEvent, sync: Boolean) {
            ensureThat(
                    "Injecting motion event",
                    /* timeout= */ Duration.ofMillis(INJECT_EVENT_TIMEOUT_MILLIS),
                    /* errorProvider= */ {
                        "Injecting motion event $event failed after retrying for 10 seconds, " +
                            "see logcat for the error"
                    }
                )
                /* condition= */ {
                    try {
                        return@ensureThat getInstrumentation()
                            .uiAutomation
                            .injectInputEvent(event, sync, /* waitForAnimations= */ false)
                    } catch (t: Throwable) {
                        throw RuntimeException(t)
                    }
                }
        }

        /** Returns the time when movement finished. */
        private fun movePointer(
            duration: Duration,
            from: PointF,
            to: PointF,
            interpolator: TimeInterpolator,
            stepTime: Duration
        ): Long {
            val stepTimeMs = stepTime.toMillis()
            val durationMs = duration.toMillis()
            val steps = durationMs / stepTimeMs
            val startTime = lastTime
            var currentTime = lastTime
            val startRealTime = SystemClock.uptimeMillis()
            for (i in 0 until steps) {
                // The next pointer event shouldn't be dispatched before its time. However, the code
                // below might take time. So the time to sleep is calculated dynamically, based on
                // the expected time of this event.
                val timeToWait = stepTimeMs * i - (SystemClock.uptimeMillis() - startRealTime)
                if (timeToWait > 0) sleep(stepTimeMs)
                currentTime += stepTimeMs
                val progress = interpolator.getInterpolation(i / (steps - 1f))
                val point = from.lerp(progress, to)
                sendPointer(currentTime, MotionEvent.ACTION_MOVE, point)
            }
            assertThat(currentTime).isEqualTo(startTime + stepTimeMs * steps)
            return currentTime
        }
    }

    /** Collection of swipes. This can be used to simulate multitouch. */
    class Swipes internal constructor(vararg starts: PointF) {

        private var lastTime: Long = SystemClock.uptimeMillis()
        private val swipes: List<Swipe> = starts.map { Swipe(it) }

        /** Moves all the swipes by [delta], in [duration] time with constant speed. */
        fun moveBy(delta: PointF, duration: Duration = DEFAULT_DURATION): Swipes {
            log("Moving ${swipes.size} touches by $delta")

            val stepTimeMs = calculateStepTime().toMillis()
            val durationMs = duration.toMillis()
            val steps = durationMs / stepTimeMs
            val startTime = lastTime
            var currentTime = lastTime
            val stepDelta = PointF(delta.x / steps, delta.y / steps)
            (1..steps).forEach { _ ->
                sleep(stepTimeMs)
                currentTime += stepTimeMs
                swipes.forEach { swipe ->
                    // Sending the move events as not "sync". Otherwise the method waits for them
                    // to be displatched. As here we're sending many that are supposed to happen at
                    // the same time, we don't want the method to
                    // wait after each single injection.
                    swipe.moveBy(stepDelta, currentTime, sync = false)
                }
            }
            assertThat(currentTime).isEqualTo(startTime + stepTimeMs * steps)
            lastTime = currentTime
            return this
        }

        /** Moves pointers up, finishing the swipe. Further calls will result in an exception. */
        fun release() {
            swipes.forEach { it.release(sync = false) }
        }
    }

    private fun log(s: String) = Log.d("BetterSwipe", s)
}

private fun getMotionEvent(
    downTime: Long,
    eventTime: Long,
    action: Int,
    p: PointF,
    pointerId: Int,
): MotionEvent {
    val properties =
        MotionEvent.PointerProperties().apply {
            id = pointerId
            toolType = TOOL_TYPE_FINGER
        }
    val coordinates =
        MotionEvent.PointerCoords().apply {
            pressure = 1f
            size = 1f
            x = p.x
            y = p.y
        }
    return MotionEvent.obtain(
        /* downTime= */ downTime,
        /* eventTime= */ eventTime,
        /* action= */ action,
        /* pointerCount= */ 1,
        /* pointerProperties= */ arrayOf(properties),
        /* pointerCoords= */ arrayOf(coordinates),
        /* metaState= */ 0,
        /* buttonState= */ 0,
        /* xPrecision= */ 1.0f,
        /* yPrecision= */ 1.0f,
        /* deviceId= */ 0,
        /* edgeFlags= */ 0,
        /* source= */ InputDevice.SOURCE_TOUCHSCREEN,
        /* flags= */ 0
    )
}

private fun PointF.lerp(amount: Float, b: PointF) =
    PointF(lerp(x, b.x, amount), lerp(y, b.y, amount))

private fun lerp(start: Float, stop: Float, amount: Float): Float = start + (stop - start) * amount

private fun calculateStepTime(displayId: Int = DEFAULT_DISPLAY): Duration {
    return getTimeBetweenFrames(displayId).dividedBy(2)
}

private fun getTimeBetweenFrames(displayId: Int): Duration {
    return trace("getMillisBetweenFrames") {
        val displayManager =
            context.getSystemService(DisplayManager::class.java)
                ?: error("Couldn't get DisplayManager")
        val display = displayManager.getDisplay(displayId)
        val framesPerSecond = display.refreshRate // Frames per second
        val millisBetweenFrames = 1000 / framesPerSecond
        Duration.ofMillis(millisBetweenFrames.toLong())
    }
}

/**
 * Interpolator for a fling-like gesture that may leave the surface moving by inertia. Don't use it
 * to drag objects to a precisely specified position.
 */
val FLING_GESTURE_INTERPOLATOR = LinearInterpolator()

/** Interpolator for a precise drag-like gesture not triggering inertia. */
val PRECISE_GESTURE_INTERPOLATOR = DecelerateInterpolator()

private const val INJECT_EVENT_TIMEOUT_MILLIS = 10_000L
