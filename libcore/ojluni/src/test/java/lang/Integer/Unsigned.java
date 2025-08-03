/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package test.java.lang.Integer;

import org.testng.annotations.Test;
import org.testng.Assert;

/*
 * @test
 * @bug 4504839 4215269 6322074
 * @summary Basic tests for unsigned operations.
 * @author Joseph D. Darcy
 */
public class Unsigned {

    @Test
    public void testRoundtrip() {
        int[] data = {-1, 0, 1};

        for(int datum : data) {
            Assert.assertEquals(
                Integer.parseUnsignedInt(Integer.toBinaryString(datum), 2),
                datum,
                "Bad binary roundtrip conversion of " + datum);

            Assert.assertEquals(Integer.parseUnsignedInt(Integer.toOctalString(datum), 8),
                datum,
                "Bad octal roundtrip conversion of " + datum);

            Assert.assertEquals(Integer.parseUnsignedInt(Integer.toHexString(datum), 16),
                datum,
                "Bad hex roundtrip conversion of " + datum);
        }
    }

    @Test
    public void testByteToUnsignedInt() {
        for(int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
            byte datum = (byte) i;
            int ui = Byte.toUnsignedInt(datum);

            if ( (ui & (~0xff)) != 0 || ((byte)ui != datum )) {
                Assert.fail(
                    String.format("Bad conversion of byte %d to unsigned int %d%n", datum, ui));
            }
        }
    }

    @Test
    public void testShortToUnsignedInt() {
        for(int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            short datum = (short) i;
            int ui = Short.toUnsignedInt(datum);

            if ( (ui & (~0xffff)) != 0 || ((short)ui != datum )) {
                Assert.fail(
                    String.format("Bad conversion of short %d to unsigned int %d%n", datum, ui));
            }
        }
    }

    @Test
    public void testUnsignedCompare() {
        int[] data = {
            0,
            1,
            2,
            3,
            0x8000_0000,
            0x8000_0001,
            0x8000_0002,
            0x8000_0003,
            0xFFFF_FFFE,
            0xFFFF_FFFF,
        };

        for(int i : data) {
            for(int j : data) {
                int libraryResult    = Integer.compareUnsigned(i, j);
                int libraryResultRev = Integer.compareUnsigned(j, i);
                int localResult      = compUnsigned(i, j);

                if (i == j) {
                    Assert.assertEquals(libraryResult, 0,
                        String.format("Value 0x%x did not compare as " +
                                          "an unsigned value equal to itself; got %d%n",
                                          i, libraryResult));
                }

                Assert.assertEquals(Integer.signum(libraryResult),
                    Integer.signum(localResult),
                    String.format("Unsigned compare of 0x%x to 0x%x%n:" +
                                      "\texpected sign of %d, got %d%n",
                                      i, j, localResult, libraryResult));

                Assert.assertEquals(Integer.signum(libraryResult),
                    -Integer.signum(libraryResultRev),
                    String.format("signum(compareUnsigned(x, y)) != -signum(compareUnsigned(y,x))" +
                                      " for \t0x%x and 0x%x, computed %d and %d%n",
                                      i, j, libraryResult, libraryResultRev));
            }
        }
    }

    /**
     * Straightforward compare unsigned algorithm.
     */
    private static int compUnsigned(int x, int y) {
        int sign_x = x & Integer.MIN_VALUE;
        int sign_y = y & Integer.MIN_VALUE;

        int mant_x  = x & (~Integer.MIN_VALUE);
        int mant_y  = y & (~Integer.MIN_VALUE);

        if (sign_x == sign_y)
            return Integer.compare(mant_x, mant_y);
        else {
            if (sign_x == 0)
                return -1; // sign x is 0, sign y is 1 => (x < y)
            else
                return 1; //  sign x is 1, sign y is 0 => (x > y)
        }
    }

    @Test
    public void testToUnsignedLong() {
        int[] data = {
            0,
            1,
            2,
            3,
            0x1234_5678,
            0x8000_0000,
            0x8000_0001,
            0x8000_0002,
            0x8000_0003,
            0x8765_4321,
            0xFFFF_FFFE,
            0xFFFF_FFFF,
        };

        for(int datum : data) {
            long result = Integer.toUnsignedLong(datum);

            // High-order bits should be zero
            Assert.assertEquals(
                (result & 0xffff_ffff_0000_0000L),
                0L,
                String.format("High bits set converting 0x%x to 0x%x%n", datum, result));

            // Lower-order bits should be equal to datum.
            int lowOrder = (int)(result & 0x0000_0000_ffff_ffffL);
            Assert.assertEquals(lowOrder, datum,
                String.format("Low bits not preserved converting 0x%x to 0x%x%n", datum, result));
        }
    }

    @Test
    public void testToStringUnsigned() {
        int[] data = {
            0,
            1,
            2,
            3,
            99999,
            100000,
            999999,
            100000,
            999999999,
            1000000000,
            0x1234_5678,
            0x8000_0000,
            0x8000_0001,
            0x8000_0002,
            0x8000_0003,
            0x8765_4321,
            0xFFFF_FFFE,
            0xFFFF_FFFF,
        };

        for(int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
            for(int datum : data) {
                String result1 = Integer.toUnsignedString(datum, radix);
                String result2 = Long.toString(Integer.toUnsignedLong(datum), radix);

                if (!result1.equals(result2)) {
                    Assert.fail(String.format("Unexpected string difference converting 0x%x:" +
                                      "\t%s %s%n",
                                      datum, result1, result2));
                }

                if (radix == 10) {
                    String result3 = Integer.toUnsignedString(datum);
                    if (!result2.equals(result3)) {
                        Assert.fail(String.format("Unexpected string difference converting 0x%x:" +
                                          "\t%s %s%n",
                                          datum, result3, result2));
                    }
                }

                int parseResult = Integer.parseUnsignedInt(result1, radix);

                if (parseResult != datum) {
                    Assert.fail(String.format("Bad roundtrip conversion of %d in base %d" +
                                          "\tconverting back ''%s'' resulted in %d%n",
                                          datum, radix, result1,  parseResult));
                }
            }
        }
    }

    private static final long MAX_UNSIGNED_INT = Integer.toUnsignedLong(0xffff_ffff);

    @Test
    public void testParseUnsignedInt() {
        // Values include those between signed Integer.MAX_VALUE and
        // unsignted int MAX_VALUE.
        long[] inRange = {
            0L,
            1L,
            10L,
            2147483646L,   // MAX_VALUE - 1
            2147483647L,   // MAX_VALUE
            2147483648L,   // MAX_VALUE + 1

            MAX_UNSIGNED_INT - 1L,
            MAX_UNSIGNED_INT,
        };

        for(long value : inRange) {
            for(int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
                String longString = Long.toString(value, radix);
                int intResult = Integer.parseUnsignedInt(longString, radix);

                if (Integer.toUnsignedLong(intResult) != value) {
                    Assert.fail(String.format("Bad roundtrip conversion of %d in base %d" +
                                      "\tconverting back ''%s'' resulted in %d%n",
                                      value, radix, longString,  intResult));
                }

                // test offset based parse method
                intResult = Integer.parseUnsignedInt("prefix" + longString + "suffix",
                        "prefix".length(), "prefix".length() + longString.length(), radix);

                if (Integer.toUnsignedLong(intResult) != value) {
                    Assert.fail(String.format("Bad roundtrip conversion of %d in base %d" +
                            "\tconverting back ''%s'' resulted in %d%n",
                            value, radix, longString,  intResult));
                }
            }
        }

        String[] outOfRange = {
            null,
            "",
            "-1",
            Long.toString(MAX_UNSIGNED_INT + 1L),
            Long.toString(Long.MAX_VALUE)
        };

        for(String s : outOfRange) {
            try {
                int result = Integer.parseUnsignedInt(s);
                Assert.fail(
                    String.format("Unexpected got %d from an unsigned conversion of %s", result, s));
            } catch(NumberFormatException nfe) {
                ; // Correct result
            }
        }
    }

    @Test
    public void testDivideAndRemainder() {
        long[] inRange = {
            0L,
            1L,
            2L,
            2147483646L,   // MAX_VALUE - 1
            2147483647L,   // MAX_VALUE
            2147483648L,   // MAX_VALUE + 1

            MAX_UNSIGNED_INT - 1L,
            MAX_UNSIGNED_INT,
        };

        for(long dividend : inRange) {
            for(long divisor : inRange) {
                int quotient;
                long longQuotient;

                int remainder;
                long longRemainder;

                if (divisor == 0) {
                    try {
                        quotient = Integer.divideUnsigned((int) dividend, (int) divisor);
                        Assert.fail("Unexpectedly did not throw");
                    } catch(ArithmeticException ea) {
                        ; // Expected
                    }

                    try {
                        remainder = Integer.remainderUnsigned((int) dividend, (int) divisor);
                        Assert.fail("Unexpectedly did not throw");
                    } catch(ArithmeticException ea) {
                        ; // Expected
                    }
                } else {
                    quotient = Integer.divideUnsigned((int) dividend, (int) divisor);
                    longQuotient = dividend / divisor;

                    if (quotient != (int)longQuotient) {
                        Assert.fail(String.format("Unexpected unsigned divide result %s on %s/%s%n",
                                          Integer.toUnsignedString(quotient),
                                          Integer.toUnsignedString((int) dividend),
                                          Integer.toUnsignedString((int) divisor)));
                    }

                    remainder = Integer.remainderUnsigned((int) dividend, (int) divisor);
                    longRemainder = dividend % divisor;

                    if (remainder != (int)longRemainder) {
                        Assert.fail(String.format("Unexpected unsigned remainder result %s on %s%%%s%n",
                                          Integer.toUnsignedString(remainder),
                                          Integer.toUnsignedString((int) dividend),
                                          Integer.toUnsignedString((int) divisor)));
                    }
                }
            }
        }
    }
}
