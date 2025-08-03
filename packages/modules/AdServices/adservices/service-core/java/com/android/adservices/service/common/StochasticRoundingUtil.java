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

import static java.lang.Math.abs;

import com.android.internal.util.Preconditions;

/** Utility class to perform stochastic rounding. */
public final class StochasticRoundingUtil {
    private static final int MIN_8_BIT_INT_VALUE = -128;
    private static final int MAX_8_BIT_INT_VALUE = 127;

    // Number of bits in a double is 64, we can't round down to less than that.
    private static final int MAX_POSSIBLE_ROUNDING_BITS = 64;

    private static final String NUM_BITS_MIN_EXCEEDED =
            "Number of bits to round to must be greater than 0.";
    private static final String NUM_BITS_MAX_EXCEEDED =
            "Number of bits to round to must be less than the number of bits in a double.";

    private StochasticRoundingUtil() throws IllegalAccessException {
        throw new IllegalAccessException("This class cannot be instantiated!");
    }

    /**
     * Rounds a double stochastically to a given number of bits.
     *
     * <p>In Stochastic rounding, we have two options. If the number cannot be represented in {@code
     * numBits}, we either round the double up or down to a number that can be represented in {@code
     * numBits}.
     *
     * <p>Whether we round up or down is dependent on how far {@code value} is from the closest
     * smaller and closest larger numbers that can be represented in {@code numBits}.
     *
     * <p>Essentially, the closer {@code value} is to the smaller value, the more likely it is to be
     * rounded down. Conversely, the closer it is to the larger value, the more likely it is to be
     * rounded up.
     *
     * <p>This algorithm can be implemented in many ways, but we have based the implementation on
     * Chrome's example <a
     * href="https://source.chromium.org/chromium/chromium/src/+/main:content/browser
     * /interest_group/interest_group_auction_reporter.cc;l=259;bpv=0;bpt=1">...</a> to maintain
     * consistency.
     */
    public static double roundStochastically(double value, int numBits) {
        Preconditions.checkArgument(numBits > 0, NUM_BITS_MIN_EXCEEDED);
        Preconditions.checkArgument(numBits <= MAX_POSSIBLE_ROUNDING_BITS, NUM_BITS_MAX_EXCEEDED);

        if (!Double.isFinite(value)) {
            return value;
        }
        int exponent = log2(abs(value));
        double mantissa = value * Math.pow(2.0, -exponent);

        if (exponent < MIN_8_BIT_INT_VALUE) {
            return Math.copySign(0, value);
        }

        if (exponent > MAX_8_BIT_INT_VALUE) {
            return Math.copySign(Double.POSITIVE_INFINITY, value);
        }

        double precisionScaledValue = ldexp(mantissa, numBits);
        double noisyScaledValue = precisionScaledValue + (0.5f * Math.random());
        double truncatedScaledValue = Math.floor(noisyScaledValue);

        return ldexp(truncatedScaledValue, exponent - numBits);
    }

    /**
     * Mimics the C Library function <a
     * href="https://www.tutorialspoint.com/c_standard_library/c_function_ldexp.htm">...</a>.
     */
    private static double ldexp(double x, int exponent) {
        return x * Math.pow(2, exponent);
    }

    private static int log2(double x) {
        return (int) (Math.log(x) / Math.log(2));
    }
}
