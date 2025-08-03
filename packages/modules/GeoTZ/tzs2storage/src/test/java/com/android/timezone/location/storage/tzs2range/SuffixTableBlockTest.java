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

import static com.android.storage.testing.MoreAsserts.assertThrows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;

import com.android.storage.block.write.BlockWriter;
import com.android.timezone.location.storage.tzs2range.read.SuffixTableBlock;
import com.android.timezone.location.storage.tzs2range.write.SuffixTableWriter;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** Tests for {@link SuffixTableWriter} and {@link SuffixTableBlock}. */
public class SuffixTableBlockTest {

    @Test
    public void writer_createEmptyBlockWriter() throws Exception {
        BlockWriter blockWriter = SuffixTableWriter.createEmptyBlockWriter();
        BlockWriter.ReadBack readBack = blockWriter.close();
        assertEquals(TzS2RangeFileFormat.BLOCK_TYPE_SUFFIX_TABLE, readBack.getType());
        assertArrayEquals(new byte[0], readBack.getExtraBytes());
        assertEquals(0, readBack.getBlockData().getSize());
    }

    @Test
    public void writer_createPopulatedBlockWriter_noEntriesThrows() throws Exception {
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();
        assertEquals(13, fileFormat.getPrefixBitCount());

        int tablePrefixValue = 0b0010011_00110100;
        int tzIdSetBankId = 5;
        SuffixTableSharedData suffixTableSharedData =
                new SuffixTableSharedData(tablePrefixValue, tzIdSetBankId);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);
        assertThrows(IllegalStateException.class, suffixTableWriter::close);
    }

    @Test
    public void writer_createPopulatedBlockWriter_addRange() throws Exception {
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();
        assertEquals(13, fileFormat.getPrefixBitCount());
        assertEquals(14, fileFormat.getSuffixBitCount());

        int tablePrefixValue = 0b0010011_00110100;
        int maxSuffixValue = 0b00111111_11111111;
        int tzIdSetBankId = 5;
        SuffixTableSharedData suffixTableSharedData =
                new SuffixTableSharedData(tablePrefixValue, tzIdSetBankId);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);

        long invalidStartCellId = fileFormat.createCellId(tablePrefixValue - 1, 0);
        long validStartCellId = fileFormat.createCellId(tablePrefixValue, 0);
        long invalidEndCellId = fileFormat.createCellId(tablePrefixValue + 1, maxSuffixValue);
        long validEndCellId = fileFormat.createCellId(tablePrefixValue, maxSuffixValue);
        {
            SuffixTableRange badStartCellId =
                    new SuffixTableRange(invalidStartCellId, validEndCellId, 1);
            assertThrows(IllegalArgumentException.class,
                    () -> suffixTableWriter.addRange(badStartCellId));
        }
        {
            SuffixTableRange badEndCellId =
                    new SuffixTableRange(validStartCellId, invalidEndCellId, 1);
            assertThrows(IllegalArgumentException.class,
                    () -> suffixTableWriter.addRange(badEndCellId));
        }
    }

    @Test
    public void writer_createPopulatedBlockWriter_rejectOverlappingRanges() throws Exception {
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();
        assertEquals(13, fileFormat.getPrefixBitCount());
        assertEquals(14, fileFormat.getSuffixBitCount());

        int tablePrefixValue = 0b0010011_00110100;
        int maxSuffixValue = 0b00111111_11111111;
        int tzIdSetBankId = 5;
        SuffixTableSharedData suffixTableSharedData =
                new SuffixTableSharedData(tablePrefixValue, tzIdSetBankId);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);
        SuffixTableRange suffixTableRange1 = new SuffixTableRange(
                fileFormat.createCellId(tablePrefixValue, 1000),
                fileFormat.createCellId(tablePrefixValue, 1001),
                1);
        suffixTableWriter.addRange(suffixTableRange1);

        // It's fine to add a range that starts adjacent to the last one.
        SuffixTableRange suffixTableRange2 = new SuffixTableRange(
                fileFormat.createCellId(tablePrefixValue, 1001),
                fileFormat.createCellId(tablePrefixValue, 1002),
                2);
        suffixTableWriter.addRange(suffixTableRange2);

        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange2));

        // Try similar checks at the top end of the table.
        SuffixTableRange suffixTableRange3 = new SuffixTableRange(
                fileFormat.createCellId(tablePrefixValue, maxSuffixValue - 1),
                fileFormat.createCellId(tablePrefixValue, maxSuffixValue),
                3);
        suffixTableWriter.addRange(suffixTableRange3);

        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange1));
        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange2));
        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange3));

        // Now "complete" the table: there can be no entry after this one.
        SuffixTableRange suffixTableRange4 = new SuffixTableRange(
                fileFormat.createCellId(tablePrefixValue, maxSuffixValue),
                fileFormat.createCellId(tablePrefixValue + 1, 0),
                4);
        suffixTableWriter.addRange(suffixTableRange4);

        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange4));

        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange1));
        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange2));
        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange3));
        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange4));
    }

    @Test
    public void suffixTableBlock_empty() {
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();
        assertEquals(13, fileFormat.getPrefixBitCount());
        int tablePrefix = 0b10011_00110100;

        SuffixTableBlock suffixTableBlock = SuffixTableBlock.createEmpty(fileFormat, tablePrefix);
        assertEquals(tablePrefix, suffixTableBlock.getPrefix());
        assertNull(suffixTableBlock.findEntryByCellId(fileFormat.createCellId(tablePrefix, 1)));
        assertEquals(0, suffixTableBlock.getEntryCount());
        assertThrows(IndexOutOfBoundsException.class,
                () -> suffixTableBlock.getEntryByIndex(0));
        assertThrows(IndexOutOfBoundsException.class,
                () -> suffixTableBlock.getEntryByIndex(1));
    }

    @Test
    public void suffixTableBlock_populated_findEntryByCellId() throws Exception {
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();
        assertEquals(13, fileFormat.getPrefixBitCount());
        assertEquals(14, fileFormat.getSuffixBitCount());

        int tablePrefix = 0b10011_00110100;
        int maxSuffix = 0b111111_11111111;
        int tzIdSetBankId = 5;
        SuffixTableSharedData suffixTableSharedData =
                new SuffixTableSharedData(tablePrefix, tzIdSetBankId);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);

        long entry1StartCellId = fileFormat.createCellId(tablePrefix, 1000);
        long entry1EndCellId = fileFormat.createCellId(tablePrefix, 2000);
        SuffixTableRange entry1 = new SuffixTableRange(entry1StartCellId, entry1EndCellId, 2);
        suffixTableWriter.addRange(entry1);

        long entry2StartCellId = fileFormat.createCellId(tablePrefix, 2000);
        long entry2EndCellId = fileFormat.createCellId(tablePrefix, 3000);
        SuffixTableRange entry2 = new SuffixTableRange(entry2StartCellId, entry2EndCellId, 3);
        suffixTableWriter.addRange(entry2);

        // There is a deliberate gap here between entry2 and entry3.
        long entry3StartCellId = fileFormat.createCellId(tablePrefix, 4000);
        long entry3EndCellId = fileFormat.createCellId(tablePrefix, 5000);
        SuffixTableRange entry3 = new SuffixTableRange(entry3StartCellId, entry3EndCellId, 4);
        suffixTableWriter.addRange(entry3);

        long entry4StartCellId = fileFormat.createCellId(tablePrefix, maxSuffix - 999);
        long entry4EndCellId = fileFormat.createCellId(tablePrefix + 1, 0);
        SuffixTableRange entry4 = new SuffixTableRange(entry4StartCellId, entry4EndCellId, 5);
        suffixTableWriter.addRange(entry4);

        BlockWriter.ReadBack blockReadback = suffixTableWriter.close();
        SuffixTableBlock suffixTableBlock =
                SuffixTableBlock.createPopulated(fileFormat, blockReadback.getBlockData());
        assertEquals(tablePrefix, suffixTableBlock.getPrefix());

        assertNull(findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 999));
        assertEquals(entry1, findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 1000));
        assertEquals(entry1, findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 1001));
        assertEquals(entry1, findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 1999));
        assertEquals(entry2, findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 2000));
        assertEquals(entry2, findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 2001));
        assertEquals(entry2, findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 2999));
        assertNull(findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 3000));
        assertNull(findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 3999));
        assertEquals(entry3, findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 4000));
        assertEquals(entry3, findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, 4999));
        assertNull(findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, maxSuffix - 1000));
        assertEquals(
                entry4,
                findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, maxSuffix - 999));
        assertEquals(
                entry4,
                findEntryByCellId(fileFormat, suffixTableBlock, tablePrefix, maxSuffix));

        assertEquals(4, suffixTableBlock.getEntryCount());
        assertThrows(IndexOutOfBoundsException.class,
                () -> suffixTableBlock.getEntryByIndex(-1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> suffixTableBlock.getEntryByIndex(4));

        assertEquals(entry1, suffixTableBlock.getEntryByIndex(0).getSuffixTableRange());
        assertEquals(entry2, suffixTableBlock.getEntryByIndex(1).getSuffixTableRange());
        assertEquals(entry3, suffixTableBlock.getEntryByIndex(2).getSuffixTableRange());
        assertEquals(entry4, suffixTableBlock.getEntryByIndex(3).getSuffixTableRange());
    }

    @Test
    public void suffixTableBlock_populated_findEntryByCellId_cellIdOutOfRange() throws Exception {
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();

        int tablePrefix = 0b10011_00110100;
        assertEquals(13, fileFormat.getPrefixBitCount());

        int tzIdSetBankId = 5;
        assertTrue(tzIdSetBankId <= fileFormat.getMaxPrefixValue());

        SuffixTableSharedData suffixTableSharedData =
                new SuffixTableSharedData(tablePrefix, tzIdSetBankId);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);
        long entry1StartCellId = fileFormat.createCellId(tablePrefix, 1000);
        long entry1EndCellId = fileFormat.createCellId(tablePrefix, 2000);
        SuffixTableRange entry1 = new SuffixTableRange(entry1StartCellId, entry1EndCellId, 2);
        suffixTableWriter.addRange(entry1);
        BlockWriter.ReadBack blockReadback = suffixTableWriter.close();

        SuffixTableBlock suffixTableBlock =
                SuffixTableBlock.createPopulated(fileFormat, blockReadback.getBlockData());

        assertThrows(IllegalArgumentException.class, () -> suffixTableBlock.findEntryByCellId(
                        fileFormat.createCellId(tablePrefix - 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> suffixTableBlock.findEntryByCellId(
                        fileFormat.createCellId(tablePrefix + 1, 0)));
    }

    @Test
    public void suffixTableBlock_visit() throws Exception {
        TzS2RangeFileFormat fileFormat = createS2RangeFileFormat();

        int tablePrefix = 0b10011_00110100;
        assertEquals(13, fileFormat.getPrefixBitCount());

        int tzIdSetBankId = 5;
        assertTrue(tzIdSetBankId <= fileFormat.getMaxPrefixValue());

        SuffixTableSharedData sharedData = new SuffixTableSharedData(tablePrefix, tzIdSetBankId);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, sharedData);
        int tzIdSetId = 2;

        SuffixTableRange entry1 = new SuffixTableRange(
                fileFormat.createCellId(tablePrefix, 1001),
                fileFormat.createCellId(tablePrefix, 1101),
                tzIdSetId);
        suffixTableWriter.addRange(entry1);

        SuffixTableRange entry2 = new SuffixTableRange(
                fileFormat.createCellId(tablePrefix, 2001),
                fileFormat.createCellId(tablePrefix, 2101),
                tzIdSetId);
        suffixTableWriter.addRange(entry2);

        BlockWriter.ReadBack readBack = suffixTableWriter.close();

        // Read the data back and confirm it matches what we expected.
        SuffixTableBlock suffixTableBlock =
                SuffixTableBlock.createPopulated(fileFormat, readBack.getBlockData());
        SuffixTableBlock.SuffixTableBlockVisitor mockVisitor =
                Mockito.mock(SuffixTableBlock.SuffixTableBlockVisitor.class);

        suffixTableBlock.visit(mockVisitor);

        InOrder inOrder = Mockito.inOrder(mockVisitor);
        inOrder.verify(mockVisitor).begin();
        inOrder.verify(mockVisitor).visit(argThat(new SuffixTableBlockMatcher(suffixTableBlock)));
        inOrder.verify(mockVisitor).end();
    }

    private SuffixTableRange findEntryByCellId(TzS2RangeFileFormat fileFormat,
            SuffixTableBlock suffixTableBlock, int prefix, int suffix) {
        long cellId = fileFormat.createCellId(prefix, suffix);
        SuffixTableBlock.Entry entry = suffixTableBlock.findEntryByCellId(cellId);
        return entry == null ? null : entry.getSuffixTableRange();
    }

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
        return new TzS2RangeFileFormat(
                dataS2Level, prefixBitCount, suffixBitCount, suffixTableBlockIdOffset,
                suffixTableEntryBitCount, endCellIdOffsetBitCount);
    }
}
