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

import android.os.Build;
import android.os.Process;

import com.android.compatibility.common.util.PropertyUtil;

import java.io.File;
import java.io.IOException;

/**
 * Basic static method tests. The "nonce" class being tested by this
 * class is a class defined in this package that declares the bulk of
 * its methods as native.
 */
public class JniStaticTest extends JniTestCase {

    static {
        if (!JniTestCase.isCpuAbiNone()) {
            System.loadLibrary("jnitest");
        }
    }

    /**
     * Test library accessibility. Internal platform libraries should not
     * be accessible from the jni code.
     */
    public void test_linker_namespaces() throws IOException {
        String error = LinkerNamespacesHelper.runAccessibilityTest();
        if (error != null) {
            fail(error);
        }
    }

    public void test_linker_namespaces_classloaders() throws Exception {
        String error = LinkerNamespacesHelper.runClassLoaderNamespaces();
        if (error != null) {
            fail(error);
        }
    }

    public void test_loader_basic() throws Exception {
        String error = BasicLoaderTestHelper.nativeRunTests();
        if (error != null) {
            fail(error);
        }
    }

    /**
     * Test that accessing classes true JNI works as expected. b/19382130
     */
    public void test_classload() {
        // Use an independent class to do this.
        assertEquals(true, ClassLoaderHelper.run());
    }

    /**
     * Test native method call without implementation.
     */
    public void test_missing() {
        try {
            StaticNonce.missing();
            throw new Error("Unreachable");
        } catch (UnsatisfiedLinkError expected) {
        }
    }
    public void test_missingFast() {
        try {
            StaticNonce.missingFast();
            throw new Error("Unreachable");
        } catch (UnsatisfiedLinkError expected) {
        }
    }
    public void test_missingCritical() {
        try {
            StaticNonce.missingCritical();
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
        StaticNonce.nop();
    }
    public void test_nopDlsym() {
        StaticNonce.nopDlsym();
    }
    public void test_nopFast() {
        StaticNonce.nopFast();
    }
    public void test_nopFastDlsym() {
        StaticNonce.nopFastDlsym();
    }
    public void test_nopCritical() {
        StaticNonce.nopCritical();
    }
    public void test_nopCriticalDlsym() {
        StaticNonce.nopCriticalDlsym();
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnBoolean() {
        assertEquals(true, StaticNonce.returnBoolean());
    }
    public void test_returnBooleanFast() {
        assertEquals(true, StaticNonce.returnBooleanFast());
    }
    public void test_returnBooleanCritical() {
        assertEquals(true, StaticNonce.returnBooleanCritical());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnByte() {
        assertEquals(123, StaticNonce.returnByte());
    }
    public void test_returnByteFast() {
        assertEquals(123, StaticNonce.returnByteFast());
    }
    public void test_returnByteCritical() {
        assertEquals(123, StaticNonce.returnByteCritical());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnShort() {
        assertEquals(-12345, StaticNonce.returnShort());
    }
    public void test_returnShortFast() {
        assertEquals(-12345, StaticNonce.returnShortFast());
    }
    public void test_returnShortCritical() {
        assertEquals(-12345, StaticNonce.returnShortCritical());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnChar() {
        assertEquals(34567, StaticNonce.returnChar());
    }
    public void test_returnCharFast() {
        assertEquals(34567, StaticNonce.returnCharFast());
    }
    public void test_returnCharCritical() {
        assertEquals(34567, StaticNonce.returnCharCritical());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnInt() {
        assertEquals(12345678, StaticNonce.returnInt());
    }
    public void test_returnIntFast() {
        assertEquals(12345678, StaticNonce.returnIntFast());
    }
    public void test_returnIntCritical() {
        assertEquals(12345678, StaticNonce.returnIntCritical());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnLong() {
        assertEquals(-1098765432109876543L, StaticNonce.returnLong());
    }
    public void test_returnLongFast() {
        assertEquals(-1098765432109876543L, StaticNonce.returnLongFast());
    }
    public void test_returnLongCritical() {
        assertEquals(-1098765432109876543L, StaticNonce.returnLongCritical());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnFloat() {
        assertEquals(-98765.4321F, StaticNonce.returnFloat());
    }
    public void test_returnFloatFast() {
        assertEquals(-98765.4321F, StaticNonce.returnFloatFast());
    }
    public void test_returnFloatCritical() {
        assertEquals(-98765.4321F, StaticNonce.returnFloatCritical());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnDouble() {
        assertEquals(12345678.9, StaticNonce.returnDouble());
    }
    public void test_returnDoubleFast() {
        assertEquals(12345678.9, StaticNonce.returnDoubleFast());
    }
    public void test_returnDoubleCritical() {
        assertEquals(12345678.9, StaticNonce.returnDoubleCritical());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnNull() {
        assertNull(StaticNonce.returnNull());
    }
    public void test_returnNullFast() {
        assertNull(StaticNonce.returnNullFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnString() {
        assertEquals("blort", StaticNonce.returnString());
    }
    public void test_returnStringFast() {
        assertEquals("blort", StaticNonce.returnStringFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call.
     */
    public void test_returnShortArray() {
        checkShortArray(StaticNonce.returnShortArray());
    }
    public void test_returnShortArrayFast() {
        checkShortArray(StaticNonce.returnShortArrayFast());
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
        checkStringArray(StaticNonce.returnStringArray());
    }
    public void test_returnStringArrayFast() {
        checkStringArray(StaticNonce.returnStringArrayFast());
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
     * that returns the class that the method is defined on.
     */
    public void test_returnThisClass() {
        assertSame(StaticNonce.class, StaticNonce.returnThisClass());
    }
    public void test_returnThisClassFast() {
        assertSame(StaticNonce.class, StaticNonce.returnThisClassFast());
    }

    /**
     * Test a simple value-returning (but otherwise no-op) method call,
     * that returns the class that the method is defined on.
     */
    public void test_returnInstance() {
        StaticNonce nonce = StaticNonce.returnInstance();
        assertSame(StaticNonce.class, nonce.getClass());
    }
    public void test_returnInstanceFast() {
        StaticNonce nonce = StaticNonce.returnInstanceFast();
        assertSame(StaticNonce.class, nonce.getClass());
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeBoolean() {
        assertTrue(StaticNonce.takeBoolean(true));
    }
    public void test_takeBooleanFast() {
        assertTrue(StaticNonce.takeBooleanFast(true));
    }
    public void test_takeBooleanCritical() {
        assertTrue(StaticNonce.takeBooleanCritical(true));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeByte() {
        assertTrue(StaticNonce.takeByte((byte) -99));
    }
    public void test_takeByteFast() {
        assertTrue(StaticNonce.takeByteFast((byte) -99));
    }
    public void test_takeByteCritical() {
        assertTrue(StaticNonce.takeByteCritical((byte) -99));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeShort() {
        assertTrue(StaticNonce.takeShort((short) 19991));
    }
    public void test_takeShortFast() {
        assertTrue(StaticNonce.takeShortFast((short) 19991));
    }
    public void test_takeShortCritical() {
        assertTrue(StaticNonce.takeShortCritical((short) 19991));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeChar() {
        assertTrue(StaticNonce.takeChar((char) 999));
    }
    public void test_takeCharFast() {
        assertTrue(StaticNonce.takeCharFast((char) 999));
    }
    public void test_takeCharCritical() {
        assertTrue(StaticNonce.takeCharCritical((char) 999));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeInt() {
        assertTrue(StaticNonce.takeInt(-999888777));
    }
    public void test_takeIntFast() {
        assertTrue(StaticNonce.takeIntFast(-999888777));
    }
    public void test_takeIntCritical() {
        assertTrue(StaticNonce.takeIntCritical(-999888777));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeLong() {
        assertTrue(StaticNonce.takeLong(999888777666555444L));
    }
    public void test_takeLongFast() {
        assertTrue(StaticNonce.takeLongFast(999888777666555444L));
    }
    public void test_takeLongCritical() {
        assertTrue(StaticNonce.takeLongCritical(999888777666555444L));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeFloat() {
        assertTrue(StaticNonce.takeFloat(-9988.7766F));
    }
    public void test_takeFloatFast() {
        assertTrue(StaticNonce.takeFloatFast(-9988.7766F));
    }
    public void test_takeFloatCritical() {
        assertTrue(StaticNonce.takeFloatCritical(-9988.7766F));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeDouble() {
        assertTrue(StaticNonce.takeDouble(999888777.666555));
    }
    public void test_takeDoubleFast() {
        assertTrue(StaticNonce.takeDoubleFast(999888777.666555));
    }
    public void test_takeDoubleCritical() {
        assertTrue(StaticNonce.takeDoubleCritical(999888777.666555));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeNull() {
        assertTrue(StaticNonce.takeNull(null));
    }
    public void test_takeNullFast() {
        assertTrue(StaticNonce.takeNullFast(null));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value.
     */
    public void test_takeString() {
        assertTrue(StaticNonce.takeString("fuzzbot"));
    }
    public void test_takeStringFast() {
        assertTrue(StaticNonce.takeStringFast("fuzzbot"));
    }

    /**
     * Test a simple value-taking method call, that returns whether it
     * got the expected value. In particular, this test passes the
     * class the method is defined on.
     */
    public void test_takeThisClass() {
        assertTrue(StaticNonce.takeThisClass(StaticNonce.class));
    }
    public void test_takeThisClassFast() {
        assertTrue(StaticNonce.takeThisClassFast(StaticNonce.class));
    }

    /**
     * Test a simple multiple value-taking method call, that returns whether it
     * got the expected values.
     */
    public void test_takeIntLong() {
        assertTrue(StaticNonce.takeIntLong(914, 9140914091409140914L));
    }
    public void test_takeIntLongFast() {
        assertTrue(StaticNonce.takeIntLongFast(914, 9140914091409140914L));
    }
    public void test_takeIntLongCritical() {
        assertTrue(StaticNonce.takeIntLongCritical(914, 9140914091409140914L));
    }

    /**
     * Test a simple multiple value-taking method call, that returns whether it
     * got the expected values.
     */
    public void test_takeLongInt() {
        assertTrue(StaticNonce.takeLongInt(-4321L, 12341234));
    }
    public void test_takeLongIntFast() {
        assertTrue(StaticNonce.takeLongIntFast(-4321L, 12341234));
    }
    public void test_takeLongIntCritical() {
        assertTrue(StaticNonce.takeLongIntCritical(-4321L, 12341234));
    }

    /**
     * Test a simple multiple value-taking method call, that returns whether it
     * got the expected values.
     *
     * The "Dlsym" versions use dynamic lookup instead of explicitly
     * registering the native method implementation.
     */
    public void test_takeOneOfEach() {
        assertTrue(StaticNonce.takeOneOfEach((boolean) false, (byte) 1,
                        (short) 2, (char) 3, (int) 4, 5L, "six", 7.0f, 8.0,
                        new int[] { 9, 10 }));
    }
    public void test_takeOneOfEachDlsym() {
        assertTrue(StaticNonce.takeOneOfEachDlsym((boolean) false,
                        (byte) 1, (short) 2, (char) 3, (int) 4, 5L, "six",
                        7.0f, 8.0, new int[] { 9, 10 }));
    }
    public void test_takeOneOfEachFast() {
        assertTrue(StaticNonce.takeOneOfEachFast((boolean) false, (byte) 1,
                        (short) 2, (char) 3, (int) 4, 5L, "six", 7.0f, 8.0,
                        new int[] { 9, 10 }));
    }
    public void test_takeOneOfEachFastDlsym() {
        assertTrue(StaticNonce.takeOneOfEachFastDlsym((boolean) false,
                        (byte) 1, (short) 2, (char) 3, (int) 4, 5L, "six",
                        7.0f, 8.0, new int[] { 9, 10 }));
    }
    public void test_takeOneOfEachCritical() {
        assertTrue(StaticNonce.takeOneOfEachCritical((boolean) false, (byte) 1,
                        (short) 2, (char) 3, (int) 4, 5L, 6.0f, 7.0));
    }
    public void test_takeOneOfEachCriticalDlsym() {
        assertTrue(StaticNonce.takeOneOfEachCriticalDlsym((boolean) false,
                        (byte) 1, (short) 2, (char) 3, (int) 4, 5L, 6.0f,
                        7.0));
    }

    /**
     * Test a simple multiple value-taking method call, that returns whether it
     * got the expected values.
     */
    public void test_takeCoolHandLuke() {
        assertTrue(StaticNonce.takeCoolHandLuke(1, 2, 3, 4, 5, 6, 7, 8, 9,
                        10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
                        20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
                        30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
                        40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
                        50));
    }
    public void test_takeCoolHandLukeFast() {
        assertTrue(StaticNonce.takeCoolHandLukeFast(1, 2, 3, 4, 5, 6, 7, 8, 9,
                        10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
                        20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
                        30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
                        40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
                        50));
    }
    public void test_takeCoolHandLukeCritical() {
        assertTrue(StaticNonce.takeCoolHandLukeCritical(
                        1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                        11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                        21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
                        31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                        41, 42, 43, 44, 45, 46, 47, 48, 49, 50));
    }

    public void test_takeCoolHandLukeWithFloatsCritical() {
        assertTrue(StaticNonce.takeCoolHandLukeWithFloatsCritical(
                        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14));
    }

    /**
     * dlopen(3) any of the public lib via file name (non-absolute path) should succeed.
     */
    public void test_dlopenPublicLibraries() {
        if (PropertyUtil.isVendorApiLevelAtLeast(Build.VERSION_CODES.R)) {
            String error = LinkerNamespacesHelper.runDlopenPublicLibraries();
            if (error != null) {
                fail(error);
            }
        }
    }

    /**
     * If ICU4C native libraries, i.e. libicuuc.so and libicui18n.so, have been moved into APEX,
     * app with targetSdkVersion < Q can still dlopen the /system/{LIB}/libicuuc.so even though
     * the file does not exist in the file system. It's done by a redirect in linker.
     * http://b/121248172
     *
     * This test ensures that dlopen fail with a target version SDK of Q or above.
     */
    public void test_dlopenIcu4cInSystemShouldFail() {
        File systemBaseDir = new File("/system/lib" + (Process.is64Bit() ? "64" : ""));
        String[] libs = new String[] { "libicuuc.so", "libicui18n.so"};

        for (String lib : libs) {
            File f = new File(systemBaseDir, lib);
            assertFalse("The same native library should exist in the Runtime APEX."
                + " It should not exist in /system: " + f , f.exists());
            String error = LinkerNamespacesHelper.tryDlopen(f.toString());
            assertNotNull("The native library file does not exist in the file system, "
                + "but dlopen(" + f + ", RTLD_NOW) succeeds.", error);
        }
    }
}
