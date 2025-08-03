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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;

import com.android.storage.block.write.BlockWriter;
import com.android.storage.s2.S2LevelRange;
import com.android.telephony.sats2range.read.SatS2RangeFileFormat;
import com.android.telephony.sats2range.read.SuffixTableBlock;
import com.android.telephony.sats2range.read.SuffixTableSharedData;
import com.android.telephony.sats2range.utils.TestUtils;
import com.android.telephony.sats2range.write.SuffixTableWriter;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** Tests for {@link SuffixTableWriter} and {@link SuffixTableBlock}. */
public class SuffixTableBlockTest {
    @Test
    public void writer_createEmptyBlockWriter() throws Exception {
        BlockWriter blockWriter = SuffixTableWriter.createEmptyBlockWriter();
        BlockWriter.ReadBack readBack = blockWriter.close();
        assertEquals(SatS2RangeFileFormat.BLOCK_TYPE_SUFFIX_TABLE, readBack.getType());
        assertArrayEquals(new byte[0], readBack.getExtraBytes());
        assertEquals(0, readBack.getBlockData().getSize());
    }

    @Test
    public void writer_createPopulatedBlockWriter_noEntriesThrows() throws Exception {
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);
        assertEquals(13, fileFormat.getPrefixBitCount());

        int tablePrefixValue = 0b0010011_00110100;
        SuffixTableSharedData suffixTableSharedData = new SuffixTableSharedData(tablePrefixValue);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);
        // IllegalStateException is thrown because there is no entry in the block
        assertThrows(IllegalStateException.class, suffixTableWriter::close);
    }

    @Test
    public void writer_createPopulatedBlockWriter_addRange() throws Exception {
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);
        assertEquals(13, fileFormat.getPrefixBitCount());
        assertEquals(14, fileFormat.getSuffixBitCount());

        int tablePrefixValue = 0b0010011_00110100;
        int maxSuffixValue = 0b00111111_11111111;
        SuffixTableSharedData suffixTableSharedData = new SuffixTableSharedData(tablePrefixValue);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);

        long invalidStartCellId = fileFormat.createCellId(tablePrefixValue - 1, 0);
        long validStartCellId = fileFormat.createCellId(tablePrefixValue, 0);
        long invalidEndCellId = fileFormat.createCellId(tablePrefixValue + 1, maxSuffixValue);
        long validEndCellId = fileFormat.createCellId(tablePrefixValue, maxSuffixValue);
        {
            S2LevelRange badStartCellId = new S2LevelRange(invalidStartCellId, validEndCellId);
            assertThrows(IllegalArgumentException.class,
                    () -> suffixTableWriter.addRange(badStartCellId));
        }
        {
            S2LevelRange badEndCellId = new S2LevelRange(validStartCellId, invalidEndCellId);
            assertThrows(IllegalArgumentException.class,
                    () -> suffixTableWriter.addRange(badEndCellId));
        }
    }

    @Test
    public void writer_createPopulatedBlockWriter_rejectOverlappingRanges() throws Exception {
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);
        assertEquals(13, fileFormat.getPrefixBitCount());
        assertEquals(14, fileFormat.getSuffixBitCount());

        int tablePrefixValue = 0b0010011_00110100;
        int maxSuffixValue = 0b00111111_11111111;
        SuffixTableSharedData suffixTableSharedData = new SuffixTableSharedData(tablePrefixValue);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);
        S2LevelRange suffixTableRange1 = new S2LevelRange(
                fileFormat.createCellId(tablePrefixValue, 1000),
                fileFormat.createCellId(tablePrefixValue, 1001));
        suffixTableWriter.addRange(suffixTableRange1);

        // It's fine to add a range that starts adjacent to the last one.
        S2LevelRange suffixTableRange2 = new S2LevelRange(
                fileFormat.createCellId(tablePrefixValue, 1001),
                fileFormat.createCellId(tablePrefixValue, 1002));
        suffixTableWriter.addRange(suffixTableRange2);

        // IllegalArgumentException is thrown because suffixTableRange2 already exists
        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange2));

        // Try similar checks at the top end of the table.
        S2LevelRange suffixTableRange3 = new S2LevelRange(
                fileFormat.createCellId(tablePrefixValue, maxSuffixValue - 1),
                fileFormat.createCellId(tablePrefixValue, maxSuffixValue));
        suffixTableWriter.addRange(suffixTableRange3);

        // IllegalArgumentException is thrown because ranges already exist
        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange1));
        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange2));
        assertThrows(IllegalArgumentException.class,
                () -> suffixTableWriter.addRange(suffixTableRange3));

        // Now "complete" the table: there can be no entry after this one.
        S2LevelRange suffixTableRange4 = new S2LevelRange(
                fileFormat.createCellId(tablePrefixValue, maxSuffixValue),
                fileFormat.createCellId(tablePrefixValue + 1, 0));
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
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);
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
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);
        assertEquals(13, fileFormat.getPrefixBitCount());
        assertEquals(14, fileFormat.getSuffixBitCount());

        int tablePrefix = 0b10011_00110100;
        int maxSuffix = 0b111111_11111111;
        SuffixTableSharedData suffixTableSharedData = new SuffixTableSharedData(tablePrefix);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);

        long entry1StartCellId = fileFormat.createCellId(tablePrefix, 1000);
        long entry1EndCellId = fileFormat.createCellId(tablePrefix, 2000);
        S2LevelRange entry1 = new S2LevelRange(entry1StartCellId, entry1EndCellId);
        suffixTableWriter.addRange(entry1);

        long entry2StartCellId = fileFormat.createCellId(tablePrefix, 2000);
        long entry2EndCellId = fileFormat.createCellId(tablePrefix, 3000);
        S2LevelRange entry2 = new S2LevelRange(entry2StartCellId, entry2EndCellId);
        suffixTableWriter.addRange(entry2);

        // There is a deliberate gap here between entry2 and entry3.
        long entry3StartCellId = fileFormat.createCellId(tablePrefix, 4000);
        long entry3EndCellId = fileFormat.createCellId(tablePrefix, 5000);
        S2LevelRange entry3 = new S2LevelRange(entry3StartCellId, entry3EndCellId);
        suffixTableWriter.addRange(entry3);

        long entry4StartCellId = fileFormat.createCellId(tablePrefix, maxSuffix - 999);
        long entry4EndCellId = fileFormat.createCellId(tablePrefix + 1, 0);
        S2LevelRange entry4 = new S2LevelRange(entry4StartCellId, entry4EndCellId);
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
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);

        int tablePrefix = 0b10011_00110100;
        assertEquals(13, fileFormat.getPrefixBitCount());

        int tzIdSetBankId = 5;
        assertTrue(tzIdSetBankId <= fileFormat.getMaxPrefixValue());

        SuffixTableSharedData suffixTableSharedData = new SuffixTableSharedData(tablePrefix);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, suffixTableSharedData);
        long entry1StartCellId = fileFormat.createCellId(tablePrefix, 1000);
        long entry1EndCellId = fileFormat.createCellId(tablePrefix, 2000);
        S2LevelRange entry1 = new S2LevelRange(entry1StartCellId, entry1EndCellId);
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
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);

        int tablePrefix = 0b10011_00110100;
        assertEquals(13, fileFormat.getPrefixBitCount());

        SuffixTableSharedData sharedData = new SuffixTableSharedData(tablePrefix);

        SuffixTableWriter suffixTableWriter =
                SuffixTableWriter.createPopulated(fileFormat, sharedData);

        S2LevelRange entry1 = new S2LevelRange(
                fileFormat.createCellId(tablePrefix, 1001),
                fileFormat.createCellId(tablePrefix, 1101));
        suffixTableWriter.addRange(entry1);

        S2LevelRange entry2 = new S2LevelRange(
                fileFormat.createCellId(tablePrefix, 2001),
                fileFormat.createCellId(tablePrefix, 2101));
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

    private S2LevelRange findEntryByCellId(SatS2RangeFileFormat fileFormat,
            SuffixTableBlock suffixTableBlock, int prefix, int suffix) {
        long cellId = fileFormat.createCellId(prefix, suffix);
        SuffixTableBlock.Entry entry = suffixTableBlock.findEntryByCellId(cellId);
        return entry == null ? null : entry.getSuffixTableRange();
    }
}
