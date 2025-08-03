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

import static org.junit.Assert.assertEquals;

import com.android.storage.block.read.BlockInfo;
import com.android.storage.block.write.BlockWriter;
import com.android.storage.s2.S2LevelRange;
import com.android.telephony.sats2range.read.SatS2RangeFileFormat;
import com.android.telephony.sats2range.read.SuffixTableExtraInfo;
import com.android.telephony.sats2range.read.SuffixTableSharedData;
import com.android.telephony.sats2range.utils.TestUtils;
import com.android.telephony.sats2range.write.SuffixTableWriter;

import org.junit.Test;
public class SuffixTableExtraInfoTest {

    @Test
    public void create_emptyBlock() throws Exception {
        // Generate a real suffix table block info and an empty block.
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);
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
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);
        SuffixTableSharedData suffixTableSharedData = createSuffixTableSharedData();
        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);

        int tablePrefix = suffixTableSharedData.getTablePrefix();
        S2LevelRange range1 = new S2LevelRange(
                fileFormat.createCellId(tablePrefix, 1000),
                fileFormat.createCellId(tablePrefix, 1001));
        S2LevelRange range2 = new S2LevelRange(
                fileFormat.createCellId(tablePrefix, 1002),
                fileFormat.createCellId(tablePrefix, 1003));
        S2LevelRange range3 = new S2LevelRange(
                fileFormat.createCellId(tablePrefix, 1004),
                fileFormat.createCellId(tablePrefix, 1005));

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
        return new SuffixTableSharedData(arbitraryPrefixValue);
    }

    /** Creates a BlockInfo for a written block. */
    private static BlockInfo createBlockInfo(BlockWriter.ReadBack readBack) {
        int arbitraryBlockId = 2222;
        long arbitraryByteOffset = 12345L;
        return new BlockInfo(
                arbitraryBlockId, readBack.getType(), arbitraryByteOffset,
                readBack.getBlockData().getSize(), readBack.getExtraBytes());
    }
}
