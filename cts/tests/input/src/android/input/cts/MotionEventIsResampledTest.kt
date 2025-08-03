/*
 * Copyright 2023 The Android Open Source Project
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

import android.graphics.Point
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.UinputTouchDevice
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.hardware.input.Flags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

/**
 * Tests the {@link MotionEvent.PointerCoords#isResampled()} method.
 *
 * We do not have a way to deterministically cause input events to be resampled, so this test can
 * only deterministically check that the isResampled method returns false for the events that
 * correspond to the evdev events that were initially created. However, almost all runs do trigger
 * at least one resampled event, meaning that the true case is quite reliably tested.
 *
 * On newer versions of the Linux kernel, this test could explicitly set the timestamps of the input
 * events it sends, but since a delay-based approach is sufficient to trigger resampling the added
 * complexity doesn't seem worthwhile.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class MotionEventIsResampledTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var touchScreen: UinputTouchDevice
    private lateinit var verifier: EventVerifier

    @get:Rule
    val testName = TestName()
    @get:Rule
    val virtualDisplayRule = VirtualDisplayActivityScenarioRule<CaptureEventActivity>(testName)
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setUp() {
        touchScreen = UinputTouchDevice(
                instrumentation,
                virtualDisplayRule.virtualDisplay.display,
                R.raw.test_touchscreen_register,
                InputDevice.SOURCE_TOUCHSCREEN,
                useDisplaySize = true,
        )
        verifier = EventVerifier(virtualDisplayRule.activity::getInputEvent)
    }

    @After
    fun tearDown() {
        if (this::touchScreen.isInitialized) {
            touchScreen.close()
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_POINTER_COORDS_IS_RESAMPLED_API)
    fun testIsResampled() {
        var x = 100
        val y = 100
        val step = 5
        val pointer = Point(x, y)

        // ACTION_DOWN
        val pointerId = 0
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(pointerId, pointer)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))

        // Three ACTION_MOVEs
        val xCoords = HashSet<Float>()
        for (i in 0..<5) {
            x += step
            xCoords.add(x.toFloat())

            // Resampling only occurs if events are spaced at least 2ms (RESAMPLE_MIN_DELTA in
            // frameworks/native/libs/input/InputTransport.cpp) apart. Add another 1ms to be safe.
            touchScreen.delay(3)

            pointer.offset(step, 0)
            touchScreen.sendMove(pointerId, pointer)
            touchScreen.sync()
        }

        var numResamples = 0
        fun checkPointerCoords(coords: MotionEvent.PointerCoords) {
            // Since the points are all in a line, Y should always be the same even when resampled.
            assertEquals(y.toFloat(), coords.y)

            // We can use the X coordinates to uniquely identify events. Since the resampled events
            // will have different timestamps than the originals, they'll also have different X
            // coordinates. For simplicity, we don't check the ordering of the X coordinates (e.g.
            // that X = 105 occurs before X = 110); that should be handled by other tests.
            val shouldBeResampled = !xCoords.contains(coords.x)
            assertEquals(shouldBeResampled, coords.isResampled())
            if (coords.isResampled()) numResamples++
        }

        var event = virtualDisplayRule.activity.getInputEvent()
        while (event != null) {
            if (event !is MotionEvent) {
                continue
            }
            var coords = MotionEvent.PointerCoords()
            for (i in 0..<event.historySize) {
                event.getHistoricalPointerCoords(0, i, coords)
                checkPointerCoords(coords)
            }
            event.getPointerCoords(0, coords)
            checkPointerCoords(coords)

            event = virtualDisplayRule.activity.getInputEvent() as? MotionEvent
        }

        // ACTION_UP
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(pointerId)
        touchScreen.sync()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))

        Log.d(TAG, "Number of resampled PointerCoords: $numResamples")
    }

    companion object {
        private const val TAG = "MotionEventIsResampledTest"
    }
}
