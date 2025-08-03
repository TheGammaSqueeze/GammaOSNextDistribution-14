/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StochasticRoundingUtilTest {
    @Test
    public void testRoundStochasticallyMatchesExpectedArguments() {
        RoundingTestCase[] roundingTestCases =
                new RoundingTestCase[] {
                    new RoundingTestCase(0, 8, 0),
                    new RoundingTestCase(-0, 8, -0),
                    new RoundingTestCase(1, 8, 1),
                    new RoundingTestCase(-1, 8, -1),
                    // infinity passes through
                    new RoundingTestCase(Double.POSITIVE_INFINITY, 7, Double.POSITIVE_INFINITY),
                    new RoundingTestCase(Double.NEGATIVE_INFINITY, 7, Double.NEGATIVE_INFINITY),
                    // not clipped
                    new RoundingTestCase(255, 8, 255),
                    // positive overflow
                    // TODO(b/287134507): delete this after after aligning with chrome's algorithm
                    new RoundingTestCase(2E39, 8, Double.POSITIVE_INFINITY),
                    // TODO(b/287134507): go back to this after aligning with chrome's algorithm
                    // new RoundingTestCase(2E38, 8, Double.POSITIVE_INFINITY),
                    // positive underflow
                    new RoundingTestCase(1e-39, 8, 0),
                    // negative overflow
                    // TODO(b/287134507): delete this after after aligning with chrome's algorithm
                    new RoundingTestCase(-2e39, 8, Double.NEGATIVE_INFINITY),
                    // TODO(b/287134507): go back to this after aligning with chrome's algorithm
                    // new RoundingTestCase(-2e38, 8, Double.NEGATIVE_INFINITY),
                    // negative underflow
                    new RoundingTestCase(-1e-39, 8, -0)
                };

        for (RoundingTestCase testCase : roundingTestCases) {
            assertEquals(
                    String.format("Testcase failed for input: %f", testCase.input),
                    testCase.output,
                    StochasticRoundingUtil.roundStochastically(testCase.input, testCase.numBits),
                    0);
        }
    }

    @Test
    public void testRoundStochasticallyPassesNaN() {
        assertThat(Double.isNaN(StochasticRoundingUtil.roundStochastically(Double.NaN, 8)))
                .isTrue();
    }

    @Test
    public void testRoundStochasticallyIsNonDeterministic() {
        // Since 0.3 can't be represented with 8 bits of precision, this value will be
        // clipped to either the nearest lower number or nearest higher number.
        double input = 0.3;
        Set<Double> seen = new HashSet<>();
        while (seen.size() < 2) {
            double result = StochasticRoundingUtil.roundStochastically(input, 8);
            assertThat(result).isIn(List.of(0.298828125, 0.30078125));
            seen.add(result);
        }
    }

    @Test
    public void testRoundStochasticallyApproximatesTrueSim() {
        // Since 0.3 can't be represented with 8 bits of precision, this value will be
        // clipped randomly. Because 0.3 is 60% of the way from the nearest
        // representable number smaller than it and 40% of the way to the nearest
        // representable number larger than it, the value should be rounded down to
        // 0.2988... 60% of the time and rounded up to 0.30078... 40% of the time.
        // This ensures that if you add the result N times you roughly get 0.3 * N.

        int numIters = 10000;
        double input = 0.3;
        double total = 0;

        for (int i = 0; i < numIters; i++) {
            total += StochasticRoundingUtil.roundStochastically(input, 8);
        }

        assertTrue(total > .9 * input * numIters);
        assertTrue(total < 1.1 * input * numIters);
    }

    @Test
    public void testRoundStochasticallyNegativeNumBitsThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    StochasticRoundingUtil.roundStochastically(0, -1);
                });
    }

    @Test
    public void testRoundStochasticallyGreaterThanMaxNumBitsThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    StochasticRoundingUtil.roundStochastically(0, 66);
                });
    }

    private static final class RoundingTestCase {
        public final double input;
        public final int numBits;
        public final double output;

        RoundingTestCase(double input, int numBits, double output) {
            this.input = input;
            this.numBits = numBits;
            this.output = output;
        }
    }
}
