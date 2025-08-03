/*
 * Copyright 2024 The Android Open Source Project
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
import android.view.InputDevice
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_HOVER_ENTER
import android.view.MotionEvent.ACTION_HOVER_EXIT
import android.view.MotionEvent.ACTION_HOVER_MOVE
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.MotionEvent.TOOL_TYPE_FINGER
import android.view.MotionEvent.TOOL_TYPE_STYLUS
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.UinputTouchDevice
import com.android.cts.input.inputeventmatchers.withDeviceId
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withRawCoords
import com.android.cts.input.inputeventmatchers.withSource
import com.android.cts.input.inputeventmatchers.withToolType
import com.android.input.flags.Flags
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

/**
 * Tests the multi-device input feature by checking whether touch and stylus devices can be used
 * at the same time on different windows for the same app.
 *
 * Launch two windows side-by-side, and create two uinput devices - touchscreen and stylus.

 * Touch the left window with touchscreen and then touch the right window with stylus.
 * Next, add some MOVE events for both devices. Ensure that the two windows are seeing independent
 * input event streams.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SimultaneousTouchAndStylusTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var touchScreen: UinputTouchDevice
    private lateinit var stylus: UinputTouchDevice
    private lateinit var leftWindowVerifier: EventVerifier
    private lateinit var rightWindowVerifier: EventVerifier

    @get:Rule
    val testName = TestName()
    @get:Rule
    val virtualDisplayRule = VirtualDisplayActivityScenarioRule<TwoWindowsActivity>(testName)
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
        stylus = UinputTouchDevice(
                instrumentation,
                virtualDisplayRule.virtualDisplay.display,
                R.raw.test_capacitive_stylus_register,
                InputDevice.SOURCE_STYLUS,
                useDisplaySize = true,
        )
        virtualDisplayRule.activity.launchTwoWindows()
        leftWindowVerifier = EventVerifier(virtualDisplayRule.activity::getLeftWindowInputEvent)
        rightWindowVerifier = EventVerifier(virtualDisplayRule.activity::getRightWindowInputEvent)
    }

    @After
    fun tearDown() {
        if (this::touchScreen.isInitialized) {
            touchScreen.close()
        }
        if (this::stylus.isInitialized) {
            stylus.close()
        }
    }

    private fun getWidth(): Int {
        return virtualDisplayRule.virtualDisplay.display.getMode().getPhysicalWidth()
    }

    private fun getHeight(): Int {
        return virtualDisplayRule.virtualDisplay.display.getMode().getPhysicalHeight()
    }

    /**
     * Launch two windows. Touch down on the left window, and stylus down on the right window.
     * Left window should receive a consistent touch event stream (DOWN -> MOVE -> UP).
     * Right window should receive a consistent stylus event stream (DOWN -> MOVE -> UP).
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MULTI_DEVICE_INPUT)
    fun testLeftTouchRightStylusMove() {
        val leftWindowLocation = Point(getWidth() * 1 / 4, getHeight() * 1 / 2)

        val commonTouchEventMatcher = allOf(
                withDeviceId(touchScreen.getDeviceId()),
                withToolType(TOOL_TYPE_FINGER),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
        )
        val commonStylusEventMatcher = allOf(
                withDeviceId(stylus.getDeviceId()),
                withToolType(TOOL_TYPE_STYLUS),
                withSource(InputDevice.SOURCE_TOUCHSCREEN or InputDevice.SOURCE_STYLUS),
        )

        // ACTION_DOWN - touch - left window
        val pointerId = 0
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(pointerId, leftWindowLocation)
        touchScreen.sync()
        leftWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_DOWN),
                withRawCoords(leftWindowLocation),
                commonTouchEventMatcher
            )
        )

        val rightWindowLocation = Point(getWidth() * 3 / 4, getHeight() * 1 / 2)

        // ACTION_DOWN - stylus - right window
        stylus.sendBtnTouch(true)
        stylus.sendPressure(255)
        stylus.sendDown(pointerId, rightWindowLocation, UinputTouchDevice.MT_TOOL_PEN)
        stylus.sync()

        rightWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_DOWN),
                withRawCoords(rightWindowLocation),
                commonStylusEventMatcher
            )
        )

        // ACTION_MOVE - touch - left window
        leftWindowLocation.offset(1, 2)
        touchScreen.sendMove(pointerId, leftWindowLocation)
        touchScreen.sync()
        leftWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_MOVE),
                withRawCoords(leftWindowLocation),
                commonTouchEventMatcher
            )
        )

        // ACTION_MOVE - stylus - right window
        rightWindowLocation.offset(-1, -2)
        stylus.sendMove(pointerId, rightWindowLocation)
        stylus.sync()
        rightWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_MOVE),
                withRawCoords(rightWindowLocation),
                commonStylusEventMatcher
            )
        )

        // ACTION_UP - touch - left window
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(pointerId)
        touchScreen.sync()
        leftWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_UP),
                withRawCoords(leftWindowLocation),
                commonTouchEventMatcher
            )
        )

        // ACTION_UP - stylus - right window
        stylus.sendBtnTouch(false)
        stylus.sendPressure(0)
        stylus.sendUp(pointerId)
        stylus.sync()
        rightWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_UP),
                withRawCoords(rightWindowLocation),
                commonStylusEventMatcher
            )
        )
    }

    /**
     * Launch two windows. Touch down on the left window, and stylus hover on the right window.
     * Left window should receive a consistent touch event stream (DOWN -> MOVE -> UP).
     * Right window should receive a consistent stylus event stream
     * (HOVER_ENTER -> HOVER_MOVE -> HOVER_EXIT).
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MULTI_DEVICE_INPUT)
    fun testLeftTouchRightStylusHover() {
        val leftWindowLocation = Point(getWidth() * 1 / 4, getHeight() * 1 / 2)

        val commonTouchEventMatcher = allOf(
                withDeviceId(touchScreen.getDeviceId()),
                withToolType(TOOL_TYPE_FINGER),
                withSource(InputDevice.SOURCE_TOUCHSCREEN),
        )
        val commonStylusEventMatcher = allOf(
                withDeviceId(stylus.getDeviceId()),
                withToolType(TOOL_TYPE_STYLUS),
                withSource(InputDevice.SOURCE_TOUCHSCREEN or InputDevice.SOURCE_STYLUS),
        )

        // ACTION_DOWN - touch - left window
        val pointerId = 0
        touchScreen.sendBtnTouch(true)
        touchScreen.sendDown(pointerId, leftWindowLocation)
        touchScreen.sync()
        leftWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_DOWN),
                withRawCoords(leftWindowLocation),
                commonTouchEventMatcher
            )
        )

        val rightWindowLocation = Point(getWidth() * 3 / 4, getHeight() * 1 / 2)

        // ACTION_HOVER_ENTER - stylus - right window
        stylus.sendDown(pointerId, rightWindowLocation, UinputTouchDevice.MT_TOOL_PEN)
        stylus.sendPressure(0)
        stylus.sync()
        rightWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_HOVER_ENTER),
                withRawCoords(rightWindowLocation),
                commonStylusEventMatcher
            )
        )
        // For some reason, we are also seeing an extraneous HOVER_MOVE event
        rightWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_HOVER_MOVE),
                withRawCoords(rightWindowLocation),
                commonStylusEventMatcher
            )
        )

        // ACTION_MOVE - touch - left window
        leftWindowLocation.offset(1, 2)
        touchScreen.sendMove(pointerId, leftWindowLocation)
        touchScreen.sync()
        leftWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_MOVE),
                withRawCoords(leftWindowLocation),
                commonTouchEventMatcher
            )
        )

        // ACTION_HOVER_MOVE - stylus - right window
        rightWindowLocation.offset(-1, -2)
        stylus.sendMove(pointerId, rightWindowLocation)
        stylus.sync()
        rightWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_HOVER_MOVE),
                withRawCoords(rightWindowLocation),
                commonStylusEventMatcher
            )
        )

        // ACTION_UP - touch - left window
        touchScreen.sendBtnTouch(false)
        touchScreen.sendUp(pointerId)
        touchScreen.sync()
        leftWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_UP),
                withRawCoords(leftWindowLocation),
                commonTouchEventMatcher
            )
        )

        // ACTION_UP - stylus - right window
        stylus.sendUp(pointerId)
        stylus.sync()
        rightWindowVerifier.assertReceivedMotion(
            allOf(
                withMotionAction(ACTION_HOVER_EXIT),
                withRawCoords(rightWindowLocation),
                commonStylusEventMatcher
            )
        )
    }

    companion object {
        private const val TAG = "SimultaneousTouchAndStylusTest"
    }
}
