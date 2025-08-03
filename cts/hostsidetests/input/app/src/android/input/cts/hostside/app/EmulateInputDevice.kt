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
package android.input.cts.hostside.app

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.hardware.input.InputManager
import android.util.DisplayMetrics
import android.util.Size
import android.view.InputDevice
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.UinputDevice
import com.android.cts.input.UinputTouchDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This class contains device-side parts of input host tests. In particular, it is used to
 * emulate input device connections and interactions for host tests.
 */
@RunWith(AndroidJUnit4::class)
class EmulateInputDevice {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: Context
    private lateinit var screenSize: Size

    @get:Rule
    val activityRule = ActivityScenarioRule(Activity::class.java)

    @Suppress("DEPRECATION")
    @Before
    fun setUp() {
        activityRule.scenario.onActivity { context = it }
        val dm = DisplayMetrics().also { context.display.getRealMetrics(it) }
        screenSize = Size(dm.widthPixels, dm.heightPixels)
    }

    @After
    fun tearDown() {
    }

    /**
     * Registers a USB touchscreen through uinput, interacts with it for at least
     * five seconds, and disconnects the device.
     */
    @Test
    fun useTouchscreenForFiveSeconds() {
        UinputTouchDevice(
                instrumentation,
                context.display,
                R.raw.test_touchscreen_register,
                InputDevice.SOURCE_TOUCHSCREEN,
                useDisplaySize = true,
        ).use { touchscreen ->
            // Start the usage session.
            touchscreen.tapOnScreen()

            // Continue using the touchscreen for at least five more seconds.
            for (i in 0 until 5) {
                Thread.sleep(1000)
                touchscreen.tapOnScreen()
            }
        }
    }

    private fun UinputTouchDevice.tapOnScreen() {
        val pointer = Point(screenSize.width / 2, screenSize.height / 2)
        val pointerId = 0

        // Down
        sendBtnTouch(true)
        sendDown(pointerId, pointer)
        sync()

        // Move
        pointer.offset(1, 1)
        sendMove(pointerId, pointer)
        sync()

        // Up
        sendBtnTouch(false)
        sendUp(pointerId)
        sync()
    }

    @Test
    fun useTouchpadWithFingersAndPalms() {
        UinputTouchDevice(
                instrumentation,
                context.display,
                R.raw.test_touchpad_register,
                InputDevice.SOURCE_TOUCHPAD or InputDevice.SOURCE_MOUSE,
        ).use { touchpad ->
            for (i in 0 until 3) {
                val pointer = Point(100, 200)
                touchpad.sendBtnTouch(true)
                touchpad.sendBtn(UinputTouchDevice.BTN_TOOL_FINGER, true)
                touchpad.sendDown(0, pointer, UinputTouchDevice.MT_TOOL_FINGER)
                touchpad.sync()

                touchpad.sendBtnTouch(false)
                touchpad.sendBtn(UinputTouchDevice.BTN_TOOL_FINGER, false)
                touchpad.sendUp(0)
                touchpad.sync()
            }
            for (i in 0 until 2) {
                val pointer = Point(100, 200)
                touchpad.sendBtnTouch(true)
                touchpad.sendBtn(UinputTouchDevice.BTN_TOOL_FINGER, true)
                touchpad.sendDown(0, pointer, UinputTouchDevice.MT_TOOL_PALM)
                touchpad.sync()

                touchpad.sendBtnTouch(false)
                touchpad.sendBtn(UinputTouchDevice.BTN_TOOL_FINGER, false)
                touchpad.sendUp(0)
                touchpad.sync()
            }
        }
    }

    @Test
    fun pinchOnTouchpad() {
        UinputTouchDevice(
            instrumentation,
            context.display,
            R.raw.test_touchpad_register,
            InputDevice.SOURCE_TOUCHPAD or InputDevice.SOURCE_MOUSE
        ).use { touchpad ->
            val pointer0 = Point(500, 500)
            val pointer1 = Point(700, 700)
            touchpad.sendBtnTouch(true)
            touchpad.sendBtn(UinputTouchDevice.BTN_TOOL_FINGER, true)
            touchpad.sendDown(0, pointer0)
            touchpad.sync()

            touchpad.sendBtn(UinputTouchDevice.BTN_TOOL_FINGER, false)
            touchpad.sendBtn(UinputTouchDevice.BTN_TOOL_DOUBLETAP, true)
            touchpad.sendDown(1, pointer1)
            touchpad.sync()
            Thread.sleep(TOUCHPAD_SCAN_DELAY_MILLIS)

            for (rep in 0 until 10) {
                pointer0.offset(-20, -20)
                touchpad.sendMove(0, pointer0)
                pointer1.offset(20, 20)
                touchpad.sendMove(1, pointer1)
                touchpad.sync()
                Thread.sleep(TOUCHPAD_SCAN_DELAY_MILLIS)
            }

            touchpad.sendBtn(UinputTouchDevice.BTN_TOOL_DOUBLETAP, false)
            touchpad.sendBtn(UinputTouchDevice.BTN_TOOL_FINGER, true)
            touchpad.sendUp(0)
            touchpad.sync()

            touchpad.sendBtn(UinputTouchDevice.BTN_TOOL_FINGER, false)
            touchpad.sendBtnTouch(false)
            touchpad.sendUp(1)
            touchpad.sync()
            Thread.sleep(TOUCHPAD_POST_GESTURE_DELAY_MILLIS)
        }
    }

    @Test
    fun twoFingerSwipeOnTouchpad() {
        multiFingerSwipe(2)
    }

    @Test
    fun threeFingerSwipeOnTouchpad() {
        multiFingerSwipe(3)
    }

    @Test
    fun fourFingerSwipeOnTouchpad() {
        multiFingerSwipe(4)
    }

    private fun multiFingerSwipe(numFingers: Int) {
        UinputTouchDevice(
            instrumentation,
            context.display,
            R.raw.test_touchpad_register,
            InputDevice.SOURCE_TOUCHPAD or InputDevice.SOURCE_MOUSE
        ).use { touchpad ->
            val pointers = Array(numFingers) { i -> Point(500 + i * 200, 500) }
            touchpad.sendBtnTouch(true)
            touchpad.sendBtn(UinputTouchDevice.toolBtnForFingerCount(numFingers), true)
            for (i in pointers.indices) {
                touchpad.sendDown(i, pointers[i])
            }
            touchpad.sync()
            Thread.sleep(TOUCHPAD_SCAN_DELAY_MILLIS)

            for (rep in 0 until 10) {
                for (i in pointers.indices) {
                    pointers[i].offset(0, 40)
                    touchpad.sendMove(i, pointers[i])
                }
                touchpad.sync()
                Thread.sleep(TOUCHPAD_SCAN_DELAY_MILLIS)
            }

            for (i in pointers.indices) {
                touchpad.sendUp(i)
            }
            touchpad.sendBtn(UinputTouchDevice.toolBtnForFingerCount(numFingers), false)
            touchpad.sendBtnTouch(false)
            touchpad.sync()
            Thread.sleep(TOUCHPAD_POST_GESTURE_DELAY_MILLIS)
        }
    }

    @Test
    fun createKeyboardDevice() {
        UinputDevice.create(
            instrumentation,
            R.raw.test_keyboard_register,
            InputDevice.SOURCE_KEYBOARD
        ).use {
            // do nothing: Adding a device should trigger the logging logic.
        }
    }

    @Test
    fun createKeyboardDeviceAndSendCapsLockKey() {
        UinputDevice.create(
                instrumentation,
                R.raw.test_keyboard_register,
                InputDevice.SOURCE_KEYBOARD
        ).use { keyboard ->
            // Wait for device to be added
            injectEvents(keyboard, intArrayOf(EV_KEY, KEY_CAPSLOCK, KEY_PRESS, 0, 0, 0))
            injectEvents(keyboard, intArrayOf(EV_KEY, KEY_CAPSLOCK, KEY_RELEASE, 0, 0, 0))
            Thread.sleep(KEYBOARD_POST_EVENT_DELAY_MILLIS)
        }
    }

    private fun injectEvents(device: UinputDevice, events: IntArray) {
        device.injectEvents(events.joinToString(prefix = "[", postfix = "]", separator = ","))
    }

    companion object {
        const val TOUCHPAD_SCAN_DELAY_MILLIS: Long = 5
        const val KEY_CAPSLOCK: Int = 58
        const val EV_KEY: Int = 1
        const val KEY_PRESS: Int = 1
        const val KEY_RELEASE: Int = 0

        // This delay seems to be necessary to let the gesture be properly processed and added to
        // metrics before the touchpad device is torn down.
        const val TOUCHPAD_POST_GESTURE_DELAY_MILLIS: Long = 500

        // This delay is required for key events to be sent and handled correctly.
        const val KEYBOARD_POST_EVENT_DELAY_MILLIS: Long = 500
    }
}
