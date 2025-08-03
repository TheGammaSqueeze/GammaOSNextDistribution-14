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

package com.android.server.net;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class InterfaceMapValueTest {
    private static final String IF_NAME = "wlan0";
    private static final byte[] IF_NAME_BYTE = new byte[]{'w', 'l', 'a', 'n', '0'};
    private static final byte[] IF_NAME_BYTE_WITH_PADDING =
            new byte[]{'w', 'l', 'a', 'n', '0', 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0}; // IF_NAME_BYTE_WITH_PADDING.length = 16
    private static final byte[] IF_NAME_BYTE_LONG =
            new byte[]{'w', 'l', 'a', 'n', '0', 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0}; // IF_NAME_BYTE_LONG.length = 24

    @Test
    public void testInterfaceMapValueFromString() {
        final InterfaceMapValue value = new InterfaceMapValue(IF_NAME);
        assertArrayEquals(IF_NAME_BYTE_WITH_PADDING, value.interfaceName);
    }

    @Test
    public void testInterfaceMapValueFromByte() {
        final InterfaceMapValue value = new InterfaceMapValue(IF_NAME_BYTE_WITH_PADDING);
        assertArrayEquals(IF_NAME_BYTE_WITH_PADDING, value.interfaceName);
    }

    @Test
    public void testInterfaceMapValueFromByteShort() {
        final InterfaceMapValue value = new InterfaceMapValue(IF_NAME_BYTE);
        assertArrayEquals(IF_NAME_BYTE_WITH_PADDING, value.interfaceName);
    }

    @Test
    public void testInterfaceMapValueFromByteLong() {
        final InterfaceMapValue value = new InterfaceMapValue(IF_NAME_BYTE_LONG);
        assertArrayEquals(IF_NAME_BYTE_WITH_PADDING, value.interfaceName);
    }

    @Test
    public void testGetInterfaceNameString() {
        final InterfaceMapValue value = new InterfaceMapValue(IF_NAME_BYTE_WITH_PADDING);
        assertEquals(IF_NAME, value.getInterfaceNameString());
    }
}
