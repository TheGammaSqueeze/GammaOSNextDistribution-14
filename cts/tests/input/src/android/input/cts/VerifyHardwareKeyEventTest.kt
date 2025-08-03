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

import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.UinputDevice
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun injectEvents(device: UinputDevice, events: IntArray) {
    device.injectEvents(events.joinToString(prefix = "[", postfix = "]", separator = ","))
}

/**
 * Create a virtual keyboard and inject a 'hardware' key event. Ensure that the event can be
 * verified using the InputManager::verifyInputEvent api.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class VerifyHardwareKeyEventTest {
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

    fun assertReceivedEventsCanBeVerified(numEvents: Int) {
        for (i in 1..numEvents) {
            val lastInputEvent = activity.getInputEvent()
            assertNotNull("Event number $i is null!", lastInputEvent)
            assertNotNull(inputManager.verifyInputEvent(lastInputEvent!!))
        }
    }

    /**
     * Send a hardware key event and check that InputManager::verifyInputEvent returns non-null
     * result.
     */
    @Test
    fun testVerifyHardwareKeyEvent() {
        val keyboardDevice = UinputDevice.create(
                instrumentation,
                R.raw.test_keyboard_register,
                InputDevice.SOURCE_KEYBOARD,
        )

        val EV_SYN = 0
        val SYN_REPORT = 0
        val EV_KEY = 1
        val EV_KEY_DOWN = 1
        val EV_KEY_UP = 0
        val KEY_A = 30

        injectEvents(keyboardDevice, intArrayOf(EV_KEY, KEY_A, EV_KEY_DOWN, EV_SYN, SYN_REPORT, 0))
        // Send the UP event right away to avoid key repeat
        injectEvents(keyboardDevice, intArrayOf(EV_KEY, KEY_A, EV_KEY_UP, EV_SYN, SYN_REPORT, 0))

        assertReceivedEventsCanBeVerified(numEvents = 2)

        keyboardDevice.close()
    }
}
