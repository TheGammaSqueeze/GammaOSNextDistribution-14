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
import static com.android.storage.testing.TestSupport.setOf;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.storage.block.read.BlockData;
import com.android.storage.table.packed.read.IntValueTypedPackedTable;
import com.android.storage.table.packed.write.PackedTableWriter;
import com.android.storage.table.reader.IntValueTable;
import com.android.storage.util.BitwiseUtils;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Tests for {@link IntValueTypedPackedTable}. */
public class IntValuePackedTableTest {

    @Test(expected = IllegalArgumentException.class)
    public void valueSizeTooBig() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        PackedTableWriter writer = PackedTableWriter.create(baos, 5, 4, signedValue, null);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        // This should fail because 5 bytes (minus 4 bits for the key) exceeds the 4 bytes allowed
        // for the int value.
        new IntValueTypedPackedTable(blockData);
    }

    @Test
    public void valueSizeMax_unsigned() throws IOException {
        long minUnsignedInt = 0;
        long maxUnsignedInt = BitwiseUtils.maxUnsignedValue(31);
        assertEquals(maxUnsignedInt, Integer.MAX_VALUE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        try (PackedTableWriter writer = PackedTableWriter.create(baos, 5, 9, signedValue, null)) {
            assertThrows(IllegalArgumentException.class,
                    () -> writer.addEntry(1, maxUnsignedInt + 1));
            assertThrows(IllegalArgumentException.class,
                    () -> writer.addEntry(1, minUnsignedInt - 1));
            writer.addEntry(1, maxUnsignedInt);
            writer.addEntry(2, minUnsignedInt);
        }

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable table = new IntValueTypedPackedTable(blockData);
        assertEquals(maxUnsignedInt, table.getEntry(1).getValue());
        assertEquals(minUnsignedInt, table.getEntry(2).getValue());
    }

    @Test
    public void valueSizeMax_signed() throws IOException {
        long minSignedInt = Integer.MIN_VALUE;
        long maxSignedInt = BitwiseUtils.maxSignedValue(32);
        assertEquals(maxSignedInt, Integer.MAX_VALUE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = true;
        try (PackedTableWriter writer = PackedTableWriter.create(baos, 5, 8, signedValue, null)) {
            assertThrows(IllegalArgumentException.class,
                    () -> writer.addEntry(1, maxSignedInt + 1));
            assertThrows(IllegalArgumentException.class,
                    () -> writer.addEntry(1, minSignedInt - 1));
            writer.addEntry(1, maxSignedInt);
            writer.addEntry(2, minSignedInt);
        }

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable table = new IntValueTypedPackedTable(blockData);
        assertEquals(maxSignedInt, table.getEntry(1).getValue());
        assertEquals(minSignedInt, table.getEntry(2).getValue());
    }

    @Test
    public void addEntry_outOfOrder() throws IOException {
        long maxSignedInt = BitwiseUtils.maxSignedValue(32);
        assertEquals(maxSignedInt, Integer.MAX_VALUE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = true;
        try (PackedTableWriter writer = PackedTableWriter.create(baos, 5, 8, signedValue, null)) {
            writer.addEntry(100, 1);
            assertThrows(IllegalArgumentException.class, () -> writer.addEntry(1, 2));
            assertThrows(IllegalArgumentException.class, () -> writer.addEntry(99, 2));
            writer.addEntry(100, 2);
            writer.addEntry(101, 3);
        }

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable table = new IntValueTypedPackedTable(blockData);
        assertEquals(3, table.getEntryCount());
        IntValueTable.TableEntry entry = table.getEntry(100);
        assertTrue(setOf(1, 2).contains(entry.getValue()));
        assertTrue(setOf(0, 1).contains(entry.getIndex()));
        assertEquals(3, table.getEntry(101).getValue());
    }

    @Test
    public void getSharedData() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] sharedData = "Shared data".getBytes(StandardCharsets.UTF_8);
        boolean signedValue = false;
        PackedTableWriter writer = PackedTableWriter.create(baos, 2, 4, signedValue, sharedData);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable intValuePackedTable = new IntValueTypedPackedTable(blockData);
        assertArrayEquals(sharedData, intValuePackedTable.getSharedData());
    }

    @Test
    public void getEntry_unsignedValue() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        try (PackedTableWriter writer = PackedTableWriter.create(baos, 2, 4, signedValue, null)) {
            writer.addEntry(1, 1234);
            writer.addEntry(2, 2345);
            writer.addEntry(3, 3456);
        }

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable intValuePackedTable = new IntValueTypedPackedTable(blockData);
        assertEquals(3456, intValuePackedTable.getEntry(3).getValue());
        assertEquals(1234, intValuePackedTable.getEntry(1).getValue());
        assertEquals(2345, intValuePackedTable.getEntry(2).getValue());
        assertNull(intValuePackedTable.getEntry(0));
        assertNull(intValuePackedTable.getEntry(4));
    }

    @Test
    public void getEntryByIndex() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        try (PackedTableWriter writer = PackedTableWriter.create(baos, 2, 4, signedValue, null)) {
            writer.addEntry(1, 1234);
            writer.addEntry(2, 2345);
            writer.addEntry(3, 3456);
        }

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable intValuePackedTable = new IntValueTypedPackedTable(blockData);
        assertEquals(3, intValuePackedTable.getEntryCount());
        assertEquals(1234, intValuePackedTable.getEntryByIndex(0).getValue());
        assertEquals(2345, intValuePackedTable.getEntryByIndex(1).getValue());
        assertEquals(3456, intValuePackedTable.getEntryByIndex(2).getValue());
        assertThrows(IndexOutOfBoundsException.class,
                () -> intValuePackedTable.getEntryByIndex(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> intValuePackedTable.getEntryByIndex(3));
        assertNull(intValuePackedTable.getEntry(0));
        assertNull(intValuePackedTable.getEntry(4));
    }

    @Test
    public void getEntry_signedValue() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = true;
        try (PackedTableWriter writer = PackedTableWriter.create(baos, 3, 4, signedValue, null)) {
            writer.addEntry(1, -1234);
            writer.addEntry(2, 2345);
            writer.addEntry(3, -3456);
        }

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable intValuePackedTable = new IntValueTypedPackedTable(blockData);
        assertEquals(-3456, intValuePackedTable.getEntry(3).getValue());
        assertEquals(-1234, intValuePackedTable.getEntry(1).getValue());
        assertEquals(2345, intValuePackedTable.getEntry(2).getValue());
        assertNull(intValuePackedTable.getEntry(0));
        assertNull(intValuePackedTable.getEntry(4));
    }

    @Test
    public void getEntry_emptyTable() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        PackedTableWriter writer = PackedTableWriter.create(baos, 2, 4, signedValue, null);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable intValuePackedTable = new IntValueTypedPackedTable(blockData);
        assertArrayEquals(new byte[0], intValuePackedTable.getSharedData());

        assertNull(intValuePackedTable.getEntry(12));
    }

    @Test
    public void getEntry_badKey() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        int keyBits = 4;
        PackedTableWriter writer = PackedTableWriter.create(baos, 2, keyBits, signedValue, null);
        writer.close();

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable intValuePackedTable = new IntValueTypedPackedTable(blockData);
        assertArrayEquals(new byte[0], intValuePackedTable.getSharedData());

        int negativeKey = -1;
        assertThrows(IllegalArgumentException.class,
                () -> intValuePackedTable.getEntry(negativeKey));

        int keyTooBig = (int) BitwiseUtils.maxUnsignedValue(keyBits) + 1;
        assertThrows(IllegalArgumentException.class, () -> intValuePackedTable.getEntry(keyTooBig));
    }

    @Test
    public void entryNextAndPrevious() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        int keyBits = 4;
        try (PackedTableWriter writer = PackedTableWriter.create(baos, 2, keyBits, signedValue,
                null)) {
            writer.addEntry(1, 123);
            writer.addEntry(2, 234);
            writer.addEntry(4, 456);
        }

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable intValuePackedTable = new IntValueTypedPackedTable(blockData);
        IntValueTable.TableEntry one = intValuePackedTable.getEntry(1);
        IntValueTable.TableEntry two = intValuePackedTable.getEntry(2);
        IntValueTable.TableEntry four = intValuePackedTable.getEntry(4);
        assertEquals(two, one.getNext());
        assertEquals(four, two.getNext());
        assertNull(one.getPrevious());
        assertNull(four.getNext());
    }

    @Test
    public void entryEquals() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        boolean signedValue = false;
        try (PackedTableWriter writer = PackedTableWriter.create(baos, 2, 4, signedValue, null)) {
            writer.addEntry(1, 1234);
            writer.addEntry(2, 2345);
        }

        BlockData blockData = new BlockData(createByteBuffer(baos.toByteArray()));
        IntValueTypedPackedTable intValuePackedTable = new IntValueTypedPackedTable(blockData);

        assertEquals(intValuePackedTable.getEntryByIndex(0),
                intValuePackedTable.getEntryByIndex(0));
        assertNotEquals(intValuePackedTable.getEntryByIndex(0),
                intValuePackedTable.getEntryByIndex(1));

        assertNotEquals(intValuePackedTable.getEntryByIndex(1),
                intValuePackedTable.getEntryByIndex(0));
        assertEquals(intValuePackedTable.getEntryByIndex(1),
                intValuePackedTable.getEntryByIndex(1));
    }

    private static ByteBuffer createByteBuffer(byte[] bytes) {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }
}
