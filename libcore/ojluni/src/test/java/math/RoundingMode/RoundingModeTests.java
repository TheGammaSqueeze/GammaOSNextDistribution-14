/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
package test.java.math.RoundingMode;

/*
 * @test
 * @bug 4851776 4891522 4905335
 * @summary Basic tests for the RoundingMode class.
 * @author Joseph D. Darcy
 */

import java.math.RoundingMode;
import java.math.BigDecimal;

import org.testng.Assert;
import org.testng.annotations.Test;

// Android-changed: Replace error throws with asserts.
public class RoundingModeTests {

    @Test
    public void testRoundingMode() {

        // For each member of the family, make sure
        // rm == valueOf(rm.toString())

        for(RoundingMode rm: RoundingMode.values()) {
            Assert.assertEquals(RoundingMode.valueOf(rm.toString()), rm,
                    "Bad roundtrip conversion of " + rm);
        }

        // Test that mapping of old integers to new values is correct
        Assert.assertEquals(RoundingMode.CEILING, RoundingMode.valueOf(BigDecimal.ROUND_CEILING),
                "Bad mapping for ROUND_CEILING");

        Assert.assertEquals(RoundingMode.DOWN, RoundingMode.valueOf(BigDecimal.ROUND_DOWN),
                "Bad mapping for ROUND_DOWN");

        Assert.assertEquals(RoundingMode.FLOOR, RoundingMode.valueOf(BigDecimal.ROUND_FLOOR),
                "Bad mapping for ROUND_FLOOR");

        Assert.assertEquals(RoundingMode.HALF_DOWN, RoundingMode.valueOf(BigDecimal.ROUND_HALF_DOWN),
                "Bad mapping for ROUND_HALF_DOWN");

        Assert.assertEquals(RoundingMode.HALF_EVEN, RoundingMode.valueOf(BigDecimal.ROUND_HALF_EVEN),
                "Bad mapping for ROUND_HALF_EVEN");

        Assert.assertEquals(RoundingMode.HALF_UP, RoundingMode.valueOf(BigDecimal.ROUND_HALF_UP),
                "Bad mapping for ROUND_HALF_UP");

        Assert.assertEquals(RoundingMode.UNNECESSARY, RoundingMode.valueOf(BigDecimal.ROUND_UNNECESSARY),
                "Bad mapping for ROUND_UNNECESSARY");
    }
}
