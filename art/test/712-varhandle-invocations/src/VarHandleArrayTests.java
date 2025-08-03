/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// These tests cover DoVarHandleInvokeCommon in interpreter_common.cc.

public class VarHandleArrayTests {
    public static class ArrayStoreTest extends VarHandleUnitTest {
        private static final Integer ZERO = Integer.valueOf(0);
        private static final Integer ONE = Integer.valueOf(1);
        private static final Integer TWO = Integer.valueOf(2);

        private final Integer[] values = new Integer[10];

        private void testIntegerArrayVarHandle() {
            final VarHandle vh = MethodHandles.arrayElementVarHandle(Integer[].class);

            // AccessModeTemplate::kSet
            vh.set(values, 0, ZERO);
            assertEquals(0, values[0].intValue());
            vh.set((Object[]) values, 1, ONE);
            assertEquals(ONE, values[1]);
            assertThrowsAIOBE(() -> vh.set(values, values.length, null));
            assertThrowsCCE(() -> vh.set(values, 6, new Object()));
            assertThrowsCCE(() -> vh.set((Object[]) values, 6, new Object()));
            assertThrowsNPE(() -> vh.set((Integer[]) null, 6, ONE));
            assertThrowsWMTE(() -> vh.set(values, 'c'));
            assertThrowsWMTE(() -> vh.set((Object[]) values, 5, 'c'));

            // AccessModeTemplate::kGetAndUpdate
            assertEquals(ZERO, (Integer) vh.getAndSet(values, 0, ONE));
            assertEquals(ONE, values[0]);
            assertThrowsAIOBE(() -> vh.getAndSet(values, values.length, null));
            assertThrowsCCE(() -> vh.getAndSet(values, 6, new Object()));
            assertThrowsCCE(() -> vh.getAndSet((Object[]) values, 6, new Object()));
            assertThrowsNPE(() -> vh.getAndSet((Integer[]) null, 6, ONE));
            assertThrowsWMTE(() -> vh.getAndSet(values, 'c'));
            assertThrowsWMTE(() -> vh.getAndSet((Object[]) values, 5, 'c'));

            // AccessModeTemplate::kCompareAndExchange
            assertEquals(ONE, (Integer) vh.compareAndExchange(values, 0, ONE, TWO));
            assertEquals(TWO, values[0]);
            assertEquals(TWO, (Integer) vh.compareAndExchange(values, 0, ONE, ZERO));
            assertEquals(TWO, values[0]);
            assertThrowsAIOBE(() -> vh.compareAndExchange(values, values.length, null, null));
            assertThrowsCCE(() -> vh.compareAndExchange(values, 6, 6, new Object()));
            assertThrowsCCE(() -> vh.compareAndExchange((Object[]) values, 6, 6, new Object()));
            assertThrowsNPE(() -> vh.compareAndExchange((Integer[]) null, 6, ONE, ONE));
            assertThrowsWMTE(() -> vh.compareAndExchange(values, null, 'c'));
            assertThrowsWMTE(() -> vh.compareAndExchange((Object[]) values, 5, null, 'c'));

            // AccessModeTemplate::kCompareAndSet
            assertEquals(true, (boolean) vh.compareAndSet(values, 0, TWO, ONE));
            assertEquals(ONE, values[0]);
            assertEquals(false, (boolean) vh.compareAndSet(values, 0, ZERO, TWO));
            assertEquals(ONE, values[0]);
            assertThrowsAIOBE(() -> vh.compareAndSet(values, values.length, null, null));
            assertThrowsCCE(() -> vh.compareAndSet(values, 6, 6, new Object()));
            assertThrowsCCE(() -> vh.compareAndSet((Object[]) values, 6, 6, new Object()));
            assertThrowsNPE(() -> vh.compareAndSet((Integer[]) null, 6, ONE, ONE));
            assertThrowsWMTE(() -> vh.compareAndSet(values, null, 'c'));
            assertThrowsWMTE(() -> vh.compareAndSet((Object[]) values, 5, null, 'c'));
        }

        private void testObjectArrayVarHandle() {
            final VarHandle vho = MethodHandles.arrayElementVarHandle(Object[].class);

            // AccessModeTemplate::kSet
            vho.set(values, 0, ONE);
            assertEquals(ONE, values[0]);
            assertThrowsAIOBE(() -> vho.set(values, values.length, null));
            assertThrowsASE(() -> vho.set(values, 0, new Object()));
            assertThrowsASE(() -> vho.set(values, 0, "hello"));
            assertThrowsNPE(() -> vho.set(null, 0, ZERO));
            assertThrowsWMTE(() -> vho.set(0, ZERO));
            assertThrowsWMTE(() -> vho.set(values, ZERO));

            // AccessModeTemplate::kGetAndUpdate
            assertEquals(ONE, vho.getAndSetAcquire(values, 0, TWO));
            assertThrowsAIOBE(() -> vho.getAndSetRelease(values, values.length, null));
            assertThrowsASE(() -> vho.getAndSet(values, 0, new Object()));
            assertThrowsASE(() -> vho.getAndSet(values, 0, "hello"));
            assertThrowsNPE(() -> vho.getAndSet(null, 0, ZERO));
            assertThrowsWMTE(() -> vho.getAndSet(0, ZERO));
            assertThrowsWMTE(() -> vho.getAndSet(values, ZERO));

            // AccessModeTemplate::kCompareAndExchange
            assertEquals(TWO, vho.compareAndExchange(values, 0, TWO, ZERO));
            assertThrowsAIOBE(() -> vho.compareAndExchange(values, values.length, ONE, TWO));
            assertThrowsASE(() -> vho.compareAndExchange(values, 0, ONE, new Object()));
            assertThrowsASE(() -> vho.compareAndExchange(values, 0, ONE, "hello"));
            assertThrowsNPE(() -> vho.compareAndExchange(null, 0, ONE, ZERO));
            assertThrowsWMTE(() -> vho.compareAndExchange(0, ZERO, ONE));
            assertThrowsWMTE(() -> vho.compareAndExchange(values, ONE, ZERO));

            // AccessModeTemplate::kCompareAndSet
            assertEquals(true, (boolean) vho.compareAndSet(values, 0, ZERO, ONE));
            assertThrowsAIOBE(() -> vho.compareAndSet(values, values.length, ONE, TWO));
            assertThrowsASE(() -> vho.compareAndSet(values, 0, ONE, new Object()));
            assertThrowsASE(() -> vho.compareAndSet(values, 0, ONE, "hello"));
            assertThrowsNPE(() -> vho.compareAndSet(null, 0, ONE, ZERO));
            assertThrowsWMTE(() -> vho.compareAndSet(0, ZERO, ONE));
            assertThrowsWMTE(() -> vho.compareAndSet(values, ONE, ZERO));
        }

        private short toHost(ByteOrder order, byte b0, byte b1) {
            final int u0 = Byte.toUnsignedInt(b0);
            final int u1 = Byte.toUnsignedInt(b1);
            if (order == ByteOrder.LITTLE_ENDIAN) {
                return (short) (u0 + (u1 << 8));
            } else {
                return (short) (u1 + (u0 << 8));
            }
        }

        private int toHost(ByteOrder order, byte b0, byte b1, byte b2, byte b3) {
            final int u0 = Byte.toUnsignedInt(b0);
            final int u1 = Byte.toUnsignedInt(b1);
            final int u2 = Byte.toUnsignedInt(b2);
            final int u3 = Byte.toUnsignedInt(b3);
            if (order == ByteOrder.LITTLE_ENDIAN) {
                return u0 + (u1 << 8) + (u2 << 16) + (u3 << 24);
            } else {
                return u3 + (u2 << 8) + (u1 << 16) + (u0 << 24);
            }
        }

        private void testByteArrayViewVarHandle() {
            final int BITS_PER_BYTE = 8;
            byte[] array = new byte[32];

            final ByteOrder[] byteOrders =
                    new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN};

            for (ByteOrder order : byteOrders) {
                {
                    final VarHandle vhShort =
                            MethodHandles.byteArrayViewVarHandle(short[].class, order);
                    assertThrowsIOOBE(() -> vhShort.get(array, -1));
                    assertThrowsIOOBE(() -> vhShort.get(array, Integer.MIN_VALUE));
                    assertThrowsIOOBE(() -> vhShort.get(array, array.length));
                    assertThrowsIOOBE(() -> vhShort.get(array, array.length - 1));
                    assertThrowsIOOBE(() -> vhShort.get(array, Integer.MAX_VALUE));

                    for (int i = 0; i < array.length - 1; ++i) {
                        final boolean isAligned = (i % 2) == 0;
                        final short value = (short) ((i + 1) * 0xff);
                        vhShort.set(array, i, value);
                        assertEquals(value, (short) vhShort.get(array, i));
                        assertEquals(value, toHost(order, array[i], array[i + 1]));
                        for (int j = 0; j < array.length; ++j) {
                            if (j < i || j > i + 1) {
                                assertEquals((byte) 0, array[j]);
                            }
                        }
                        if (isAligned) {
                            vhShort.getAcquire(array, i);
                            vhShort.setRelease(array, i, (short) 0);
                        } else {
                            final int fi = i;
                            assertThrowsISE(() -> vhShort.getAcquire(array, fi));
                            assertThrowsISE(() -> vhShort.setRelease(array, fi, (short) 0));
                        }
                        vhShort.set(array, i, (short) 0);
                    }
                }
                {
                    final VarHandle vhInt =
                            MethodHandles.byteArrayViewVarHandle(int[].class, order);
                    assertThrowsIOOBE(() -> vhInt.get(array, -1));
                    assertThrowsIOOBE(() -> vhInt.get(array, Integer.MIN_VALUE));
                    assertThrowsIOOBE(() -> vhInt.get(array, array.length));
                    assertThrowsIOOBE(() -> vhInt.get(array, array.length - 1));
                    assertThrowsIOOBE(() -> vhInt.get(array, array.length - 2));
                    assertThrowsIOOBE(() -> vhInt.get(array, array.length - 3));
                    assertThrowsIOOBE(() -> vhInt.get(array, Integer.MAX_VALUE));
                    for (int i = 0; i < array.length - 3; ++i) {
                        final boolean isAligned = (i % 4) == 0;
                        final int value = (i + 1) * 0x11223344;
                        vhInt.set(array, i, value);
                        assertEquals(value, vhInt.get(array, i));
                        assertEquals(
                                value,
                                toHost(order, array[i], array[i + 1], array[i + 2], array[i + 3]));
                        for (int j = 0; j < array.length; ++j) {
                            if (j < i || j > i + 3) {
                                assertEquals((byte) 0, array[j]);
                            }
                        }
                        if (isAligned) {
                            vhInt.getAcquire(array, i);
                            vhInt.setRelease(array, i, (int) 0);
                        } else {
                            final int fi = i;
                            assertThrowsISE(() -> vhInt.getAcquire(array, fi));
                            assertThrowsISE(() -> vhInt.setRelease(array, fi, (int) 0));
                        }
                        vhInt.set(array, i, 0);
                    }
                }
            }
        }

        private void testByteBufferVarHandle() {
            final ByteOrder[] byteOrders =
                    new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN};

            for (final ByteOrder byteOrder : byteOrders) {
                final ByteBuffer heapBuffer = ByteBuffer.allocate(32);
                final ByteBuffer directBuffer = ByteBuffer.allocateDirect(32);
                final ByteBuffer arrayBuffer = ByteBuffer.wrap(new byte[32]);
                final ByteBuffer anotherArrayBuffer = ByteBuffer.wrap(new byte[32], 3, 23);
                final ByteBuffer[] buffers = {
                    heapBuffer,
                    ((ByteBuffer) heapBuffer.duplicate().position(1)).slice(),
                    directBuffer,
                    ((ByteBuffer) directBuffer.duplicate().position(1)).slice(),
                    arrayBuffer,
                    ((ByteBuffer) arrayBuffer.duplicate().position(1)).slice(),
                    anotherArrayBuffer,
                    ((ByteBuffer) anotherArrayBuffer.duplicate().position(1)).slice()
                };
                for (final ByteBuffer buffer : buffers) {
                    {
                        final VarHandle vhShort =
                                MethodHandles.byteBufferViewVarHandle(short[].class, byteOrder);
                        assertThrowsIOOBE(() -> vhShort.get(buffer, -1));
                        assertThrowsIOOBE(() -> vhShort.get(buffer, Integer.MIN_VALUE));
                        assertThrowsIOOBE(() -> vhShort.get(buffer, Integer.MAX_VALUE));
                        assertThrowsIOOBE(() -> vhShort.get(buffer, buffer.limit()));
                        assertThrowsIOOBE(() -> vhShort.get(buffer, buffer.limit() - 1));
                        final int zeroAlignment = buffer.alignmentOffset(0, Short.BYTES);
                        for (int i = 0; i < buffer.limit() - 1; ++i) {
                            boolean isAligned = (zeroAlignment + i) % Short.BYTES == 0;
                            final short value = (short) ((i + 1) * 0xff);
                            vhShort.set(buffer, i, value);
                            assertEquals(value, (short) vhShort.get(buffer, i));
                            assertEquals(
                                    value, toHost(byteOrder, buffer.get(i), buffer.get(i + 1)));
                            for (int j = 0; j < buffer.limit(); ++j) {
                                if (j < i || j > i + 1) {
                                    assertEquals((byte) 0, buffer.get(j));
                                }
                            }
                            if (isAligned) {
                                vhShort.getAcquire(buffer, i);
                                vhShort.setRelease(buffer, i, (short) 0);
                            } else {
                                final int fi = i;
                                assertThrowsISE(() -> vhShort.getAcquire(buffer, fi));
                                assertThrowsISE(() -> vhShort.setRelease(buffer, fi, (short) 0));
                            }
                            vhShort.set(buffer, i, (short) 0);
                        }
                    }
                    {
                        final VarHandle vhInt =
                                MethodHandles.byteBufferViewVarHandle(int[].class, byteOrder);
                        assertThrowsIOOBE(() -> vhInt.get(buffer, -1));
                        assertThrowsIOOBE(() -> vhInt.get(buffer, Integer.MIN_VALUE));
                        assertThrowsIOOBE(() -> vhInt.get(buffer, Integer.MAX_VALUE));
                        assertThrowsIOOBE(() -> vhInt.get(buffer, buffer.limit()));
                        assertThrowsIOOBE(() -> vhInt.get(buffer, buffer.limit() - 1));
                        assertThrowsIOOBE(() -> vhInt.get(buffer, buffer.limit() - 2));
                        assertThrowsIOOBE(() -> vhInt.get(buffer, buffer.limit() - 3));
                        final int zeroAlignment = buffer.alignmentOffset(0, Integer.BYTES);
                        for (int i = 0; i < buffer.limit() - 3; ++i) {
                            boolean isAligned = (zeroAlignment + i) % Integer.BYTES == 0;
                            final int value = (i + 1) * 0x11223344;
                            vhInt.set(buffer, i, value);
                            assertEquals(value, vhInt.get(buffer, i));
                            assertEquals(
                                    value,
                                    toHost(
                                            byteOrder,
                                            buffer.get(i),
                                            buffer.get(i + 1),
                                            buffer.get(i + 2),
                                            buffer.get(i + 3)));
                            for (int j = 0; j < buffer.limit(); ++j) {
                                if (j < i || j > i + 3) {
                                    assertEquals((byte) 0, buffer.get(j));
                                }
                            }
                            if (isAligned) {
                                vhInt.getAcquire(buffer, i);
                                vhInt.setRelease(buffer, i, (int) 0);
                            } else {
                                final int fi = i;
                                assertThrowsISE(() -> vhInt.getAcquire(buffer, fi));
                                assertThrowsISE(() -> vhInt.setRelease(buffer, fi, (int) 0));
                            }
                            vhInt.set(buffer, i, 0);
                        }
                    }
                }
            }
        }

        @Override
        protected void doTest() throws Exception {
            testIntegerArrayVarHandle();
            testObjectArrayVarHandle();
            testByteArrayViewVarHandle();
            testByteBufferVarHandle();
        }

        public static void main(String[] args) {
            new ArrayStoreTest().run();
        }
    }

    public static void main(String[] args) {
        ArrayStoreTest.main(args);
    }
}
