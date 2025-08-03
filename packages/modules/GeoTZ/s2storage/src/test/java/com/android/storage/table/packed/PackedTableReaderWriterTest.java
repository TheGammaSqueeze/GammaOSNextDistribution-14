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

package com.android.storage.table.packed;

import static com.android.storage.testing.MoreAsserts.assertThrows;
import static com.android.storage.testing.TestSupport.listOf;
import static com.android.storage.testing.TestSupport.setOf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.storage.block.read.BlockData;
import com.android.storage.table.packed.read.PackedTableReader;
import com.android.storage.table.packed.write.PackedTableWriter;
import com.android.storage.table.reader.IntValueTable;
import com.android.storage.table.reader.LongValueTable;
import com.android.storage.util.BitwiseUtils;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link PackedTableReader} and {@link PackedTableWriter}
 */
public class PackedTableReaderWriterTest {

    private static class IntValueEntryKeyMatcher implements IntValueTable.IntValueEntryMatcher {

        final int mSearchKey;

        IntValueEntryKeyMatcher(int searchKey) {
            mSearchKey = searchKey;
        }

        @Override
        public int compare(int key, int value) {
            return Integer.compare(mSearchKey, key);
        }
    }

    private static class LongValueEntryKeyMatcher implements LongValueTable.LongValueEntryMatcher {

        final int mSearchKey;

        LongValueEntryKeyMatcher(int searchKey) {
            mSearchKey = searchKey;
        }

        @Override
        public int compare(int key, long value) {
            return Integer.compare(mSearchKey, key);
        }
    }

    @Test
    public void headerFields() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int entrySizeBytes = 8;
        int keyBits = 4;
        final boolean signedValue = true;
        byte[] sharedData = "Shared data".getBytes(StandardCharsets.UTF_8);
        PackedTableWriter tableWriter = PackedTableWriter.create(baos, entrySizeBytes, keyBits,
                signedValue, sharedData);
        tableWriter.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertEquals(entrySizeBytes, tableReader.getEntrySizeBytes());
        assertEquals(keyBits, tableReader.getKeySizeBits());
        assertEquals(signedValue, tableReader.isValueSigned());
        assertArrayEquals(sharedData, tableReader.getSharedData());
        assertEquals((entrySizeBytes * Byte.SIZE) - keyBits, tableReader.getValueSizeBits());
        assertEquals(0, tableReader.getEntryCount());
    }

    @Test
    public void entryGetValue_int() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        int entrySizeBytes = 4;
        int keyBits = 4;
        PackedTableWriter writer = PackedTableWriter.create(
                baos, entrySizeBytes, keyBits, signedValue, null);
        long maxRepresentableValue =
                BitwiseUtils.maxValue((entrySizeBytes * Byte.SIZE) - keyBits, signedValue);
        writer.addEntry(1, maxRepresentableValue);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertEquals((int) maxRepresentableValue, tableReader.getEntry(1).getIntValue());
        assertEquals(maxRepresentableValue, tableReader.getEntry(1).getLongValue());
    }

    @Test
    public void entryGetValue_long() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = true;
        int entrySizeBytes = 8;
        int keyBits = 4;
        PackedTableWriter writer = PackedTableWriter.create(
                baos, entrySizeBytes, keyBits, signedValue, null);
        int valueSizeBits = (entrySizeBytes * Byte.SIZE) - keyBits;
        long maxRepresentableValue = BitwiseUtils.maxValue(valueSizeBits, signedValue);
        int maxKey = 3;
        writer.addEntry(maxKey, maxRepresentableValue);

        long minRepresentableValue =
                BitwiseUtils.minValue(valueSizeBits, signedValue);
        int minKey = 6;
        writer.addEntry(minKey, minRepresentableValue);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertThrows(IllegalStateException.class, () -> tableReader.getEntry(maxKey).getIntValue());
        assertEquals(maxRepresentableValue, tableReader.getEntry(maxKey).getLongValue());

        assertThrows(IllegalStateException.class, () -> tableReader.getEntry(minKey).getIntValue());
        assertEquals(minRepresentableValue, tableReader.getEntry(minKey).getLongValue());
    }

    @Test
    public void valueSizeMax_unsigned() throws IOException {
        int maxUnsignedInt = (int) BitwiseUtils.maxUnsignedValue(31);
        long minUnsignedInt = 0;
        assertEquals(maxUnsignedInt, Integer.MAX_VALUE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        PackedTableWriter writer = PackedTableWriter.create(baos, 5, 9, signedValue, null);

        assertThrows(IllegalArgumentException.class, () -> writer.addEntry(1, maxUnsignedInt + 1));
        assertThrows(IllegalArgumentException.class, () -> writer.addEntry(1, minUnsignedInt - 1));

        writer.addEntry(1, maxUnsignedInt);
        writer.addEntry(2, minUnsignedInt);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertEquals(2, tableReader.getEntryCount());
        assertEquals(maxUnsignedInt, tableReader.getEntry(1).getIntValue());
        assertEquals(minUnsignedInt, tableReader.getEntry(2).getIntValue());
    }

    @Test
    public void valueSizeMax_signed() throws IOException {
        long minSignedInt = Integer.MIN_VALUE;
        long maxSignedInt = BitwiseUtils.maxSignedValue(32);
        assertEquals(maxSignedInt, Integer.MAX_VALUE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = true;
        PackedTableWriter writer = PackedTableWriter.create(baos, 5, 8, signedValue, null);

        assertThrows(IllegalArgumentException.class, () -> writer.addEntry(1, maxSignedInt + 1));
        assertThrows(IllegalArgumentException.class, () -> writer.addEntry(1, minSignedInt - 1));

        writer.addEntry(1, maxSignedInt);
        writer.addEntry(2, minSignedInt);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertEquals(2, tableReader.getEntryCount());
        assertEquals(maxSignedInt, tableReader.getEntry(1).getIntValue());
        assertEquals(minSignedInt, tableReader.getEntry(2).getIntValue());
    }

    @Test
    public void addEntry_outOfOrder() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = true;
        PackedTableWriter writer = PackedTableWriter.create(baos, 5, 8, signedValue, null);
        writer.addEntry(100, 1);

        assertThrows(IllegalArgumentException.class, () -> writer.addEntry(1, 2));
        assertThrows(IllegalArgumentException.class, () -> writer.addEntry(99, 2));

        writer.addEntry(100, 2);
        writer.addEntry(101, 3);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertEquals(3, tableReader.getEntryCount());
        assertTrue(setOf(1, 2).contains(tableReader.getEntry(100).getIntValue()));
        assertTrue(setOf(0, 1).contains(tableReader.getEntry(100).getIndex()));
        assertEquals(3, tableReader.getEntry(101).getIntValue());
    }

    @Test
    public void getSharedData() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] sharedData = "Shared data".getBytes(StandardCharsets.UTF_8);
        boolean signedValue = false;
        PackedTableWriter writer = PackedTableWriter.create(baos, 2, 4, signedValue, sharedData);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertArrayEquals(sharedData, tableReader.getSharedData());
    }

    @Test
    public void getEntry_unsignedValue() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        PackedTableWriter writer = PackedTableWriter.create(baos, 2, 4, signedValue, null);
        writer.addEntry(1, 1234);
        writer.addEntry(2, 2345);
        writer.addEntry(3, 3456);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertEquals(3456, tableReader.getEntry(3).getIntValue());
        assertEquals(1234, tableReader.getEntry(1).getIntValue());
        assertEquals(2345, tableReader.getEntry(2).getIntValue());
        assertNull(tableReader.getEntry(0));
        assertNull(tableReader.getEntry(4));
    }

    @Test
    public void getEntry_signedValue() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = true;
        PackedTableWriter writer = PackedTableWriter.create(baos, 3, 4, signedValue, null);
        writer.addEntry(1, -1234);
        writer.addEntry(2, 2345);
        writer.addEntry(3, -3456);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertEquals(-3456, tableReader.getEntry(3).getIntValue());
        assertEquals(-1234, tableReader.getEntry(1).getIntValue());
        assertEquals(2345, tableReader.getEntry(2).getIntValue());
        assertNull(tableReader.getEntry(0));
        assertNull(tableReader.getEntry(4));
    }

    @Test
    public void getEntry_emptyTable() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        PackedTableWriter writer = PackedTableWriter.create(baos, 2, 4, signedValue, null);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertArrayEquals(new byte[0], tableReader.getSharedData());

        assertNull(tableReader.getEntry(12));
    }

    @Test
    public void getEntry_badKey() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        int keyBits = 4;
        PackedTableWriter writer = PackedTableWriter.create(baos, 2, keyBits, signedValue, null);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        assertArrayEquals(new byte[0], tableReader.getSharedData());

        int negativeKey = -1;
        assertThrows(IllegalArgumentException.class, () -> tableReader.getEntry(negativeKey));

        int keyTooBig = (int) BitwiseUtils.maxUnsignedValue(keyBits) + 1;
        assertThrows(IllegalArgumentException.class, () -> tableReader.getEntry(keyTooBig));
    }

    @Test
    public void findIntValueEntry() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        int entrySizeBytes = 2;
        int keyBits = 4;
        PackedTableWriter writer =
                PackedTableWriter.create(baos, entrySizeBytes, keyBits, signedValue, null);
        writer.addEntry(3, 1234);
        writer.addEntry(5, 2345);
        writer.addEntry(7, 3456);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);

        assertNull(tableReader.findIntValueEntry(new IntValueEntryKeyMatcher(entrySizeBytes)));
        assertEquals(1234,
                tableReader.findIntValueEntry(new IntValueEntryKeyMatcher(3)).getIntValue());
        assertNull(tableReader.findIntValueEntry(new IntValueEntryKeyMatcher(4)));
        assertEquals(2345,
                tableReader.findIntValueEntry(new IntValueEntryKeyMatcher(5)).getIntValue());
    }

    @Test
    public void findIntValueEntryDuplicateKeys() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        int entrySizeBytes = 1;
        int keyBits = 3;
        PackedTableWriter writer =
                PackedTableWriter.create(baos, entrySizeBytes, keyBits, signedValue, null);
        // Storing more than 2^3 deliberately to demonstrate tables are not limited to 2^{key bits}
        // entries as duplicates are allowed.
        writer.addEntry(3, 1);
        writer.addEntry(5, 2);
        writer.addEntry(5, 3);
        writer.addEntry(5, 4);
        writer.addEntry(5, 5);
        writer.addEntry(5, 6);
        writer.addEntry(5, 7);
        writer.addEntry(5, 8);
        writer.addEntry(7, 9);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);

        // findIntValueEntry
        PackedTableReader.Entry oneOfMany =
                tableReader.findIntValueEntry(new IntValueEntryKeyMatcher(5));
        List<Integer> expectedValuesForKey = listOf(2, 3, 4, 5, 6, 7, 8);
        assertTrue(oneOfMany.getIntValue() + " not expected",
                expectedValuesForKey.contains(oneOfMany.getIntValue()));
        PackedTableReader.Entry currentEntry = oneOfMany;
        while (currentEntry.getIntValue() != expectedValuesForKey.get(0)) {
            currentEntry = currentEntry.getPrevious();
        }
        assertEquals(3, currentEntry.getPrevious().getKey());
        for (int expectedValue : expectedValuesForKey) {
            assertEquals(expectedValue, currentEntry.getIntValue());
            currentEntry = currentEntry.getNext();
        }
        assertEquals(7, currentEntry.getKey());
    }

    @Test
    public void findLongValueEntry() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = true;
        int entrySizeBytes = 8;
        int keyBits = 4;
        PackedTableWriter writer =
                PackedTableWriter.create(baos, entrySizeBytes, keyBits, signedValue, null);
        writer.addEntry(3, 1234L);
        writer.addEntry(5, -2345L);
        writer.addEntry(7, 3456L);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);

        LongValueEntryKeyMatcher entryMatcher2 = new LongValueEntryKeyMatcher(2);
        assertNull(tableReader.findLongValueEntry(entryMatcher2));

        LongValueEntryKeyMatcher entryMatcher3 = new LongValueEntryKeyMatcher(3);
        assertEquals(1234L, tableReader.findLongValueEntry(entryMatcher3).getLongValue());

        LongValueEntryKeyMatcher entryMatcher4 = new LongValueEntryKeyMatcher(4);
        assertNull(tableReader.findLongValueEntry(entryMatcher4));

        LongValueEntryKeyMatcher entryMatcher5 = new LongValueEntryKeyMatcher(5);
        assertEquals(-2345L, tableReader.findLongValueEntry(entryMatcher5).getLongValue());
    }

    @Test
    public void entryBasicInfo_int() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        int keyBits = 4;
        int entrySizeBytes = 2;
        PackedTableWriter writer =
                PackedTableWriter.create(baos, entrySizeBytes, keyBits, signedValue, null);
        writer.addEntry(1, 123);
        writer.addEntry(3, 456);
        writer.addEntry(5, 789);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        PackedTableReader.Entry entry = tableReader.getEntry(3);
        assertEquals(3, entry.getKey());
        assertEquals(1, entry.getIndex());
        assertEquals(456, entry.getIntValue());
        assertEquals(456L, entry.getLongValue());
    }

    @Test
    public void entryBasicInfo_long() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        int keyBits = 4;
        int entrySizeBytes = 8;
        PackedTableWriter writer =
                PackedTableWriter.create(baos, entrySizeBytes, keyBits, signedValue, null);
        writer.addEntry(1, 1234567);
        writer.addEntry(3, 4567890);
        writer.addEntry(5, 7890123);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        PackedTableReader.Entry entry = tableReader.getEntry(3);
        assertEquals(3, entry.getKey());
        assertEquals(1, entry.getIndex());
        assertThrows(IllegalStateException.class, entry::getIntValue);
        assertEquals(4567890, entry.getLongValue());
    }

    @Test
    public void entryNextAndPrevious() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        int keyBits = 4;
        PackedTableWriter writer = PackedTableWriter.create(baos, 2, keyBits, signedValue, null);
        writer.addEntry(1, 123);
        writer.addEntry(2, 234);
        writer.addEntry(4, 456);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        PackedTableReader.Entry one = tableReader.getEntry(1);
        PackedTableReader.Entry two = tableReader.getEntry(2);
        PackedTableReader.Entry four = tableReader.getEntry(4);
        assertEquals(two, one.getNext());
        assertEquals(four, two.getNext());
        assertNull(one.getPrevious());
        assertEquals(one, two.getPrevious());
        assertNull(four.getNext());
    }

    @Test
    public void entryFindNearbyEntry_intMatcher() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        int keyBits = 4;
        int entrySizeBytes = 4;
        PackedTableWriter writer =
                PackedTableWriter.create(baos, entrySizeBytes, keyBits, signedValue, null);
        writer.addEntry(3, 123);
        writer.addEntry(5, 234);
        writer.addEntry(7, 456);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        PackedTableReader.Entry entry3 = tableReader.getEntry(3);
        PackedTableReader.Entry entry5 = tableReader.getEntry(5);
        PackedTableReader.Entry entry7 = tableReader.getEntry(7);

        IntValueEntryKeyMatcher entryMatcherMin = new IntValueEntryKeyMatcher(Integer.MIN_VALUE);
        IntValueEntryKeyMatcher entryMatcher2 = new IntValueEntryKeyMatcher(2);
        IntValueEntryKeyMatcher entryMatcher3 = new IntValueEntryKeyMatcher(3);
        IntValueEntryKeyMatcher entryMatcher4 = new IntValueEntryKeyMatcher(4);
        IntValueEntryKeyMatcher entryMatcher5 = new IntValueEntryKeyMatcher(5);
        IntValueEntryKeyMatcher entryMatcher7 = new IntValueEntryKeyMatcher(7);
        IntValueEntryKeyMatcher entryMatcher8 = new IntValueEntryKeyMatcher(8);
        IntValueEntryKeyMatcher entryMatcherMax = new IntValueEntryKeyMatcher(Integer.MAX_VALUE);

        for (PackedTableReader.Entry baseEntry : Arrays.asList(entry3, entry5, entry7)) {
            assertNull(baseEntry.findNearbyEntry(entryMatcherMin));
            assertNull(baseEntry.findNearbyEntry(entryMatcher2));
            assertEquals(entry3, baseEntry.findNearbyEntry(entryMatcher3));
            assertNull(baseEntry.findNearbyEntry(entryMatcher4));
            assertEquals(entry5, baseEntry.findNearbyEntry(entryMatcher5));
            assertEquals(entry7, baseEntry.findNearbyEntry(entryMatcher7));
            assertNull(baseEntry.findNearbyEntry(entryMatcher8));
            assertNull(baseEntry.findNearbyEntry(entryMatcherMax));
        }
    }

    @Test
    public void entryFindNearbyEntry_longMatcher() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = true;
        int keyBits = 4;
        int entrySizeBytes = 8;
        PackedTableWriter writer =
                PackedTableWriter.create(baos, entrySizeBytes, keyBits, signedValue, null);
        writer.addEntry(3, 1234);
        writer.addEntry(5, -2345);
        writer.addEntry(7, 4567);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        PackedTableReader tableReader = new PackedTableReader(blockData);
        PackedTableReader.Entry entry3 = tableReader.getEntry(3);
        PackedTableReader.Entry entry5 = tableReader.getEntry(5);
        PackedTableReader.Entry entry7 = tableReader.getEntry(7);

        LongValueEntryKeyMatcher entryMatcherMin = new LongValueEntryKeyMatcher(Integer.MIN_VALUE);
        LongValueEntryKeyMatcher entryMatcher2 = new LongValueEntryKeyMatcher(2);
        LongValueEntryKeyMatcher entryMatcher3 = new LongValueEntryKeyMatcher(3);
        LongValueEntryKeyMatcher entryMatcher4 = new LongValueEntryKeyMatcher(4);
        LongValueEntryKeyMatcher entryMatcher5 = new LongValueEntryKeyMatcher(5);
        LongValueEntryKeyMatcher entryMatcher7 = new LongValueEntryKeyMatcher(7);
        LongValueEntryKeyMatcher entryMatcher8 = new LongValueEntryKeyMatcher(8);
        LongValueEntryKeyMatcher entryMatcherMax = new LongValueEntryKeyMatcher(Integer.MAX_VALUE);

        for (PackedTableReader.Entry baseEntry : Arrays.asList(entry3, entry5, entry7)) {
            assertNull(baseEntry.findNearbyEntry(entryMatcherMin));
            assertNull(baseEntry.findNearbyEntry(entryMatcher2));
            assertEquals(entry3, baseEntry.findNearbyEntry(entryMatcher3));
            assertNull(baseEntry.findNearbyEntry(entryMatcher4));
            assertEquals(entry5, baseEntry.findNearbyEntry(entryMatcher5));
            assertEquals(entry7, baseEntry.findNearbyEntry(entryMatcher7));
            assertNull(baseEntry.findNearbyEntry(entryMatcher8));
            assertNull(baseEntry.findNearbyEntry(entryMatcherMax));
        }
    }

    private static ByteBuffer createByteBuffer(byte[] bytes) {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }
}
