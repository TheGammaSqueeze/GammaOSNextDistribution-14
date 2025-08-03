/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4654323
 * @summary Tests functionality of isProbablePrime(Integer.MAX_VALUE)
 */
package test.java.math.BigInteger;

import java.math.*;

import org.testng.Assert;
import org.testng.annotations.Test;

// Android-changed: Replace error printing with asserts.
public class ProbablePrime {

    @Test
    public void testProbablePrime() {
        BigInteger num = new BigInteger("4");
        int[] certainties = {-1, 0, 1, 2, 100, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        boolean[] expectations = {true, true, false, false, false, false, false};

        for (int i = 0; i < certainties.length; i++) {
            boolean b = num.isProbablePrime(certainties[i]);
            Assert.assertEquals(b, expectations[i], "Unexpected answer " + b +
                        " for certainty " +  certainties[i]);
        }
    }
}
