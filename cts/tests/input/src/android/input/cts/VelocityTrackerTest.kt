/*
 * Copyright (C) 2008 The Android Open Source Project
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
package android.input.cts

import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test [VelocityTracker].
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class VelocityTrackerTest {
    private lateinit var mPlanarVelocityTracker: VelocityTracker
    private lateinit var mScrollVelocityTracker: VelocityTracker

    // Current axis value, velocity and acceleration.
    private var mTime: Long = 0
    private var mLastTime: Long = 0

    private var mPx = 0f
    private var mPy = 0f
    private var mScroll = 0f

    private var mVx = 0f
    private var mVy = 0f
    private var mVscroll = 0f

    private var mAx = 0f
    private var mAy = 0f
    private var mAscroll = 0f

    private val mOldThreadPolicy = StrictMode.getThreadPolicy()
    private val mOldVmPolicy = StrictMode.getVmPolicy()

    @Before
    fun setup() {
        mPlanarVelocityTracker = VelocityTracker.obtain()
        mScrollVelocityTracker = VelocityTracker.obtain()

        mTime = 1000
        mLastTime = 0
        mPx = 300f
        mPy = 600f
        mVx = 0f
        mVy = 0f
        mAx = 0f
        mAy = 0f
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyDeath()
                .build()
        )
    }

    @After
    fun teardown() {
        mPlanarVelocityTracker.recycle()
        mScrollVelocityTracker.recycle()
        StrictMode.setThreadPolicy(mOldThreadPolicy)
        StrictMode.setVmPolicy(mOldVmPolicy)
    }

    @Test
    fun testNoMovement() {
        move(100, 10)
        assertVelocity(TOLERANCE_EXACT, "Expect exact bound when no movement occurs.")
    }

    @Test
    fun testLinearMovement() {
        mVx = 2.0f
        mVy = -4.0f
        mVscroll = 3.0f
        move(100, 10)
        assertVelocity(TOLERANCE_TIGHT, "Expect tight bound for linear motion.")
    }

    @Test
    fun testAcceleratingMovement() {
        // A very good velocity tracking algorithm will produce a tight bound on
        // simple acceleration.  Certain alternate algorithms will fare less well but
        // may be more stable in the presence of bad input data.
        mVx = 2.0f
        mVy = -4.0f
        mVscroll = 3.0f
        mAx = 1.0f
        mAy = -0.5f
        mAscroll = 2.0f
        move(200, 10)
        assertVelocity(TOLERANCE_WEAK, "Expect weak bound when there is acceleration.")
    }

    @Test
    fun testDeceleratingMovement() {
        // A very good velocity tracking algorithm will produce a tight bound on
        // simple acceleration.  Certain alternate algorithms will fare less well but
        // may be more stable in the presence of bad input data.
        mVx = 2.0f
        mVy = -4.0f
        mVscroll = 3.0f
        mAx = -1.0f
        mAy = 0.2f
        mAscroll = -0.5f
        move(200, 10)
        assertVelocity(TOLERANCE_WEAK, "Expect weak bound when there is deceleration.")
    }

    @Test
    fun testLinearSharpDirectionChange() {
        // After a sharp change of direction we expect the velocity to eventually
        // converge but it might take a moment to get there.
        mVx = 2.0f
        mVy = -4.0f
        mVscroll = 3.0f
        move(100, 10)
        assertVelocity(TOLERANCE_TIGHT, "Expect tight bound for linear motion.")
        mVx = -1.0f
        mVy = -3.0f
        mVscroll = -2.0f
        move(100, 10)
        assertVelocity(TOLERANCE_WEAK, "Expect weak bound after 100ms of new direction.")
        move(100, 10)
        assertVelocity(TOLERANCE_TIGHT, "Expect tight bound after 200ms of new direction.")
    }

    @Test
    fun testLinearSharpDirectionChangeAfterALongPause() {
        // Should be able to get a tighter bound if there is a pause before the
        // change of direction.
        mVx = 2.0f
        mVy = -4.0f
        mVscroll = 3.0f
        move(100, 10)
        assertVelocity(TOLERANCE_TIGHT, "Expect tight bound for linear motion.")
        pause(100)
        mVx = -1.0f
        mVy = -3.0f
        mVscroll = -2.0f
        move(100, 10)
        assertVelocity(
            TOLERANCE_TIGHT,
                "Expect tight bound after a 100ms pause and 100ms of new direction."
        )
    }

    @Test
    fun testChangingAcceleration() {
        // In real circumstances, the acceleration changes continuously throughout a
        // gesture.  Try to model this and see how the algorithm copes.
        mVx = 2.0f
        mVy = -4.0f
        mVscroll = -2.0f
        for (change in floatArrayOf(1f, -2f, -3f, -1f, 1f)) {
            mAx += 1.0f * change
            mAy += -0.5f * change
            mAscroll += 2.0f * change
            move(30, 10)
        }
        assertVelocity(
            TOLERANCE_VERY_WEAK,
                "Expect weak bound when there is changing acceleration."
        )
    }

    @Test
    fun testUsesRawCoordinates() {
        val vt = VelocityTracker.obtain()
        val numEvents = 5
        val downTime = SystemClock.uptimeMillis()
        for (i in 0 until numEvents) {
            val eventTime = downTime + i * 10
            val action = if (i == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE
            val event = MotionEvent.obtain(downTime, eventTime, action, 0f, 0f, 0)
            // MotionEvent translation/offset is only applied to pointer sources, like touchscreens.
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            event.offsetLocation((i * 10).toFloat(), (i * 10).toFloat())
            vt.addMovement(event)
        }
        vt.computeCurrentVelocity(1000)
        if (vt.xVelocity == 0f || vt.yVelocity == 0f) {
            fail(
                "VelocityTracker is using raw coordinates," +
                 " but it should be using adjusted coordinates"
            )
        }
    }

    @Test
    fun testIsAxisSupported() {
        val expectedSupportedAxes =
            setOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_SCROLL)
        val vt = VelocityTracker.obtain()
        // Note that we are testing up to the max possible axis value, plus 3 more values. We are
        // going beyond the max value to add a bit more protection. "3" is chosen arbitrarily to
        // cover a few more values beyond the max.
        for (axis in 0..largestDefinedMotionEventAxisValue + 3) {
            val expectedSupport = expectedSupportedAxes.contains(axis)
            if (vt.isAxisSupported(axis) != expectedSupport) {
                fail("Unexpected support found for axis $axis (expected support=$expectedSupport)")
            }
        }
    }

    @Test
    fun testVelocityCallsWithUnusedPointers() {
        mVx = 2.0f
        mVy = -4.0f
        mVscroll = 3.0f

        move(100, 10)

        assertThat(mPlanarVelocityTracker.getXVelocity(1)).isZero()
        assertThat(mPlanarVelocityTracker.getYVelocity(2)).isZero()
        assertThat(mScrollVelocityTracker.getAxisVelocity(MotionEvent.AXIS_SCROLL, 100)).isZero()
    }

    @Test
    fun testVelocityCallsForUnsupportedAxis() {
        assertThat(mPlanarVelocityTracker.getAxisVelocity(MotionEvent.AXIS_DISTANCE)).isZero()
        assertThat(mPlanarVelocityTracker.getAxisVelocity(MotionEvent.AXIS_GENERIC_10)).isZero()
    }

    private fun move(duration: Long, step: Long) {
        var remainingDuration = duration
        addMovement()
        while (remainingDuration > 0) {
            remainingDuration -= step
            mTime += step

            mPx += (mAx / 2 * step + mVx) * step
            mPy += (mAy / 2 * step + mVy) * step
            // Note that we are not incrementing the scroll-value. Instead, we are overriding the
            // previous value with the new one. This is in accordance to the differential nature of
            // the scroll axis. That is, the axis reports differential values since previous motion
            // events, instead of absolute values.
            mScroll = (mAscroll / 2 * step + mVscroll) * step

            mVx += mAx * step
            mVy += mAy * step
            mVscroll += mAscroll * step
            addMovement()
        }
    }

    private fun pause(duration: Long) {
        mTime += duration
    }

    private fun createScrollMotionEvent(): MotionEvent {
        val props = MotionEvent.PointerProperties()
        props.id = 0
        val coords = MotionEvent.PointerCoords()
        coords.setAxisValue(MotionEvent.AXIS_SCROLL, mScroll)
        return MotionEvent.obtain(
                /* downTime */
                0,
                mTime,
                MotionEvent.ACTION_SCROLL,
                /* pointerCount */
                1,
                arrayOf(props), arrayOf(coords),
                /* metaState */
                0,
                /* buttonState */
                0,
                /* xPrecision */
                0f,
                /* yPrecision */
                0f,
                /* deviceId */
                1,
                /* edgeFlags */
                0,
                InputDevice.SOURCE_ROTARY_ENCODER,
                /* flags */
                0)
    }

    private fun addMovement() {
        if (mTime <= mLastTime) {
            return
        }

        var ev = MotionEvent.obtain(0L, mTime, MotionEvent.ACTION_MOVE, mPx, mPy, 0)
        mPlanarVelocityTracker.addMovement(ev)
        ev.recycle()

        ev = createScrollMotionEvent()
        mScrollVelocityTracker.addMovement(ev)
        ev.recycle()

        mLastTime = mTime

        mPlanarVelocityTracker.computeCurrentVelocity(1)
        mScrollVelocityTracker.computeCurrentVelocity(1)

        if (DEBUG) {
            val estimatedVx = mPlanarVelocityTracker.xVelocity
            val estimatedVy = mPlanarVelocityTracker.yVelocity
            val estimatedVscroll = mPlanarVelocityTracker.getAxisVelocity(MotionEvent.AXIS_SCROLL)
            logTrackingInfo(MotionEvent.AXIS_X, mTime, mPx, mVx, estimatedVx, mAx)
            logTrackingInfo(MotionEvent.AXIS_Y, mTime, mPy, mVy, estimatedVy, mAy)
            logTrackingInfo(
                    MotionEvent.AXIS_SCROLL,
                    mTime,
                    mScroll,
                    mVscroll,
                    estimatedVscroll,
                    mAscroll
            )
        }
    }

    private fun assertVelocity(tolerance: Float, message: String) {
        mPlanarVelocityTracker.computeCurrentVelocity(1)
        mScrollVelocityTracker.computeCurrentVelocity(1)

        assertVelocity({ mPlanarVelocityTracker.xVelocity }, tolerance, mVx, message)
        assertVelocity(mPlanarVelocityTracker, MotionEvent.AXIS_X, tolerance, mVx, message)

        assertVelocity({ mPlanarVelocityTracker.yVelocity }, tolerance, mVy, message)
        assertVelocity(mPlanarVelocityTracker, MotionEvent.AXIS_Y, tolerance, mVy, message)

        assertVelocity(
                mScrollVelocityTracker,
                MotionEvent.AXIS_SCROLL,
                tolerance,
                mVscroll,
                message
        )
    }

    companion object {
        private const val TAG = "VelocityTrackerTest"

        // To enable these logs, run:
        // 'adb shell setprop log.tag.VelocityTrackerTest DEBUG' (requires restart)
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
        private const val TOLERANCE_EXACT = 0.01f
        private const val TOLERANCE_TIGHT = 0.05f
        private const val TOLERANCE_WEAK = 0.1f
        private const val TOLERANCE_VERY_WEAK = 0.2f
        private fun assertVelocity(
                velocityTracker: VelocityTracker?,
                axis: Int,
                tolerance: Float,
                expectedVelocity: Float,
                message: String
        ) {
            assertVelocity(
                    { velocityTracker!!.getAxisVelocity(axis) },
                tolerance,
                expectedVelocity,
                message
            )
        }

        private fun assertVelocity(
                estimatedVelocitySupplier: () -> Float,
                tolerance: Float,
                expectedVelocity: Float,
                message: String
        ) {
            val estimatedVelocity = estimatedVelocitySupplier()
            val error = error(expectedVelocity, estimatedVelocity)
            if (error > tolerance) {
                fail(String.format(
                    "Velocity exceeds tolerance of %6.1f%%: " +
                        "expected=%6.1f. " +
                        "actual=%6.1f (%6.1f%%). %s",
                        tolerance * 100f,
                    expectedVelocity,
                    estimatedVelocity,
                    error * 100f,
                    message
                ))
            }
        }

        private fun error(expected: Float, actual: Float): Float {
            val absError = Math.abs(actual - expected)
            if (absError < 0.001f) {
                return 0f
            }
            return if (Math.abs(expected) < 0.001f) {
                1f
            } else {
                absError / Math.abs(expected)
            }
        }

        private fun logTrackingInfo(
                axis: Int,
                time: Long,
                value: Float,
                actualV: Float,
                estimatedV: Float,
                acc: Float
        ) {
            Log.d(TAG, String.format(
                    "[%d] %s: val=%6.1f, v=%6.1f, acc=%6.1f, estimatedV=%6.1f (%6.1f%%)",
                    time,
                    MotionEvent.axisToString(axis),
                    value,
                    actualV,
                    acc,
                    estimatedV,
                    error(actualV, estimatedV) * 100f
            ))
        }

        private val largestDefinedMotionEventAxisValue: Int
            get() {
                var i = 0
                while (Integer.toString(i) != MotionEvent.axisToString(i)) {
                    i++
                }
                return i - 1
            }
    }
}
