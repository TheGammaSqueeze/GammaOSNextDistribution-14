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

package com.android.cobalt.data;

import static com.google.common.truth.Truth.assertThat;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.LocalIndexHistogram;
import com.google.cobalt.LocalIndexHistogram.Bucket;
import com.google.common.hash.HashCode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LogAggregatorsTest {
    private static final ReportKey REPORT_KEY = ReportKey.create(0, 1, 2, 3);
    private static final int DAY_INDEX = 4;
    private static final HashCode HASH_CODE = HashCode.fromInt(1234);

    /** Returns an {@link AggregateValue} holding an integer value. */
    private static AggregateValue integerAggregate(long value) {
        return AggregateValue.newBuilder().setIntegerValue(value).build();
    }

    /** Returns an {@link AggregateValue} holding an index histogram with a single bucket. */
    private static AggregateValue indexHistogramAggregate(int index, int count) {
        return AggregateValue.newBuilder()
                .setIndexHistogram(
                        LocalIndexHistogram.newBuilder()
                                .addBuckets(Bucket.newBuilder().setIndex(index).setCount(count)))
                .build();
    }

    /** Returns an {@link AggregateValue} holding an index histogram with two buckets. */
    private static AggregateValue indexHistogramAggregate(
            int index1, int count1, int index2, int count2) {
        return AggregateValue.newBuilder()
                .setIndexHistogram(
                        LocalIndexHistogram.newBuilder()
                                .addBuckets(Bucket.newBuilder().setIndex(index1).setCount(count1))
                                .addBuckets(Bucket.newBuilder().setIndex(index2).setCount(count2)))
                .build();
    }

    @Test
    public void countAggregator_initialValue_returnsValue() {
        LogAggregator<Long> aggregator = LogAggregators.countAggregator();
        assertThat(aggregator.initialValue(5L)).isEqualTo(integerAggregate(5));
    }

    @Test
    public void countAggregator_aggregateValues_sumsIntegerValue() {
        LogAggregator<Long> aggregator = LogAggregators.countAggregator();
        assertThat(aggregator.aggregateValues(5L, integerAggregate(6)))
                .isEqualTo(integerAggregate(11));
    }

    @Test
    public void countAggregator_aggregateValues_notIntegerValue_returnsValue() {
        LogAggregator<Long> aggregator = LogAggregators.countAggregator();
        assertThat(aggregator.aggregateValues(5L, AggregateValue.getDefaultInstance()))
                .isEqualTo(integerAggregate(5));
    }

    @Test
    public void stringIndexAggregator_initialValue_returnsSingleBucketHistogram() {
        LogAggregator<StringHashEntity> aggregator = LogAggregators.stringIndexAggregator();
        assertThat(
                        aggregator.initialValue(
                                StringHashEntity.create(
                                        REPORT_KEY, DAY_INDEX, /* listIndex= */ 5, HASH_CODE)))
                .isEqualTo(indexHistogramAggregate(/* index= */ 5, /* count= */ 1));
    }

    @Test
    public void stringIndexAggregator_aggregateValues_incrementsBucketCount() {
        LogAggregator<StringHashEntity> aggregator = LogAggregators.stringIndexAggregator();
        AggregateValue existing = indexHistogramAggregate(/* index= */ 5, /* count= */ 1);
        assertThat(
                        aggregator.aggregateValues(
                                StringHashEntity.create(
                                        REPORT_KEY, DAY_INDEX, /* listIndex= */ 5, HASH_CODE),
                                existing))
                .isEqualTo(indexHistogramAggregate(/* index= */ 5, /* count= */ 2));
    }

    @Test
    public void stringIndexAggregator_aggregateValues_addsNewBucket() {
        LogAggregator<StringHashEntity> aggregator = LogAggregators.stringIndexAggregator();
        AggregateValue existing = indexHistogramAggregate(/* index= */ 4, /* count= */ 1);
        assertThat(
                        aggregator.aggregateValues(
                                StringHashEntity.create(
                                        REPORT_KEY, DAY_INDEX, /* listIndex= */ 5, HASH_CODE),
                                existing))
                .isEqualTo(
                        indexHistogramAggregate(
                                /* index1= */ 4,
                                /* count1= */ 1,
                                /* index2= */ 5,
                                /* count2= */ 1));
    }

    @Test
    public void
            stringIndexAggregator_aggregateValues_notIndexHistogram_returnsSingleBucketHistogram() {
        LogAggregator<StringHashEntity> aggregator = LogAggregators.stringIndexAggregator();
        assertThat(
                        aggregator.aggregateValues(
                                StringHashEntity.create(
                                        REPORT_KEY, DAY_INDEX, /* listIndex= */ 5, HASH_CODE),
                                AggregateValue.getDefaultInstance()))
                .isEqualTo(indexHistogramAggregate(/* index= */ 5, /* count= */ 1));
    }
}
