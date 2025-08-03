/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
package test.java.math.BigDecimal;

/*
 * @test
 * @bug 6325535
 * @summary Test for the rounding behavior of negate(MathContext)
 * @author Joseph D. Darcy
 */

import java.math.*;

import org.testng.Assert;
import org.testng.annotations.Test;

// Android-changed: Replace error counting with asserts.
public class NegateTests {

    static BigDecimal negateThenRound(BigDecimal bd, MathContext mc) {
        return bd.negate().plus(mc);
    }


    static BigDecimal absThenRound(BigDecimal bd, MathContext mc) {
        return bd.abs().plus(mc);
    }


    static void negateTest(BigDecimal[][] testCases,  MathContext mc) {
        for (BigDecimal [] testCase : testCases) {

            BigDecimal bd = testCase[0];
            BigDecimal neg1 = bd.negate(mc);
            BigDecimal neg2 = negateThenRound(bd, mc);
            BigDecimal expected = testCase[1];

            Assert.assertEquals(neg1, expected, "(" + bd + ").negate(" + mc + ") => " +
                                   neg1 + " != expected " + expected);

            Assert.assertEquals(neg1, neg2, "(" + bd + ").negate(" + mc + ")  => " +
                                   neg1 + " != ntr " + neg2);

            // Test abs consistency
            BigDecimal abs = bd.abs(mc);
            BigDecimal expectedAbs = absThenRound(bd,mc);
            Assert.assertEquals(abs, expectedAbs, "(" + bd + ").abs(" + mc + ")  => " +
                                   abs + " != atr " +  expectedAbs);
        }
    }

    @Test
    public void negateTests() {
        BigDecimal [][] testCasesCeiling = {
            {new BigDecimal("1.3"),     new BigDecimal("-1")},
            {new BigDecimal("-1.3"),    new BigDecimal("2")},
        };

        negateTest(testCasesCeiling, new MathContext(1, RoundingMode.CEILING));

        BigDecimal [][] testCasesFloor = {
            {new BigDecimal("1.3"),     new BigDecimal("-2")},
            {new BigDecimal("-1.3"),    new BigDecimal("1")},
        };

        negateTest(testCasesFloor, new MathContext(1, RoundingMode.FLOOR));
    }
}
