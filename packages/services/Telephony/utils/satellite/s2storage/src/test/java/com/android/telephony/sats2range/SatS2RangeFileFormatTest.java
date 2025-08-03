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

package com.android.telephony.sats2range;

import static com.android.storage.s2.S2Support.cellId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.telephony.sats2range.read.SatS2RangeFileFormat;

import org.junit.Test;

/** Tests for {@link SatS2RangeFileFormat}. */
public class SatS2RangeFileFormatTest {
    @Test
    public void accessors() {
        int s2Level = 12;
        int prefixBitCount = 11;
        int suffixBitCount = 16;
        int suffixTableBlockIdOffset = 5;
        int tableEntryBitCount = 24;
        int entryRangeLengthBitCount = 8;
        boolean isAllowedList = true;
        SatS2RangeFileFormat satS2RangeFileFormat = new SatS2RangeFileFormat(s2Level,
                prefixBitCount, suffixBitCount, suffixTableBlockIdOffset, tableEntryBitCount,
                isAllowedList);

        assertEquals(s2Level, satS2RangeFileFormat.getS2Level());
        assertEquals(prefixBitCount, satS2RangeFileFormat.getPrefixBitCount());
        assertEquals(suffixBitCount, satS2RangeFileFormat.getSuffixBitCount());
        assertEquals(suffixTableBlockIdOffset, satS2RangeFileFormat.getSuffixTableBlockIdOffset());
        assertEquals(tableEntryBitCount, satS2RangeFileFormat.getTableEntryBitCount());
        assertEquals(entryRangeLengthBitCount,
                satS2RangeFileFormat.getTableEntryRangeLengthBitCount());

        // Derived values
        assertEquals((6 * intPow2(prefixBitCount - 3)) - 1,
                satS2RangeFileFormat.getMaxPrefixValue());
        assertEquals(maxValForBits(suffixBitCount), satS2RangeFileFormat.getMaxSuffixValue());
        assertEquals(tableEntryBitCount / 8, satS2RangeFileFormat.getTableEntryByteCount());
        assertEquals(maxValForBits(entryRangeLengthBitCount),
                satS2RangeFileFormat.getTableEntryMaxRangeLengthValue());
        assertTrue(satS2RangeFileFormat.isAllowedList());
    }

    @Test
    public void calculateRangeLength() {
        int s2Level = 12;
        int prefixBitCount = 11;
        int suffixBitCount = 16;
        int suffixTableBlockIdOffset = 5;
        int suffixTableEntryBitCount = 24;
        boolean isAllowedList = false;
        SatS2RangeFileFormat satS2RangeFileFormat = new SatS2RangeFileFormat(s2Level,
                prefixBitCount, suffixBitCount, suffixTableBlockIdOffset, suffixTableEntryBitCount,
                isAllowedList);

        assertEquals(2, satS2RangeFileFormat.calculateRangeLength(
                cellId(s2Level, 0, 0), cellId(s2Level, 0, 2)));
        assertEquals(2, satS2RangeFileFormat.calculateRangeLength(
                cellId(s2Level, 0, 2), cellId(s2Level, 0, 4)));

        int cellsPerFace = intPow2(s2Level * 2);
        assertEquals(cellsPerFace + 2,
                satS2RangeFileFormat.calculateRangeLength(
                        cellId(s2Level, 0, 2), cellId(s2Level, 1, 4)));
        assertFalse(satS2RangeFileFormat.isAllowedList());
    }

    @Test
    public void createCellId() {
        int s2Level = 12;
        int prefixBitCount = 11;
        int suffixBitCount = 16;
        int suffixTableBlockIdOffset = 5;
        int suffixTableEntryBitCount = 24;
        boolean isAllowedList = true;
        SatS2RangeFileFormat satS2RangeFileFormat = new SatS2RangeFileFormat(s2Level,
                prefixBitCount, suffixBitCount, suffixTableBlockIdOffset, suffixTableEntryBitCount,
                isAllowedList);

        // Too many bits for prefixValue
        assertThrows(IllegalArgumentException.class,
                () -> satS2RangeFileFormat.createCellId(0b1000_00000000, 0b10000000_00000000));

        // Too many bits for suffixValue
        assertThrows(IllegalArgumentException.class,
                () -> satS2RangeFileFormat.createCellId(0b1000_00000000, 0b100000000_00000000));

        // Some valid cases.
        assertEquals(cellId(s2Level, 4, 0),
                satS2RangeFileFormat.createCellId(0b100_00000000, 0b00000000_00000000));
        assertEquals(cellId(s2Level, 4, 1),
                satS2RangeFileFormat.createCellId(0b100_00000000, 0b00000000_00000001));

        assertEquals(cellId(s2Level, 5, intPow2(0)),
                satS2RangeFileFormat.createCellId(0b101_00000000, 0b00000000_00000001));
        assertEquals(cellId(s2Level, 5, intPow2(8)),
                satS2RangeFileFormat.createCellId(0b101_00000000, 0b00000001_00000000));
        assertEquals(cellId(s2Level, 5, intPow2(16)),
                satS2RangeFileFormat.createCellId(0b101_00000001, 0b00000000_00000000));
        assertTrue(satS2RangeFileFormat.isAllowedList());
    }

    @Test
    public void extractFaceIdFromPrefix() {
        int s2Level = 12;
        int prefixBitCount = 11;
        int suffixBitCount = 16;
        int suffixTableBlockIdOffset = 5;
        int suffixTableEntryBitCount = 24;
        boolean isAllowedList = true;
        SatS2RangeFileFormat satS2RangeFileFormat = new SatS2RangeFileFormat(s2Level,
                prefixBitCount, suffixBitCount, suffixTableBlockIdOffset, suffixTableEntryBitCount,
                isAllowedList);

        assertEquals(0, satS2RangeFileFormat.extractFaceIdFromPrefix(0b00000000000));
        assertEquals(5, satS2RangeFileFormat.extractFaceIdFromPrefix(0b10100000000));
        // We require this (invalid) face ID to work, since this method is used to detect face ID
        // overflow.
        assertEquals(6, satS2RangeFileFormat.extractFaceIdFromPrefix(0b11000000000));
        assertTrue(satS2RangeFileFormat.isAllowedList());
    }

    @Test
    public void createSuffixTableValue() {
        int s2Level = 12;
        int prefixBitCount = 11;
        int suffixBitCount = 16;
        int suffixTableBlockIdOffset = 5;
        int suffixTableEntryBitCount = 24;
        boolean isAllowedList = true;
        SatS2RangeFileFormat satS2RangeFileFormat = new SatS2RangeFileFormat(s2Level,
                prefixBitCount, suffixBitCount, suffixTableBlockIdOffset, suffixTableEntryBitCount,
                isAllowedList);

        // Too many bits for rangeLength
        assertThrows(IllegalArgumentException.class,
                () -> satS2RangeFileFormat.createSuffixTableValue(0b100000000));

        // Some valid cases.
        assertEquals(0b10101, satS2RangeFileFormat.createSuffixTableValue(0b10101));
        assertEquals(0b00000, satS2RangeFileFormat.createSuffixTableValue(0b00000));
        assertTrue(satS2RangeFileFormat.isAllowedList());
    }

    private static int maxValForBits(int bits) {
        return intPow2(bits) - 1;
    }

    private static int intPow2(int value) {
        return (int) Math.pow(2, value);
    }
}
