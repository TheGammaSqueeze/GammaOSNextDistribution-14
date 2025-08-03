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

import static org.junit.Assert.assertEquals;

import com.android.storage.block.read.BlockInfo;
import com.android.storage.block.write.BlockWriter;
import com.android.timezone.location.storage.tzs2range.read.SuffixTableExtraInfo;
import com.android.timezone.location.storage.tzs2range.write.SuffixTableWriter;

import org.junit.Test;

public class SuffixTableExtraInfoTest {

    @Test
    public void create_emptyBlock() throws Exception {
        // Generate a real suffix table block info and an empty block.
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();
        BlockWriter emptyBlockWriter =
                SuffixTableWriter.createEmptyBlockWriter();
        BlockWriter.ReadBack readBack = emptyBlockWriter.close();

        // Read back the block info.
        BlockInfo blockInfo = createBlockInfo(readBack);

        SuffixTableExtraInfo extraInfo = SuffixTableExtraInfo.create(fileFormat, blockInfo);
        assertEquals(0, extraInfo.getEntryCount());
    }

    @Test
    public void create_nonEmptyBlock() throws Exception {
        // Generate a real suffix table block info and block containing some elements.
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();
        SuffixTableSharedData suffixTableSharedData = createSuffixTableSharedData();
        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);

        int arbitraryTzIdSetId = 7;
        int tablePrefix = suffixTableSharedData.getTablePrefix();
        SuffixTableRange range1 = new SuffixTableRange(
                fileFormat.createCellId(tablePrefix, 1000),
                fileFormat.createCellId(tablePrefix, 1001), arbitraryTzIdSetId);
        SuffixTableRange range2 = new SuffixTableRange(
                fileFormat.createCellId(tablePrefix, 1002),
                fileFormat.createCellId(tablePrefix, 1003), arbitraryTzIdSetId);
        SuffixTableRange range3 = new SuffixTableRange(
                fileFormat.createCellId(tablePrefix, 1004),
                fileFormat.createCellId(tablePrefix, 1005), arbitraryTzIdSetId);

        suffixTableWriter.addRange(range1);
        suffixTableWriter.addRange(range2);
        suffixTableWriter.addRange(range3);
        BlockWriter.ReadBack readBack = suffixTableWriter.close();

        // Read back the block info.
        BlockInfo blockInfo = createBlockInfo(readBack);

        SuffixTableExtraInfo extraInfo = SuffixTableExtraInfo.create(fileFormat, blockInfo);
        assertEquals(3, extraInfo.getEntryCount());
    }

    private static SuffixTableSharedData createSuffixTableSharedData() {
        int arbitraryPrefixValue = 1111;
        int arbitraryTzIdSetBank = 9;
        return new SuffixTableSharedData(arbitraryPrefixValue, arbitraryTzIdSetBank);
    }

    /** Creates a BlockInfo for a written block. */
    private static BlockInfo createBlockInfo(BlockWriter.ReadBack readBack) {
        int arbitraryBlockId = 2222;
        long arbitraryByteOffset = 12345L;
        return new BlockInfo(
                arbitraryBlockId, readBack.getType(), arbitraryByteOffset,
                readBack.getBlockData().getSize(), readBack.getExtraBytes());
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
