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

import static com.android.cobalt.collect.ImmutableHelpers.toImmutableList;

import static java.lang.Math.max;
import static java.lang.Math.min;

import com.android.cobalt.data.EventVector;

import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricDimension;
import com.google.cobalt.ReportDefinition;
import com.google.common.collect.ImmutableList;

import java.security.SecureRandom;
import java.util.List;

/** Functions for calculating private indices for privacy-enabled report. */
final class PrivateIndexCalculations {
    private PrivateIndexCalculations() {}

    /**
     * Encodes a double value into an index.
     *
     * <p>Given a `value`, returns an index in the range [0, `numIndexPoints` - 1] using randomized
     * fixed-point encoding.
     *
     * <p>The `value` is snapped to one of the `numIndexPoints` boundary points of the equal
     * subsegments of the range [`minValue`, `maxValue`]. If `value` is not equal to a boundary
     * point, then `value` is snapped to one of the two nearest boundary points, with probability
     * proportional to its distance from each of those points.
     *
     * @param value the value to convert; it must be in the range [`minValue`, `maxValue`]
     * @param minValue the minimum of `value`
     * @param maxValue the maximum of `value`
     * @param numIndexPoints the number of index points
     * @param secureRandom the random number generator to use
     * @return the index for the given `value`
     */
    static int doubleToIndex(
            double value,
            double minValue,
            double maxValue,
            int numIndexPoints,
            SecureRandom secureRandom) {
        double intervalSize = (maxValue - minValue) / (double) (numIndexPoints - 1);
        double approxIndex = (value - minValue) / intervalSize;
        double lowerIndex = Math.floor(approxIndex);
        double distanceToIndex = approxIndex - lowerIndex;

        return ((int) lowerIndex) + indexOffset(distanceToIndex, secureRandom);
    }

    /**
     * Encodes a long to an index.
     *
     * <p>See {@link doubleToIndex} for a description of the encoding scheme.
     *
     * @param value the value to convert; it must be in the range [`minValue`, `maxValue`]
     * @param minValue the minimum of `value`
     * @param maxValue the maximum of `value`
     * @param numIndexPoints the number of index points
     * @param secureRandom the random number generator to use
     * @return the index for the given `value`
     */
    static int longToIndex(
            long value,
            long minValue,
            long maxValue,
            int numIndexPoints,
            SecureRandom secureRandom) {
        return doubleToIndex(
                (double) value, (double) minValue, (double) maxValue, numIndexPoints, secureRandom);
    }

    /**
     * Provides an index offset based on the distance to the index.
     *
     * <p>When a numerical value is converted to an index, an approximate index is calculated for
     * where that value falls between two indexes. The distance from the floor of that estimate is
     * then used to randomly determine whether to use the lower or upper index. The larger the
     * distance, the higher the probability of returning an offset of 1.
     *
     * @param distance the distance an approximate index is from the floor index. This should be in
     *     the range [0.0, 1.0)
     * @param secureRandom the random number generator to use
     * @return the offset value (either 0 or 1) to use for the given distance
     */
    static int indexOffset(double distance, SecureRandom secureRandom) {
        if (secureRandom.nextDouble() > distance) {
            return 0;
        }
        return 1;
    }

    /** Clips a value between the min and max values of a report */
    static long clipValue(long value, ReportDefinition report) {
        return min(report.getMaxValue(), max(report.getMinValue(), value));
    }

    /**
     * Calculate the total number of possible event vectors for a metric.
     *
     * @param metricDimensions the metric's dimensions
     * @return the total number of possible event vectors
     */
    static int getNumEventVectors(List<MetricDimension> metricDimensions) {
        int numEventVectors = 1;
        for (MetricDimension dimension : metricDimensions) {
            numEventVectors *= getNumEventCodes(dimension);
        }
        return numEventVectors;
    }

    /**
     * Convert an event vector to a private index for a metric.
     *
     * @param eventVector the event vector to convert
     * @param metric the metric that the event vector is for
     * @return the private index
     */
    static int eventVectorToIndex(EventVector eventVector, MetricDefinition metric) {
        int multiplier = 1;
        int result = 0;
        for (int i = 0; i < eventVector.eventCodes().size(); i++) {
            int eventCode = eventVector.eventCodes().get(i);
            MetricDimension dimension = metric.getMetricDimensions(i);
            int index = eventCodeToIndexForDimension(eventCode, dimension);
            result += index * multiplier;
            multiplier *= getNumEventCodes(dimension);
        }
        return result;
    }

    /**
     * Uniquely map a `valueIndex`, `eventVectorIndex` pair to a single index.
     *
     * <p>If `valueIndex` is the result of a call to doubleToIndex() with numIndexPoints =
     * `numIndexPoints`, then the returned index is in the range [0, ((`maxEventVectorIndex` + 1) *
     * `numIndexPoints`) - 1].
     *
     * @param valueIndex the value's index to encode
     * @param eventVectorIndex the event vector's index to encode
     * @param maxEventVectorIndex the maximum event vector index
     * @return a single index
     */
    static int valueAndEventVectorIndicesToIndex(
            int valueIndex, int eventVectorIndex, int maxEventVectorIndex) {
        return valueIndex * (maxEventVectorIndex + 1) + eventVectorIndex;
    }

    private static int eventCodeToIndexForDimension(int eventCode, MetricDimension dimension) {
        // If dimension has a max_event_code, just use the eventCode.
        if (dimension.getMaxEventCode() != 0) {
            if (eventCode > dimension.getMaxEventCode()) {
                throw new PrivacyGenerationException(
                        String.format(
                                "event_code %s is larger than max_event_code %s",
                                eventCode, dimension.getMaxEventCode()));
            }
            return eventCode;
        }
        // Otherwise, find the index of eventCode in the sorted list of enumerated event codes.
        ImmutableList<Integer> dimensionEventCodes = getSortedEnumeratedEventCodes(dimension);
        for (int i = 0; i < dimensionEventCodes.size(); i++) {
            if (eventCode == dimensionEventCodes.get(i)) {
                return i;
            }
        }
        throw new PrivacyGenerationException(
                String.format(
                        "event_code %s was not found in the dimension's event codes", eventCode));
    }

    private static ImmutableList<Integer> getSortedEnumeratedEventCodes(MetricDimension dimension) {
        return dimension.getEventCodesMap().keySet().stream().sorted().collect(toImmutableList());
    }

    private static int getNumEventCodes(MetricDimension dimension) {
        if (dimension.getMaxEventCode() != 0) {
            return dimension.getMaxEventCode() + 1;
        }
        return dimension.getEventCodesCount();
    }
}
