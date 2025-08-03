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

package com.android.storage.io;

import static com.android.storage.testing.MoreAsserts.assertThrows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.storage.io.read.TypedInputStream;
import com.android.storage.io.write.TypedOutputStream;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Tests for {@link TypedInputStream} and {@link TypedOutputStream}.
 */
public class TypedStreamsTest {

    @Test
    public void writeReadOk() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TypedOutputStream tos = new TypedOutputStream(baos);

        tos.writeChar(Character.MIN_VALUE);
        tos.writeChar(1234);
        tos.writeChar(Character.MAX_VALUE);

        tos.writeUnsignedByte(0);
        tos.writeUnsignedByte(123);
        tos.writeUnsignedByte(255);

        tos.writeInt(0x77777777);

        tos.writeByte(Byte.MIN_VALUE);
        tos.writeByte(123);
        tos.writeByte(Byte.MAX_VALUE);

        tos.writeBytes(new byte[] { 0x11, 0x22, 0x00, 0x7F, -0x80 });

        tos.writeLong(0x5555555555555555L);

        tos.writeVarByteValue(1, 0x0000000000000012L);
        tos.writeVarByteValue(2, 0x0000000000002223L);
        tos.writeVarByteValue(3, 0x0000000000333334L);
        tos.writeVarByteValue(4, 0x0000000044444445L);
        tos.writeVarByteValue(5, 0x0000005555555556L);
        tos.writeVarByteValue(6, 0x0000666666666667L);
        tos.writeVarByteValue(7, 0x0077777777777778L);
        tos.writeVarByteValue(8, 0x8888888888888889L);

        tos.writeTinyByteArray(new byte[0]);
        tos.writeTinyByteArray(new byte[] { (byte) 0xAA, (byte) 0xBB, 0 });

        tos.writeTinyCharArray(new char[0]);
        tos.writeTinyCharArray(new char[] { 0xAAAA, 0xBBBB, 0 });

        tos.flush();
        byte[] bytes = baos.toByteArray();

        TypedInputStream tis = new TypedInputStream(new ByteArrayInputStream(bytes));
        assertEquals(Character.MIN_VALUE, tis.readChar());
        assertEquals(1234, tis.readChar());
        assertEquals(Character.MAX_VALUE, tis.readChar());

        assertEquals(0, tis.readUnsignedByte());
        assertEquals(123, tis.readUnsignedByte());
        assertEquals(255, tis.readUnsignedByte());

        assertEquals(0x77777777, tis.readInt());

        assertEquals(Byte.MIN_VALUE, tis.readSignedByte());
        assertEquals(123, tis.readSignedByte());
        assertEquals(Byte.MAX_VALUE, tis.readSignedByte());

        // There is no readBytes()
        assertEquals(0x11, tis.readSignedByte());
        assertEquals(0x22, tis.readSignedByte());
        assertEquals(0x00, tis.readSignedByte());
        assertEquals(0x7F, tis.readSignedByte());
        assertEquals(-0x80, tis.readSignedByte());

        assertEquals(0x5555555555555555L, tis.readLong());

        expectBytes(tis, 1, 0x12);

        expectBytes(tis, 1, 0x22);
        expectBytes(tis, 1, 0x23);

        expectBytes(tis, 2, 0x33);
        expectBytes(tis, 1, 0x34);

        expectBytes(tis, 3, 0x44);
        expectBytes(tis, 1, 0x45);

        expectBytes(tis, 4, 0x55);
        expectBytes(tis, 1, 0x56);

        expectBytes(tis, 5, 0x66);
        expectBytes(tis, 1, 0x67);

        expectBytes(tis, 6, 0x77);
        expectBytes(tis, 1, 0x78);

        expectBytes(tis, 7, 0x88);
        expectBytes(tis, 1, 0x89);

        assertArrayEquals(new byte[0], tis.readTinyVarByteArray());
        assertArrayEquals(new byte[] { (byte) 0xAA, (byte) 0xBB, 0 }, tis.readTinyVarByteArray());

        assertArrayEquals(new char[0], tis.readTinyVarCharArray());
        assertArrayEquals(new char[] { 0xAAAA, 0xBBBB, 0 }, tis.readTinyVarCharArray());
    }

    private static void expectBytes(TypedInputStream tis, int count, int expected)
            throws IOException {
        for (int i = 0; i < count; i++) {
            assertEquals(expected, tis.readUnsignedByte());
        }
    }

    @Test
    public void writeVarByteValueUnusedBitsCheck() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TypedOutputStream tos = new TypedOutputStream(baos);

        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(1, 0x00000000000001FFL));
        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(1, 0xE0000000000000FFL));

        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(2, 0x000000000001FFFFL));
        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(2, 0xE00000000000FFFFL));

        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(3, 0x0000000001FFFFFFL));
        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(3, 0xE000000000FFFFFFL));

        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(4, 0x00000001FFFFFFFFL));
        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(4, 0xE0000000FFFFFFFFL));

        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(5, 0x000001FFFFFFFFFFL));
        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(5, 0xE00000FFFFFFFFFFL));

        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(6, 0x0001FFFFFFFFFFFFL));
        assertThrows(IllegalArgumentException.class, () -> tos.writeVarByteValue(6,
                0xE000FFFFFFFFFFFFL));

        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(7, 0x01FFFFFFFFFFFFFFL));
        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(7, 0xE0FFFFFFFFFFFFFFL));
    }


    @Test
    public void writeBoundChecks() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TypedOutputStream tos = new TypedOutputStream(baos);

        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(0, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> tos.writeVarByteValue(9, Long.MAX_VALUE));

        assertThrows(IllegalArgumentException.class, () -> tos.writeByte(Byte.MIN_VALUE - 1));
        assertThrows(IllegalArgumentException.class, () -> tos.writeByte(Byte.MAX_VALUE + 1));

        assertThrows(IllegalArgumentException.class, () -> tos.writeChar(Character.MIN_VALUE - 1));
        assertThrows(IllegalArgumentException.class, () -> tos.writeChar(Character.MAX_VALUE + 1));

        assertThrows(IllegalArgumentException.class, () -> tos.writeUnsignedByte(-1));
        assertThrows(IllegalArgumentException.class, () -> tos.writeUnsignedByte(256));

        assertEquals(0, baos.toByteArray().length);
    }
}
