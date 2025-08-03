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
package android.platform.helpers.uinput

import android.platform.uiautomator_helpers.DeviceHelpers.waitForObj
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2

/**
 * Class to represent a stylus as a [UInputDevice]. This can then be used to register a stylus using
 * [android.platform.test.rule.InputDeviceRule].
 *
 * @param supportedKeys should be keycodes obtained from the linux event code set. See
 *   https://cs.android.com/android/kernel/superproject/+/common-android-mainline:common/include/uapi/linux/input-event-codes.h
 */
class UInputStylus(
    override val inputDeviceId: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    override val vendorId: Int = VENDOR_ID,
    override val productId: Int = PRODUCT_ID,
    override val name: String = "Test capacitive stylus with buttons",
    override val supportedKeys: List<Int> = SUPPORTED_KEYBITS,
) : UInputDevice() {
    override val bus = "bluetooth"

    override fun getRegisterCommand() =
        """{
      "id": $inputDeviceId,
      "type": "uinput",
      "command": "register",
      "name": "$name",
      "vid": $vendorId,
      "pid": $productId,
      "bus": "$bus",
      "configuration": [
        {"type": 100, "data": $SUPPORTED_ENVBITS },  // UI_SET_EVBIT
        {"type": 101, "data": $SUPPORTED_KEYBITS },  // UI_SET_KEYBIT
        {"type": 103, "data": $SUPPORTED_ABSBITS },  // UI_SET_ABSBIT
        {"type": 110, "data": [1]}  // UI_SET_PROPBIT : INPUT_PROP_DIRECT
      ],
      "abs_info": [
        {"code":0x00, "info": {       // ABS_X
          "value": 0,
          "minimum": 0,
          // Scaling factor included to mimic actual registration behaviour.
          "maximum": ${(displayWidth * SCALING_FACTOR) - 1},
          "fuzz": 0,
          "flat": 0,
          "resolution": 0
        }},
        {"code":0x01, "info": {       // ABS_Y
          "value": 0,
          "minimum": 0,
          // Scaling factor included to mimic actual registration behaviour.
          "maximum": ${(displayHeight * SCALING_FACTOR) - 1},
          "fuzz": 0,
          "flat": 0,
          "resolution": 0
        }},
        {"code":0x18, "info": {       // ABS_PRESSURE
          "value": 0,
          "minimum": 0,
          "maximum": 4095,
          "fuzz": 0,
          "flat": 0,
          "resolution": 0
        }},
        {"code":0x1a, "info": {       // ABS_TILT_X
          "value": 0,
          "minimum": -60,
          "maximum": 60,
          "fuzz": 0,
          "flat": 0,
          "resolution": 0
        }},
        {"code":0x1b, "info": {       // ABS_TILT_Y
          "value": 0,
          "minimum": -60,
          "maximum": 60,
          "fuzz": 0,
          "flat": 0,
          "resolution": 0
        }}
      ]
    }

    {
      "id": 1,
      "command": "delay",
      "duration": 3000
    }

    // Sends an event which tells the device that this is a stylus device not a finger.
    {
      "id": $inputDeviceId,
      "command": "inject",
      "events": [
        $EV_KEY,
        $BTN_TOOL_PEN,
        1
      ]
    }
    """

    /** Sends the STYLUS_BUTTON_TAIL event. */
    fun sendTailButtonClickEvent(eventInjector: EventInjector) {
        // KEY_JOURNAL is remapped to STYLUS_BUTTON_TAIL.
        eventInjector.sendKeyEvent(inputDeviceId, KEY_JOURNAL)
    }

    /** Sends the events contained within the specified file. */
    fun sendEventsFromInputFile(eventInjector: EventInjector, assetsFilePath: String) {
        eventInjector.sendEventsFromInputFile(inputDeviceId, assetsFilePath)
    }

    /** Taps on a point on the screen. */
    fun tapOnObject(eventInjector: EventInjector, selector: BySelector) {
        val uiObject: UiObject2 = waitForObj(selector)
        var xCoordinate: Int = uiObject.visibleBounds.centerX() * SCALING_FACTOR
        var yCoordinate: Int = uiObject.visibleBounds.centerY() * SCALING_FACTOR

        with(eventInjector) {
            sendEventWithValues(inputDeviceId, EV_ABS, ABS_X, xCoordinate)
            sendEventWithValues(inputDeviceId, EV_ABS, ABS_Y, yCoordinate)
            sendEventWithValues(inputDeviceId, EV_ABS, ABS_PRESSURE, 1000)
            sendEventWithValues(inputDeviceId, EV_KEY, BTN_TOUCH, KEY_DOWN)
            sendEventWithValues(inputDeviceId, EV_KEY, BTN_TOUCH, KEY_UP)
        }
    }
    private companion object {
        const val KEY_UP = 0
        const val KEY_DOWN = 1

        const val EV_KEY = 1
        const val EV_ABS = 3
        // EV_KEY
        val SUPPORTED_ENVBITS =
            listOf(
                EV_KEY,
                EV_ABS,
            )
        const val ABS_X = 0
        const val ABS_Y = 1
        const val ABS_PRESSURE = 24
        const val ABS_TILT_X = 26
        const val ABS_TILT_Y = 27
        // UI_SET_ABSBIT
        val SUPPORTED_ABSBITS =
            listOf(
                ABS_X,
                ABS_Y,
                ABS_PRESSURE,
                ABS_TILT_X,
                ABS_TILT_Y,
            )
        const val BTN_TOOL_PEN = 320
        const val BTN_TOUCH = 330
        const val BTN_STYLUS = 331
        const val BTN_STYLUS2 = 332
        const val KEY_JOURNAL = 578
        // UI_SET_KEYBIT
        val SUPPORTED_KEYBITS =
            listOf(
                BTN_TOOL_PEN,
                BTN_TOUCH,
                BTN_STYLUS,
                BTN_STYLUS2,
                KEY_JOURNAL,
            )
        // Using the below combination of product and vendor ID allows us to remap KEY_JOURNAL to
        // STYLUS_BUTTON_TAIL. This is because STYLUS_BUTTON_TAIL is not a proper Linux input event
        // code yet and we remap the buttons for generic android stylus which is represented by the
        // below combination of IDs.
        const val VENDOR_ID = 0x18d1
        const val PRODUCT_ID = 0x4f80

        // Scaling factor set to 2 as ABS_X, ABS_Y to screen resolution 2:1 for this stylus.
        const val SCALING_FACTOR = 2
    }
}
