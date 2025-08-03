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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.runner.AndroidJUnit4;

import com.google.cobalt.PrivateIndexObservation;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.PrivacyLevel;
import com.google.common.collect.ImmutableList;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;

/**
 * Tests for the PrivacyGenerator that use a real random number generator.
 *
 * <p>Unlike the PrivacyGeneratorTest, which uses a FakeSecureRandom, this test uses a real
 * SecureRandom and analyzes the test results based on the statistical expectations of the resulting
 * data.
 */
@RunWith(AndroidJUnit4.class)
public final class PrivacyGeneratorStatisticalTest {
    private static final ReportDefinition sReportTemplate =
            ReportDefinition.newBuilder()
                    .setId(5)
                    .setPrivacyLevel(PrivacyLevel.LOW_PRIVACY)
                    .build();

    private final SecureRandom mSecureRandom;
    private final PrivacyGenerator mPrivacyGenerator;

    public PrivacyGeneratorStatisticalTest() {
        mSecureRandom = new SecureRandom();
        mPrivacyGenerator = new PrivacyGenerator(mSecureRandom);
    }

    /**
     * To reduce chance of flaky test failure, run the test multiple times, using the majority
     * result.
     *
     * <p>Based on
     * https://github.com/google/differential-privacy/blob/main/java/main/com/google/privacy/differentialprivacy/testing/VotingUtil.java
     *
     * @param numberOfVotes maximum number of times to run the test
     * @param testMethod test method to run, must throw AssertionError for a failure
     */
    private void assertStatisticalTest(int numberOfVotes, Runnable testMethod) throws Exception {
        int acceptVotes = 0;
        int rejectVotes = 0;
        AssertionError lastError = null;
        while (acceptVotes <= numberOfVotes / 2 && rejectVotes <= numberOfVotes / 2) {
            try {
                testMethod.run();
            } catch (AssertionError e) {
                rejectVotes++;
                lastError = e;
                continue;
            }
            acceptVotes++;
        }
        assertWithMessage(
                        "Statistical test failure: majority of attempts failed, last failure was:\n"
                                + "%s",
                        lastError)
                .that(acceptVotes)
                .isGreaterThan(rejectVotes);
    }

    /**
     * Tests that the total indices added is within 3 standard deviations of the expected Poisson
     * mean.
     *
     * @param numTrials the number of times to generate indices
     * @param maxIndex the maximum private index value to generate
     */
    private void totalIndicesAddedTest(int numTrials, int maxIndex, double poissonMean) {
        ReportDefinition report = sReportTemplate.toBuilder().setPoissonMean(poissonMean).build();
        int totalIndicesAdded = 0;
        for (int i = 0; i < numTrials; i++) {
            totalIndicesAdded += mPrivacyGenerator.generateNoise(maxIndex, report).size();
        }
        double stddev = Math.sqrt((double) (numTrials * (1 + maxIndex)) * poissonMean);
        double expectedIndicesAdded = (double) (numTrials * (1 + maxIndex)) * poissonMean;
        assertThat((double) totalIndicesAdded).isWithin(3 * stddev).of(expectedIndicesAdded);
    }

    // Check that the total number of indices added over 1000 trials is within 3 standard deviations
    // of the expected number. Ported from:
    // https://fuchsia.googlesource.com/cobalt/+/refs/heads/main/src/algorithms/privacy/poisson_test.cc
    @Test
    public void testAddNoise_totalNumberIndicesAdded_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> totalIndicesAddedTest(1000, 99, 0.1));
    }

    @Test
    public void testAddNoise_oneIndex_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> totalIndicesAddedTest(1000, 0, 0.1));
    }

    @Test
    public void testAddNoise_smallPoissonMean_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> totalIndicesAddedTest(10000, 99, 0.001));
    }

    /**
     * Tests that the sum of indexOffset calls over a number of samples is approximately equal to
     * the distance times the number of samples.
     *
     * @param numTrials the number of times to sum indexOffset
     * @param distance the distance an approximate index is from the floor index
     */
    private void indexOffsetTest(int numTrials, double distance) {
        int totalSum = 0;
        for (int i = 0; i < numTrials; i++) {
            totalSum += PrivateIndexCalculations.indexOffset(distance, mSecureRandom);
        }
        double stddev = Math.sqrt(numTrials * distance * (1 - distance));
        double expectedSum = numTrials * distance;
        assertThat((double) totalSum).isWithin(3 * stddev).of(expectedSum);
    }

    @Test
    public void testIndexOffset_minimumDistance_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> indexOffsetTest(1000, 0.0));
    }

    @Test
    public void testIndexOffset_maximumDistance_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> indexOffsetTest(1000, 0.999999999999999));
    }

    @Test
    public void testIndexOffset_middleDistance_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> indexOffsetTest(1000, 0.5));
    }

    @Test
    public void testIndexOffset_quarterDistance_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> indexOffsetTest(1000, 0.25));
        assertStatisticalTest(9, () -> indexOffsetTest(1000, 0.75));
    }

    /**
     * Tests that the sum of converting a value to an index then back to a value is approximately
     * equal to the number of trials times the value.
     *
     * @param numTrials the number of times to sum indexOffset
     * @param value the value to convert to an index
     * @param minValue the minimum value
     * @param maxValue the maximum value
     * @param numIndexPoints the number of index points
     */
    private void doubleToIndexTest(
            int numTrials, double value, double minValue, double maxValue, int numIndexPoints) {
        double intervalSize = (maxValue - minValue) / (double) (numIndexPoints - 1);
        double approxIndex = (value - minValue) / intervalSize;
        double lowerIndex = Math.floor(approxIndex);
        double distance = approxIndex - lowerIndex;

        double totalSum = 0;
        for (int i = 0; i < numTrials; i++) {
            // Convert the index back into an approximate value by multiplying by the interval size
            // and adding to the minimum value. Then add that to the total sum.
            totalSum +=
                    (minValue
                            + PrivateIndexCalculations.doubleToIndex(
                                            value,
                                            minValue,
                                            maxValue,
                                            numIndexPoints,
                                            mSecureRandom)
                                    * intervalSize);
        }
        double stddev = Math.sqrt(numTrials * distance * (1 - distance) * intervalSize);
        double expectedSum = numTrials * value;
        assertThat(totalSum).isWithin(3 * stddev).of(expectedSum);
    }

    @Test
    public void testDoubleToIndex_minimumDistance_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> doubleToIndexTest(1000, 5.0, 0.0, 10.0, 11));
    }

    @Test
    public void testDoubleToIndex_halfDistance_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> doubleToIndexTest(1000, 5.5, 0.0, 10.0, 11));
    }

    @Test
    public void testDoubleToIndex_quarterDistance_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> doubleToIndexTest(1000, 5.25, 0.0, 10.0, 11));
        assertStatisticalTest(9, () -> doubleToIndexTest(1000, 5.75, 0.0, 10.0, 11));
    }

    /**
     * Runs a Chi-Squared test on the uniform distribution of the indices that are generated.
     *
     * <p>The distribution of uniformly distributed indices is compared with the 99th percentile of
     * the chi-squared distribution with a degree of freedom equal to the maxIndex.
     *
     * @param numTrials the number of indices to generate
     * @param maxIndex the maximum private index value to generate
     */
    private void distributionIndicesAdded(int numTrials, int maxIndex) {
        // Use a larger Poisson mean so that more uniformly distributed values are generated per
        // call.
        ReportDefinition report = sReportTemplate.toBuilder().setPoissonMean(1.0).build();
        int n = maxIndex + 1;
        int numIndicesAdded = 0;
        int[] indexCounts = new int[n];
        while (numIndicesAdded < numTrials) {
            ImmutableList<PrivateIndexObservation> noisedIndices =
                    mPrivacyGenerator.generateNoise(maxIndex, report);
            for (PrivateIndexObservation o : noisedIndices) {
                numIndicesAdded++;
                indexCounts[(int) o.getIndex()]++;
                if (numIndicesAdded >= numTrials) {
                    break;
                }
            }
        }
        double nullHypothesis = (double) numTrials / n;
        double chiSquared = 0.0;
        for (int i = 0; i < n; i++) {
            chiSquared += Math.pow(indexCounts[i] - nullHypothesis, 2) / nullHypothesis;
        }
        try {
            assertThat(new ChiSquaredDistributionImpl(n - 1).cumulativeProbability(chiSquared))
                    .isLessThan(0.99);
        } catch (MathException e) {
            throw new RuntimeException("Math exception occurred");
        }
    }

    @Test
    public void testAddNoise_distributionOfIndices_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> distributionIndicesAdded(10000, 99));
    }

    @Test
    public void testAddNoise_smallMaxIndex_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> distributionIndicesAdded(1000, 9));
    }

    @Test
    public void testAddNoise_twoIndices_withinExpectedVariance() throws Exception {
        assertStatisticalTest(9, () -> distributionIndicesAdded(1000, 1));
    }
}
