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

import com.google.common.truth.Truth.assertThat


/**
 * Class to represent a keyboard as a [UInputDevice]. This can then be used to register a keyboard
 * using [android.platform.test.rule.InputDeviceRule].
 *
 * @param supportedKeys should be keycodes obtained from the linux event code set. See
 *   https://source.corp.google.com/kernel-upstream/include/uapi/linux/input-event-codes.h
 */
class UInputKeyboard
@JvmOverloads
constructor(
    override val productId: Int = ARBITRARY_PRODUCT_ID,
    override val vendorId: Int = ARBITRARY_VENDOR_ID,
    override val name: String = "Test Keyboard",
    override val supportedKeys: List<Int> = SUPPORTED_KEYS_QWEABC,
) : UInputDevice() {
    override val inputDeviceId = 2
    override val bus = "usb"

    /** Sends a click of the requested keycode. */
    fun sendKeyClickEvent(eventInjector: EventInjector, keycode: Int) {
        assertThat(supportedKeys).contains(keycode)

        eventInjector.sendKeyEvent(inputDeviceId, keycode)
    }

    companion object {
        val SUPPORTED_KEYS_QWEABC = listOf(16, 17, 18, 30, 48, 46)

        const val ARBITRARY_PRODUCT_ID = 0xabcd
        const val ARBITRARY_VENDOR_ID = 0x18d1
    }
}
