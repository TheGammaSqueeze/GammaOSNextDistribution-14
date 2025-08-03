/*
 * Copyright (C) 2009 The Android Open Source Project
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

package libcore.sun.misc;

import static org.junit.Assert.assertEquals;

import junit.framework.TestCase;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import sun.misc.Unsafe;

public class UnsafeTest extends TestCase {

    public void test_getUnsafeForbidden() {
        try {
            Unsafe.getUnsafe();
            fail();
        } catch (SecurityException expected) {
        }
    }

    /**
     * Regression for 2053217. We used to look one level higher than necessary
     * on the stack.
     */
    public void test_getUnsafeForbiddenWithSystemCaller() throws Exception {
        Callable<Object> callable = Executors.callable(new Runnable() {
            public void run() {
                Unsafe.getUnsafe();
            }
        });

        try {
            callable.call();
            fail();
        } catch (SecurityException expected) {
        }
    }

    private class AllocateInstanceTestClass {
        public int i = 123;
        public String s = "hello";
        public Object getThis() { return AllocateInstanceTestClass.this; }
    }

    private static Unsafe getUnsafe() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    public void test_allocateInstance() throws Exception {
        AllocateInstanceTestClass i = (AllocateInstanceTestClass)
                getUnsafe().allocateInstance(AllocateInstanceTestClass.class);
        assertEquals(0, i.i);
        assertEquals(null, i.s);
        assertEquals(i, i.getThis());
    }

    public void test_copyMemory() throws Exception {
        Unsafe unsafe = getUnsafe();

        // Source buffer.
        byte[] msg = "All your base are belong to us.".getBytes();
        long srcBuf = unsafe.allocateMemory(msg.length);
        {
            long srcPtr = srcBuf;
            for (byte b : msg){
                unsafe.putByte(srcPtr++, b);
            }
        }

        // Destination buffer.
        long dstBuf = getUnsafe().allocateMemory(msg.length);
        unsafe.copyMemory(srcBuf, dstBuf, msg.length);

        // Compare buffers.
        long srcPtr = srcBuf;
        long dstPtr = dstBuf;
        for (int i = 0; i < msg.length; ++i) {
            byte srcByte = unsafe.getByte(srcPtr++);
            byte dstByte = unsafe.getByte(dstPtr++);
            assertEquals(String.format("Content mismatch at offset %d: src = '%c', dst = '%c'",
                            i, srcByte, dstByte),
                    srcByte, dstByte);
        }

        // Clean up.
        unsafe.freeMemory(dstBuf);
        unsafe.freeMemory(srcBuf);
    }

    private class TestFixture {
        public boolean booleanVar = true;
        public byte byteVar = 42;
        public int intVar = 2046;
        public long longVar = 123456789;
        public float floatVar = 1.618f;
        public double doubleVar = 3.141;
        public Object objectVar = new Object();
    }

    public void test_getBoolean_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field booleanField = TestFixture.class.getDeclaredField("booleanVar");
        long booleanFieldOffset = unsafe.objectFieldOffset(booleanField);
        boolean booleanValue = unsafe.getBoolean(tf, booleanFieldOffset);
        assertEquals(tf.booleanVar, booleanValue);
    }

    public void test_getByte_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field byteField = TestFixture.class.getDeclaredField("byteVar");
        long byteFieldOffset = unsafe.objectFieldOffset(byteField);
        byte byteValue = unsafe.getByte(tf, byteFieldOffset);
        assertEquals(tf.byteVar, byteValue);
    }

    public void test_getInt_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field intField = TestFixture.class.getDeclaredField("intVar");
        long intFieldOffset = unsafe.objectFieldOffset(intField);
        int intValue = unsafe.getInt(tf, intFieldOffset);
        assertEquals(tf.intVar, intValue);
    }

    public void test_getLong_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field longField = TestFixture.class.getDeclaredField("longVar");
        long longFieldOffset = unsafe.objectFieldOffset(longField);
        long longValue = unsafe.getLong(tf, longFieldOffset);
        assertEquals(tf.longVar, longValue);
    }

    public void test_getFloat_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field floatField = TestFixture.class.getDeclaredField("floatVar");
        long floatFieldOffset = unsafe.objectFieldOffset(floatField);
        float floatValue = unsafe.getFloat(tf, floatFieldOffset);
        assertEquals(tf.floatVar, floatValue);
    }

    public void test_getDouble_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field doubleField = TestFixture.class.getDeclaredField("doubleVar");
        long doubleFieldOffset = unsafe.objectFieldOffset(doubleField);
        double doubleValue = unsafe.getDouble(tf, doubleFieldOffset);
        assertEquals(tf.doubleVar, doubleValue);
    }

    public void test_getObject_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field objectField = TestFixture.class.getDeclaredField("objectVar");
        long objectFieldOffset = unsafe.objectFieldOffset(objectField);
        Object objectValue = unsafe.getObject(tf, objectFieldOffset);
        assertEquals(tf.objectVar, objectValue);
    }

    public void test_putBoolean_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field booleanField = TestFixture.class.getDeclaredField("booleanVar");
        long booleanFieldOffset = unsafe.objectFieldOffset(booleanField);
        boolean booleanValue = false;
        unsafe.putBoolean(tf, booleanFieldOffset, booleanValue);
        assertEquals(booleanValue, tf.booleanVar);
    }

    public void test_putByte_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field byteField = TestFixture.class.getDeclaredField("byteVar");
        long byteFieldOffset = unsafe.objectFieldOffset(byteField);
        byte byteValue = 83;
        unsafe.putByte(tf, byteFieldOffset, byteValue);
        assertEquals(byteValue, tf.byteVar);
    }

    public void test_putInt_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field intField = TestFixture.class.getDeclaredField("intVar");
        long intFieldOffset = unsafe.objectFieldOffset(intField);
        int intValue = 3000;
        unsafe.putInt(tf, intFieldOffset, intValue);
        assertEquals(intValue, tf.intVar);
    }

    public void test_putLong_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field longField = TestFixture.class.getDeclaredField("longVar");
        long longFieldOffset = unsafe.objectFieldOffset(longField);
        long longValue = 9000;
        unsafe.putLong(tf, longFieldOffset, longValue);
        assertEquals(longValue, tf.longVar);
    }

    public void test_putFloat_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field floatField = TestFixture.class.getDeclaredField("floatVar");
        long floatFieldOffset = unsafe.objectFieldOffset(floatField);
        float floatValue = 0.987f;
        unsafe.putFloat(tf, floatFieldOffset, floatValue);
        assertEquals(floatValue, tf.floatVar);
    }

    public void test_putDouble_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field doubleField = TestFixture.class.getDeclaredField("doubleVar");
        long doubleFieldOffset = unsafe.objectFieldOffset(doubleField);
        double doubleValue = 0.123;
        unsafe.putDouble(tf, doubleFieldOffset, doubleValue);
        assertEquals(doubleValue, tf.doubleVar);
    }

    public void test_putObject_Relative() throws Exception {
        Unsafe unsafe = getUnsafe();
        TestFixture tf = new TestFixture();
        Field objectField = TestFixture.class.getDeclaredField("objectVar");
        long objectFieldOffset = unsafe.objectFieldOffset(objectField);
        Object objectValue = new Object();
        unsafe.putObject(tf, objectFieldOffset, objectValue);
        assertEquals(objectValue, tf.objectVar);
    }

    public void test_putByte_getByte_Absolute() throws Exception {
        Unsafe unsafe = getUnsafe();
        long buffer = unsafe.allocateMemory(Byte.BYTES);
        byte byteValue1 = 51;
        unsafe.putByte(buffer, byteValue1);
        byte byteValue2 = unsafe.getByte(buffer);
        assertEquals(byteValue2, byteValue1);
        unsafe.freeMemory(buffer);
    }

    public void test_putInt_getInt_Absolute() throws Exception {
        Unsafe unsafe = getUnsafe();
        long buffer = unsafe.allocateMemory(Integer.BYTES);
        int intValue1 = 2047;
        unsafe.putInt(buffer, intValue1);
        int intValue2 = unsafe.getInt(buffer);
        assertEquals(intValue2, intValue1);
        unsafe.freeMemory(buffer);
    }

    public void test_putLong_getLong_Absolute() throws Exception {
        Unsafe unsafe = getUnsafe();
        long buffer = unsafe.allocateMemory(Long.BYTES);
        long longValue1 = 987654321;
        unsafe.putLong(buffer, longValue1);
        long longValue2 = unsafe.getLong(buffer);
        assertEquals(longValue2, longValue1);
        unsafe.freeMemory(buffer);
    }

    public void test_putFloat_getFloat_Absolute() throws Exception {
        Unsafe unsafe = getUnsafe();
        long buffer = unsafe.allocateMemory(Float.BYTES);
        float floatValue1 = 2.718f;
        unsafe.putFloat(buffer, floatValue1);
        float floatValue2 = unsafe.getFloat(buffer);
        assertEquals(floatValue2, floatValue1);
        unsafe.freeMemory(buffer);
    }

    public void test_putDouble_getDouble_Absolute() throws Exception {
        Unsafe unsafe = getUnsafe();
        long buffer = unsafe.allocateMemory(Double.BYTES);
        double doubleValue1 = 6.283;
        unsafe.putDouble(buffer, doubleValue1);
        double doubleValue2 = unsafe.getDouble(buffer);
        assertEquals(doubleValue2, doubleValue1);
        unsafe.freeMemory(buffer);
    }
}
