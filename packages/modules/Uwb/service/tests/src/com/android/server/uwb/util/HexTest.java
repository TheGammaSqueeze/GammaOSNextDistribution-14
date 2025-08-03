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

package com.android.server.uwb.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test class for {@link Hex}.
 */
@RunWith(JUnit4.class)
public class HexTest {

    private final String mLower = "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff";
    private final String mUpper = "F0F1F2F3F4F5F6F7F8F9FAFBFCFDFEFF";
    private final byte[] mBytes = {
            (byte) 0xf0,
            (byte) 0xf1,
            (byte) 0xf2,
            (byte) 0xf3,
            (byte) 0xf4,
            (byte) 0xf5,
            (byte) 0xf6,
            (byte) 0xf7,
            (byte) 0xf8,
            (byte) 0xf9,
            (byte) 0xfa,
            (byte) 0xfb,
            (byte) 0xfc,
            (byte) 0xfd,
            (byte) 0xfe,
            (byte) 0xff,
    };

    @Test
    public void testClass() {
        assertEquals(Hex.RADIX, 16);
        assertEquals(Hex.RADIX, Hex.UPPER.length);
        assertEquals(Hex.RADIX, Hex.LOWER.length);
    }

    @Test
    public void testEncode() {
        assertEquals(mLower, Hex.encode(mBytes));
    }

    @Test
    public void testEncodeUpper() {
        assertEquals(mUpper, Hex.encodeUpper(mBytes));
    }

    @Test
    public void testDecodeLower() {
        assertArrayEquals(mBytes, Hex.decode(mLower));
    }

    @Test
    public void testDecodeUpper() {
        assertArrayEquals(mBytes, Hex.decode(mUpper));
    }

    @Test
    public void testDecodeEmptyString() {
        assertArrayEquals(new byte[0], Hex.decode(""));
    }

    @Test
    public void testDecodeOddLength() {
        assertThat(Hex.decode("fff"))
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("0fff"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecodeIllegalHighNibble() {
        Hex.decode("g0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecodeIllegalLowNibble() {
        Hex.decode("0g");
    }
}
