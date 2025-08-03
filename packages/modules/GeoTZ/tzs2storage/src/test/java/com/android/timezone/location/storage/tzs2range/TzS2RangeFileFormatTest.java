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

package com.android.timezone.location.storage.tzs2range;

import static com.android.storage.s2.S2Support.cellId;
import static com.android.storage.testing.MoreAsserts.assertThrows;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Tests for {@link TzS2RangeFileFormat}. */
public class TzS2RangeFileFormatTest {

    @Test
    public void accessors() {
        int s2Level = 12;
        int prefixBitCount = 11;
        int suffixBitCount = 16;
        int suffixTableBlockIdOffset = 5;
        int tableEntryBitCount = 24;
        int entryRangeLengthBitCount = 5;
        TzS2RangeFileFormat tzS2RangeFileFormat = new TzS2RangeFileFormat(s2Level, prefixBitCount,
                suffixBitCount, suffixTableBlockIdOffset, tableEntryBitCount,
                entryRangeLengthBitCount);

        assertEquals(s2Level, tzS2RangeFileFormat.getS2Level());
        assertEquals(prefixBitCount, tzS2RangeFileFormat.getPrefixBitCount());
        assertEquals(suffixBitCount, tzS2RangeFileFormat.getSuffixBitCount());
        assertEquals(suffixTableBlockIdOffset, tzS2RangeFileFormat.getSuffixTableBlockIdOffset());
        assertEquals(tableEntryBitCount, tzS2RangeFileFormat.getTableEntryBitCount());
        assertEquals(entryRangeLengthBitCount,
                tzS2RangeFileFormat.getTableEntryRangeLengthBitCount());

        // Derived values
        assertEquals((6 * intPow2(prefixBitCount - 3)) - 1,
                tzS2RangeFileFormat.getMaxPrefixValue());
        assertEquals(maxValForBits(suffixBitCount), tzS2RangeFileFormat.getMaxSuffixValue());

        int expectedTzIdSetIdBitCount =
                tableEntryBitCount - suffixBitCount - entryRangeLengthBitCount;
        assertEquals(maxValForBits(expectedTzIdSetIdBitCount),
                tzS2RangeFileFormat.getMaxTzIdSetIdValue());
        assertEquals(tableEntryBitCount / 8, tzS2RangeFileFormat.getTableEntryByteCount());
        assertEquals(maxValForBits(entryRangeLengthBitCount),
                tzS2RangeFileFormat.getTableEntryMaxRangeLengthValue());
    }

    @Test
    public void calculateRangeLength() {
        int s2Level = 12;
        int prefixBitCount = 11;
        int suffixBitCount = 16;
        int suffixTableBlockIdOffset = 5;
        int suffixTableEntryBitCount = 24;
        int entryRangeLengthBitCount = 5;
        TzS2RangeFileFormat tzS2RangeFileFormat = new TzS2RangeFileFormat(s2Level, prefixBitCount,
                suffixBitCount, suffixTableBlockIdOffset, suffixTableEntryBitCount,
                entryRangeLengthBitCount);

        assertEquals(2, tzS2RangeFileFormat.calculateRangeLength(
                cellId(s2Level, 0, 0), cellId(s2Level, 0, 2)));
        assertEquals(2, tzS2RangeFileFormat.calculateRangeLength(
                cellId(s2Level, 0, 2), cellId(s2Level, 0, 4)));

        int cellsPerFace = intPow2(s2Level * 2);
        assertEquals(cellsPerFace + 2,
                tzS2RangeFileFormat.calculateRangeLength(
                        cellId(s2Level, 0, 2), cellId(s2Level, 1, 4)));
    }

    @Test
    public void createCellId() {
        int s2Level = 12;
        int prefixBitCount = 11;
        int suffixBitCount = 16;
        int suffixTableBlockIdOffset = 5;
        int suffixTableEntryBitCount = 24;
        int entryRangeLengthBitCount = 5;
        TzS2RangeFileFormat tzS2RangeFileFormat = new TzS2RangeFileFormat(s2Level, prefixBitCount,
                suffixBitCount, suffixTableBlockIdOffset, suffixTableEntryBitCount,
                entryRangeLengthBitCount);

        // Too many bits for prefixValue
        assertThrows(IllegalArgumentException.class,
                () -> tzS2RangeFileFormat.createCellId(0b1000_00000000, 0b10000000_00000000));

        // Too many bits for suffixValue
        assertThrows(IllegalArgumentException.class,
                () -> tzS2RangeFileFormat.createCellId(0b1000_00000000, 0b100000000_00000000));

        // Some valid cases.
        assertEquals(cellId(s2Level, 4, 0),
                tzS2RangeFileFormat.createCellId(0b100_00000000, 0b00000000_00000000));
        assertEquals(cellId(s2Level, 4, 1),
                tzS2RangeFileFormat.createCellId(0b100_00000000, 0b00000000_00000001));

        assertEquals(cellId(s2Level, 5, intPow2(0)),
                tzS2RangeFileFormat.createCellId(0b101_00000000, 0b00000000_00000001));
        assertEquals(cellId(s2Level, 5, intPow2(8)),
                tzS2RangeFileFormat.createCellId(0b101_00000000, 0b00000001_00000000));
        assertEquals(cellId(s2Level, 5, intPow2(16)),
                tzS2RangeFileFormat.createCellId(0b101_00000001, 0b00000000_00000000));
    }

    @Test
    public void extractFaceIdFromPrefix() {
        int s2Level = 12;
        int prefixBitCount = 11;
        int suffixBitCount = 16;
        int suffixTableBlockIdOffset = 5;
        int suffixTableEntryBitCount = 24;
        int entryRangeLengthBitCount = 5;
        TzS2RangeFileFormat tzS2RangeFileFormat = new TzS2RangeFileFormat(s2Level, prefixBitCount,
                suffixBitCount, suffixTableBlockIdOffset, suffixTableEntryBitCount,
                entryRangeLengthBitCount);

        assertEquals(0, tzS2RangeFileFormat.extractFaceIdFromPrefix(0b00000000000));
        assertEquals(5, tzS2RangeFileFormat.extractFaceIdFromPrefix(0b10100000000));
        // We require this (invalid) face ID to work, since this method is used to detect face ID
        // overflow.
        assertEquals(6, tzS2RangeFileFormat.extractFaceIdFromPrefix(0b11000000000));
    }

    @Test
    public void createSuffixTableValue() {
        int s2Level = 12;
        int prefixBitCount = 11;
        int suffixBitCount = 16;
        int suffixTableBlockIdOffset = 5;
        int suffixTableEntryBitCount = 24;
        int entryRangeLengthBitCount = 5;
        TzS2RangeFileFormat tzS2RangeFileFormat = new TzS2RangeFileFormat(s2Level, prefixBitCount,
                suffixBitCount, suffixTableBlockIdOffset, suffixTableEntryBitCount,
                entryRangeLengthBitCount);

        // The format above sets 5 bits for range length and implicitly sets 3 bits for TZ IDs set
        // ID: suffixTableEntryBitCount(24) - suffixBitCount(16) - entryRangeLengthBitCount(5) = 3

        // Too many bits for rangeLength
        assertThrows(IllegalArgumentException.class,
                () -> tzS2RangeFileFormat.createSuffixTableValue(0b100000, 0b101));

        // Too many bits for tzIdSetId
        assertThrows(IllegalArgumentException.class,
                () -> tzS2RangeFileFormat.createSuffixTableValue(0b10101, 0b1101));

        // Some valid cases.
        assertEquals(0b10101101, tzS2RangeFileFormat.createSuffixTableValue(0b10101, 0b101));
        assertEquals(0b10101000, tzS2RangeFileFormat.createSuffixTableValue(0b10101, 0b000));
        assertEquals(0b00000101, tzS2RangeFileFormat.createSuffixTableValue(0b00000, 0b101));
    }

    private static int maxValForBits(int bits) {
        return intPow2(bits) - 1;
    }

    private static int intPow2(int value) {
        return (int) Math.pow(2, value);
    }
}
