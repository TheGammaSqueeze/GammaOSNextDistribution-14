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

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;

import com.android.cobalt.data.EventVector;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.PrivateIndexObservation;
import com.google.cobalt.ReportDefinition;

import java.security.SecureRandom;

/**
 * Encodes an integer event as an {@link PrivateIndexObservation}.
 *
 * <p>Note, this encoder expects input {@link AggregateValue} objects to have an inner integer value
 * set and will use 0 if not.
 */
final class PrivateIntegerEncoder implements PrivateObservationGenerator.Encoder {
    private final MetricDefinition mMetric;
    private final ReportDefinition mReport;
    private final SecureRandom mSecureRandom;

    PrivateIntegerEncoder(
            @NonNull SecureRandom secureRandom,
            @NonNull MetricDefinition metric,
            @NonNull ReportDefinition report) {
        this.mSecureRandom = requireNonNull(secureRandom);
        this.mMetric = requireNonNull(metric);
        this.mReport = requireNonNull(report);
    }

    /**
     * Encodes one event and aggregated value as a single private observation.
     *
     * @param eventVector the event vector to encode
     * @param aggregateValue the aggregated value to encode
     * @return the privacy encoded observation
     */
    @Override
    public PrivateIndexObservation encode(EventVector eventVector, AggregateValue aggregateValue) {
        int maxEventVectorIndex =
                PrivateIndexCalculations.getNumEventVectors(mMetric.getMetricDimensionsList()) - 1;
        int eventVectorIndex = PrivateIndexCalculations.eventVectorToIndex(eventVector, mMetric);

        long clippedValue =
                PrivateIndexCalculations.clipValue(aggregateValue.getIntegerValue(), mReport);
        int clippedValueIndex =
                PrivateIndexCalculations.longToIndex(
                        clippedValue,
                        mReport.getMinValue(),
                        mReport.getMaxValue(),
                        mReport.getNumIndexPoints(),
                        mSecureRandom);
        int privateIndex =
                PrivateIndexCalculations.valueAndEventVectorIndicesToIndex(
                        clippedValueIndex, eventVectorIndex, maxEventVectorIndex);
        return PrivateIndexObservation.newBuilder().setIndex(privateIndex).build();
    }
}
