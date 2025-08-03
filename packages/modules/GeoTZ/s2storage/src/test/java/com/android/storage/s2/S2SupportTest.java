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

package com.android.storage.s2;

import static com.android.storage.testing.MoreAsserts.assertThrows;

import static org.junit.Assert.assertEquals;

import com.android.storage.util.BitwiseUtils;

import org.junit.Test;

public class S2SupportTest {

    @Test
    public void cellIdToString() {
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellIdToString(0));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellIdToString(
                0b11100000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L));
        // Valid face IDs
        S2Support.cellIdToString(
                0b00010000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L);
        S2Support.cellIdToString(
                0b00110000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L);
        S2Support.cellIdToString(
                0b01010000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L);
        S2Support.cellIdToString(
                0b01110000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L);
        S2Support.cellIdToString(
                0b10010000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L);
        S2Support.cellIdToString(
                0b10110000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L);
        // Invalid face ID
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellIdToString(
                0b11010000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellIdToString(
                0b11110000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L));

        // Final bit in a bad position.
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellIdToString(
                0b11101000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellIdToString(
                0b11101010_00000000_00000000_00000000_00000000_00000000_00000000_00000000L));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellIdToString(
                0b11101010_10000000_00000000_00000000_00000000_00000000_00000000_00000000L));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellIdToString(
                0b11101010_10100000_00000000_00000000_00000000_00000000_00000000_00000000L));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellIdToString(
                0b11101010_10100000_00000000_00000000_00000000_00000000_00000000_00000010L));

        // Valid
        S2Support.cellIdToString(
                0b10100100_00000000_00000000_00000000_00000000_00000000_00000000_00000000L);
        S2Support.cellIdToString(
                0b10100000_00000000_00000000_00000000_00000000_00000000_00000000_00000001L);
    }

    @Test
    public void cellId() {
        // Invalid levels
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellId(-1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.cellId(S2Support.MAX_S2_LEVEL + 1, 0, 0));

        // level 0 (face only)
        assertCellIdEquals(0b00010000L << 56, S2Support.cellId(0, 0, 0));
        assertCellIdEquals(0b00110000L << 56, S2Support.cellId(0, 1, 0));
        assertCellIdEquals(0b10110000L << 56, S2Support.cellId(0, S2Support.MAX_FACE_ID, 0));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellId(0, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.cellId(0, S2Support.MAX_FACE_ID + 1, 0));

        assertThrows(IllegalArgumentException.class, () -> S2Support.cellId(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellId(0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.cellId(0, S2Support.MAX_FACE_ID, 1));

        // level 1, 2 bits for index.
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellId(1, -1, 0));
        assertCellIdEquals(0b00000100L << 56, S2Support.cellId(1, 0, 0));
        assertCellIdEquals(0b00100100L << 56, S2Support.cellId(1, 1, 0));
        assertCellIdEquals(0b10100100L << 56, S2Support.cellId(1, S2Support.MAX_FACE_ID, 0));
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.cellId(1, S2Support.MAX_FACE_ID + 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.cellId(1, S2Support.MAX_FACE_ID, -1));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellId(1, 0, -1));

        assertCellIdEquals(0b00001100L << 56, S2Support.cellId(1, 0, 1));
        assertCellIdEquals(0b00010100L << 56, S2Support.cellId(1, 0, 2));
        assertCellIdEquals(0b00011100L << 56, S2Support.cellId(1, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellId(1, 0, 4));
        assertCellIdEquals(0b00101100L << 56, S2Support.cellId(1, 1, 1));
        assertCellIdEquals(0b00110100L << 56, S2Support.cellId(1, 1, 2));
        assertCellIdEquals(0b00111100L << 56, S2Support.cellId(1, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> S2Support.cellId(1, 0, 4));

        // Level 30 - 3 face bits + 60 "storage bits" + 1-bit (1)
        assertCellIdEquals(
                0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000001L,
                S2Support.cellId(30, 0, 0));
        assertCellIdEquals(
                0b00100000_00000000_00000000_00000000_00000000_00000000_00000000_00000001L,
                S2Support.cellId(30, 1, 0));
        assertCellIdEquals(
                0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000011L,
                S2Support.cellId(30, 0, 1));
        assertCellIdEquals(
                0b00100000_00000000_00000000_00000000_00000000_00000000_00000000_00000011L,
                S2Support.cellId(30, 1, 1));
        assertCellIdEquals(
                0b00011111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L,
                S2Support.cellId(30, 0, 0x0FFFFFFFFFFFFFFFL /* (2^61)-1 */));
        assertCellIdEquals(
                0b00111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L,
                S2Support.cellId(30, 1, 0x0FFFFFFFFFFFFFFFL /* (2^61)-1 */));
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.cellId(30, 1, 0x1000000000000000L /* 2^61 */));
    }

    @Test
    public void storageBitCountForLevel() {
        assertEquals(3, S2Support.storageBitCountForLevel(0));
        assertEquals(5, S2Support.storageBitCountForLevel(1));
        assertEquals(7, S2Support.storageBitCountForLevel(2));
        assertEquals(63, S2Support.storageBitCountForLevel(30));
    }

    @Test
    public void validateCellId() {
        // At the trailing 1.
        assertThrows(IllegalArgumentException.class, () -> S2Support.validateCellId(0b00000000));
        S2Support.validateCellId(0b00000001);
        assertThrows(IllegalArgumentException.class, () -> S2Support.validateCellId(0b00000010));
        S2Support.validateCellId(0b00000100);
        assertThrows(IllegalArgumentException.class, () -> S2Support.validateCellId(0b00001000));

        // Max valid face ID (101 = 5)
        S2Support.validateCellId((0b10100000L << 56) | 0b00000001L);
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.validateCellId((0b10100000L << 56) | 0b00000010));
        S2Support.validateCellId((0b10100000L << 56) | 0b00000100L);
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.validateCellId((0b10100000L << 56) | 0b00001000));

        // Invalid face ID (111 == 6)
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.validateCellId(0b11110000L << 56));
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.validateCellId(0b11100100L << 56));
    }

    @Test
    public void offsetCellId() {
        // Check offset = 0.
        assertEquals(0b00010000L << 56, S2Support.offsetCellId(0b00010000L << 56, 0));
        assertEquals(0b00000100L << 56, S2Support.offsetCellId(0b00000100L << 56, 0));

        assertThrows(IllegalArgumentException.class,
                () -> S2Support.offsetCellId(0b00010000L << 56, 6));
        assertThrows(IllegalArgumentException.class,
                () -> S2Support.offsetCellId(0b10111100L << 56, 24));

        // Try level 0 behavior.
        assertEquals(0b00110000L << 56, S2Support.offsetCellId(0b00010000L << 56, 1));
        // Wrap behavior: face ID 4 (100) incremented by 4 -> face 2
        assertEquals(0b01010000L << 56, S2Support.offsetCellId(0b10010000L << 56, 4));
        // Wrap behavior: face ID 4 (100) decremented by 5 -> face 5.
        assertEquals(0b10110000L << 56, S2Support.offsetCellId(0b10010000L << 56, -5));

        // Try level 1 behavior.
        assertEquals(0b00110100L << 56, S2Support.offsetCellId(0b00101100L << 56, 1));
        // Face advances...
        assertEquals(0b01000100L << 56, S2Support.offsetCellId(0b00101100L << 56, 3));
        // Face wraps positive...
        assertEquals(0b00000100L << 56, S2Support.offsetCellId(0b10111100L << 56, 1));
        // Face wraps positive (max positive offset)...
        assertEquals(0b10110100L << 56, S2Support.offsetCellId(0b10111100L << 56, 23));
        // Face wraps negative...
        assertEquals(0b10111100L << 56, S2Support.offsetCellId(0b00000100L << 56, -1));
        // Face wraps negative (max negative offset ...
        assertEquals(0b00001100L << 56, S2Support.offsetCellId(0b00000100L << 56, -23));
    }

    @Test
    public void getS2Level() {
        assertEquals(30, S2Support.getS2Level(0b00000001L));
        assertThrows(IllegalArgumentException.class, () -> S2Support.getS2Level(0b00000010L));
        assertEquals(29, S2Support.getS2Level(0b00000100L));

        assertEquals(1, S2Support.getS2Level(0b00000100L << 56));
        assertThrows(IllegalArgumentException.class, () -> S2Support.getS2Level(0b00001000L << 56));
        assertEquals(0, S2Support.getS2Level(0b00010000L << 56));
    }

    @Test
    public void getMinCellId() {
        assertEquals(S2Support.cellId(0, 0, 0), S2Support.getMinCellId(0));
        assertEquals(S2Support.cellId(1, 0, 0), S2Support.getMinCellId(1));
        assertEquals(S2Support.cellId(2, 0, 0), S2Support.getMinCellId(2));
        assertEquals(S2Support.cellId(29, 0, 0), S2Support.getMinCellId(29));
        assertEquals(S2Support.cellId(30, 0, 0), S2Support.getMinCellId(30));
    }

    @Test
    public void getMaxCellId() {
        assertEquals(S2Support.cellId(0, 5, 0), S2Support.getMaxCellId(0));
        assertEquals(S2Support.cellId(1, 5, BitwiseUtils.maxUnsignedValue(2)),
                S2Support.getMaxCellId(1));
        assertEquals(S2Support.cellId(2, 5, BitwiseUtils.maxUnsignedValue(4)),
                S2Support.getMaxCellId(2));
        assertEquals(S2Support.cellId(29, 5, BitwiseUtils.maxUnsignedValue(29 * 2)),
                S2Support.getMaxCellId(29));
        assertEquals(S2Support.cellId(30, 5, BitwiseUtils.maxUnsignedValue(30 * 2)),
                S2Support.getMaxCellId(30));
    }

    private void assertCellIdEquals(long expectedCellId, long actualCellId) {
        assertEquals("expected=" + S2Support.cellIdToString(expectedCellId)
                        + ", actual=" + S2Support.cellIdToString(actualCellId),
                expectedCellId, actualCellId);
    }
}
