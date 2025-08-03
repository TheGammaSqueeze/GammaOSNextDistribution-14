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

/*
 * @test
 * @bug 4504839 4215269 6322074 8030814
 * @summary Basic tests for unsigned operations
 * @author Joseph D. Darcy
 */
package test.java.lang.Long;

// Android-added: support for wrapper to avoid d8 backporting of Integer.parseInt (b/215435867).
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.math.*;

import org.testng.annotations.Test;
import org.testng.Assert;

public class Unsigned {
    private static final BigInteger TWO = BigInteger.valueOf(2L);

    @Test
    public void testRoundtrip() {
        long[] data = {-1L, 0L, 1L};

        for(long datum : data) {
            Assert.assertEquals(
                Long.parseUnsignedLong(Long.toBinaryString(datum), 2),
                datum,
                "Bad binary roundtrip conversion of " + datum);

            Assert.assertEquals(
                Long.parseUnsignedLong(Long.toOctalString(datum), 8),
                datum,
                "Bad octal roundtrip conversion of " + datum);

            Assert.assertEquals(
                Long.parseUnsignedLong(Long.toHexString(datum), 16),
                datum,
                "Bad hex roundtrip conversion of " + datum);
        }
    }

    @Test
    public void testByteToUnsignedLong() {
        for(int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
            byte datum = (byte) i;
            long ui = Byte.toUnsignedLong(datum);

            if ( (ui & (~0xffL)) != 0L || ((byte)ui != datum )) {
                Assert.fail(
                    String.format("Bad conversion of byte %d to unsigned long %d%n", datum, ui));
            }
        }
    }

    @Test
    public void testShortToUnsignedLong() {
        for(int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; i++) {
            short datum = (short) i;
            long ui = Short.toUnsignedLong(datum);

            if ( (ui & (~0xffffL)) != 0L || ((short)ui != datum )) {
                Assert.fail(
                    String.format("Bad conversion of short %d to unsigned long %d%n", datum, ui));
            }
        }
    }

    @Test
    public void testUnsignedCompare() {
        long[] data = {
            0L,
            1L,
            2L,
            3L,
            0x00000000_80000000L,
            0x00000000_FFFFFFFFL,
            0x00000001_00000000L,
            0x80000000_00000000L,
            0x80000000_00000001L,
            0x80000000_00000002L,
            0x80000000_00000003L,
            0x80000000_80000000L,
            0xFFFFFFFF_FFFFFFFEL,
            0xFFFFFFFF_FFFFFFFFL,
        };

        for(long i : data) {
            for(long j : data) {
                long libraryResult    = Long.compareUnsigned(i, j);
                long libraryResultRev = Long.compareUnsigned(j, i);
                long localResult      = compUnsigned(i, j);

                if (i == j) {
                    Assert.assertEquals(
                        libraryResult,
                        0,
                        String.format("Value 0x%x did not compare as " +
                                          "an unsigned equal to itself; got %d%n",
                                          i, libraryResult));
                }

                Assert.assertEquals(
                    Long.signum(libraryResult),
                    Long.signum(localResult),
                    String.format("Unsigned compare of 0x%x to 0x%x%n:" +
                                     "\texpected sign of %d, got %d%n",
                                     i, j, localResult, libraryResult));

                Assert.assertEquals(
                    Long.signum(libraryResult),
                    -Long.signum(libraryResultRev),
                    String.format("signum(compareUnsigned(x, y)) != -signum(compareUnsigned(y,x))" +
                                          " for \t0x%x and 0x%x, computed %d and %d%n",
                                          i, j, libraryResult, libraryResultRev));
            }
        }
    }

    private static int compUnsigned(long x, long y) {
        BigInteger big_x = toUnsignedBigInt(x);
        BigInteger big_y = toUnsignedBigInt(y);

        return big_x.compareTo(big_y);
    }

    private static BigInteger toUnsignedBigInt(long x) {
        if (x >= 0)
            return BigInteger.valueOf(x);
        else {
            int upper = (int)(((long)x) >> 32);
            int lower = (int) x;

            BigInteger bi = // (upper << 32) + lower
                (BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).
                add(BigInteger.valueOf(Integer.toUnsignedLong(lower)));

            // System.out.printf("%n\t%d%n\t%s%n", x, bi.toString());
            return bi;
        }
    }

    @Test
    public void testToStringUnsigned() {
        long[] data = {
            0L,
            1L,
            2L,
            3L,
            99999L,
            100000L,
            999999L,
            100000L,
            999999999L,
            1000000000L,
            0x1234_5678L,
            0x8000_0000L,
            0x8000_0001L,
            0x8000_0002L,
            0x8000_0003L,
            0x8765_4321L,
            0xFFFF_FFFEL,
            0xFFFF_FFFFL,

            // Long-range values
              999_999_999_999L,
            1_000_000_000_000L,

              999_999_999_999_999_999L,
            1_000_000_000_000_000_000L,

            0xFFFF_FFFF_FFFF_FFFEL,
            0xFFFF_FFFF_FFFF_FFFFL,
        };

        for(int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
            for(long datum : data) {
                String result1 = Long.toUnsignedString(datum, radix);
                String result2 = toUnsignedBigInt(datum).toString(radix);

                Assert.assertEquals(
                    result1,
                    result2,
                    String.format("Unexpected string difference converting 0x%x:" +
                                      "\t%s %s%n", datum, result1, result2));

                if (radix == 10) {
                    String result3 = Long.toUnsignedString(datum);
                    Assert.assertEquals(
                        result2,
                        result3,
                        String.format("Unexpected string difference converting 0x%x:" +
                                          "\t%s %s%n", datum, result3, result2));
                }

                long parseResult = Long.parseUnsignedLong(result1, radix);

                Assert.assertEquals(
                    parseResult,
                    datum,
                    String.format("Bad roundtrip conversion of %d in base %d" +
                                          "\tconverting back ''%s'' resulted in %d%n",
                                          datum, radix, result1,  parseResult));
            }
        }
    }

    @Test
    public void testParseUnsignedLong() {
        long maxUnsignedInt = Integer.toUnsignedLong(0xffff_ffff);

        // Values include those between signed Long.MAX_VALUE and
        // unsignted Long MAX_VALUE.
        BigInteger[] inRange = {
            BigInteger.valueOf(0L),
            BigInteger.valueOf(1L),
            BigInteger.valueOf(10L),
            BigInteger.valueOf(2147483646L),   // Integer.MAX_VALUE - 1
            BigInteger.valueOf(2147483647L),   // Integer.MAX_VALUE
            BigInteger.valueOf(2147483648L),   // Integer.MAX_VALUE + 1

            BigInteger.valueOf(maxUnsignedInt - 1L),
            BigInteger.valueOf(maxUnsignedInt),

            BigInteger.valueOf(Long.MAX_VALUE - 1L),
            BigInteger.valueOf(Long.MAX_VALUE),
            BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),

            TWO.pow(64).subtract(BigInteger.ONE)
        };

        for(BigInteger value : inRange) {
            for(int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
                String bigString = value.toString(radix);
                long longResult = Long.parseUnsignedLong(bigString, radix);

                Assert.assertEquals(
                    toUnsignedBigInt(longResult),
                    value,
                    String.format("Bad roundtrip conversion of %d in base %d" +
                                      "\tconverting back ''%s'' resulted in %d%n",
                                      value, radix, bigString,  longResult));

                // test offset based parse method
                // Android-changed: avoid d8 backporting Long.parseUnsignedLong (b/215435867).
                // longResult = Long.parseUnsignedLong("prefix" + bigString + "suffix", "prefix".length(),
                //        "prefix".length() + bigString.length(), radix);
                longResult = Long_parseUnsignedLong("prefix" + bigString + "suffix", "prefix".length(),
                        "prefix".length() + bigString.length(), radix);

                Assert.assertEquals(
                    toUnsignedBigInt(longResult),
                    value,
                    String.format("Bad roundtrip conversion of %d in base %d" +
                            "\tconverting back ''%s'' resulted in %d%n",
                            value, radix, bigString,  longResult));
            }
        }

        String[] outOfRange = {
            null,
            "",
            "-1",
            TWO.pow(64).toString(),
        };

        for(String s : outOfRange) {
            try {
                long result = Long.parseUnsignedLong(s);
                Assert.fail(String.format("Unexpected got %d from an unsigned conversion of %s",
                                  result, s));
            } catch(NumberFormatException nfe) {
                ; // Correct result
            }
        }

        // test case known at one time to fail
        testUnsignedOverflow("1234567890abcdef1", 16, true);

        // largest value with guard = 91 = 13*7; radix = 13
        testUnsignedOverflow("196a78a44c3bba320c", 13, false);

        // smallest value with guard = 92 = 23*2*2; radix = 23
        testUnsignedOverflow("137060c6g1c1dg0", 23, false);

        // guard in [92,98]: no overflow

        // one less than smallest guard value to overflow: guard = 99 = 11*3*3, radix = 33
        testUnsignedOverflow("b1w8p7j5q9r6f", 33, false);

        // smallest guard value to overflow: guard = 99 = 11*3*3, radix = 33
        testUnsignedOverflow("b1w8p7j5q9r6g", 33, true);

        // test overflow of overflow
        BigInteger maxUnsignedLong =
                BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        for (int radix = Character.MIN_RADIX; radix <= Character.MAX_RADIX; radix++) {
            BigInteger quotient = maxUnsignedLong.divide(BigInteger.valueOf(radix));
            for (int addend = 2; addend <= radix; addend++) {
                BigInteger b = quotient.multiply(BigInteger.valueOf(radix + addend));
                testUnsignedOverflow(b.toString(radix), radix, b.compareTo(maxUnsignedLong) > 0);
            }
        }
    }

    // test for missing or unexpected unsigned overflow exception
    private static void testUnsignedOverflow(String s, int radix, boolean exception) {
        long result;
        try {
            result = Long.parseUnsignedLong(s, radix);
            if (exception) {
                Assert.fail(String.format("Unexpected result %d for Long.parseUnsignedLong(%s,%d)\n",
                        result, s, radix));
            }
        } catch (NumberFormatException nfe) {
            if (!exception) {
                Assert.fail(
                    String.format("Unexpected exception %s for Long.parseUnsignedLong(%s,%d)\n",
                    nfe.toString(), s, radix));
            }
        }
    }

    @Test
    public void testDivideAndRemainder() {
        long MAX_UNSIGNED_INT = Integer.toUnsignedLong(0xffff_ffff);

        BigInteger[] inRange = {
            BigInteger.valueOf(0L),
            BigInteger.valueOf(1L),
            BigInteger.valueOf(10L),
            BigInteger.valueOf(2147483646L),   // Integer.MAX_VALUE - 1
            BigInteger.valueOf(2147483647L),   // Integer.MAX_VALUE
            BigInteger.valueOf(2147483648L),   // Integer.MAX_VALUE + 1

            BigInteger.valueOf(MAX_UNSIGNED_INT - 1L),
            BigInteger.valueOf(MAX_UNSIGNED_INT),

            BigInteger.valueOf(Long.MAX_VALUE - 1L),
            BigInteger.valueOf(Long.MAX_VALUE),
            BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),

            TWO.pow(64).subtract(BigInteger.ONE)
        };

        for(BigInteger dividend : inRange) {
            for(BigInteger divisor : inRange) {
                long quotient;
                BigInteger longQuotient;

                long remainder;
                BigInteger longRemainder;

                if (divisor.equals(BigInteger.ZERO)) {
                    try {
                        quotient = Long.divideUnsigned(dividend.longValue(), divisor.longValue());
                        Assert.fail("Unexpectedly did not throw while dividing by zero");
                    } catch(ArithmeticException ea) {
                        ; // Expected
                    }

                    try {
                        remainder = Long.remainderUnsigned(dividend.longValue(), divisor.longValue());
                        Assert.fail("Unexpectedly did not throw while dividing by zero");
                    } catch(ArithmeticException ea) {
                        ; // Expected
                    }
                } else {
                    quotient = Long.divideUnsigned(dividend.longValue(), divisor.longValue());
                    longQuotient = dividend.divide(divisor);

                    Assert.assertEquals(
                        quotient,
                        longQuotient.longValue(),
                        String.format("Unexpected unsigned divide result %s on %s/%s%n",
                                          Long.toUnsignedString(quotient),
                                          Long.toUnsignedString(dividend.longValue()),
                                          Long.toUnsignedString(divisor.longValue())));

                    remainder = Long.remainderUnsigned(dividend.longValue(), divisor.longValue());
                    longRemainder = dividend.remainder(divisor);

                    Assert.assertEquals(
                        remainder,
                        longRemainder.longValue(),
                        String.format("Unexpected unsigned remainder result %s on %s%%%s%n",
                                          Long.toUnsignedString(remainder),
                                          Long.toUnsignedString(dividend.longValue()),
                                          Long.toUnsignedString(divisor.longValue())));
                }
            }
        }
    }

    // Android-added: wrapper to avoid d8 backporting of Long.parseUnsignedLong(JIII) (b/215435867).
    private static long Long_parseUnsignedLong(String val, int start, int end, int radix) {
        try {
            MethodType parseType = MethodType.methodType(long.class,
                                                         CharSequence.class,
                                                         int.class,
                                                         int.class,
                                                         int.class);
            MethodHandle parse =
                    MethodHandles.lookup().findStatic(Long.class, "parseUnsignedLong", parseType);
            return (long) parse.invokeExact((CharSequence) val, start, end, radix);
        } catch (IndexOutOfBoundsException | NullPointerException | NumberFormatException e) {
            // Expected exceptions from the target method during the tests here.
            throw e;
        } catch (Throwable t) {
            // Everything else.
            throw new RuntimeException(t);
        }
    }
}
