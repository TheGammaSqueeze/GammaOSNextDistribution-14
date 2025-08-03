/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.helper.aoa;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

/**
 * Simulated human interface devices used by {@link AoaDevice}.
 *
 * @see <a href="https://www.usb.org/hid">USB HID information</a>
 */
enum AoaHID {
    /** 360 x 640 touch screen: 6-bit padding, 2-bit type, 16-bit X coord., 16-bit Y coord. */
    TOUCH_SCREEN(
            new Integer[] {
                0x05, 0x0D, //      Usage Page (Digitizer)
                0x09, 0x04, //      Usage (Touch Screen)
                0xA1, 0x01, //      Collection (Application)
                0x09, 0x32, //          Usage (In Range) - proximity to screen
                0x09, 0x33, //          Usage (Touch) - contact with screen
                0x15, 0x00, //          Logical Minimum (0)
                0x25, 0x01, //          Logical Maximum (1)
                0x75, 0x01, //          Report Size (1)
                0x95, 0x02, //          Report Count (2)
                0x81, 0x02, //          Input (Data, Variable, Absolute)
                0x75, 0x01, //          Report Size (1)
                0x95, 0x06, //          Report Count (6) - padding
                0x81, 0x01, //          Input (Constant)
                0x05, 0x01, //          Usage Page (Generic)
                0x09, 0x30, //          Usage (X)
                0x15, 0x00, //          Logical Minimum (0)
                0x26, 0x68, 0x01, //    Logical Maximum (360)
                0x75, 0x10, //          Report Size (16)
                0x95, 0x01, //          Report Count (1)
                0x81, 0x02, //          Input (Data, Variable, Absolute)
                0x09, 0x31, //          Usage (Y)
                0x15, 0x00, //          Logical Minimum (0)
                0x26, 0x80, 0x02, //    Logical Maximum (640)
                0x75, 0x10, //          Report Size (16)
                0x95, 0x01, //          Report Count (1)
                0x81, 0x02, //          Input (Data, Variable, Absolute)
                0xC0, //            End Collection
            }),

    /** 101-key keyboard: 8-bit modifier (left & right CTRL, SHIFT, ALT, GUI), 8-bit keycode. */
    KEYBOARD(
            new Integer[] {
                0x05, 0x01, //      Usage Page (Generic)
                0x09, 0x06, //      Usage (Keyboard)
                0xA1, 0x01, //      Collection (Application)
                0x05, 0x07, //          Usage Page (Key Codes)
                0x19, 0xE0, //          Usage Minimum (Left Control)
                0x29, 0xE7, //          Usage Maximum (Right GUI)
                0x15, 0x00, //          Logical Minimum (0)
                0x25, 0x01, //          Logical Maximum (1)
                0x75, 0x01, //          Report Size (1)
                0x95, 0x08, //          Report Count (8)
                0x81, 0x02, //          Input (Data, Variable, Absolute)
                0x19, 0x00, //          Usage Minimum (0)
                0x29, 0x65, //          Usage Maximum (101)
                0x15, 0x00, //          Logical Minimum (0)
                0x25, 0x65, //          Logical Maximum (101)
                0x75, 0x08, //          Report Size (8)
                0x95, 0x01, //          Report Count (1)
                0x81, 0x00, //          Input (Data, Array, Absolute)
                0xC0, //            End Collection
            }),

    /** System buttons: 5-bit padding, 3-bit flags (wake, home, back). */
    SYSTEM(
            new Integer[] {
                0x05, 0x01, //      Usage Page (Generic)
                0x09, 0x80, //      Usage (System Control)
                0xA1, 0x01, //      Collection (Application)
                0x15, 0x00, //          Logical Minimum (0)
                0x25, 0x01, //          Logical Maximum (1)
                0x75, 0x01, //          Report Size (1)
                0x95, 0x01, //          Report Count (1)
                0x09, 0x83, //          Usage (Wake)
                0x81, 0x06, //          Input (Data, Variable, Relative)
                0xC0, //            End Collection
                0x05, 0x0C, //      Usage Page (Consumer)
                0x09, 0x01, //      Usage (Consumer Control)
                0xA1, 0x01, //      Collection (Application)
                0x15, 0x00, //          Logical Minimum (0)
                0x25, 0x01, //          Logical Maximum (1)
                0x75, 0x01, //          Report Size (1)
                0x95, 0x01, //          Report Count (1)
                0x0A, 0x23, 0x02, //    Usage (Home)
                0x81, 0x06, //          Input (Data, Variable, Relative)
                0x0A, 0x24, 0x02, //    Usage (Back)
                0x81, 0x06, //          Input (Data, Variable, Relative)
                0x75, 0x01, //          Report Size (1)
                0x95, 0x05, //          Report Count (5) - padding
                0x81, 0x01, //          Input (Constant)
                0xC0, //            End Collection
            });

    private final ImmutableList<Integer> mDescriptor;

    AoaHID(Integer[] descriptor) {
        mDescriptor = ImmutableList.copyOf(descriptor);
    }

    /** @return HID identifier */
    int getId() {
        return ordinal();
    }

    /** @return HID descriptor */
    byte[] getDescriptor() {
        return Bytes.toArray(mDescriptor);
    }
}
