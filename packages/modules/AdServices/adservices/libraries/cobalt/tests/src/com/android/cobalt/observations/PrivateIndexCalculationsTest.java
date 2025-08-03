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

import androidx.test.runner.AndroidJUnit4;

import com.android.cobalt.data.EventVector;
import com.android.cobalt.observations.testing.FakeSecureRandom;

import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricDimension;
import com.google.cobalt.ReportDefinition;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class PrivateIndexCalculationsTest {
    private final SecureRandom mSecureRandom = new FakeSecureRandom();

    public PrivateIndexCalculationsTest() {}

    @Test
    public void testDoubleToIndex_maxValue_equalsMaxIndex() throws Exception {
        double minValue = -4.0;
        double maxValue = 6.0;
        int numIndexPoints = 6;
        double value = 6.0;
        int expectedIndex = 5;

        assertThat(
                        PrivateIndexCalculations.doubleToIndex(
                                value, minValue, maxValue, numIndexPoints, mSecureRandom))
                .isEqualTo(expectedIndex);
    }

    @Test
    public void testDoubleToIndex_minValue_equalsMinIndex() throws Exception {
        double minValue = -4.0;
        double maxValue = 6.0;
        int numIndexPoints = 6;
        double value = -4.0;
        int expectedIndex = 0;

        assertThat(
                        PrivateIndexCalculations.doubleToIndex(
                                value, minValue, maxValue, numIndexPoints, mSecureRandom))
                .isEqualTo(expectedIndex);
    }

    @Test
    public void testDoubleToIndex_middleValue_equalsInteriorIndex() throws Exception {
        double minValue = -4.0;
        double maxValue = 6.0;
        int numIndexPoints = 6;
        double value = -2.0;
        int expectedIndex = 1;

        assertThat(
                        PrivateIndexCalculations.doubleToIndex(
                                value, minValue, maxValue, numIndexPoints, mSecureRandom))
                .isEqualTo(expectedIndex);
    }

    @Test
    public void testDoubleToIndex_distanceAboveLambda_returnsUpperIndex() throws Exception {
        double minValue = -4.0;
        double maxValue = 6.0;
        int numIndexPoints = 11;
        // Choose a value so that distanceToIndex = 0.91. Since FakeSecureRandom returns 0.905 for
        // nextDouble(), this value should always cause an upper index to be returned.
        double value = -2.09;
        int expectedIndex = 2;

        assertThat(
                        PrivateIndexCalculations.doubleToIndex(
                                value, minValue, maxValue, numIndexPoints, mSecureRandom))
                .isEqualTo(expectedIndex);
    }

    @Test
    public void testDoubleToIndex_distanceBelowLambda_returnsLowerIndex() throws Exception {
        double minValue = -4.0;
        double maxValue = 6.0;
        int numIndexPoints = 11;
        // Choose a value so that distanceToIndex = 0.90. Since FakeSecureRandom returns 0.905 for
        // nextDouble(), this value should always cause a lower index to be returned.
        double value = -2.10;
        int expectedIndex = 1;

        assertThat(
                        PrivateIndexCalculations.doubleToIndex(
                                value, minValue, maxValue, numIndexPoints, mSecureRandom))
                .isEqualTo(expectedIndex);
    }

    @Test
    public void testClipValue_allCases_expectedResult() throws Exception {
        ReportDefinition report =
                ReportDefinition.newBuilder().setId(1).setMinValue(5).setMaxValue(10).build();
        assertThat(PrivateIndexCalculations.clipValue(2, report)).isEqualTo(5);
        assertThat(PrivateIndexCalculations.clipValue(7, report)).isEqualTo(7);
        assertThat(PrivateIndexCalculations.clipValue(15, report)).isEqualTo(10);
    }

    @Test
    public void testGetNumEventVectors_empty_nonZero() throws Exception {
        assertThat(PrivateIndexCalculations.getNumEventVectors(List.of())).isEqualTo(1);
    }

    @Test
    public void testGetNumEventVectors_oneDimensionWithEventCodes_countsEventCodes()
            throws Exception {
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(
                                        MetricDimension.newBuilder()
                                                .putEventCodes(5, "5")
                                                .build())))
                .isEqualTo(1);
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(
                                        MetricDimension.newBuilder()
                                                .putEventCodes(5, "5")
                                                .putEventCodes(6, "6")
                                                .build())))
                .isEqualTo(2);
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(
                                        MetricDimension.newBuilder()
                                                .putEventCodes(5, "5")
                                                .putEventCodes(7, "7")
                                                .putEventCodes(9, "9")
                                                .build())))
                .isEqualTo(3);
    }

    @Test
    public void testGetNumEventVectors_oneDimensionWithMaxEventCode_usesMaxEventCode()
            throws Exception {
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(MetricDimension.newBuilder().setMaxEventCode(1).build())))
                .isEqualTo(2);
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(MetricDimension.newBuilder().setMaxEventCode(2).build())))
                .isEqualTo(3);
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(
                                        MetricDimension.newBuilder()
                                                .setMaxEventCode(1000)
                                                .build())))
                .isEqualTo(1001);
    }

    @Test
    public void testGetNumEventVectors_oneDimensionWithBothEventCodesAndMax_usesMaxEventCode()
            throws Exception {
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(
                                        MetricDimension.newBuilder()
                                                .putEventCodes(5, "5")
                                                .putEventCodes(6, "6")
                                                .setMaxEventCode(1000)
                                                .build())))
                .isEqualTo(1001);
    }

    @Test
    public void testGetNumEventVectors_multipleDimensionsWithEventCodes_multiplied()
            throws Exception {
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(
                                        MetricDimension.newBuilder().putEventCodes(5, "5").build(),
                                        MetricDimension.newBuilder()
                                                .putEventCodes(6, "6")
                                                .build())))
                .isEqualTo(1);
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(
                                        MetricDimension.newBuilder()
                                                .putEventCodes(5, "5")
                                                .putEventCodes(6, "6")
                                                .build(),
                                        MetricDimension.newBuilder()
                                                .putEventCodes(5, "5")
                                                .putEventCodes(7, "7")
                                                .putEventCodes(9, "9")
                                                .build())))
                .isEqualTo(6);
    }

    @Test
    public void testGetNumEventVectors_multipleDimensionWithMaxEventCode_multiplied()
            throws Exception {
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(
                                        MetricDimension.newBuilder().setMaxEventCode(1).build(),
                                        MetricDimension.newBuilder().setMaxEventCode(2).build())))
                .isEqualTo(6);
    }

    @Test
    public void testGetNumEventVectors_multipleDimensionsWithMixedEventCodesAndMax_multiplied()
            throws Exception {
        assertThat(
                        PrivateIndexCalculations.getNumEventVectors(
                                List.of(
                                        MetricDimension.newBuilder()
                                                .putEventCodes(5, "5")
                                                .putEventCodes(6, "6")
                                                .build(),
                                        MetricDimension.newBuilder().setMaxEventCode(2).build())))
                .isEqualTo(6);
    }

    @Test
    public void testEventVectorToIndex_oneEventCode_correctIndex() throws Exception {
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .addMetricDimensions(
                                MetricDimension.newBuilder()
                                        .putEventCodes(5, "5")
                                        .putEventCodes(6, "6"))
                        .build();
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(5), metric))
                .isEqualTo(0);
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(6), metric))
                .isEqualTo(1);
    }

    @Test
    public void testEventVectorToIndex_oneEventCodeWithMax_correctIndex() throws Exception {
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .addMetricDimensions(MetricDimension.newBuilder().setMaxEventCode(10))
                        .build();
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(5), metric))
                .isEqualTo(5);
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(6), metric))
                .isEqualTo(6);
    }

    @Test
    public void testEventVectorToIndex_twoEventCodes_correctIndex() throws Exception {
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .addMetricDimensions(
                                MetricDimension.newBuilder()
                                        .putEventCodes(5, "5")
                                        .putEventCodes(6, "6"))
                        .addMetricDimensions(
                                MetricDimension.newBuilder()
                                        .putEventCodes(5, "5")
                                        .putEventCodes(6, "6"))
                        .build();
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(5, 5), metric))
                .isEqualTo(0);
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(6, 5), metric))
                .isEqualTo(1);
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(5, 6), metric))
                .isEqualTo(2);
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(6, 6), metric))
                .isEqualTo(3);
    }

    @Test
    public void testEventVectorToIndex_twoEventCodesOneWithMax_correctIndex() throws Exception {
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .addMetricDimensions(MetricDimension.newBuilder().setMaxEventCode(10))
                        .addMetricDimensions(
                                MetricDimension.newBuilder()
                                        .putEventCodes(5, "5")
                                        .putEventCodes(6, "6"))
                        .build();
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(9, 5), metric))
                .isEqualTo(9); // (0-10,5) -> 0-10
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(9, 6), metric))
                .isEqualTo(20); // (0-10,6) -> 11-21
    }

    @Test
    public void testEventVectorToIndex_twoDimensionOneEventCode_correctIndex() throws Exception {
        MetricDefinition metric =
                MetricDefinition.newBuilder()
                        .addMetricDimensions(
                                MetricDimension.newBuilder()
                                        .putEventCodes(5, "5")
                                        .putEventCodes(6, "6"))
                        .addMetricDimensions(MetricDimension.newBuilder().putEventCodes(7, "7"))
                        .build();
        assertThat(PrivateIndexCalculations.eventVectorToIndex(EventVector.create(6), metric))
                .isEqualTo(1);
    }
}
