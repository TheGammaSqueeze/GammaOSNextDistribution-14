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

package android.input.cts

import android.view.InputDevice.SOURCE_STYLUS
import android.view.InputDevice.SOURCE_TOUCHSCREEN
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.MotionEvent.TOOL_TYPE_FINGER
import android.view.MotionEvent.TOOL_TYPE_STYLUS
import android.view.MotionPredictor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private val STYLUS_DEVICE_ID = 0
private val TOUCH_DEVICE_ID = 1

private fun getStylusMotionEvent(
        eventTime: Duration,
        action: Int,
        x: Float,
        y: Float,
        tilt: Float = 0.0f,
        orientation: Float = 0.0f,
        ): MotionEvent {
    return getMotionEvent(
            eventTime, action, STYLUS_DEVICE_ID, x, y, tilt, orientation,
            SOURCE_STYLUS, TOOL_TYPE_STYLUS)
}

private fun getTouchMotionEvent(
        eventTime: Duration,
        action: Int,
        x: Float,
        y: Float,
        tilt: Float = 0.0f,
        orientation: Float = 0.0f,
        ): MotionEvent {
    return getMotionEvent(
            eventTime, action, TOUCH_DEVICE_ID, x, y, tilt, orientation,
            SOURCE_TOUCHSCREEN, TOOL_TYPE_FINGER)
}

private fun getMotionEvent(
        eventTime: Duration,
        action: Int,
        deviceId: Int,
        x: Float,
        y: Float,
        tilt: Float,
        orientation: Float,
        source: Int,
        toolType: Int,
        ): MotionEvent {
    val pointerCount = 1
    val properties = arrayOfNulls<MotionEvent.PointerProperties>(pointerCount)
    val coords = arrayOfNulls<MotionEvent.PointerCoords>(pointerCount)

    for (i in 0 until pointerCount) {
        properties[i] = PointerProperties()
        properties[i]!!.id = i
        properties[i]!!.toolType = toolType
        coords[i] = PointerCoords()
        coords[i]!!.x = x
        coords[i]!!.y = y
        coords[i]!!.setAxisValue(MotionEvent.AXIS_TILT, tilt)
        coords[i]!!.orientation = orientation
    }

    return MotionEvent.obtain(
            0, // downTime
            eventTime.toMillis(),
            action,
            properties.size,
            properties,
            coords,
            0, // metaState
            0, // buttonState
            0f, // xPrecision
            0f, // yPrecision
            deviceId,
            0, // edgeFlags
            source,
            0 // flags
    )
}

/**
 * Test {@link android.view.MotionPredictor}.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class MotionPredictorTest {
    val context = InstrumentationRegistry.getInstrumentation().getTargetContext()

    @Test
    fun testFeatureDisabled() {
        val predictor = MotionPredictor(context)
        assumeFalse(predictor.isPredictionAvailable(STYLUS_DEVICE_ID, SOURCE_STYLUS))
        // Now that we know prediction is off, feed some events and ensure prediction doesn't work

        var eventTime = Duration.ofMillis(0)
        predictor.record(getStylusMotionEvent(eventTime, ACTION_DOWN, x = 10f, y = 20f))

        eventTime += Duration.ofMillis(8)
        // Send MOVE event and then call .predict
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, x = 11f, y = 24f))

        // Check that predictions aren't generated
        assertNull(predictor.predict(Duration.ofMillis(10).toNanos()))
    }

    /**
     * A still motion should remain still.
     */
    @Test
    fun testNoMotion() {
        val predictor = MotionPredictor(context)
        assumeTrue(predictor.isPredictionAvailable(STYLUS_DEVICE_ID, SOURCE_STYLUS))

        // One-time: send a DOWN event
        var eventTime = Duration.ofMillis(0) // t= 0 ms
        predictor.record(getStylusMotionEvent(eventTime, ACTION_DOWN, x = 10f, y = 20f))

        // Send a few coordinates
        eventTime += Duration.ofMillis(8) // t= 8 ms
        // Send MOVE event and then call .predict
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, x = 10f, y = 20f))

        eventTime += Duration.ofMillis(8) // t= 16 ms
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, x = 10f, y = 20f))

        val predicted = predictor.predict(Duration.ofMillis(24).toNanos())
        // There should either be no predictions (indicating no movement), or a prediction at the
        // same location.
        if (predicted != null) {
            val xDelta = 1f
            val yDelta = 2f
            assertEquals(10f, predicted.getX(), xDelta)
            assertEquals(20f, predicted.getY(), yDelta)
            assertEquals(STYLUS_DEVICE_ID, predicted.deviceId)
        }
    }

    /**
     * A linear motion should be predicted to remain linear.
     */
    @Test
    fun testLinearMotion() {
        val predictor = MotionPredictor(context)
        assumeTrue(predictor.isPredictionAvailable(STYLUS_DEVICE_ID, SOURCE_STYLUS))

        // One-time: send a DOWN event
        var eventTime = Duration.ofMillis(0) // t= 0 ms
        predictor.record(getStylusMotionEvent(eventTime, ACTION_DOWN, x = 10f, y = 10f))

        // Send a few coordinates
        eventTime += Duration.ofMillis(8) // t= 8 ms
        // Send MOVE event and then call .predict
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, x = 10f, y = 20f))

        eventTime += Duration.ofMillis(8) // t= 16 ms
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, x = 10f, y = 30f))

        val predicted = predictor.predict(Duration.ofMillis(24).toNanos())
        assertNotNull(predicted)

        // Calculate the expected location based on the timestamp of the prediction.
        val yMovement = 10 / 8f // px per ms
        // The last event was at t=16, y=30.
        val expectedY = 30 + ((predicted!!.getEventTime() - 16) * yMovement)

        val xDelta = 5f
        val yDelta = 15f
        assertEquals(10f, predicted.getX(), xDelta)
        assertEquals(expectedY, predicted.getY(), yDelta)
        assertEquals(STYLUS_DEVICE_ID, predicted.deviceId)
    }

    /**
     * Tilt and orientation should be set for predictions.
     */
    @Test
    fun testTiltOrientation() {
        val predictor = MotionPredictor(context)
        assumeTrue(predictor.isPredictionAvailable(STYLUS_DEVICE_ID, SOURCE_STYLUS))

        val tilt = Math.PI.toFloat() / 4
        val orientation = -Math.PI.toFloat() / 4

        var eventTime = Duration.ofMillis(0) // t= 0 ms
        predictor.record(
            getStylusMotionEvent(eventTime, ACTION_DOWN, x = 10f, y = 20f, tilt, orientation)
        )

        // Send a few coordinates
        eventTime += Duration.ofMillis(8) // t= 8 ms
        predictor.record(
            getStylusMotionEvent(eventTime, ACTION_MOVE, x = 10f, y = 20f, tilt, orientation)
        )

        eventTime += Duration.ofMillis(8) // t= 16 ms
        predictor.record(
            getStylusMotionEvent(eventTime, ACTION_MOVE, x = 10f, y = 30f, tilt, orientation)
        )

        // Ask for a prediction.
        val predicted = predictor.predict(Duration.ofMillis(24).toNanos())
        assertNotNull(predicted)

        // The predicted tilt and orientation should closely match that of the recorded events.
        val tolerance = Math.PI.toFloat() / 16
        assertEquals(tilt, predicted!!.getAxisValue(MotionEvent.AXIS_TILT, 0), tolerance)
        assertEquals(orientation, predicted.getOrientation(0), tolerance)
    }

    /**
     * If an inconsistent MotionEvent stream is provided, an IllegalArgumentException should be
     * thrown.
     */
    @Test
    fun testInconsistentEventStream() {
        val predictor = MotionPredictor(context)
        assumeTrue(predictor.isPredictionAvailable(STYLUS_DEVICE_ID, SOURCE_STYLUS))

        // Send some events from the stylus device
        var eventTime = Duration.ofMillis(0) // t= 0 ms
        predictor.record(getStylusMotionEvent(eventTime, ACTION_DOWN, x = 10f, y = 20f))

        // Send a few coordinates
        eventTime += Duration.ofMillis(8) // t= 8 ms
        // Send MOVE event and then call .predict
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, x = 10f, y = 21f))

        // Now send an event from touch device without first ending the stylus gesture. The
        // new event should be rejected with an exception
        try {
            eventTime += Duration.ofMillis(8) // t= 16 ms
            predictor.record(getTouchMotionEvent(eventTime, ACTION_DOWN, x = 20f, y = 30f))
            fail("Expected an IllegalArgumentException due to an inconsistent event stream")
        } catch (e: IllegalArgumentException) {
            // This is expected!
        }

        // Complete the stylus gesture
        eventTime += Duration.ofMillis(8) // t= 24 ms
        predictor.record(getStylusMotionEvent(eventTime, ACTION_UP, x = 10f, y = 21f))

        // Now that there's no active gesture, the subsequent events from another device
        // should not throw.
        eventTime += Duration.ofMillis(8) // t= 32 ms
        predictor.record(getTouchMotionEvent(eventTime, ACTION_DOWN, x = 20f, y = 30f))
    }
}
