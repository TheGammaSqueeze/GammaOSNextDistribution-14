/*
 * Copyright 2019 The Android Open Source Project
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

public class Main {
    public static void main(String[] args) {
        testAppendStringAndLong();
        testAppendStringAndInt();
        testAppendStringAndFloat();
        testAppendStringAndDouble();
        testAppendDoubleAndFloat();
        testAppendStringAndString();
        testMiscelaneous();
        testNoArgs();
        testInline();
        testEquals();
        System.out.println("passed");
    }

    private static final String APPEND_LONG_PREFIX = "Long/";
    private static final String[] APPEND_LONG_TEST_CASES = {
        "Long/0",
        "Long/1",
        "Long/9",
        "Long/10",
        "Long/99",
        "Long/100",
        "Long/999",
        "Long/1000",
        "Long/9999",
        "Long/10000",
        "Long/99999",
        "Long/100000",
        "Long/999999",
        "Long/1000000",
        "Long/9999999",
        "Long/10000000",
        "Long/99999999",
        "Long/100000000",
        "Long/999999999",
        "Long/1000000000",
        "Long/9999999999",
        "Long/10000000000",
        "Long/99999999999",
        "Long/100000000000",
        "Long/999999999999",
        "Long/1000000000000",
        "Long/9999999999999",
        "Long/10000000000000",
        "Long/99999999999999",
        "Long/100000000000000",
        "Long/999999999999999",
        "Long/1000000000000000",
        "Long/9999999999999999",
        "Long/10000000000000000",
        "Long/99999999999999999",
        "Long/100000000000000000",
        "Long/999999999999999999",
        "Long/1000000000000000000",
        "Long/9223372036854775807",  // Long.MAX_VALUE
        "Long/-1",
        "Long/-9",
        "Long/-10",
        "Long/-99",
        "Long/-100",
        "Long/-999",
        "Long/-1000",
        "Long/-9999",
        "Long/-10000",
        "Long/-99999",
        "Long/-100000",
        "Long/-999999",
        "Long/-1000000",
        "Long/-9999999",
        "Long/-10000000",
        "Long/-99999999",
        "Long/-100000000",
        "Long/-999999999",
        "Long/-1000000000",
        "Long/-9999999999",
        "Long/-10000000000",
        "Long/-99999999999",
        "Long/-100000000000",
        "Long/-999999999999",
        "Long/-1000000000000",
        "Long/-9999999999999",
        "Long/-10000000000000",
        "Long/-99999999999999",
        "Long/-100000000000000",
        "Long/-999999999999999",
        "Long/-1000000000000000",
        "Long/-9999999999999999",
        "Long/-10000000000000000",
        "Long/-99999999999999999",
        "Long/-100000000000000000",
        "Long/-999999999999999999",
        "Long/-1000000000000000000",
        "Long/-9223372036854775808",  // Long.MIN_VALUE
    };

    /// CHECK-START: java.lang.String Main.$noinline$appendStringAndLong(java.lang.String, long) instruction_simplifier (before)
    /// CHECK-NOT:              StringBuilderAppend

    /// CHECK-START: java.lang.String Main.$noinline$appendStringAndLong(java.lang.String, long) instruction_simplifier (after)
    /// CHECK:                  StringBuilderAppend
    public static String $noinline$appendStringAndLong(String s, long l) {
        return new StringBuilder().append(s).append(l).toString();
    }

    public static void testAppendStringAndLong() {
        for (String expected : APPEND_LONG_TEST_CASES) {
            long l = Long.valueOf(expected.substring(APPEND_LONG_PREFIX.length()));
            String result = $noinline$appendStringAndLong(APPEND_LONG_PREFIX, l);
            assertEquals(expected, result);
        }
    }

    private static final String APPEND_INT_PREFIX = "Int/";
    private static final String[] APPEND_INT_TEST_CASES = {
        "Int/0",
        "Int/1",
        "Int/9",
        "Int/10",
        "Int/99",
        "Int/100",
        "Int/999",
        "Int/1000",
        "Int/9999",
        "Int/10000",
        "Int/99999",
        "Int/100000",
        "Int/999999",
        "Int/1000000",
        "Int/9999999",
        "Int/10000000",
        "Int/99999999",
        "Int/100000000",
        "Int/999999999",
        "Int/1000000000",
        "Int/2147483647",  // Integer.MAX_VALUE
        "Int/-1",
        "Int/-9",
        "Int/-10",
        "Int/-99",
        "Int/-100",
        "Int/-999",
        "Int/-1000",
        "Int/-9999",
        "Int/-10000",
        "Int/-99999",
        "Int/-100000",
        "Int/-999999",
        "Int/-1000000",
        "Int/-9999999",
        "Int/-10000000",
        "Int/-99999999",
        "Int/-100000000",
        "Int/-999999999",
        "Int/-1000000000",
        "Int/-2147483648",  // Integer.MIN_VALUE
    };

    /// CHECK-START: java.lang.String Main.$noinline$appendStringAndInt(java.lang.String, int) instruction_simplifier (before)
    /// CHECK-NOT:              StringBuilderAppend

    /// CHECK-START: java.lang.String Main.$noinline$appendStringAndInt(java.lang.String, int) instruction_simplifier (after)
    /// CHECK:                  StringBuilderAppend
    public static String $noinline$appendStringAndInt(String s, int i) {
        return new StringBuilder().append(s).append(i).toString();
    }

    public static void testAppendStringAndInt() {
        for (String expected : APPEND_INT_TEST_CASES) {
            int i = Integer.valueOf(expected.substring(APPEND_INT_PREFIX.length()));
            String result = $noinline$appendStringAndInt(APPEND_INT_PREFIX, i);
            assertEquals(expected, result);
        }
    }

    private static final String APPEND_FLOAT_PREFIX = "Float/";
    private static final String[] APPEND_FLOAT_TEST_CASES = {
        // We're testing only exact values here, i.e. values that do not require rounding.
        "Float/1.0",
        "Float/9.0",
        "Float/10.0",
        "Float/99.0",
        "Float/100.0",
        "Float/999.0",
        "Float/1000.0",
        "Float/9999.0",
        "Float/10000.0",
        "Float/99999.0",
        "Float/100000.0",
        "Float/999999.0",
        "Float/1000000.0",
        "Float/9999999.0",
        "Float/1.0E7",
        "Float/1.0E10",
        "Float/-1.0",
        "Float/-9.0",
        "Float/-10.0",
        "Float/-99.0",
        "Float/-100.0",
        "Float/-999.0",
        "Float/-1000.0",
        "Float/-9999.0",
        "Float/-10000.0",
        "Float/-99999.0",
        "Float/-100000.0",
        "Float/-999999.0",
        "Float/-1000000.0",
        "Float/-9999999.0",
        "Float/-1.0E7",
        "Float/-1.0E10",
        "Float/0.25",
        "Float/1.625",
        "Float/9.3125",
        "Float/-0.25",
        "Float/-1.625",
        "Float/-9.3125",
    };

    /// CHECK-START: java.lang.String Main.$noinline$appendStringAndFloat(java.lang.String, float) instruction_simplifier (before)
    /// CHECK-NOT:              StringBuilderAppend

    /// CHECK-START: java.lang.String Main.$noinline$appendStringAndFloat(java.lang.String, float) instruction_simplifier (after)
    /// CHECK:                  StringBuilderAppend
    public static String $noinline$appendStringAndFloat(String s, float f) {
        return new StringBuilder().append(s).append(f).toString();
    }

    public static void testAppendStringAndFloat() {
        for (String expected : APPEND_FLOAT_TEST_CASES) {
            float f = Float.valueOf(expected.substring(APPEND_FLOAT_PREFIX.length()));
            String result = $noinline$appendStringAndFloat(APPEND_FLOAT_PREFIX, f);
            assertEquals(expected, result);
        }
        // Special values.
        assertEquals("Float/NaN", $noinline$appendStringAndFloat(APPEND_FLOAT_PREFIX, Float.NaN));
        assertEquals("Float/Infinity",
                     $noinline$appendStringAndFloat(APPEND_FLOAT_PREFIX, Float.POSITIVE_INFINITY));
        assertEquals("Float/-Infinity",
                     $noinline$appendStringAndFloat(APPEND_FLOAT_PREFIX, Float.NEGATIVE_INFINITY));
    }

    private static final String APPEND_DOUBLE_PREFIX = "Double/";
    private static final String[] APPEND_DOUBLE_TEST_CASES = {
        // We're testing only exact values here, i.e. values that do not require rounding.
        "Double/1.0",
        "Double/9.0",
        "Double/10.0",
        "Double/99.0",
        "Double/100.0",
        "Double/999.0",
        "Double/1000.0",
        "Double/9999.0",
        "Double/10000.0",
        "Double/99999.0",
        "Double/100000.0",
        "Double/999999.0",
        "Double/1000000.0",
        "Double/9999999.0",
        "Double/1.0E7",
        "Double/1.0E24",
        "Double/-1.0",
        "Double/-9.0",
        "Double/-10.0",
        "Double/-99.0",
        "Double/-100.0",
        "Double/-999.0",
        "Double/-1000.0",
        "Double/-9999.0",
        "Double/-10000.0",
        "Double/-99999.0",
        "Double/-100000.0",
        "Double/-999999.0",
        "Double/-1000000.0",
        "Double/-9999999.0",
        "Double/-1.0E7",
        "Double/-1.0E24",
        "Double/0.25",
        "Double/1.625",
        "Double/9.3125",
        "Double/-0.25",
        "Double/-1.625",
        "Double/-9.3125",
    };

    /// CHECK-START: java.lang.String Main.$noinline$appendStringAndDouble(java.lang.String, double) instruction_simplifier (before)
    /// CHECK-NOT:              StringBuilderAppend

    /// CHECK-START: java.lang.String Main.$noinline$appendStringAndDouble(java.lang.String, double) instruction_simplifier (after)
    /// CHECK:                  StringBuilderAppend
    public static String $noinline$appendStringAndDouble(String s, double d) {
        return new StringBuilder().append(s).append(d).toString();
    }

    public static void testAppendStringAndDouble() {
        for (String expected : APPEND_DOUBLE_TEST_CASES) {
            double f = Double.valueOf(expected.substring(APPEND_DOUBLE_PREFIX.length()));
            String result = $noinline$appendStringAndDouble(APPEND_DOUBLE_PREFIX, f);
            assertEquals(expected, result);
        }
        // Special values.
        assertEquals(
            "Double/NaN",
            $noinline$appendStringAndDouble(APPEND_DOUBLE_PREFIX, Double.NaN));
        assertEquals(
            "Double/Infinity",
            $noinline$appendStringAndDouble(APPEND_DOUBLE_PREFIX, Double.POSITIVE_INFINITY));
        assertEquals(
            "Double/-Infinity",
            $noinline$appendStringAndDouble(APPEND_DOUBLE_PREFIX, Double.NEGATIVE_INFINITY));
    }

    /// CHECK-START: java.lang.String Main.$noinline$appendDoubleAndFloat(double, float) instruction_simplifier (before)
    /// CHECK-NOT:              StringBuilderAppend

    /// CHECK-START: java.lang.String Main.$noinline$appendDoubleAndFloat(double, float) instruction_simplifier (after)
    /// CHECK:                  StringBuilderAppend
    public static String $noinline$appendDoubleAndFloat(double d, float f) {
        return new StringBuilder().append(d).append(f).toString();
    }

    public static void testAppendDoubleAndFloat() {
        assertEquals("1.50.325", $noinline$appendDoubleAndFloat(1.5, 0.325f));
        assertEquals("1.5E170.3125", $noinline$appendDoubleAndFloat(1.5E17, 0.3125f));
        assertEquals("1.0E8NaN", $noinline$appendDoubleAndFloat(1.0E8, Float.NaN));
        assertEquals("Infinity0.5", $noinline$appendDoubleAndFloat(Double.POSITIVE_INFINITY, 0.5f));
        assertEquals("2.5-Infinity", $noinline$appendDoubleAndFloat(2.5, Float.NEGATIVE_INFINITY));
    }

    public static String $noinline$appendStringAndString(String s1, String s2) {
        return new StringBuilder().append(s1).append(s2).toString();
    }

    public static void testAppendStringAndString() {
        assertEquals("nullnull", $noinline$appendStringAndString(null, null));
        assertEquals("nullTEST", $noinline$appendStringAndString(null, "TEST"));
        assertEquals("TESTnull", $noinline$appendStringAndString("TEST", null));
        assertEquals("abcDEFGH", $noinline$appendStringAndString("abc", "DEFGH"));
        // Test with a non-ASCII character.
        assertEquals("test\u0131", $noinline$appendStringAndString("test", "\u0131"));
        assertEquals("\u0131test", $noinline$appendStringAndString("\u0131", "test"));
        assertEquals("\u0131test\u0131", $noinline$appendStringAndString("\u0131", "test\u0131"));
    }

    /// CHECK-START: java.lang.String Main.$noinline$appendSLILC(java.lang.String, long, int, long, char) instruction_simplifier (before)
    /// CHECK-NOT:              StringBuilderAppend

    /// CHECK-START: java.lang.String Main.$noinline$appendSLILC(java.lang.String, long, int, long, char) instruction_simplifier (after)
    /// CHECK:                  StringBuilderAppend
    public static String $noinline$appendSLILC(String s,
                                               long l1,
                                               int i,
                                               long l2,
                                               char c) {
        return new StringBuilder().append(s)
                                  .append(l1)
                                  .append(i)
                                  .append(l2)
                                  .append(c).toString();
    }

    public static void testMiscelaneous() {
        assertEquals("x17-1q",
                     $noinline$appendSLILC("x", 1L, 7, -1L, 'q'));
        assertEquals("null17-1q",
                     $noinline$appendSLILC(null, 1L, 7, -1L, 'q'));
        assertEquals("x\u013117-1q",
                     $noinline$appendSLILC("x\u0131", 1L, 7, -1L, 'q'));
        assertEquals("x427-1q",
                     $noinline$appendSLILC("x", 42L, 7, -1L, 'q'));
        assertEquals("x1-42-1q",
                     $noinline$appendSLILC("x", 1L, -42, -1L, 'q'));
        assertEquals("x17424242q",
                     $noinline$appendSLILC("x", 1L, 7, 424242L, 'q'));
        assertEquals("x17-1\u0131",
                     $noinline$appendSLILC("x", 1L, 7, -1L, '\u0131'));
    }

    public static String $inline$testInlineInner(StringBuilder sb, String s, int i) {
        return sb.append(s).append(i).toString();
    }

    /// CHECK-START: java.lang.String Main.$noinline$testInlineOuter(java.lang.String, int) instruction_simplifier$after_inlining (before)
    /// CHECK-NOT:              StringBuilderAppend

    /// CHECK-START: java.lang.String Main.$noinline$testInlineOuter(java.lang.String, int) instruction_simplifier$after_inlining (after)
    /// CHECK:                  StringBuilderAppend
    public static String $noinline$testInlineOuter(String s, int i) {
        StringBuilder sb = new StringBuilder();
        return $inline$testInlineInner(sb, s, i);
    }

    public static void testInline() {
        assertEquals("x42", $noinline$testInlineOuter("x", 42));
    }

    /// CHECK-START: java.lang.String Main.$noinline$appendNothing() instruction_simplifier (before)
    /// CHECK-NOT:              StringBuilderAppend

    /// CHECK-START: java.lang.String Main.$noinline$appendNothing() instruction_simplifier (after)
    /// CHECK-NOT:              StringBuilderAppend
    public static String $noinline$appendNothing() {
        return new StringBuilder().toString();
    }

    public static void testNoArgs() {
        assertEquals("", $noinline$appendNothing());
    }

    /// CHECK-START: boolean Main.$noinline$testAppendEquals(java.lang.String, int) instruction_simplifier (before)
    /// CHECK-NOT:              StringBuilderAppend

    /// CHECK-START: boolean Main.$noinline$testAppendEquals(java.lang.String, int) instruction_simplifier (after)
    /// CHECK:                  StringBuilderAppend
    public static boolean $noinline$testAppendEquals(String s, int i) {
      // Regression test for b/151107293 .
      // When a string is used as both receiver and argument of String.equals(), we DCHECK()
      // that it cannot be null. However, when replacing the call to StringBuilder.toString()
      // with the HStringBuilderAppend(), the former reported CanBeNull() as false and
      // therefore no explicit null checks were needed, but the replacement reported
      // CanBeNull() as true, so when the result was used in String.equals() for both
      // receiver and argument, the DCHECK() failed. This was fixed by overriding
      // CanBeNull() in HStringBuilderAppend to correctly return false; the string that
      // previously didn't require null check still does not require it.
      String str = new StringBuilder().append(s).append(i).toString();
      return str.equals(str);
    }

    public static void testEquals() {
      if (!$noinline$testAppendEquals("Test", 42)) {
        throw new Error("str.equals(str) is false");
      }
    }

    public static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }
}
