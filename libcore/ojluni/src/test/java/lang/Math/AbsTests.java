/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package test.java.lang.Math;

import java.util.function.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import org.testng.annotations.Test;
/*
 * @test
 * @bug 8241374
 * @summary Test abs and absExact for Math and StrictMath
 */
public class AbsTests {

    @Test
    public void testInRangeIntAbs() {
        int[][] testCases  = {
            // Argument to abs, expected result
            {+0, 0},
            {+1, 1},
            {-1, 1},
            {-2, 2},
            {+2, 2},
            {-Integer.MAX_VALUE, Integer.MAX_VALUE},
            {+Integer.MAX_VALUE, Integer.MAX_VALUE}
        };

        for(var testCase : testCases) {
            testIntAbs(Math::abs,      testCase[0], testCase[1]);
            testIntAbs(Math::absExact, testCase[0], testCase[1]);
        }
    }

    @Test
    public void testIntMinValue() {
        // Strange but true
        testIntAbs(Math::abs, Integer.MIN_VALUE, Integer.MIN_VALUE);

        // Test exceptional behavior for absExact
        assertThrows(ArithmeticException.class, () -> Math.absExact(Integer.MIN_VALUE));
    }

    private static void testIntAbs(IntUnaryOperator absFunc,
                           int argument, int expected) {
        int result = absFunc.applyAsInt(argument);
        assertEquals(result, expected,
                     String.format("Unexpected int abs result %d for argument %d%n",
                                   result, argument));
    }

    // --------------------------------------------------------------------

    @Test
    public void testInRangeLongAbs() {
        long[][] testCases  = {
            // Argument to abs, expected result
            {+0L, 0L},
            {+1L, 1L},
            {-1L, 1L},
            {-2L, 2L},
            {+2L, 2L},
            {-Integer.MAX_VALUE, Integer.MAX_VALUE},
            {+Integer.MAX_VALUE, Integer.MAX_VALUE},
            { Integer.MIN_VALUE, -((long)Integer.MIN_VALUE)},
            {-Long.MAX_VALUE, Long.MAX_VALUE},
        };

        for(var testCase : testCases) {
            testLongAbs(Math::abs,      testCase[0], testCase[1]);
            testLongAbs(Math::absExact, testCase[0], testCase[1]);
        }
    }

    @Test
    public void testLongMinValue() {
        // Strange but true
        testLongAbs(Math::abs, Long.MIN_VALUE, Long.MIN_VALUE);

        // Test exceptional behavior for absExact
        assertThrows(ArithmeticException.class, () -> Math.absExact(Long.MIN_VALUE));
    }

    private static void testLongAbs(LongUnaryOperator absFunc,
                           long argument, long expected) {
        long result = absFunc.applyAsLong(argument);
        assertEquals(result, expected,
                     String.format("Unexpected long abs result %d for argument %d%n",
                                   result, argument));
    }
}
