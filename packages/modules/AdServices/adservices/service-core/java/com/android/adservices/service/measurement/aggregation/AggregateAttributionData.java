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

package com.android.adservices.service.measurement.aggregation;

import android.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class that contains the data specific to the aggregate report.
 */
public class AggregateAttributionData {
    private List<AggregateHistogramContribution> mContributions;
    @Nullable
    private Long mId;

    private AggregateAttributionData() {
        mContributions = new ArrayList<>();
        mId = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateAttributionData)) {
            return false;
        }
        AggregateAttributionData aggregateAttributionData = (AggregateAttributionData) obj;
        return Objects.equals(mContributions, aggregateAttributionData.mContributions)
                && Objects.equals(mId, aggregateAttributionData.mId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mContributions, mId);
    }

    /**
     * Returns all the aggregate histogram contributions.
     */
    public List<AggregateHistogramContribution> getContributions() {
        return mContributions;
    }

    /**
     * Id assigned by storage to uniquely identify an aggregate contribution. If null, an ID has
     * not been assigned yet.
     */
    @Nullable
    public Long getId() {
        return mId;
    }

    /**
     * Builder for {@link AggregateAttributionData}.
     */
    public static final class Builder {
        private final AggregateAttributionData mAggregateAttributionData;

        public Builder() {
            mAggregateAttributionData = new AggregateAttributionData();
        }

        /**
         * See {@link AggregateAttributionData#getContributions()}.
         */
        public Builder setContributions(List<AggregateHistogramContribution> contributions) {
            mAggregateAttributionData.mContributions = contributions;
            return this;
        }

        /**
         * See {@link AggregateAttributionData#getId()}.
         */
        public Builder setId(@Nullable Long id) {
            mAggregateAttributionData.mId = id;
            return this;
        }

        /**
         * Build the {@link AggregateAttributionData}.
         */
        public AggregateAttributionData build() {
            return mAggregateAttributionData;
        }
    }
}
