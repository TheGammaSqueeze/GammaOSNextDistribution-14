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

import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.UinputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val EV_KEY = 1
private const val KEY_DOWN = 1
private const val KEY_UP = 0
private const val EV_SYN = 0
private const val SYN_REPORT = 0

private fun injectEvents(device: UinputDevice, events: IntArray) {
    device.injectEvents(events.joinToString(prefix = "[", postfix = "]", separator = ","))
}

private fun injectKeyDown(device: UinputDevice, scanCode: Int) {
    injectEvents(device, intArrayOf(EV_KEY, scanCode, KEY_DOWN, EV_SYN, SYN_REPORT, 0))
}

private fun injectKeyUp(device: UinputDevice, scanCode: Int) {
    injectEvents(device, intArrayOf(EV_KEY, scanCode, KEY_UP, EV_SYN, SYN_REPORT, 0))
}

/**
 * Create virtual keyboard devices and inject 'hardware' key combinations for Back shortcuts
 * and check if KEYCODE_BACK is dispatched to the applications.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class BackKeyShortcutsTest {

    companion object {
        // Linux keycodes defined in the "linux/input-event-codes.h" header.
        const val KEY_LEFTMETA = 125
        const val KEY_ESC = 1
        const val KEY_BACKSPACE = 14
        const val KEY_LEFT = 105
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val rule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    private lateinit var activity: CaptureEventActivity
    private lateinit var inputManager: InputManager

    @Before
    fun setUp() {
        rule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    private fun assertReceivedEventsCorrectlyMapped(numEvents: Int, expectedKeyCode: Int) {
        for (i in 1..numEvents) {
            val lastInputEvent = activity.getInputEvent() as? KeyEvent
            assertNotNull("Failed to receive key event number $i", lastInputEvent)
            assertEquals(
                    "Key code should be " + KeyEvent.keyCodeToString(expectedKeyCode),
                    expectedKeyCode,
                    lastInputEvent!!.keyCode
            )
        }
        activity.assertNoEvents()
    }

    @Test
    fun testBackKeyMetaShortcuts() {
        UinputDevice.create(
            instrumentation,
            R.raw.test_keyboard_register,
            InputDevice.SOURCE_KEYBOARD
        ).use { keyboardDevice ->
            activity.assertNoEvents()

            for (scanCode in intArrayOf(KEY_ESC, KEY_BACKSPACE, KEY_LEFT)) {
                injectKeyDown(keyboardDevice, KEY_LEFTMETA)
                injectKeyDown(keyboardDevice, scanCode)
                injectKeyUp(keyboardDevice, scanCode)
                injectKeyUp(keyboardDevice, KEY_LEFTMETA)

                assertReceivedEventsCorrectlyMapped(2, KeyEvent.KEYCODE_BACK)
            }
        }
    }
}
