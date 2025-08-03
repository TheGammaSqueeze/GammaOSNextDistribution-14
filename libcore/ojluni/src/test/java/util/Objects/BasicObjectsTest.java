/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6797535 6889858 6891113 8013712 8011800 8014365
 * @summary Basic tests for methods in java.util.Objects
 * @author  Joseph D. Darcy
 */
package test.java.util.Objects;

import java.util.*;
import java.util.function.*;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class BasicObjectsTest {

    @Test
    public void testEquals() {
        Object[] values = {null, "42", 42};
        for(int i = 0; i < values.length; i++) {
            for (int j = 0; j < values.length; j++) {
                boolean expected = (i == j);
                Object a = values[i];
                Object b = values[j];
                boolean result = Objects.equals(a, b);
                assertEquals(result, expected);
            }
        }
    }

    @Test
    public void testDeepEquals() {
        Object[] values = {null,
                           null, // Change to values later
                           new byte[]  {(byte)1},
                           new short[] {(short)1},
                           new int[]   {1},
                           new long[]  {1L},
                           new char[]  {(char)1},
                           new float[] {1.0f},
                           new double[]{1.0d},
                           new String[]{"one"}};
        values[1] = values;

        for(int i = 0; i < values.length; i++) {
            for (int j = 0; j < values.length; j++) {
                boolean expected = (i == j);
                Object a = values[i];
                Object b = values[j];
                boolean result = Objects.deepEquals(a, b);
                assertEquals(result, expected);
            }
        }
    }

    @Test
    public void testHashCode() {
        assertEquals(Objects.hashCode(null), 0);
        String s = "42";
        assertEquals(Objects.hashCode(s), s.hashCode());
    }

    @Test
    public void testHash() {
        Object[] data = new String[]{"perfect", "ham", "THC"};
        assertEquals(Objects.hash((Object[])null), 0);
        assertEquals(Objects.hash("perfect", "ham", "THC"), Arrays.hashCode(data));
    }

    @Test
    public void testToString() {
        assertEquals(Objects.toString(null), "null");
        String s = "Some string";
        assertEquals(Objects.toString(s), s);
    }

    @Test
    public void testToString2() {
        String s = "not the default";
        assertEquals(Objects.toString(null, s), s);
        assertEquals(Objects.toString(s, "another string"), s);
    }

    @Test
    public void testCompare() {
        String[] values = {"e. e. cummings", "zzz"};
        String[] VALUES = {"E. E. Cummings", "ZZZ"};
        compareTest(null, null, 0);
        for(int i = 0; i < values.length; i++) {
            String a = values[i];
            compareTest(a, a, 0);
            for(int j = 0; j < VALUES.length; j++) {
                int expected = Integer.compare(i, j);
                String b = VALUES[j];
                compareTest(a, b, expected);
            }
        }
    }

    private static void compareTest(String a, String b, int expected) {
        int result = Objects.compare(a, b, String.CASE_INSENSITIVE_ORDER);
        assertEquals(Integer.signum(result), Integer.signum(expected));
    }

    @Test
    public void testRequireNonNull() {
        final String RNN_1 = "1-arg requireNonNull";
        final String RNN_2 = "2-arg requireNonNull";
        final String RNN_3 = "Supplier requireNonNull";

        Function<String, String> rnn1 = s -> Objects.requireNonNull(s);
        Function<String, String> rnn2 = s -> Objects.requireNonNull(s, "trousers");
        Function<String, String> rnn3 = s -> Objects.requireNonNull(s, () -> "trousers");

        testRNN_NonNull(rnn1, RNN_1);
        testRNN_NonNull(rnn2, RNN_2);
        testRNN_NonNull(rnn3, RNN_3);

        testRNN_Null(rnn1, RNN_1, null);
        testRNN_Null(rnn2, RNN_2, "trousers");
        testRNN_Null(rnn3, RNN_3, "trousers");
    }

    private static void testRNN_NonNull(Function<String, String> testFunc,
                                       String testFuncName) {
        try {
            String s = testFunc.apply("pants");
            if (s != "pants") {
                fail(testFuncName + " failed to return its arg");
            }
        } catch (NullPointerException e) {
            fail(testFuncName + " threw unexpected NPE");
        }
    }

    private static void testRNN_Null(Function<String, String> testFunc,
                                    String testFuncName,
                                    String expectedMessage) {
        try {
            String s = testFunc.apply(null);
            fail(testFuncName + " failed to throw NPE");
        } catch (NullPointerException e) {
            assertEquals(e.getMessage(), expectedMessage);
        }
    }

    @Test
    public void testIsNull() {
        assertTrue(Objects.isNull(null));
        assertFalse(Objects.isNull(Objects.class));
    }

    @Test
    public void testNonNull() {
        assertFalse(Objects.nonNull(null));
        assertTrue(Objects.nonNull(Objects.class));
    }

    @Test
    public void testNonNullOf() {
        String defString = new String("default");
        String nullString = null;
        String nonNullString = "non-null";

        // Confirm the compile time return type matches
        String result = Objects.requireNonNullElse(nullString, defString);

        if (result != defString) {
            fail("comparison: references are not equal");
        }
        if (Objects.requireNonNullElse(nonNullString, defString) != nonNullString) {
            fail("comparison: Objects.requireNonNullElse(..., default)");
        }
        if (Objects.requireNonNullElse(nonNullString, null) != nonNullString) {
            fail("comparison: Objects.requireNonNullElse(..., null)");
        }
        try {
            Objects.requireNonNullElse(null, null);
            fail("Unexpectedly didn't throw NPE");
        } catch (NullPointerException npe) {
            // expected
            assertEquals(npe.getMessage(), "defaultObj");
        }


        // Test requireNonNullElseGet with a supplier
        if (Objects.requireNonNullElseGet(nullString, () -> defString) != defString) {
            fail("supplier: Objects.requireNonNullElseGet(nullString, () -> defString))");
        }
        if (Objects.requireNonNullElseGet(nonNullString, () -> defString) != nonNullString) {
            fail("supplier: Objects.requireNonNullElseGet(nonNullString, () -> defString))");
        }
        if (Objects.requireNonNullElseGet(nonNullString, () -> null) != nonNullString) {
            fail("Objects.requireNonNullElseGet(nonNullString, () -> null))");
        }

        try {
            Objects.requireNonNullElseGet(null, () -> null);
            fail("Unexpectedly didn't throw NPE");
        } catch (NullPointerException npe) {
            // expected
            assertEquals(npe.getMessage(), "supplier.get()");
        }
        try {       // supplier is null
            Objects.requireNonNullElseGet(null, null);
            fail("Unexpectedly didn't throw NPE");
        } catch (NullPointerException npe) {
            // expected
            assertEquals(npe.getMessage(), "supplier");
        }
    }
}
