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

package android.jni.cts;


/**
 * Basic native instance method tests. The "nonce" class being tested
 * by this class is a class defined in this package that declares the
 * bulk of its methods as native.
 */
public class JniInstanceTest extends JniTestCase {
    /** instance to use for all the tests */
    private InstanceNonce target;

    @Override
    protected void setUp() {
        target = new InstanceNonce();
    }

    /**
     * Test native method call without implementation.
     */
    public void test_missing() {
        try {
            target.missing();
            throw new Error("Unreachable");
        } catch (UnsatisfiedLinkError expected) {
        }
    }
    public void test_missingFast() {
        try {
            target.missingFast();
            throw new Error("Unreachable");
        } catch (UnsatisfiedLinkError expected) {
        }
    }

    /**
     * Test a simple no-op and void-returning method call.
     *
     * The "Dlsym" versions use dynamic lookup instead of explicitly
     * registering the native method implementation.
     */
    public void test_nop() {
        target.nop();
    }
    public void test_nopDlsym() {
        target.nopDlsym();
    }
    public void test_nopFast() {
        target.nopFast();
    }
    public void test_nopFastDlsym() {
        target.nopFastDlsym();
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnBoolean() {
        assertEquals(false, target.returnBoolean());
    }
    public void test_returnBooleanFast() {
        assertEquals(false, target.returnBooleanFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnByte() {
        assertEquals(123, target.returnByte());
    }
    public void test_returnByteFast() {
        assertEquals(123, target.returnByteFast());
    }


    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnShort() {
        assertEquals(-12345, target.returnShort());
    }
    public void test_returnShortFast() {
        assertEquals(-12345, target.returnShortFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnChar() {
        assertEquals(34567, target.returnChar());
    }
    public void test_returnCharFast() {
        assertEquals(34567, target.returnCharFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnInt() {
        assertEquals(12345678, target.returnInt());
    }
    public void test_returnIntFast() {
        assertEquals(12345678, target.returnIntFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnLong() {
        assertEquals(-1098765432109876543L, target.returnLong());
    }
    public void test_returnLongFast() {
        assertEquals(-1098765432109876543L, target.returnLongFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnFloat() {
        assertEquals(-98765.4321F, target.returnFloat());
    }
    public void test_returnFloatFast() {
        assertEquals(-98765.4321F, target.returnFloatFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnDouble() {
        assertEquals(12345678.9, target.returnDouble());
    }
    public void test_returnDoubleFast() {
        assertEquals(12345678.9, target.returnDoubleFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnNull() {
        assertNull(target.returnNull());
    }
    public void test_returnNullFast() {
        assertNull(target.returnNullFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnString() {
        assertEquals("blort", target.returnString());
    }
    public void test_returnStringFast() {
        assertEquals("blort", target.returnStringFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnShortArray() {
        checkShortArray(target.returnShortArray());
    }
    public void test_returnShortArrayFast() {
        checkShortArray(target.returnShortArrayFast());
    }
    private void checkShortArray(short[] array) {
        assertSame(short[].class, array.getClass());
        assertEquals(3, array.length);
        assertEquals(10, array[0]);
        assertEquals(20, array[1]);
        assertEquals(30, array[2]);
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnStringArray() {
        checkStringArray(target.returnStringArray());
    }
    public void test_returnStringArrayFast() {
        checkStringArray(target.returnStringArrayFast());
    }
    private void checkStringArray(String[] array) {
        assertSame(String[].class, array.getClass());
        assertEquals(100, array.length);
        assertEquals("blort", array[0]);
        assertEquals(null,    array[1]);
        assertEquals("zorch", array[50]);
        assertEquals("fizmo", array[99]);
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call,
     * that returns the implicit {@code this} argument.
     */
    public void test_returnThis() {
        assertSame(target, target.returnThis());
    }
    public void test_returnThisFast() {
        assertSame(target, target.returnThis());
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeBoolean() {
        assertTrue(target.takeBoolean(false));
    }
    public void test_takeBooleanFast() {
        assertTrue(target.takeBooleanFast(false));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeByte() {
        assertTrue(target.takeByte((byte) -99));
    }
    public void test_takeByteFast() {
        assertTrue(target.takeByteFast((byte) -99));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeShort() {
        assertTrue(target.takeShort((short) 19991));
    }
    public void test_takeShortFast() {
        assertTrue(target.takeShortFast((short) 19991));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeChar() {
        assertTrue(target.takeChar((char) 999));
    }
    public void test_takeCharFast() {
        assertTrue(target.takeCharFast((char) 999));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeInt() {
        assertTrue(target.takeInt(-999888777));
    }
    public void test_takeIntFast() {
        assertTrue(target.takeIntFast(-999888777));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeLong() {
        assertTrue(target.takeLong(999888777666555444L));
    }
    public void test_takeLongFast() {
        assertTrue(target.takeLongFast(999888777666555444L));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeFloat() {
        assertTrue(target.takeFloat(-9988.7766F));
    }
    public void test_takeFloatFast() {
        assertTrue(target.takeFloatFast(-9988.7766F));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeDouble() {
        assertTrue(target.takeDouble(999888777.666555));
    }
    public void test_takeDoubleFast() {
        assertTrue(target.takeDoubleFast(999888777.666555));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeNull() {
        assertTrue(target.takeNull(null));
    }
    public void test_takeNullFast() {
        assertTrue(target.takeNullFast(null));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeString() {
        assertTrue(target.takeString("fuzzbot"));
    }
    public void test_takeStringFast() {
        assertTrue(target.takeStringFast("fuzzbot"));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value. In particular, this test passes the
     * instance the method is called on.
     */
    public void test_takeThis() {
        assertTrue(target.takeThis(target));
    }
    public void test_takeThisFast() {
        assertTrue(target.takeThisFast(target));
    }

    /**
     * Test a simple multiple value-taking method call, that returns whether it
     * got the expected values.
     */
    public void test_takeIntLong() {
        assertTrue(target.takeIntLong(914, 9140914091409140914L));
    }
    public void test_takeIntLongFast() {
        assertTrue(target.takeIntLongFast(914, 9140914091409140914L));
    }

    /**
     * Test a simple multiple value-taking method call, that returns whether it
     * got the expected values.
     */
    public void test_takeLongInt() {
        assertTrue(target.takeLongInt(-4321L, 12341234));
    }
    public void test_takeLongIntFast() {
        assertTrue(target.takeLongIntFast(-4321L, 12341234));
    }

    /**
     * Test a simple multiple value-taking method call, that returns whether it
     * got the expected values.
     *
     * The "Dlsym" versions use dynamic lookup instead of explicitly
     * registering the native method implementation.
     */
    public void test_takeOneOfEach() {
        assertTrue(target.takeOneOfEach((boolean) false, (byte) 1,
                        (short) 2, (char) 3, (int) 4, 5L, "six", 7.0f, 8.0,
                        new int[] { 9, 10 }));
    }
    public void test_takeOneOfEachDlsym() {
        assertTrue(target.takeOneOfEachDlsym((boolean) false, (byte) 1,
                        (short) 2, (char) 3, (int) 4, 5L, "six", 7.0f, 8.0,
                        new int[] { 9, 10 }));
    }
    public void test_takeOneOfEachFast() {
        assertTrue(target.takeOneOfEachFast((boolean) false, (byte) 1,
                        (short) 2, (char) 3, (int) 4, 5L, "six", 7.0f, 8.0,
                        new int[] { 9, 10 }));
    }
    public void test_takeOneOfEachFastDlsym() {
        assertTrue(target.takeOneOfEachFastDlsym((boolean) false, (byte) 1,
                        (short) 2, (char) 3, (int) 4, 5L, "six", 7.0f, 8.0,
                        new int[] { 9, 10 }));
    }

    /**
     * Test a simple multiple value-taking method call, that returns whether it
     * got the expected values.
     */
    public void test_takeCoolHandLuke() {
        assertTrue(target.takeCoolHandLuke(1, 2, 3, 4, 5, 6, 7, 8, 9,
                        10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
                        20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
                        30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
                        40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
                        50));
    }
    public void test_takeCoolHandLukeFast() {
        assertTrue(target.takeCoolHandLukeFast(1, 2, 3, 4, 5, 6, 7, 8, 9,
                        10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
                        20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
                        30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
                        40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
                        50));
    }
}
