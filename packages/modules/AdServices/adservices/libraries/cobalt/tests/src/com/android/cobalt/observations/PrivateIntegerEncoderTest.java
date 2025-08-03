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

import com.android.cobalt.data.EventVector;
import com.android.cobalt.observations.testing.FakeSecureRandom;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricDimension;
import com.google.cobalt.PrivateIndexObservation;
import com.google.cobalt.ReportDefinition;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.SecureRandom;

@RunWith(JUnit4.class)
public final class PrivateIntegerEncoderTest {
    private static final SecureRandom SECURE_RANDOM = new FakeSecureRandom();

    @Test
    public void encodesAsPrivateIndex() throws Exception {
        // Use a metric with 6 possible event vectors: [(0,5), (1,5), (2,5), (0,6), (1,6), (2,6)].
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .addMetricDimensions(MetricDimension.newBuilder().setMaxEventCode(2))
                        .addMetricDimensions(
                                MetricDimension.newBuilder()
                                        .putEventCodes(5, "5")
                                        .putEventCodes(6, "6"))
                        .build();

        // Use a report with 21 possible values and 11 index points so private index encoding of a
        // value `v` is always `6 * floor(v/2) + eventIndex` where `eventIndex` is the index of the
        // event vector in the above list.
        ReportDefinition report =
                ReportDefinition.newBuilder()
                        .setNumIndexPoints(11)
                        .setMinValue(0)
                        .setMaxValue(20)
                        .build();
        PrivateIntegerEncoder encoder = new PrivateIntegerEncoder(SECURE_RANDOM, metric, report);
        assertThat(
                        encoder.encode(
                                EventVector.create(1, 5),
                                AggregateValue.newBuilder().setIntegerValue(3).build()))
                .isEqualTo(PrivateIndexObservation.newBuilder().setIndex(7).build());
        assertThat(
                        encoder.encode(
                                EventVector.create(2, 6),
                                AggregateValue.newBuilder().setIntegerValue(17).build()))
                .isEqualTo(PrivateIndexObservation.newBuilder().setIndex(53).build());
    }

    @Test
    public void valueBelowMinimum_encodedAsMinimumValue() throws Exception {
        // Use a metric with 6 possible event vectors: [(0,5), (1,5), (2,5), (0,6), (1,6), (2,6)].
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .addMetricDimensions(MetricDimension.newBuilder().setMaxEventCode(2))
                        .addMetricDimensions(
                                MetricDimension.newBuilder()
                                        .putEventCodes(5, "5")
                                        .putEventCodes(6, "6"))
                        .build();

        // Use a report with 21 possible values and 11 index points so private index encoding of a
        // value `v` is always `6 * floor(v/2) + eventIndex` where `eventIndex` is the index of the
        // event vector in the above list.
        ReportDefinition report =
                ReportDefinition.newBuilder()
                        .setNumIndexPoints(11)
                        .setMinValue(0)
                        .setMaxValue(20)
                        .build();
        PrivateIntegerEncoder encoder = new PrivateIntegerEncoder(SECURE_RANDOM, metric, report);
        assertThat(
                        encoder.encode(
                                EventVector.create(1, 5),
                                AggregateValue.newBuilder().setIntegerValue(-1).build()))
                .isEqualTo(
                        encoder.encode(
                                EventVector.create(1, 5),
                                AggregateValue.newBuilder().setIntegerValue(0).build()));
    }

    @Test
    public void valueAboveMaximum_encodedAsMaximumValue() throws Exception {
        // Use a metric with 6 possible event vectors: [(0,5), (1,5), (2,5), (0,6), (1,6), (2,6)].
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .addMetricDimensions(MetricDimension.newBuilder().setMaxEventCode(2))
                        .addMetricDimensions(
                                MetricDimension.newBuilder()
                                        .putEventCodes(5, "5")
                                        .putEventCodes(6, "6"))
                        .build();

        // Use a report with 21 possible values and 11 index points so private index encoding of a
        // value `v` is always `6 * floor(v/2) + eventIndex` where `eventIndex` is the index of the
        // event vector in the above list.
        ReportDefinition report =
                ReportDefinition.newBuilder()
                        .setNumIndexPoints(11)
                        .setMinValue(0)
                        .setMaxValue(20)
                        .build();
        PrivateIntegerEncoder encoder = new PrivateIntegerEncoder(SECURE_RANDOM, metric, report);
        assertThat(
                        encoder.encode(
                                EventVector.create(1, 5),
                                AggregateValue.newBuilder().setIntegerValue(21).build()))
                .isEqualTo(
                        encoder.encode(
                                EventVector.create(1, 5),
                                AggregateValue.newBuilder().setIntegerValue(20).build()));
    }
}
