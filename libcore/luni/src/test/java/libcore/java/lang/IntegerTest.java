/*
 * Copyright (C) 2011 The Android Open Source Project
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

package libcore.java.lang;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Properties;

@RunWith(JUnit4.class)
public class IntegerTest {
    static final int[] INT_VALUES = {0, 1, 23, 456, 0x7fff_ffff, 0x8000_0000, 0xffff_ffff};

    static final long[] LONG_VALUES = {
        0x1_0000_0000L, (long) Integer.MIN_VALUE - 1L, Long.MIN_VALUE, Long.MAX_VALUE
    };

    @Test
    public void testSystemProperties() {
        Properties originalProperties = System.getProperties();
        try {
            Properties testProperties = new Properties();
            testProperties.put("testIncInt", "notInt");
            System.setProperties(testProperties);
            assertNull("returned incorrect default Integer", Integer.getInteger("testIncInt"));
            assertEquals(new Integer(4), Integer.getInteger("testIncInt", 4));
            assertEquals(new Integer(4), Integer.getInteger("testIncInt", new Integer(4)));
        } finally {
            System.setProperties(originalProperties);
        }
    }

    @Test
    public void testCompare() throws Exception {
        final int min = Integer.MIN_VALUE;
        final int zero = 0;
        final int max = Integer.MAX_VALUE;
        assertTrue(Integer.compare(max, max) == 0);
        assertTrue(Integer.compare(min, min) == 0);
        assertTrue(Integer.compare(zero, zero) == 0);
        assertTrue(Integer.compare(max, zero) > 0);
        assertTrue(Integer.compare(max, min) > 0);
        assertTrue(Integer.compare(zero, max) < 0);
        assertTrue(Integer.compare(zero, min) > 0);
        assertTrue(Integer.compare(min, zero) < 0);
        assertTrue(Integer.compare(min, max) < 0);
    }

    @Test
    public void testParseInt() throws Exception {
        assertEquals(0, Integer.parseInt("+0", 10));
        assertEquals(473, Integer.parseInt("+473", 10));
        assertEquals(255, Integer.parseInt("+FF", 16));
        assertEquals(102, Integer.parseInt("+1100110", 2));
        assertEquals(2147483647, Integer.parseInt("+2147483647", 10));
        assertEquals(411787, Integer.parseInt("Kona", 27));
        assertEquals(411787, Integer.parseInt("+Kona", 27));
        assertEquals(-145, Integer.parseInt("-145", 10));

        // multiple sign chars
        assertThrows(NumberFormatException.class, () -> Integer.parseInt("--1", 10));
        assertThrows(NumberFormatException.class, () -> Integer.parseInt("++1", 10));

        // base too small
        assertThrows(NumberFormatException.class, () -> Integer.parseInt("Kona", 10));
    }

    @Test
    public void testDecodeInt() throws Exception {
        assertEquals(0, Integer.decode("+0").intValue());
        assertEquals(473, Integer.decode("+473").intValue());
        assertEquals(255, Integer.decode("+0xFF").intValue());
        assertEquals(16, Integer.decode("+020").intValue());
        assertEquals(2147483647, Integer.decode("+2147483647").intValue());
        assertEquals(-73, Integer.decode("-73").intValue());
        assertEquals(-255, Integer.decode("-0xFF").intValue());
        assertEquals(255, Integer.decode("+#FF").intValue());
        assertEquals(-255, Integer.decode("-#FF").intValue());

        assertThrows(NumberFormatException.class, () -> Integer.decode("--1"));
        assertThrows(NumberFormatException.class, () -> Integer.decode("++1"));
        assertThrows(NumberFormatException.class, () -> Integer.decode("-+1"));
        assertThrows(NumberFormatException.class, () -> Integer.decode("Kona"));
    }

    /*
    public void testParsePositiveInt() throws Exception {
      assertEquals(0, Integer.parsePositiveInt("0", 10));
      assertEquals(473, Integer.parsePositiveInt("473", 10));
      assertEquals(255, Integer.parsePositiveInt("FF", 16));

      try {
        Integer.parsePositiveInt("-1", 10);
        fail();
      } catch (NumberFormatException e) {}

      try {
        Integer.parsePositiveInt("+1", 10);
        fail();
      } catch (NumberFormatException e) {}

      try {
        Integer.parsePositiveInt("+0", 16);
        fail();
      } catch (NumberFormatException e) {}
    }
    */

    @Test
    public void testStaticHashCode() {
        assertEquals(Integer.valueOf(567).hashCode(), Integer.hashCode(567));
    }

    @Test
    public void testMax() {
        int a = 567;
        int b = 578;
        assertEquals(Math.max(a, b), Integer.max(a, b));
    }

    @Test
    public void testMin() {
        int a = 567;
        int b = 578;
        assertEquals(Math.min(a, b), Integer.min(a, b));
    }

    @Test
    public void testSum() {
        int a = 567;
        int b = 578;
        assertEquals(a + b, Integer.sum(a, b));
    }

    @Test
    public void testBYTES() {
        assertEquals(4, Integer.BYTES);
    }

    @Test
    public void testCompareUnsigned() {
        for (int i = 0; i < INT_VALUES.length; ++i) {
            for (int j = 0; j < INT_VALUES.length; ++j) {
                assertEquals(
                        Integer.compare(i, j),
                        Integer.compareUnsigned(INT_VALUES[i], INT_VALUES[j]));
            }
        }
    }

    @Test
    public void testDivideAndRemainderUnsigned() {
        long[] vals = {1L, 23L, 456L, 0x7fff_ffffL, 0x8000_0000L, 0xffff_ffffL};

        for (long dividend : vals) {
            for (long divisor : vals) {
                int uq = Integer.divideUnsigned((int) dividend, (int) divisor);
                int ur = Integer.remainderUnsigned((int) dividend, (int) divisor);
                assertEquals((int) (dividend / divisor), uq);
                assertEquals((int) (dividend % divisor), ur);
                assertEquals((int) dividend, uq * (int) divisor + ur);
            }
        }

        for (long dividend : vals) {
            assertThrows(
                    ArithmeticException.class, () -> Integer.divideUnsigned((int) dividend, 0));
            assertThrows(
                    ArithmeticException.class, () -> Integer.remainderUnsigned((int) dividend, 0));
        }
    }

    @Test
    public void testParseUnsignedInt() {
        for (int value : INT_VALUES) {
            // Special radices
            assertEquals(value, Integer.parseUnsignedInt(Integer.toBinaryString(value), 2));
            assertEquals(value, Integer.parseUnsignedInt(Integer.toOctalString(value), 8));
            assertEquals(value, Integer.parseUnsignedInt(Integer.toUnsignedString(value)));
            assertEquals(value, Integer.parseUnsignedInt(Integer.toHexString(value), 16));

            for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; ++radix) {
                assertEquals(
                        value,
                        Integer.parseUnsignedInt(Integer.toUnsignedString(value, radix), radix));
            }
        }

        for (long longValue : LONG_VALUES) {
            // Special radices
            assertThrows(
                    NumberFormatException.class,
                    () -> Integer.parseUnsignedInt(Long.toBinaryString(longValue), 2));
            assertThrows(
                    NumberFormatException.class,
                    () -> Integer.parseUnsignedInt(Long.toOctalString(longValue), 8));
            assertThrows(
                    NumberFormatException.class,
                    () -> Integer.parseUnsignedInt(Long.toUnsignedString(longValue), 10));
            assertThrows(
                    NumberFormatException.class,
                    () -> Integer.parseUnsignedInt(Long.toHexString(longValue), 16));
            for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; ++radix) {
                final int r = radix;
                assertThrows(
                        NumberFormatException.class,
                        () -> Integer.parseUnsignedInt(Long.toUnsignedString(longValue, r), r));
            }
        }

        assertThrows(NumberFormatException.class, () -> Integer.parseUnsignedInt("-1"));
        assertThrows(NumberFormatException.class, () -> Integer.parseUnsignedInt("123", 2));
        assertThrows(NumberFormatException.class, () -> Integer.parseUnsignedInt(null));
        assertThrows(
                NumberFormatException.class,
                () -> Integer.parseUnsignedInt("0", Character.MAX_RADIX + 1));
        assertThrows(
                NumberFormatException.class,
                () -> Integer.parseUnsignedInt("0", Character.MIN_RADIX - 1));
    }

    @Test
    public void testParseUnsignedIntSubstring() {
        final String LEFT = "1";
        final String RIGHT = "0";

        for (int ii = 0; ii < 8; ii = 2 * ii + 1) {
            for (int jj = 0; jj < 8; jj = 2 * jj + 1) {
                final int i = ii; // final for use in lambdas
                final int j = jj; // final for use in lambdas
                final String leftPad = LEFT.repeat(i);
                final String rightPad = RIGHT.repeat(j);
                for (int value : INT_VALUES) {
                    String binary = leftPad + Integer.toBinaryString(value) + rightPad;
                    assertEquals(
                            value, Integer.parseUnsignedInt(binary, i, binary.length() - j, 2));

                    String octal = leftPad + Integer.toOctalString(value) + rightPad;
                    assertEquals(value, Integer.parseUnsignedInt(octal, i, octal.length() - j, 8));

                    String denary = leftPad + Integer.toUnsignedString(value) + rightPad;
                    assertEquals(
                            value, Integer.parseUnsignedInt(denary, i, denary.length() - j, 10));

                    String hex = leftPad + Integer.toHexString(value) + rightPad;
                    assertEquals(value, Integer.parseUnsignedInt(hex, i, hex.length() - j, 16));

                    for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; ++radix) {
                        String arb = leftPad + Integer.toUnsignedString(value, radix) + rightPad;
                        assertEquals(
                                value, Integer.parseUnsignedInt(arb, i, arb.length() - j, radix));
                    }
                }

                for (long large_value : LONG_VALUES) {
                    {
                        final String input = leftPad + Long.toBinaryString(large_value) + rightPad;
                        assertThrows(
                                NumberFormatException.class,
                                () -> Integer.parseUnsignedInt(input, i, input.length() - j, 2));
                    }
                    {
                        final String input = leftPad + Long.toOctalString(large_value) + rightPad;
                        assertThrows(
                                NumberFormatException.class,
                                () -> Integer.parseUnsignedInt(input, i, input.length() - j, 8));
                    }
                    {
                        final String input =
                                leftPad + Long.toUnsignedString(large_value) + rightPad;
                        assertThrows(
                                NumberFormatException.class,
                                () -> Integer.parseUnsignedInt(input, i, input.length() - j, 10));
                    }
                    {
                        final String input = leftPad + Long.toHexString(large_value) + rightPad;
                        assertThrows(
                                NumberFormatException.class,
                                () -> Integer.parseUnsignedInt(input, i, input.length() - j, 16));
                    }
                    for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; ++radix) {
                        final int r = radix;
                        String input =
                                leftPad + Long.toUnsignedString(large_value, radix) + rightPad;
                        assertThrows(
                                NumberFormatException.class,
                                () -> Integer.parseUnsignedInt(input, i, input.length() - j, r));
                    }
                }
            }
        }

        assertThrows(
                IndexOutOfBoundsException.class, () -> Integer.parseUnsignedInt("123", -1, 3, 10));
        assertThrows(
                IndexOutOfBoundsException.class, () -> Integer.parseUnsignedInt("123", 4, 5, 10));
        assertThrows(
                IndexOutOfBoundsException.class, () -> Integer.parseUnsignedInt("123", 2, 1, 10));
        assertThrows(
                IndexOutOfBoundsException.class, () -> Integer.parseUnsignedInt("123", 2, 4, 10));
        assertThrows(NumberFormatException.class, () -> Integer.parseUnsignedInt("-1", 0, 2, 10));
        assertThrows(NumberFormatException.class, () -> Integer.parseUnsignedInt("123", 0, 3, 2));
        assertThrows(
                NumberFormatException.class,
                () -> Integer.parseUnsignedInt("0", 0, 1, Character.MAX_RADIX + 1));
        assertThrows(
                NumberFormatException.class,
                () -> Integer.parseUnsignedInt("0", 0, 1, Character.MIN_RADIX - 1));
        assertThrows(NullPointerException.class, () -> Integer.parseUnsignedInt(null, 0, 1, 10));
    }

    @Test
    public void testToUnsignedLong() {
        for (int val : INT_VALUES) {
            long ul = Integer.toUnsignedLong(val);
            assertEquals(0, ul >>> Integer.BYTES * 8);
            assertEquals(val, (int) ul);
        }
    }

    @Test
    public void testToUnsignedString() {
        for (int val : INT_VALUES) {
            // Special radices
            assertTrue(Integer.toUnsignedString(val, 2).equals(Integer.toBinaryString(val)));
            assertTrue(Integer.toUnsignedString(val, 8).equals(Integer.toOctalString(val)));
            assertTrue(Integer.toUnsignedString(val, 10).equals(Integer.toUnsignedString(val)));
            assertTrue(Integer.toUnsignedString(val, 16).equals(Integer.toHexString(val)));

            for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; ++radix) {
                assertTrue(
                        Integer.toUnsignedString(val, radix)
                                .equals(Long.toString(Integer.toUnsignedLong(val), radix)));
            }

            // Behavior is not defined by Java API specification if the radix falls outside of valid
            // range, thus we don't test for such cases.
        }
    }

    @Test
    public void testParseUnsignedIntSubstringForBackports() {
        // Rudimentary test to register coverage on older branches where we may see backports for
        // OpenJDK 11 methods (b/191859202). NB using Integer_parseUnsignedInt rather than
        // Integer.parseUnsignedInt.
        assertEquals(
                0xaa55aa55,
                Integer_parseUnsignedInt("left10101010010101011010101001010101right", 4, 36, 2));
        assertEquals(8003, Integer_parseUnsignedInt("left17503right", 4, 9, 8));
        assertEquals(0xffff_ffff, Integer_parseUnsignedInt("left4294967295right", 4, 14, 10));
        assertEquals(0x1234_5678, Integer_parseUnsignedInt("lefty12345678righty", 5, 13, 16));
    }

    /**
     * Parses an unsigned integer using a {@code MethodHandle} to invoke {@code
     * Integer.parseUnsignedInt}.
     *
     * @param val the {@code CharSequence} to be parsed.
     * @param start the starting index in {@code val}.
     * @param end the ending ing index in {@code val}, exclusive.
     * @param radix the radix to parse {@code val} with.
     * @return the parsed unsigned integer.
     */
    private static int Integer_parseUnsignedInt(CharSequence val, int start, int end, int radix) {
        try {
            MethodType parseUnsignedIntType =
                    MethodType.methodType(
                            int.class, CharSequence.class, int.class, int.class, int.class);
            MethodHandle parseUnsignedInt =
                    MethodHandles.lookup()
                            .findStatic(Integer.class, "parseUnsignedInt", parseUnsignedIntType);
            return (int) parseUnsignedInt.invokeExact(val, start, end, radix);
        } catch (IndexOutOfBoundsException | NullPointerException | NumberFormatException e) {
            // Expected exceptions from the target method during the tests here.
            throw e;
        } catch (Throwable t) {
            // Everything else.
            throw new RuntimeException(t);
        }
    }
}
