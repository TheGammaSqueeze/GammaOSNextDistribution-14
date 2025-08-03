/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main PrimeTest
 * @bug 8026236 8074460 8078672
 * @summary test primality verification methods in BigInteger (use -Dseed=X to set PRNG seed)
 * @author bpb
 * @key randomness
 */
package test.java.math.BigInteger;

import java.math.BigInteger;
import java.util.BitSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import android.platform.test.annotations.LargeTest;

import org.testng.Assert;
import org.testng.annotations.Test;

// Android-changed: Replace error printing with asserts.
public class PrimeTest {

    private static final int DEFAULT_UPPER_BOUND = 1299709; // 100000th prime
    private static final int DEFAULT_CERTAINTY = 100;
    private static final int NUM_NON_PRIMES = 10000;

    @LargeTest
    @Test
    public void testPrimes() throws Exception {
        // Get primes through specified bound (inclusive) and Integer.MAX_VALUE
        NavigableSet<BigInteger> primes = getPrimes();

        // Check whether known primes are identified as such
        checkPrime(primes, DEFAULT_CERTAINTY);

        // Check whether known non-primes are not identified as primes
        checkNonPrime(primes, DEFAULT_CERTAINTY);

        checkMersennePrimes(DEFAULT_CERTAINTY);
    }

    /**
     * Create a {@code BitSet} wherein a set bit indicates the corresponding
     * index plus 2 is prime. That is, if bit N is set, then the integer N + 2
     * is prime. The values 0 and 1 are intentionally excluded. See the
     * <a
     * href="http://en.wikipedia.org/wiki/Sieve_of_Eratosthenes#Algorithm_description">
     * Sieve of Eratosthenes</a> algorithm description for more information.
     *
     * @return bits indicating which indexes represent primes
     */
    private static BitSet createPrimes() {
        int nbits = PrimeTest.DEFAULT_UPPER_BOUND - 1;
        BitSet bs = new BitSet(nbits);
        for (int p = 2; p * p < PrimeTest.DEFAULT_UPPER_BOUND;) {
            for (int i = p * p; i < nbits + 2; i += p) {
                bs.set(i - 2, true);
            }
            do {
                ++p;
            } while (p > 1 && bs.get(p - 2));
        }
        bs.flip(0, nbits);
        return bs;
    }

    /**
     * Load the primes up to the specified bound (inclusive) into a
     * {@code NavigableSet}, appending the prime {@code Integer.MAX_VALUE}.
     *
     * @return a set of primes
     */
    private static NavigableSet<BigInteger> getPrimes() {
        BitSet bs = createPrimes();
        NavigableSet<BigInteger> primes = bs.stream()
                .mapToObj(p -> BigInteger.valueOf(p + 2))
                .collect(toCollection(TreeSet::new));
        primes.add(BigInteger.valueOf(Integer.MAX_VALUE));
        return primes;
    }

    /**
     * Verifies whether the fraction of probable primes detected is at least 1 -
     * 1/2^certainty.
     */
    private static void checkPrime(Set<BigInteger> primes, int certainty) {
        long probablePrimes = (primes.parallelStream())
                .filter(bi -> bi.isProbablePrime(certainty))
                .count();

        // N = certainty / 2
        // Success if p/t >= 1 - 1/4^N
        // or (p/t)*4^N >= 4^N - 1
        // or p*4^N >= t*(4^N - 1)
        BigInteger p = BigInteger.valueOf(probablePrimes);
        BigInteger t = BigInteger.valueOf(primes.size());
        BigInteger fourToTheC = BigInteger.valueOf(4).pow(certainty / 2);
        BigInteger fourToTheCMinusOne = fourToTheC.subtract(BigInteger.ONE);
        BigInteger left = p.multiply(fourToTheC);
        BigInteger right = t.multiply(fourToTheCMinusOne);

        Assert.assertFalse(left.compareTo(right) < 0,
                "Probable prime certainty test failed");
    }

    /**
     * Verifies whether all {@code BigInteger}s in the tested range for which
     * {@code isProbablePrime()} returns {@code false} are <i>not</i>
     * prime numbers.
     */
    private static void checkNonPrime(NavigableSet<BigInteger> primes, int certainty) {
        int maxPrime = DEFAULT_UPPER_BOUND;
        try {
            maxPrime = primes.last().intValueExact();
        } catch (ArithmeticException e) {
            // ignore it
        }

        // Create a list of non-prime BigIntegers.
        SplittableRandom splitRandom = new SplittableRandom(0L);
        List<BigInteger> nonPrimeBigInts = (splitRandom)
                .ints(NUM_NON_PRIMES, 2, maxPrime).mapToObj(BigInteger::valueOf)
                .filter(b -> !b.isProbablePrime(certainty)).collect(toList());

        // If there are any non-probable primes also in the primes list then fail.
        boolean failed = nonPrimeBigInts.stream().anyMatch(primes::contains);

        // In the event, print which purported non-primes were actually prime.
        if (failed) {
            for (BigInteger bigInt : nonPrimeBigInts) {
                if (primes.contains(bigInt)) {
                    Assert.fail("Prime value thought to be non-prime: " + bigInt);
                }
            }
        }
    }

    /**
     * Verifies whether a specified subset of Mersenne primes are correctly
     * identified as being prime. See
     * <a href="https://en.wikipedia.org/wiki/Mersenne_prime">Mersenne prime</a>
     * for more information.
     */
    private static void checkMersennePrimes(int certainty) {
        int[] MERSENNE_EXPONENTS = {
            2, 3, 5, 7, 13, 17, 19, 31, 61, 89, 107, 127, 521, 607, 1279, 2203,
            2281, 3217, 4253, // uncomment remaining array elements to make this test run a long time
            /* 4423, 9689, 9941, 11213, 19937, 21701, 23209, 44497,
            86243, 110503, 132049, 216091, 756839, 859433, 1257787, 1398269,
            2976221, 3021377, 6972593, 13466917, 20996011, 24036583, 25964951,
            30402457, 32582657, 37156667, 42643801, 43112609, 57885161 */
        };

        for (int n : MERSENNE_EXPONENTS) {
            BigInteger mp = BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE);
            Assert.assertTrue(mp.isProbablePrime(certainty),
                    "Mp with p = "+n+" not classified as prime");
        }
    }
}
