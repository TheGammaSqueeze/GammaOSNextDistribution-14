/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

import android.text.TextUtils
import android.util.ArrayMap
import android.view.KeyEvent
import android.view.MotionEvent

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest

import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class InputEventTest {

    @Test
    fun testKeyCodeToString() {
        assertEquals("KEYCODE_UNKNOWN", KeyEvent.keyCodeToString(KeyEvent.KEYCODE_UNKNOWN))
        assertEquals("KEYCODE_HOME", KeyEvent.keyCodeToString(KeyEvent.KEYCODE_HOME))
        assertEquals("KEYCODE_0", KeyEvent.keyCodeToString(KeyEvent.KEYCODE_0))
        assertEquals("KEYCODE_POWER", KeyEvent.keyCodeToString(KeyEvent.KEYCODE_POWER))
        assertEquals("KEYCODE_A", KeyEvent.keyCodeToString(KeyEvent.KEYCODE_A))
        assertEquals("KEYCODE_SPACE", KeyEvent.keyCodeToString(KeyEvent.KEYCODE_SPACE))
        assertEquals("KEYCODE_MENU", KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MENU))
        assertEquals("KEYCODE_BACK", KeyEvent.keyCodeToString(KeyEvent.KEYCODE_BACK))
        assertEquals("KEYCODE_BUTTON_A", KeyEvent.keyCodeToString(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals("KEYCODE_PROFILE_SWITCH",
                        KeyEvent.keyCodeToString(KeyEvent.KEYCODE_PROFILE_SWITCH))
    }

    @Test
    fun testAxisFromToString() {
        val axes = ArrayMap<Int, String>()
        axes.put(MotionEvent.AXIS_X, "AXIS_X")
        axes.put(MotionEvent.AXIS_Y, "AXIS_Y")
        axes.put(MotionEvent.AXIS_PRESSURE, "AXIS_PRESSURE")
        axes.put(MotionEvent.AXIS_SIZE, "AXIS_SIZE")
        axes.put(MotionEvent.AXIS_TOUCH_MAJOR, "AXIS_TOUCH_MAJOR")
        axes.put(MotionEvent.AXIS_TOUCH_MINOR, "AXIS_TOUCH_MINOR")
        axes.put(MotionEvent.AXIS_TOOL_MAJOR, "AXIS_TOOL_MAJOR")
        axes.put(MotionEvent.AXIS_TOOL_MINOR, "AXIS_TOOL_MINOR")
        axes.put(MotionEvent.AXIS_ORIENTATION, "AXIS_ORIENTATION")
        axes.put(MotionEvent.AXIS_VSCROLL, "AXIS_VSCROLL")
        axes.put(MotionEvent.AXIS_HSCROLL, "AXIS_HSCROLL")
        axes.put(MotionEvent.AXIS_Z, "AXIS_Z")
        axes.put(MotionEvent.AXIS_RX, "AXIS_RX")
        axes.put(MotionEvent.AXIS_RY, "AXIS_RY")
        axes.put(MotionEvent.AXIS_RZ, "AXIS_RZ")
        axes.put(MotionEvent.AXIS_HAT_X, "AXIS_HAT_X")
        axes.put(MotionEvent.AXIS_HAT_Y, "AXIS_HAT_Y")
        axes.put(MotionEvent.AXIS_LTRIGGER, "AXIS_LTRIGGER")
        axes.put(MotionEvent.AXIS_RTRIGGER, "AXIS_RTRIGGER")
        axes.put(MotionEvent.AXIS_THROTTLE, "AXIS_THROTTLE")
        axes.put(MotionEvent.AXIS_RUDDER, "AXIS_RUDDER")
        axes.put(MotionEvent.AXIS_WHEEL, "AXIS_WHEEL")
        axes.put(MotionEvent.AXIS_GAS, "AXIS_GAS")
        axes.put(MotionEvent.AXIS_BRAKE, "AXIS_BRAKE")
        axes.put(MotionEvent.AXIS_DISTANCE, "AXIS_DISTANCE")
        axes.put(MotionEvent.AXIS_TILT, "AXIS_TILT")
        axes.put(MotionEvent.AXIS_SCROLL, "AXIS_SCROLL")
        axes.put(MotionEvent.AXIS_RELATIVE_X, "AXIS_RELATIVE_X")
        axes.put(MotionEvent.AXIS_RELATIVE_Y, "AXIS_RELATIVE_Y")
        axes.put(MotionEvent.AXIS_GENERIC_1, "AXIS_GENERIC_1")
        axes.put(MotionEvent.AXIS_GENERIC_2, "AXIS_GENERIC_2")
        axes.put(MotionEvent.AXIS_GENERIC_3, "AXIS_GENERIC_3")
        axes.put(MotionEvent.AXIS_GENERIC_4, "AXIS_GENERIC_4")
        axes.put(MotionEvent.AXIS_GENERIC_5, "AXIS_GENERIC_5")
        axes.put(MotionEvent.AXIS_GENERIC_6, "AXIS_GENERIC_6")
        axes.put(MotionEvent.AXIS_GENERIC_7, "AXIS_GENERIC_7")
        axes.put(MotionEvent.AXIS_GENERIC_8, "AXIS_GENERIC_8")
        axes.put(MotionEvent.AXIS_GENERIC_9, "AXIS_GENERIC_9")
        axes.put(MotionEvent.AXIS_GENERIC_10, "AXIS_GENERIC_10")
        axes.put(MotionEvent.AXIS_GENERIC_11, "AXIS_GENERIC_11")
        axes.put(MotionEvent.AXIS_GENERIC_12, "AXIS_GENERIC_12")
        axes.put(MotionEvent.AXIS_GENERIC_13, "AXIS_GENERIC_13")
        axes.put(MotionEvent.AXIS_GENERIC_14, "AXIS_GENERIC_14")
        axes.put(MotionEvent.AXIS_GENERIC_15, "AXIS_GENERIC_15")
        axes.put(MotionEvent.AXIS_GENERIC_16, "AXIS_GENERIC_16")
        // As Axes values definition is not continuous from AXIS_RELATIVE_Y to AXIS_GENERIC_1,
        // Need to verify MotionEvent.axisToString returns axis name correctly.
        // Also verify that we are not crashing on those calls, and that the return result on each
        // is not empty. We do expect the two-way call chain of to/from to get us back to the
        // original integer value.
        for (entry in axes.entries) {
            val axis = entry.key
            val axisToString = MotionEvent.axisToString(axis)
            assertFalse(TextUtils.isEmpty(axisToString))
            assertEquals(axisToString, entry.value)
            assertEquals(axis, MotionEvent.axisFromString(axisToString))
        }
    }
}
