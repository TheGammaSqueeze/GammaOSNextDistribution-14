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

package com.android.storage.util;

import static com.android.storage.testing.MoreAsserts.assertThrows;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;

public class BitwiseUtilsTest {

    @Test
    public void maxUnsignedValue() {
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.maxUnsignedValue(0));
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.maxUnsignedValue(64));

        assertEquals(1, BitwiseUtils.maxUnsignedValue(1));
        assertEquals(3, BitwiseUtils.maxUnsignedValue(2));
        assertEquals(7, BitwiseUtils.maxUnsignedValue(3));
        assertEquals(15, BitwiseUtils.maxUnsignedValue(4));
        assertEquals(Long.MAX_VALUE, BitwiseUtils.maxUnsignedValue(63));
    }

    @Test
    public void getLowBitsMask() {
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.getLowBitsMask(-1));
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.getLowBitsMask(65));

        assertEquals(0x0, BitwiseUtils.getLowBitsMask(0));
        assertEquals(0x1, BitwiseUtils.getLowBitsMask(1));
        assertEquals(0x3, BitwiseUtils.getLowBitsMask(2));
        assertEquals(0x7, BitwiseUtils.getLowBitsMask(3));
        assertEquals(0xFFFFFFFFFFFFFFFFL, BitwiseUtils.getLowBitsMask(64));
    }

    @Test
    public void getMidBitsMask() {
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.getMidBitsMask(0, 1));
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.getMidBitsMask(65, 1));
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.getMidBitsMask(64, 65));
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.getMidBitsMask(16, 17));

        assertEquals(0b1000, BitwiseUtils.getMidBitsMask(4, 1));
        assertEquals(0b1100, BitwiseUtils.getMidBitsMask(4, 2));
        assertEquals(0b1110, BitwiseUtils.getMidBitsMask(4, 3));
        assertEquals(0b1111, BitwiseUtils.getMidBitsMask(4, 4));

        assertEquals(0b10000000, BitwiseUtils.getMidBitsMask(8, 1));
        assertEquals(0b11000000, BitwiseUtils.getMidBitsMask(8, 2));
        assertEquals(0b11100000, BitwiseUtils.getMidBitsMask(8, 3));
        assertEquals(0b11111111, BitwiseUtils.getMidBitsMask(8, 8));

        assertEquals(0b1000000000000000, BitwiseUtils.getMidBitsMask(16, 1));
        assertEquals(0b1100000000000000, BitwiseUtils.getMidBitsMask(16, 2));
        assertEquals(0b1110000000000000, BitwiseUtils.getMidBitsMask(16, 3));
        assertEquals(0b1111111111111111, BitwiseUtils.getMidBitsMask(16, 16));

        assertEquals(0x8000000000000000L, BitwiseUtils.getMidBitsMask(64, 1));
        assertEquals(0xC000000000000000L, BitwiseUtils.getMidBitsMask(64, 2));
        assertEquals(0xE000000000000000L, BitwiseUtils.getMidBitsMask(64, 3));
        assertEquals(0xFFFFFFFFFFFFFFFFL, BitwiseUtils.getMidBitsMask(64, 64));
    }

    @Test
    public void checkValueInRange() {
        assertCheckValueInRangeThrowsException(-1, 1, false);
        assertCheckValueInRangeThrowsException(-1, 1, true);
        assertCheckValueInRangeThrowsException(0, 1, false);
        assertCheckValueInRangeThrowsException(0, 1, true);

        BitwiseUtils.checkValueInRange(1, 0, true);
        BitwiseUtils.checkValueInRange(1, -1, true);
        assertCheckValueInRangeThrowsException(1, BitwiseUtils.minSignedValue(2) - 1, true);
        assertCheckValueInRangeThrowsException(1, BitwiseUtils.maxSignedValue(2) + 1, true);
        BitwiseUtils.checkValueInRange(1, 0, false);
        BitwiseUtils.checkValueInRange(1, 1, false);
        assertCheckValueInRangeThrowsException(1, -1, false);
        assertCheckValueInRangeThrowsException(1, BitwiseUtils.maxUnsignedValue(2) + 1, false);

        BitwiseUtils.checkValueInRange(2, 0, true);
        BitwiseUtils.checkValueInRange(2, 1, true);
        BitwiseUtils.checkValueInRange(2, -1, true);
        BitwiseUtils.checkValueInRange(2, -2, true);
        assertCheckValueInRangeThrowsException(2, BitwiseUtils.minSignedValue(2) - 1, true);
        assertCheckValueInRangeThrowsException(2, BitwiseUtils.maxSignedValue(2) + 1, true);
        BitwiseUtils.checkValueInRange(2, 0, false);
        BitwiseUtils.checkValueInRange(2, 1, false);
        BitwiseUtils.checkValueInRange(2, 2, false);
        BitwiseUtils.checkValueInRange(2, 3, false);
        assertCheckValueInRangeThrowsException(2, -1, false);
        assertCheckValueInRangeThrowsException(2, BitwiseUtils.maxUnsignedValue(2) + 1, false);

        BitwiseUtils.checkValueInRange(63, 0, true);
        BitwiseUtils.checkValueInRange(63, 1, true);
        BitwiseUtils.checkValueInRange(63, -1, true);
        BitwiseUtils.checkValueInRange(63, BitwiseUtils.maxSignedValue(63), true);
        assertCheckValueInRangeThrowsException(63, BitwiseUtils.minSignedValue(63) - 1, true);
        assertCheckValueInRangeThrowsException(63, BitwiseUtils.maxSignedValue(63) + 1, true);
        BitwiseUtils.checkValueInRange(63, 0, false);
        BitwiseUtils.checkValueInRange(63, 1, false);
        BitwiseUtils.checkValueInRange(63, 2, false);
        BitwiseUtils.checkValueInRange(63, BitwiseUtils.maxUnsignedValue(63), false);
        assertCheckValueInRangeThrowsException(63, -1, false);
        assertCheckValueInRangeThrowsException(63, BitwiseUtils.maxUnsignedValue(63) + 1, false);

        assertCheckValueInRangeThrowsException(64, 1, false);
        BitwiseUtils.checkValueInRange(64, 0, true);
        BitwiseUtils.checkValueInRange(64, 1, true);
        BitwiseUtils.checkValueInRange(64, 2, true);
        BitwiseUtils.checkValueInRange(64, BitwiseUtils.maxSignedValue(64), true);

        assertCheckValueInRangeThrowsException(65, 1, true);
    }

    private static void assertCheckValueInRangeThrowsException(int valueSizeBits, long value,
            boolean signedValue) {
        try {
            BitwiseUtils.checkValueInRange(valueSizeBits, value, signedValue);
            Assert.fail(valueSizeBits + "," + value + "," + signedValue);
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void getHighBitMask() {
        assertEquals(0x8000000000000000L, BitwiseUtils.getHighBitsMask(1));
        assertEquals(0xC000000000000000L, BitwiseUtils.getHighBitsMask(2));
        assertEquals(0xE000000000000000L, BitwiseUtils.getHighBitsMask(3));
        assertEquals(0xFFFFFFFFFFFFFFFCL, BitwiseUtils.getHighBitsMask(62));
        assertEquals(0xFFFFFFFFFFFFFFFEL, BitwiseUtils.getHighBitsMask(63));
        assertEquals(0xFFFFFFFFFFFFFFFFL, BitwiseUtils.getHighBitsMask(64));
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.getHighBitsMask(65));
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.getHighBitsMask(0));
    }

    @Test
    public void extractLowBits() {
        assertThrows(IllegalArgumentException.class,
                () -> BitwiseUtils.extractLowBits(65, 0xFFFFFFFFFFFFFFFFL));
        assertThrows(IllegalArgumentException.class,
                () -> BitwiseUtils.extractLowBits(0, 0xFFFFFFFFFFFFFFFFL));

        assertEquals(0xFFFFFFFFFFFFFFFFL, BitwiseUtils.extractLowBits(64, 0xFFFFFFFFFFFFFFFFL));
        assertEquals(0x7FFFFFFFFFFFFFFFL, BitwiseUtils.extractLowBits(63, 0xFFFFFFFFFFFFFFFFL));
        assertEquals(0x3FFFFFFFFFFFFFFFL, BitwiseUtils.extractLowBits(62, 0xFFFFFFFFFFFFFFFFL));
        assertEquals(0x0000000000000003L, BitwiseUtils.extractLowBits(2, 0xFFFFFFFFFFFFFFFFL));
        assertEquals(0x0000000000000001L, BitwiseUtils.extractLowBits(1, 0xFFFFFFFFFFFFFFFFL));
    }

    @Test
    public void getSingleBitMask() {
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.getSingleBitMask(64));
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.getSingleBitMask(-1));

        assertEquals(0x8000000000000000L, BitwiseUtils.getSingleBitMask(63));
        assertEquals(0x4000000000000000L, BitwiseUtils.getSingleBitMask(62));
        assertEquals(0x0000000000000004L, BitwiseUtils.getSingleBitMask(2));
        assertEquals(0x0000000000000002L, BitwiseUtils.getSingleBitMask(1));
        assertEquals(0x0000000000000001L, BitwiseUtils.getSingleBitMask(0));
    }

    @Test
    public void signExtendToLong() {
        assertThrows(IllegalArgumentException.class,
                () -> BitwiseUtils.signExtendToLong(65, 0x555555555555555L));
        assertThrows(IllegalArgumentException.class,
                () -> BitwiseUtils.signExtendToLong(0, 0x555555555555555L));

        assertEquals(0xF555555555555555L, BitwiseUtils.signExtendToLong(64, 0xF555555555555555L));
        assertEquals(0xE555555555555555L, BitwiseUtils.signExtendToLong(64, 0xE555555555555555L));

        assertEquals(0xF555555555555555L, BitwiseUtils.signExtendToLong(63, 0x7555555555555555L));
        assertEquals(0x3555555555555555L, BitwiseUtils.signExtendToLong(63, 0x3555555555555555L));

        assertEquals(0xFFFFFFFFFFFFFFFEL, BitwiseUtils.signExtendToLong(2, 0x0000000000000002L));
        assertEquals(0x0000000000000001L, BitwiseUtils.signExtendToLong(2, 0x0000000000000001L));

        assertEquals(0xFFFFFFFFFFFFFFFFL, BitwiseUtils.signExtendToLong(1, 0x0000000000000001L));
        assertEquals(0x0000000000000000L, BitwiseUtils.signExtendToLong(1, 0x0000000000000000L));

        assertEquals(0xFFFFFFFFFFFFFFE1L, BitwiseUtils.signExtendToLong(8, 0x55555555555555E1L));
        assertEquals(0x0000000000000071L, BitwiseUtils.signExtendToLong(8, 0x5555555555555571L));
    }

    @Test
    public void testToUnsignedString() {
        assertEquals("00000001", BitwiseUtils.toUnsignedString(8, 1));
        assertEquals("000000001", BitwiseUtils.toUnsignedString(9, 1));
        assertEquals("0000000001", BitwiseUtils.toUnsignedString(10, 1));
        assertEquals("00000010", BitwiseUtils.toUnsignedString(8, 2));
        assertEquals("000000010", BitwiseUtils.toUnsignedString(9, 2));
        assertEquals("0000000010", BitwiseUtils.toUnsignedString(10, 2));

        assertEquals("1", BitwiseUtils.toUnsignedString(1, 1));
        assertEquals("10", BitwiseUtils.toUnsignedString(2, 2));
        assertThrows(IllegalArgumentException.class, () -> BitwiseUtils.toUnsignedString(1, 2));
    }

    @Test
    public void maxValue() {
        assertEquals(3, BitwiseUtils.maxValue(3, true));
        assertEquals(7, BitwiseUtils.maxValue(3, false));
    }

    @Test
    public void minValue() {
        assertEquals(-4, BitwiseUtils.minValue(3, true));
        assertEquals(0, BitwiseUtils.minValue(3, false));
    }
}
