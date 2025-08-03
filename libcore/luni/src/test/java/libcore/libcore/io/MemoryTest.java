/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package libcore.libcore.io;

import dalvik.system.VMRuntime;
import java.util.Arrays;
import junit.framework.TestCase;

import libcore.io.Memory;

public class MemoryTest extends TestCase {
    public void testSetIntArray() {
        int[] values = { 3, 7, 31, 127, 8191, 131071, 524287, 2147483647 };
        int[] swappedValues = new int[values.length];
        for (int i = 0; i < values.length; ++i) {
            swappedValues[i] = Integer.reverseBytes(values[i]);
        }

        int scale = Integer.BYTES;
        VMRuntime runtime = VMRuntime.getRuntime();
        byte[] array = (byte[]) runtime.newNonMovableArray(byte.class, scale * values.length + 1);
        long base_ptr = runtime.addressOf(array);

        for (int ptr_offset = 0; ptr_offset < 2; ++ptr_offset) {
            long ptr = base_ptr + ptr_offset; // To test aligned and unaligned accesses.
            Arrays.fill(array, (byte) 0);

            // Regular copy.
            Memory.pokeIntArray(ptr, values, 0, values.length, false);
            assertIntsEqual(values, ptr, false);
            assertIntsEqual(swappedValues, ptr, true);

            // Swapped copy.
            Memory.pokeIntArray(ptr, values, 0, values.length, true);
            assertIntsEqual(values, ptr, true);
            assertIntsEqual(swappedValues, ptr, false);

            // Swapped copies of slices (to ensure we test non-zero offsets).
            for (int i = 0; i < values.length; ++i) {
                Memory.pokeIntArray(ptr + i * scale, values, i, 1, true);
            }
            assertIntsEqual(values, ptr, true);
            assertIntsEqual(swappedValues, ptr, false);
        }
    }

    private void assertIntsEqual(int[] expectedValues, long ptr, boolean swap) {
        for (int i = 0; i < expectedValues.length; ++i) {
            assertEquals(expectedValues[i], Memory.peekInt(ptr + Integer.BYTES * i, swap));
        }
    }

    public void testSetLongArray() {
        long[] values = { 0x1020304050607080L, 0xffeeddccbbaa9988L };
        long[] swappedValues = new long[values.length];
        for (int i = 0; i < values.length; ++i) {
            swappedValues[i] = Long.reverseBytes(values[i]);
        }

        int scale = Long.BYTES;
        VMRuntime runtime = VMRuntime.getRuntime();
        byte[] array = (byte[]) runtime.newNonMovableArray(byte.class, scale * values.length + 1);
        long base_ptr = runtime.addressOf(array);

        for (int ptr_offset = 0; ptr_offset < 2; ++ptr_offset) {
            long ptr = base_ptr + ptr_offset; // To test aligned and unaligned accesses.
            Arrays.fill(array, (byte) 0);

            // Regular copy.
            Memory.pokeLongArray(ptr, values, 0, values.length, false);
            assertLongsEqual(values, ptr, false);
            assertLongsEqual(swappedValues, ptr, true);

            // Swapped copy.
            Memory.pokeLongArray(ptr, values, 0, values.length, true);
            assertLongsEqual(values, ptr, true);
            assertLongsEqual(swappedValues, ptr, false);

            // Swapped copies of slices (to ensure we test non-zero offsets).
            for (int i = 0; i < values.length; ++i) {
                Memory.pokeLongArray(ptr + i * scale, values, i, 1, true);
            }
            assertLongsEqual(values, ptr, true);
            assertLongsEqual(swappedValues, ptr, false);
        }
    }

    private void assertLongsEqual(long[] expectedValues, long ptr, boolean swap) {
      for (int i = 0; i < expectedValues.length; ++i) {
        assertEquals(expectedValues[i], Memory.peekLong(ptr + Long.BYTES * i, swap));
      }
    }

    public void testSetShortArray() {
        short[] values = { 0x0001, 0x0020, 0x0300, 0x4000 };
        short[] swappedValues = { 0x0100, 0x2000, 0x0003, 0x0040 };

        int scale = Short.BYTES;
        VMRuntime runtime = VMRuntime.getRuntime();
        byte[] array = (byte[]) runtime.newNonMovableArray(byte.class, scale * values.length + 1);
        long base_ptr = runtime.addressOf(array);

        for (int ptr_offset = 0; ptr_offset < 2; ++ptr_offset) {
            long ptr = base_ptr + ptr_offset; // To test aligned and unaligned accesses.
            Arrays.fill(array, (byte) 0);

            // Regular copy.
            Memory.pokeShortArray(ptr, values, 0, values.length, false);
            assertShortsEqual(values, ptr, false);
            assertShortsEqual(swappedValues, ptr, true);

            // Swapped copy.
            Memory.pokeShortArray(ptr, values, 0, values.length, true);
            assertShortsEqual(values, ptr, true);
            assertShortsEqual(swappedValues, ptr, false);

            // Swapped copies of slices (to ensure we test non-zero offsets).
            for (int i = 0; i < values.length; ++i) {
                Memory.pokeShortArray(ptr + i * scale, values, i, 1, true);
            }
            assertShortsEqual(values, ptr, true);
            assertShortsEqual(swappedValues, ptr, false);
        }
    }

    private void assertShortsEqual(short[] expectedValues, long ptr, boolean swap) {
        for (int i = 0; i < expectedValues.length; ++i) {
            assertEquals(expectedValues[i], Memory.peekShort(ptr + Short.BYTES * i, swap));
        }
    }

    public void testMemmove() {
        final int size = 100;
        checkPartialMemmove(size, 0, 0, size);
        checkPartialMemmove(size, 0, 0, size / 2);
        checkPartialMemmove(size, size / 2, size / 2, size - size / 2);
        checkPartialMemmove(size, 10, 20, 20);

        checkOverlappingMemmove(size, 0, 0, size);
        checkOverlappingMemmove(size, 10, 20, 30);
        checkOverlappingMemmove(size, 20, 10, 30);
    }

    private void checkPartialMemmove(int size, int offsetDst, int offsetSrc, int count) {
        byte[] src = new byte[size];
        for (int i = 0; i < size; ++i) {
            src[i] = (byte)i;
        }
        byte[] dst = new byte[size];
        Arrays.fill(dst, (byte)-1);

        assertTrue(offsetSrc + count <= size);
        assertTrue(offsetDst + count <= size);
        Memory.memmove(dst, offsetDst, src, offsetSrc, count);
        for (int i = 0; i < size; ++i) {
            if (i >= offsetDst && i < offsetDst + count) {
                assertEquals(src[i + (offsetSrc - offsetDst)], dst[i]);
            } else {
                assertEquals((byte)-1, dst[i]);
            }
        }
    }

    private void checkOverlappingMemmove(int size, int offsetDst, int offsetSrc, int count) {
        byte[] buf = new byte[size];
        for (int i = 0; i < size; ++i) {
            buf[i] = (byte)i;
        }

        assertTrue(offsetSrc + count <= size);
        assertTrue(offsetDst + count <= size);
        Memory.memmove(buf, offsetDst, buf, offsetSrc, count);
        for (int i = 0; i < size; ++i) {
            if (i >= offsetDst && i < offsetDst + count) {
                assertEquals(i + (offsetSrc - offsetDst), buf[i]);
            } else {
                assertEquals((byte)i, buf[i]);
            }
        }
    }
}
