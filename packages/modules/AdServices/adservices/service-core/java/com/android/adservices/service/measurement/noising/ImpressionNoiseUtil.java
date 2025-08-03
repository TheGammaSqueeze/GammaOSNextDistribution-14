/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.noising;

import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Util class for generating impression noise
 */
public final class ImpressionNoiseUtil {

    private ImpressionNoiseUtil() {}

    /**
     * Randomly generate report configs based on noise params
     *
     * @param noiseParams Noise parameters to use for state generation
     * @param rand random number generator
     * @return list of reporting configs
     */
    public static List<int[]> selectRandomStateAndGenerateReportConfigs(
            ImpressionNoiseParams noiseParams, ThreadLocalRandom rand) {
        // Get total possible combinations
        long numCombinations =
                Combinatorics.getNumberOfStarsAndBarsSequences(
                        /*numStars=*/ noiseParams.getReportCount(),
                        /*numBars=*/ noiseParams.getTriggerDataCardinality()
                                * noiseParams.getReportingWindowCount()
                                * noiseParams.getDestinationTypeMultiplier());
        // Choose a sequence index
        long sequenceIndex = nextLong(rand, numCombinations);
        return getReportConfigsForSequenceIndex(noiseParams, sequenceIndex);
    }

    @VisibleForTesting
    static List<int[]> getReportConfigsForSequenceIndex(
            ImpressionNoiseParams noiseParams, long sequenceIndex) {
        List<int[]> reportConfigs = new ArrayList<>();
        // Get the configuration for the sequenceIndex
        long[] starIndices = Combinatorics.getStarIndices(
                /*numStars=*/noiseParams.getReportCount(),
                /*sequenceIndex=*/sequenceIndex);
        long[] barsPrecedingEachStar = Combinatorics.getBarsPrecedingEachStar(starIndices);
        // Generate fake reports
        // Stars: number of reports
        // Bars: (Number of windows) * (Trigger data cardinality) * (Destination multiplier)
        for (long numBars : barsPrecedingEachStar) {
            if (numBars == 0L) {
                continue;
            }

            // Extract bits for trigger data, destination type and windowIndex from encoded numBars
            int[] reportConfig = createReportingConfig(numBars, noiseParams);
            reportConfigs.add(reportConfig);
        }
        return reportConfigs;
    }

    /**
     * Extract bits for trigger data, destination type and windowIndex from encoded numBars.
     *
     * @param numBars data encoding triggerData, destinationType and window index
     * @param noiseParams noise params
     * @return array having triggerData, destinationType and windowIndex
     */
    private static int[] createReportingConfig(long numBars, ImpressionNoiseParams noiseParams) {
        long triggerData = (numBars - 1L) % ((long) noiseParams.getTriggerDataCardinality());
        long remainingData = (numBars - 1L) / ((long) noiseParams.getTriggerDataCardinality());

        int reportingWindowIndex = ((int) remainingData) % noiseParams.getReportingWindowCount();
        int destinationTypeIndex = ((int) remainingData) / noiseParams.getReportingWindowCount();
        return new int[] {(int) triggerData, reportingWindowIndex, destinationTypeIndex};
    }

    /**
     * Randomly generate report configs based on noise params
     *
     * @param triggerSpecs trigger specs to use for state generation
     * @param destinationMultiplier destination multiplier
     * @param rand random number generator
     * @return list of reporting configs
     */
    public static List<int[]> selectFlexEventReportRandomStateAndGenerateReportConfigs(
            TriggerSpecs triggerSpecs, int destinationMultiplier, ThreadLocalRandom rand) {

        // Assumes trigger specs already built privacy parameters.
        int[][] params = triggerSpecs.getPrivacyParamsForComputation();
        // Doubling the window cap for each trigger data type correlates with counting report states
        // that treat having a web destination as different from an app destination.
        int[] updatedPerTypeNumWindowList = new int[params[1].length];
        for (int i = 0; i < params[1].length; i++) {
            updatedPerTypeNumWindowList[i] = params[1][i] * destinationMultiplier;
        }
        long numStates =
                Combinatorics.getNumStatesFlexApi(
                        params[0][0], updatedPerTypeNumWindowList, params[2]);
        long sequenceIndex = nextLong(rand, numStates);
        List<Combinatorics.AtomReportState> rawFakeReports =
                Combinatorics.getReportSetBasedOnRank(
                        params[0][0],
                        updatedPerTypeNumWindowList,
                        params[2],
                        sequenceIndex,
                        new HashMap<>());
        List<int[]> fakeReportConfigs = new ArrayList<>();
        for (Combinatorics.AtomReportState rawFakeReport : rawFakeReports) {
            int[] fakeReportConfig = new int[3];
            fakeReportConfig[0] = rawFakeReport.getTriggerDataType();
            fakeReportConfig[1] = (rawFakeReport.getWindowIndex()) / destinationMultiplier;
            fakeReportConfig[2] = (rawFakeReport.getWindowIndex()) % destinationMultiplier;
            fakeReportConfigs.add(fakeReportConfig);
        }
        return fakeReportConfigs;
    }

    /** Wrapper for calls to ThreadLocalRandom visible for testing */
    @VisibleForTesting
    public static long nextLong(ThreadLocalRandom rand, long bound) {
        return rand.nextLong(bound);
    }
}
