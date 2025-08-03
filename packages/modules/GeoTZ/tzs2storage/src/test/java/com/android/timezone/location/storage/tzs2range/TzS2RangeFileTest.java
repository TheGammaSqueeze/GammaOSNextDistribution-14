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

import static com.android.storage.s2.S2Support.FACE_BIT_COUNT;
import static com.android.storage.testing.TestSupport.listOf;

import static org.junit.Assert.assertEquals;

import com.android.storage.util.BitwiseUtils;
import com.android.timezone.location.storage.tzs2range.read.TzS2RangeFileReader;
import com.android.timezone.location.storage.tzs2range.write.TzS2RangeFileWriter;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class TzS2RangeFileTest {

    @Test
    public void findEntryByCellId() throws IOException {
        File file = File.createTempFile("test", ".dat");

        TzS2RangeFileFormat fileFormat;
        TzS2Range range1, range2, range3;
        try (TzS2RangeFileWriter tzS2RangeFileWriter =
                     TzS2RangeFileWriter.open(file, createS2RangeFileFormat())) {
            fileFormat = tzS2RangeFileWriter.getFileFormat();

            // Two ranges that share a prefix.
            range1 = new TzS2Range(
                    cellId(fileFormat, 1, 1000, 1000),
                    cellId(fileFormat, 1, 1000, 2000),
                    listOf("One"));
            range2 = new TzS2Range(
                    cellId(fileFormat, 1, 1000, 2000),
                    cellId(fileFormat, 1, 1000, 3000),
                    listOf("Two"));
            // This range has a different prefix, so will be in a different suffix table.
            range3 = new TzS2Range(
                    cellId(fileFormat, 1, 1001, 1000),
                    cellId(fileFormat, 1, 1001, 2000),
                    listOf("Three"));
            tzS2RangeFileWriter.processRanges(listOf(range1, range2, range3).iterator());
        }

        try (TzS2RangeFileReader tzS2RangeFileReader = TzS2RangeFileReader.open(file)) {
            TzS2RangeFileReader.Entry entry1 =
                    tzS2RangeFileReader.findEntryByCellId(cellId(fileFormat, 1, 1000, 1500));
            assertEquals(range1, entry1.getTzS2Range());

            TzS2RangeFileReader.Entry entry2 =
                    tzS2RangeFileReader.findEntryByCellId(cellId(fileFormat, 1, 1000, 2500));
            assertEquals(range2, entry2.getTzS2Range());

            TzS2RangeFileReader.Entry entry3 =
                    tzS2RangeFileReader.findEntryByCellId(cellId(fileFormat, 1, 1001, 1500));
            assertEquals(range3, entry3.getTzS2Range());
        }
    }

    private static long cellId(
            TzS2RangeFileFormat fileFormat, int faceId, int otherPrefixBits, int suffixBits) {
        int prefixBitCount = fileFormat.getPrefixBitCount();
        int otherPrefixBitsCount = prefixBitCount - FACE_BIT_COUNT;
        int maxOtherPrefixBits = (int) BitwiseUtils.getLowBitsMask(otherPrefixBitsCount);
        if (otherPrefixBits > maxOtherPrefixBits) {
            throw new IllegalArgumentException("otherPrefixBits=" + otherPrefixBits
                    + " (" + Integer.toBinaryString(otherPrefixBits) + ")"
                    + " has more bits than otherPrefixBitsCount=" + otherPrefixBitsCount
                    + " allows");
        }

        int prefixValue = faceId;
        prefixValue <<= otherPrefixBitsCount;
        prefixValue |= otherPrefixBits;

        int suffixBitCount = fileFormat.getSuffixBitCount();
        if (suffixBits > BitwiseUtils.getLowBitsMask(suffixBitCount)) {
            throw new IllegalArgumentException(
                    "suffixBits=" + suffixBits + " (" + Integer.toBinaryString(suffixBits)
                            + ") has more bits than " + suffixBitCount + " bits allows");
        }
        return fileFormat.createCellId(prefixValue, suffixBits);
    }

    /** Returns a valid {@link TzS2RangeFileFormat}. */
    private static TzS2RangeFileFormat createS2RangeFileFormat() {
        int dataS2Level = 12;
        int faceIdBits = 3;
        int bitCountPerLevel = 2;
        int s2LevelBitCount = (dataS2Level * bitCountPerLevel) + faceIdBits;
        int prefixLevel = 5;
        int prefixBitCount = faceIdBits + (prefixLevel * bitCountPerLevel);
        int suffixBitCount = s2LevelBitCount - prefixBitCount;
        int suffixTableEntryBitCount = 4 * Byte.SIZE;
        int endCellIdOffsetBitCount = 12; // 4096
        int suffixTableBlockIdOffset = 5;
        return new TzS2RangeFileFormat(dataS2Level, prefixBitCount, suffixBitCount,
                suffixTableBlockIdOffset, suffixTableEntryBitCount, endCellIdOffsetBitCount);
    }
}
