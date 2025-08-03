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

package com.android.adservices.service.measurement.aggregation;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.util.UnsignedLong;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Aggregate Deduplication Key containing de-deup key and filters info. */
public class AggregateDeduplicationKey {
    private Optional<UnsignedLong> mDedupKey;
    private Optional<List<FilterMap>> mFilterSet;

    private Optional<List<FilterMap>> mNotFilterSet;

    /** Create a new aggregate encryption key object. */
    private AggregateDeduplicationKey() {
        mDedupKey = Optional.empty();
        mFilterSet = Optional.empty();
        mNotFilterSet = Optional.empty();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateDeduplicationKey)) {
            return false;
        }
        AggregateDeduplicationKey key = (AggregateDeduplicationKey) obj;
        return Objects.equals(mDedupKey, key.mDedupKey)
                && Objects.equals(mFilterSet, key.mFilterSet)
                && Objects.equals(mNotFilterSet, key.mNotFilterSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDedupKey, mFilterSet, mNotFilterSet);
    }

    /** Deduplication key to match dedup key with source. */
    public Optional<UnsignedLong> getDeduplicationKey() {
        return mDedupKey;
    }

    /** Filters that should match with source's. */
    public Optional<List<FilterMap>> getFilterSet() {
        return mFilterSet;
    }

    /** Returns the not_filter, reverse of filter. */
    public Optional<List<FilterMap>> getNotFilterSet() {
        return mNotFilterSet;
    }

    /** A builder for {@link AggregateDeduplicationKey}. */
    public static final class Builder {
        private final AggregateDeduplicationKey mBuilding;

        public Builder() {
            mBuilding = new AggregateDeduplicationKey();
        }
        /** See {@link AggregateDeduplicationKey#getFilterSet()}. */
        public Builder setDeduplicationKey(@NonNull UnsignedLong dedupKey) {
            mBuilding.mDedupKey = Optional.of(dedupKey);
            return this;
        }

        /** See {@link AggregateDeduplicationKey#getFilterSet()}. */
        public Builder setFilterSet(@Nullable List<FilterMap> filterSet) {
            mBuilding.mFilterSet = Optional.of(filterSet);
            return this;
        }

        /** See {@link AggregateDeduplicationKey#getNotFilterSet()} */
        public Builder setNotFilterSet(List<FilterMap> notFilterSet) {
            mBuilding.mNotFilterSet = Optional.of(notFilterSet);
            return this;
        }

        /** Build the AggregateDeduplicationKey. */
        public @NonNull AggregateDeduplicationKey build() {
            return mBuilding;
        }
    }
}
