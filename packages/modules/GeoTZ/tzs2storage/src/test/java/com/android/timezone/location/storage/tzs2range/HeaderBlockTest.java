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

import static com.android.storage.testing.TestSupport.listOf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.storage.block.write.BlockWriter;
import com.android.timezone.location.storage.tzs2range.read.HeaderBlock;
import com.android.timezone.location.storage.tzs2range.write.BankedTzIdSetsPacker;
import com.android.timezone.location.storage.tzs2range.write.HeaderBlockWriter;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;

/** Tests for {@link HeaderBlockWriter} and {@link HeaderBlock}. */
public class HeaderBlockTest {

    @Test
    public void readWrite() throws IOException {
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();

        // Create header data using HeaderBlockWriter.
        HeaderBlockWriter headerBlockWriter = HeaderBlockWriter.create(fileFormat);
        BankedTzIdSetsPacker tzIdSetPacker = headerBlockWriter.getBankedTzIdSetsPacker();
        tzIdSetPacker.addTzIdSets(listOf(
                listOf("One"),
                listOf("One", "Two"),
                listOf("Two", "Three")));
        BlockWriter.ReadBack readBack = headerBlockWriter.close();
        assertEquals(TzS2RangeFileFormat.BLOCK_TYPE_HEADER, readBack.getType());
        assertArrayEquals(new byte[0], readBack.getExtraBytes());

        // Read the data back and confirm it matches what we expected.
        HeaderBlock headerBlock = HeaderBlock.wrap(readBack.getBlockData());
        assertEquals(fileFormat, headerBlock.getFileFormat());
        assertEquals(tzIdSetPacker.create(), headerBlock.getBankedTzIdSets());
    }

    @Test
    public void visit() throws Exception {
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();

        // Create header data using HeaderBlockWriter.
        HeaderBlockWriter headerBlockWriter = HeaderBlockWriter.create(fileFormat);
        BankedTzIdSetsPacker tzIdSetPacker = headerBlockWriter.getBankedTzIdSetsPacker();
        tzIdSetPacker.addTzIdSets(listOf(
                listOf("One"),
                listOf("One", "Two"),
                listOf("Two", "Three")));
        BlockWriter.ReadBack readBack = headerBlockWriter.close();

        // Read the data back and confirm it matches what we expected.
        HeaderBlock headerBlock = HeaderBlock.wrap(readBack.getBlockData());

        HeaderBlock.HeaderBlockVisitor mockVisitor =
                Mockito.mock(HeaderBlock.HeaderBlockVisitor.class);

        headerBlock.visit(mockVisitor);

        InOrder inOrder = Mockito.inOrder(mockVisitor);
        inOrder.verify(mockVisitor).begin();
        inOrder.verify(mockVisitor).visitFileFormat(fileFormat);
        inOrder.verify(mockVisitor).visitBankedTzIdSets(tzIdSetPacker.create());
        inOrder.verify(mockVisitor).end();
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
