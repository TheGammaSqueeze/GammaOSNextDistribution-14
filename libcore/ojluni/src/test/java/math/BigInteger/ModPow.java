/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4181191 8215759
 * @summary test BigInteger modPow method (use -Dseed=X to set PRNG seed)
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main ModPow
 * @key randomness
 */
package test.java.math.BigInteger;

import java.math.BigInteger;
import java.util.Random;
import jdk.test.lib.RandomFactory;

import org.testng.Assert;
import org.testng.annotations.Test;

// Android-changed: Replace error printing with asserts.
public class ModPow {

    @Test
    public void testModPow() {
        Random rnd = new Random(1234);

        for (int i=0; i<2000; i++) {
            // Clamp random modulus to a positive value or modPow() will
            // throw an ArithmeticException.
            BigInteger m = new BigInteger(800, rnd).max(BigInteger.ONE);
            BigInteger base = new BigInteger(16, rnd);
            if (rnd.nextInt() % 2 == 0)
                base = base.negate();
            BigInteger exp = new BigInteger(8, rnd);

            BigInteger z = base.modPow(exp, m);
            BigInteger w = base.pow(exp.intValue()).mod(m);
            Assert.assertEquals(z, w,
                    base +" ** " + exp + " mod "+ m + " modPow : " + z + "pow.mod: " + w);
        }
    }
}
