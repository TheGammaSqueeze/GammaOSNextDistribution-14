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

import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.XNetworkData;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * POJO for AggregateTriggerData.
 */
public class AggregateTriggerData {

    private BigInteger mKey;
    private Set<String> mSourceKeys;
    private Optional<List<FilterMap>> mFilterSet;
    private Optional<List<FilterMap>> mNotFilterSet;
    private Optional<XNetworkData> mXNetworkData;

    private AggregateTriggerData() {
        mKey = null;
        mSourceKeys = new HashSet<>();
        mFilterSet = Optional.empty();
        mNotFilterSet = Optional.empty();
        mXNetworkData = Optional.empty();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateTriggerData)) {
            return false;
        }
        AggregateTriggerData attributionTriggerData = (AggregateTriggerData) obj;
        return Objects.equals(mKey, attributionTriggerData.mKey)
                && Objects.equals(mSourceKeys, attributionTriggerData.mSourceKeys)
                && Objects.equals(mFilterSet, attributionTriggerData.mFilterSet)
                && Objects.equals(mNotFilterSet, attributionTriggerData.mNotFilterSet)
                && Objects.equals(mXNetworkData, attributionTriggerData.mXNetworkData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mSourceKeys, mFilterSet, mNotFilterSet, mXNetworkData);
    }

    /**
     * Returns trigger_data's key which will be used to generate the aggregate key.
     */
    public BigInteger getKey() {
        return mKey;
    }

    /**
     * Returns the source_key set which represent which source this dimension applies to.
     */
    public Set<String> getSourceKeys() {
        return mSourceKeys;
    }

    /**
     * Returns the filter which controls when aggregate trigger data ise used based on impression
     * side information.
     */
    public Optional<List<FilterMap>> getFilterSet() {
        return mFilterSet;
    }

    /**
     * Returns the not_filter, reverse of filter.
     */
    public Optional<List<FilterMap>> getNotFilterSet() {
        return mNotFilterSet;
    }

    /** Returns the serving adtech network object */
    public Optional<XNetworkData> getXNetworkData() {
        return mXNetworkData;
    }

    /**
     * Builder for {@link AggregateTriggerData}.
     */
    public static final class Builder {
        private final AggregateTriggerData mBuilding;

        public Builder() {
            mBuilding = new AggregateTriggerData();
        }

        /**
         * See {@link AggregateTriggerData#getKey()}.
         */
        public Builder setKey(BigInteger key) {
            mBuilding.mKey = key;
            return this;
        }

        /**
         * See {@link AggregateTriggerData#getSourceKeys()}.
         */
        public Builder setSourceKeys(Set<String> sourceKeys) {
            mBuilding.mSourceKeys = sourceKeys;
            return this;
        }

        /** See {@link AggregateTriggerData#getFilterSet()}. */
        public Builder setFilterSet(@Nullable List<FilterMap> filterSet) {
            mBuilding.mFilterSet = Optional.ofNullable(filterSet);
            return this;
        }

        /** See {@link AggregateTriggerData#getNotFilterSet()} */
        public Builder setNotFilterSet(@Nullable List<FilterMap> notFilterSet) {
            mBuilding.mNotFilterSet = Optional.ofNullable(notFilterSet);
            return this;
        }

        /** See {@link AggregateTriggerData#getXNetworkData()} */
        public Builder setXNetworkData(@Nullable XNetworkData xNetworkData) {
            mBuilding.mXNetworkData = Optional.ofNullable(xNetworkData);
            return this;
        }

        /**
         * Build the {@link AggregateTriggerData}
         */
        public AggregateTriggerData build() {
            return mBuilding;
        }
    }
}
