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
package com.android.server.healthconnect.storage.datatypehelpers.aggregation;

import static com.android.server.healthconnect.storage.datatypehelpers.aggregation.PriorityAggregationTestDataFactory.createStepsData;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class PriorityAggregationDataComparisonTest {
    @Test
    public void testCompareTwoIntervals_orderByPriority() {
        assertThat(
                        createStepsData(10, 20, 15, 1, 1)
                                .compareTo(createStepsData(10, 20, 20, 100, 10)))
                .isLessThan(0);

        assertThat(createStepsData(10, 20, 15, 10, 1).compareTo(createStepsData(10, 20, 20, 1, 10)))
                .isGreaterThan(0);
    }

    @Test
    public void testCompareTwoIntervals_samePriority_orderByLastModified() {
        assertThat(createStepsData(10, 20, 15, 1, 1).compareTo(createStepsData(10, 20, 20, 1, 10)))
                .isLessThan(0);

        assertThat(createStepsData(10, 20, 15, 1, 10).compareTo(createStepsData(10, 20, 20, 1, 1)))
                .isGreaterThan(0);
    }

    @Test
    public void testCompareTimestamps_orderByTime() {
        AggregationRecordData data1 = createStepsData(10, 15, 15, 1, 1);
        AggregationRecordData data2 = createStepsData(12, 20, 20, 1, 10);

        assertThat(data1.getStartTimestamp().compareTo(data2.getStartTimestamp())).isLessThan(0);
        assertThat(data1.getStartTimestamp().compareTo(data2.getStartTimestamp())).isLessThan(0);
        assertThat(data2.getStartTimestamp().compareTo(data1.getEndTimestamp())).isLessThan(0);
        assertThat(data1.getEndTimestamp().compareTo(data2.getStartTimestamp())).isGreaterThan(0);
        assertThat(data2.getStartTimestamp().compareTo(data1.getStartTimestamp())).isGreaterThan(0);

        AggregationTimestamp border =
                new AggregationTimestamp(AggregationTimestamp.GROUP_BORDER, 13L);
        AggregationTimestamp border2 =
                new AggregationTimestamp(AggregationTimestamp.GROUP_BORDER, 15L);

        assertThat(data2.getStartTimestamp().compareTo(border)).isLessThan(0);
        assertThat(data2.getEndTimestamp().compareTo(border)).isGreaterThan(0);
        assertThat(border.compareTo(border2)).isLessThan(0);
    }

    @Test
    public void testCompareTimestamps_timestampsWithDifferentParentDataAreNotEqual() {
        AggregationRecordData data1 = createStepsData(10, 20, 15, 1, 1);
        AggregationRecordData data2 = createStepsData(10, 20, 15, 1, 10);

        assertThat(data1.getStartTimestamp().compareTo(data2.getStartTimestamp())).isNotEqualTo(0);
        assertThat(data1.getEndTimestamp().compareTo(data2.getEndTimestamp())).isLessThan(0);
    }
}
