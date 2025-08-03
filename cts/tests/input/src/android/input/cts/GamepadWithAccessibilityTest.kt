/*
 * Copyright 2021 The Android Open Source Project
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

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.view.InputDevice
import android.view.KeyCharacterMap.VIRTUAL_KEYBOARD
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.UinputDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun setTouchExplorationEnabled(instrumentation: Instrumentation, enabled: Boolean) {
        val TIMEOUT_FOR_SERVICE_ENABLE_MILLIS: Long = 10_000 // 10s
        val manager: AccessibilityManager =
            instrumentation.getTargetContext().getSystemService(AccessibilityManager::class.java)

        val uiAutomation = instrumentation.getUiAutomation()
        val info = uiAutomation.getServiceInfo()
        assertNotNull(info)
        if (enabled) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        } else {
            info.flags = (info.flags and
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv())
        }
        uiAutomation.setServiceInfo(info)

        PollingCheck.waitFor(TIMEOUT_FOR_SERVICE_ENABLE_MILLIS) {
            enabled == manager.isTouchExplorationEnabled()
        }
    }

/**
 * Create a virtual gamepad and enable accessibility services (any accessibility feature that would
 * engage the inputfilter functionality inside InputDispatcher).
 * Next, inject key events from the gamepad into the test activity and examine them.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class GamepadWithAccessibilityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(CaptureEventActivity::class.java)
    private lateinit var activity: CaptureEventActivity
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
        setTouchExplorationEnabled(instrumentation, true)
    }

    @After
    fun tearDown() {
        setTouchExplorationEnabled(instrumentation, false)
    }

    /**
     * The device id of input events received by the test activity should not be overwritten to -1.
     * It should match the device id of the original input event.
     */
    @Test
    fun testDeviceId() {
        val uinputDevice = UinputDevice.create(instrumentation, R.raw.google_gamepad_register,
                InputDevice.SOURCE_KEYBOARD)

        val EV_SYN = 0
        val SYN_REPORT = 0
        val EV_KEY = 1
        val EV_KEY_DOWN = 1
        val EV_KEY_UP = 0
        val BTN_GAMEPAD = 0x130
        val evdevEventsDown = intArrayOf(EV_KEY, BTN_GAMEPAD, EV_KEY_DOWN, EV_SYN, SYN_REPORT, 0)
        uinputDevice.injectEvents(evdevEventsDown.joinToString(prefix = "[", postfix = "]",
                separator = ","))

        val evdevEventsUp = intArrayOf(EV_KEY, BTN_GAMEPAD, EV_KEY_UP, EV_SYN, SYN_REPORT, 0)
        uinputDevice.injectEvents(evdevEventsUp.joinToString(prefix = "[", postfix = "]",
                separator = ","))

        val lastInputEvent = activity.getInputEvent()
        assertNotNull(lastInputEvent)
        assertTrue(lastInputEvent is KeyEvent)
        val keyEvent = lastInputEvent as KeyEvent
        // The event was not modified by accessibility in any way, so it should not have
        // KeyEvent.FLAG_IS_ACCESSIBILITY_EVENT in getFlags()
        assertEquals(KeyEvent.FLAG_FROM_SYSTEM, keyEvent.getFlags())
        assertNotEquals(keyEvent.getDeviceId(), VIRTUAL_KEYBOARD)
        assertEquals(keyEvent.getDeviceId(), uinputDevice.getDeviceId())

        uinputDevice.close()
    }
}
