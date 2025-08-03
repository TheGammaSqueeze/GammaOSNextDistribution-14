/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.Manifest
import android.hardware.input.InputManager
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.cts.input.UinputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun injectEvents(device: UinputDevice, events: IntArray) {
    device.injectEvents(events.joinToString(prefix = "[", postfix = "]", separator = ","))
}

private fun injectKeyDown(device: UinputDevice, scanCode: Int) {
    injectEvents(device, intArrayOf(1, scanCode, 1, 0, 0, 0))
}

private fun injectKeyUp(device: UinputDevice, scanCode: Int) {
    injectEvents(device, intArrayOf(1, scanCode, 0, 0, 0, 0))
}

/**
 * Create virtual keyboard devices and inject a 'hardware' key event after remapping keys. Ensure
 * that the event keys are correctly remapped.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class ModifierKeyRemappingTest {

    companion object {
        val REMAPPABLE_MODIFIER_KEYCODES = intArrayOf(
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_META_RIGHT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_CAPS_LOCK
        )

        // Linux keycode defined in the "linux/input-event-codes.h" header.
        val KEY_LEFTALT = 56
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

        // Clear any existing remappings
        clearAllModifierKeyRemappings()
        // Wait for handler to execute and clear all remappings
        PollingCheck.waitFor { getModifierKeyRemapping().isEmpty() }
    }

    private fun assertReceivedEventsCorrectlyMapped(numEvents: Int, expectedKeyCode: Int) {
        for (i in 1..numEvents) {
            val lastInputEvent: KeyEvent = activity.getInputEvent() as KeyEvent
            assertNotNull("Event number $i is null!", lastInputEvent)
            assertEquals(
                "Key code should be " + KeyEvent.keyCodeToString(expectedKeyCode),
                expectedKeyCode,
                lastInputEvent.keyCode
            )
        }
        activity.assertNoEvents()
    }

    /**
     * Test modifier key remapping APIs
     */
    @Test
    fun testModifierKeyRemapping() {
        ModifierRemappingFlag(true).use {
            val keyboardDevice = UinputDevice.create(
                instrumentation, R.raw.test_keyboard_register,
                InputDevice.SOURCE_KEYBOARD
            )

            // Wait for device to be added
            PollingCheck.waitFor { inputManager.getInputDevice(keyboardDevice.deviceId) != null }
            val inputDevice = inputManager.getInputDevice(keyboardDevice.deviceId)

            val numKeys = REMAPPABLE_MODIFIER_KEYCODES.size

            // Remap modifier keys in cyclic manner
            for (i in 0 until numKeys) {
                remapModifierKey(
                    REMAPPABLE_MODIFIER_KEYCODES[i],
                    REMAPPABLE_MODIFIER_KEYCODES[(i + 1) % numKeys]
                )
            }

            // Wait for handler to execute and add remappings
            PollingCheck.waitFor { getModifierKeyRemapping().size == numKeys }
            val remapping: Map<Int, Int> = getModifierKeyRemapping()
            for (i in 0 until numKeys) {
                val fromKeyCode = REMAPPABLE_MODIFIER_KEYCODES[i]
                val toKeyCode = REMAPPABLE_MODIFIER_KEYCODES[(i + 1) % numKeys]
                val actualToKeyCode = remapping[fromKeyCode]!!
                assertEquals(
                    "Modifier key remapping should map " + KeyEvent.keyCodeToString(fromKeyCode) +
                            " to " + KeyEvent.keyCodeToString(toKeyCode) + " but was " +
                            KeyEvent.keyCodeToString(actualToKeyCode), toKeyCode, actualToKeyCode
                )
                assertEquals(
                    "Key location" + KeyEvent.keyCodeToString(fromKeyCode) + " should map to " +
                            KeyEvent.keyCodeToString(toKeyCode) + " after remapping.", toKeyCode,
                    inputDevice?.getKeyCodeForKeyLocation(fromKeyCode)
                )
            }

            clearAllModifierKeyRemappings()

            // Wait for handler to execute and clear all remappings
            PollingCheck.waitFor { getModifierKeyRemapping().isEmpty() }

            for (i in 0 until numKeys) {
                val keyCode = REMAPPABLE_MODIFIER_KEYCODES[i]
                assertEquals(
                    "Key location" + KeyEvent.keyCodeToString(keyCode) + " should map to " +
                            KeyEvent.keyCodeToString(keyCode) + " after remapping.", keyCode,
                    inputDevice?.getKeyCodeForKeyLocation(keyCode)
                )
            }

            // Remove the device
            keyboardDevice.close()
        }
    }

    @Test
    fun testHardwareKeyEventsWithRemapping_AfterKeyboardAdded() {
        ModifierRemappingFlag(true).use {
            val keyboardDevice = UinputDevice.create(
                instrumentation, R.raw.test_keyboard_register,
                InputDevice.SOURCE_KEYBOARD
            )

            // Wait for device to be added
            PollingCheck.waitFor { inputManager.getInputDevice(keyboardDevice.deviceId) != null }

            remapModifierKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT)
            // Wait for handler to execute and add remappings
            PollingCheck.waitFor { getModifierKeyRemapping().size == 1 }
            activity.assertNoEvents()

            injectKeyDown(keyboardDevice, KEY_LEFTALT)
            injectKeyUp(keyboardDevice, KEY_LEFTALT)

            assertReceivedEventsCorrectlyMapped(2, KeyEvent.KEYCODE_SHIFT_LEFT)

            clearAllModifierKeyRemappings()
            // Wait for handler to execute and clear all remappings
            PollingCheck.waitFor { getModifierKeyRemapping().isEmpty() }

            injectKeyDown(keyboardDevice, KEY_LEFTALT)
            injectKeyUp(keyboardDevice, KEY_LEFTALT)

            assertReceivedEventsCorrectlyMapped(2, KeyEvent.KEYCODE_ALT_LEFT)

            // Remove the device
            keyboardDevice.close()
        }
    }

    @Test
    fun testHardwareKeyEventsWithRemapping_BeforeKeyboardAdded() {
        ModifierRemappingFlag(true).use {
            remapModifierKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT)
            // Wait for handler to execute and add remappings
            PollingCheck.waitFor { getModifierKeyRemapping().size == 1 }

            val keyboardDevice = UinputDevice.create(
                instrumentation, R.raw.test_keyboard_register,
                InputDevice.SOURCE_KEYBOARD
            )

            // Wait for device to be added
            PollingCheck.waitFor { inputManager.getInputDevice(keyboardDevice.deviceId) != null }
            activity.assertNoEvents()

            injectKeyDown(keyboardDevice, KEY_LEFTALT)
            injectKeyUp(keyboardDevice, KEY_LEFTALT)

            assertReceivedEventsCorrectlyMapped(2, KeyEvent.KEYCODE_SHIFT_LEFT)

            clearAllModifierKeyRemappings()
            // Wait for handler to execute and clear all remappings
            PollingCheck.waitFor { getModifierKeyRemapping().isEmpty() }

            injectKeyDown(keyboardDevice, KEY_LEFTALT)
            injectKeyUp(keyboardDevice, KEY_LEFTALT)

            assertReceivedEventsCorrectlyMapped(2, KeyEvent.KEYCODE_ALT_LEFT)

            // Remove the device
            keyboardDevice.close()
        }
    }

    /**
     * Remaps a modifier key to another modifier key
     *
     * @param fromKey modifier key getting remapped
     * @param toKey   modifier key that it is getting remapped to
     */
    private fun remapModifierKey(fromKey: Int, toKey: Int) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.remapModifierKey(fromKey, toKey) },
            Manifest.permission.REMAP_MODIFIER_KEYS
        )
    }

    /**
     * Clears remapping for a modifier key
     */
    private fun clearAllModifierKeyRemappings() {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.clearAllModifierKeyRemappings() },
            Manifest.permission.REMAP_MODIFIER_KEYS
        )
    }

    private fun getModifierKeyRemapping(): Map<Int, Int> {
        return SystemUtil.runWithShellPermissionIdentity(
            ThrowingSupplier<Map<Int, Int>> { inputManager.modifierKeyRemapping },
            Manifest.permission.REMAP_MODIFIER_KEYS
        )
    }

    private inner class ModifierRemappingFlag constructor(enabled: Boolean) : AutoCloseable {
        init {
            Settings.Global.putString(
                activity.contentResolver,
                "settings_new_keyboard_modifier_key", enabled.toString()
            )
        }

        override fun close() {
            Settings.Global.putString(
                activity.contentResolver,
                "settings_new_keyboard_modifier_key",
                ""
            )
        }
    }
}
