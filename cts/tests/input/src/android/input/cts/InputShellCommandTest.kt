/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.ShellUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun getViewCenterOnScreen(v: View): Pair<Int, Int> {
    val location = IntArray(2)
    v.getLocationOnScreen(location)
    val x = location[0] + v.width / 2
    val y = location[1] + v.height / 2
    return Pair(x, y)
}

/**
 * Tests for the 'adb shell input' command.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class InputShellCommandTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(CaptureEventActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: CaptureEventActivity

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
        instrumentation.uiAutomation.syncInputTransactions()
    }

    /**
     * Check the tool type set by default by "input tap" command
     */
    @Test
    fun testDefaultTapToolType() {
        val (x, y) = getViewCenterOnScreen(activity.window.decorView)

        ShellUtils.runShellCommand("input tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_FINGER)
    }

    /**
     * Check that the tool type of the injected events changes according to the event source.
     */
    @Test
    fun testTapToolType() {
        val (x, y) = getViewCenterOnScreen(activity.window.decorView)

        ShellUtils.runShellCommand("input touchscreen tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_FINGER)

        ShellUtils.runShellCommand("input touchpad tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_FINGER)

        ShellUtils.runShellCommand("input touchnavigation tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_FINGER)

        ShellUtils.runShellCommand("input stylus tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_STYLUS)

        ShellUtils.runShellCommand("input mouse tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_MOUSE)

        ShellUtils.runShellCommand("input trackball tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_MOUSE)

        ShellUtils.runShellCommand("input joystick tap $x $y")
        assertTapToolType(MotionEvent.TOOL_TYPE_UNKNOWN)
    }

    @Test
    fun testDefaultScroll() {
        ShellUtils.runShellCommand("input scroll")

        val event = getMotionEvent()
        assertThat(event.source).isEqualTo(InputDevice.SOURCE_ROTARY_ENCODER)
        assertThat(event.action).isEqualTo(MotionEvent.ACTION_SCROLL)
    }

    @Test
    fun testPointerScroll() {
        val (x, y) = getViewCenterOnScreen(activity.window.decorView)

        ShellUtils.runShellCommand("input mouse scroll $x $y --axis VSCROLL,-1")

        val event = getMotionEvent()
        assertThat(event.source).isEqualTo(InputDevice.SOURCE_MOUSE)
        assertThat(event.action).isEqualTo(MotionEvent.ACTION_SCROLL)
        assertThat(event.x).isEqualTo(x)
        assertThat(event.y).isEqualTo(y)
        assertThat(event.getAxisValue(MotionEvent.AXIS_VSCROLL)).isEqualTo(-1)
    }

    @Test
    fun testNonPointerScroll() {
        ShellUtils.runShellCommand("input rotaryencoder scroll --axis SCROLL,-8 --axis HSCROLL,2")

        val event = getMotionEvent()
        assertThat(event.source).isEqualTo(InputDevice.SOURCE_ROTARY_ENCODER)
        assertThat(event.action).isEqualTo(MotionEvent.ACTION_SCROLL)
        assertThat(event.getAxisValue(MotionEvent.AXIS_SCROLL)).isEqualTo(-8)
        assertThat(event.getAxisValue(MotionEvent.AXIS_HSCROLL)).isEqualTo(2)
    }

    @Test
    fun testInvalidScroll() {
        ShellUtils.runShellCommand("input scroll --axis SCROLL -8")
        ShellUtils.runShellCommand("input scroll --axis scroll,-8")
        ShellUtils.runShellCommand("input scroll --random_option SCROLL,-8")
        ShellUtils.runShellCommand("input scroll --axis X,-8")

        assertThat(activity.getInputEvent()).isNull()
    }

    private fun getMotionEvent(): MotionEvent {
        val event = activity.getInputEvent()
        assertThat(event).isNotNull()
        assertThat(event).isInstanceOf(MotionEvent::class.java)
        return event as MotionEvent
    }

    private fun assertToolType(event: MotionEvent, toolType: Int) {
        val pointerProperties = MotionEvent.PointerProperties()
        for (i in 0 until event.pointerCount) {
            event.getPointerProperties(i, pointerProperties)
            assertThat(toolType).isEqualTo(pointerProperties.toolType)
        }
    }

    private fun assertTapToolType(toolType: Int) {
        val downEvent = getMotionEvent()
        assertThat(downEvent.action).isEqualTo(MotionEvent.ACTION_DOWN)
        assertToolType(downEvent, toolType)

        val upEvent = getMotionEvent()
        assertThat(upEvent.action).isEqualTo(MotionEvent.ACTION_UP)
        assertToolType(upEvent, toolType)
    }
}
