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

package com.android.cobalt.observations;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.cobalt.PrivateIndexObservation;
import com.google.cobalt.ReportDefinition;
import com.google.common.collect.ImmutableList;
import com.google.common.math.BigDecimalMath;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;

/** Generator for private index observation noise. */
public final class PrivacyGenerator {
    private final SecureRandom mSecureRandom;

    public PrivacyGenerator(SecureRandom secureRandom) {
        this.mSecureRandom = secureRandom;
    }

    /**
     * Generates noise for a report.
     *
     * @param maxIndex the maximum private index value for the report
     * @param reportDefinition the privacy-enabled report containing the privacy parameters
     * @return private indices that include noise according to the report's privacy parameters
     */
    ImmutableList<PrivateIndexObservation> generateNoise(
            int maxIndex, ReportDefinition reportDefinition) {
        checkArgument(maxIndex >= 0, "maxIndex value cannot be negative");
        double lambda = reportDefinition.getPoissonMean();
        checkArgument(lambda > 0, "poisson_mean must be positive, got %s", lambda);

        // To minimize the number of calls to secureRandom, we draw the total number of
        // observations, then we distribute those additional observations over the indices. This is
        // more efficient than drawing from a Poisson distribution once per index when lambda < 1.
        // We expect lambda << 1 in practice.
        double lambdaTimesNumIndex =
                BigDecimalMath.roundToDouble(
                        // (maxIndex + 1) * lambda
                        new BigDecimal(maxIndex)
                                .add(BigDecimal.ONE)
                                .multiply(BigDecimal.valueOf(lambda)),
                        // Set rounding mode to rounding upwards in order to prevent accidentally
                        // using a lower-than-expected noise parameter.
                        RoundingMode.UP);

        int addedOnes = samplePoissonDistribution(lambdaTimesNumIndex);

        ImmutableList.Builder<PrivateIndexObservation> noise = ImmutableList.builder();
        for (int i = 0; i < addedOnes; ++i) {
            noise.add(
                    PrivateIndexObservation.newBuilder()
                            .setIndex(sampleUniformDistribution(maxIndex))
                            .build());
        }

        return noise.build();
    }

    /**
     * Provides samples from the poisson distribution with mean `lambda`.
     *
     * <p>Entropy is provided by a SecureRandom generator. Uses the Inverse transform sampling
     * method, which is good for small lambda, and requires only one secure random generation. We
     * expect lambda << 1 in practice. For more details, see
     * https://en.wikipedia.org/wiki/Poisson_distribution#Random_variate_generation.
     *
     * @param lambda the poisson mean
     * @return the sampled number
     */
    private int samplePoissonDistribution(double lambda) {
        int x = 0;
        double p = Math.exp(-lambda);
        double s = p;
        double u = mSecureRandom.nextDouble();
        while (u > s) {
            x++;
            p = p * lambda / x;
            s = s + p;
        }
        return x;
    }

    /**
     * Provides samples from the uniform distribution over the integers from 0 to `max`, inclusive.
     *
     * <p>Entropy is provided by a SecureRandom generator.
     *
     * @param max the maximum number to generate.
     * @return the sampled number
     */
    private int sampleUniformDistribution(int max) {
        return mSecureRandom.nextInt(max + 1);
    }
}
