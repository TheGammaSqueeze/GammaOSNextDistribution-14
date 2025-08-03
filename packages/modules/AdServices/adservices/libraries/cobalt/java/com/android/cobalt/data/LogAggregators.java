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

import com.google.cobalt.AggregateValue;
import com.google.cobalt.LocalIndexHistogram;
import com.google.cobalt.LocalIndexHistogram.Bucket;

/** Top-level class to organize various {@link LogAggregator} implementations. */
final class LogAggregators {

    /** Returns a {@link LogAggregator} that aggregates count values. */
    static LogAggregator<Long> countAggregator() {
        return new CountAggregator();
    }

    /**
     * Returns a {@link LogAggregator} that aggregates string indices against {@link
     * LocalIndexHistogram} values.
     */
    static LogAggregator<StringHashEntity> stringIndexAggregator() {
        return new StringIndexAggregator();
    }

    /**
     * Generic implementation of {@link LogAggregator} that handles converting types and aggregating
     * values on behalf.
     *
     * <p>The main benefit of the class is it can hide the type of Stored so logging paths don't
     * need to be parameterized with a type other than ToAggregate.
     *
     * @param <ToAggregate> the type of the value being aggregated against an {@link AggregateValue}
     *     in the {@link DataService}
     * @param <Stored> the type of the value wrapped by {@link AggregateValue}
     */
    private abstract static class LogAggregatorImpl<ToAggregate, Stored>
            implements LogAggregator<ToAggregate> {
        @Override
        public final AggregateValue initialValue(ToAggregate toAggregate) {
            return toAggregate(getInitial(toAggregate));
        }

        @Override
        public final AggregateValue aggregateValues(
                ToAggregate toAggregate, AggregateValue existing) {
            return toAggregate(aggregate(toAggregate, fromAggregate(existing)));
        }

        /** Returns an initial value based on a value to aggregate. */
        protected abstract Stored getInitial(ToAggregate toAggregate);

        /** Converts an {@link AggregateValue} to a type it wraps. */
        protected abstract Stored fromAggregate(AggregateValue value);

        /** Converts a value to an {@link AggregateValue}. */
        protected abstract AggregateValue toAggregate(Stored value);

        /** Aggregates a value against a value wrapped by an {@link AggregateValue}. */
        protected abstract Stored aggregate(ToAggregate toAggregate, Stored existing);
    }

    /** Implementation of an {@link LogAggregator} for count reports. */
    private static final class CountAggregator extends LogAggregatorImpl<Long, Long> {
        @Override
        protected Long getInitial(Long count) {
            return count;
        }

        @Override
        protected Long fromAggregate(AggregateValue value) {
            return value.getIntegerValue();
        }

        @Override
        protected AggregateValue toAggregate(Long value) {
            return AggregateValue.newBuilder().setIntegerValue(value).build();
        }

        @Override
        protected Long aggregate(Long toAggregate, Long existing) {
            return toAggregate + existing;
        }
    }

    /** Implementation of an {@link LogAggregator} for string count reports. */
    private static final class StringIndexAggregator
            extends LogAggregatorImpl<StringHashEntity, LocalIndexHistogram> {
        @Override
        protected LocalIndexHistogram getInitial(StringHashEntity stringHash) {
            return LocalIndexHistogram.newBuilder()
                    .addBuckets(Bucket.newBuilder().setIndex(stringHash.listIndex()).setCount(1))
                    .build();
        }

        @Override
        protected LocalIndexHistogram fromAggregate(AggregateValue value) {
            return value.getIndexHistogram();
        }

        @Override
        protected AggregateValue toAggregate(LocalIndexHistogram value) {
            return AggregateValue.newBuilder().setIndexHistogram(value).build();
        }

        @Override
        protected LocalIndexHistogram aggregate(
                StringHashEntity stringHash, LocalIndexHistogram indexHistogram) {
            LocalIndexHistogram.Builder updatedHistogram = indexHistogram.toBuilder();
            boolean updated = false;
            for (int i = 0; i < indexHistogram.getBucketsCount(); i++) {
                Bucket bucket = updatedHistogram.getBuckets(i);
                if (bucket.getIndex() == stringHash.listIndex()) {
                    updatedHistogram.setBuckets(
                            i, bucket.toBuilder().setCount(bucket.getCount() + 1));
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                updatedHistogram.addBuckets(
                        Bucket.newBuilder().setIndex(stringHash.listIndex()).setCount(1));
            }
            return updatedHistogram.build();
        }
    }
    ;

    private LogAggregators() {}
}
