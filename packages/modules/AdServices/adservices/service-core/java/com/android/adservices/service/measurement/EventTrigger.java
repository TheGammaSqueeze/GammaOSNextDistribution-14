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

package com.android.adservices.service.measurement;

import android.annotation.NonNull;

import com.android.adservices.service.measurement.aggregation.AggregatableAttributionTrigger;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.measurement.util.Validation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Event trigger containing trigger data, priority, de-deup key and filters info. */
public class EventTrigger {
    @NonNull private UnsignedLong mTriggerData;
    private long mTriggerPriority;
    private long mTriggerValue;
    private UnsignedLong mDedupKey;
    private Optional<List<FilterMap>> mFilterSet;
    private Optional<List<FilterMap>> mNotFilterSet;

    private EventTrigger() {
        mFilterSet = Optional.empty();
        mNotFilterSet = Optional.empty();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EventTrigger)) {
            return false;
        }
        EventTrigger eventTrigger = (EventTrigger) obj;
        return Objects.equals(mTriggerData, eventTrigger.mTriggerData)
                && mTriggerPriority == eventTrigger.mTriggerPriority
                && mTriggerValue == eventTrigger.mTriggerValue
                && Objects.equals(mDedupKey, eventTrigger.mDedupKey)
                && Objects.equals(mFilterSet, eventTrigger.mFilterSet)
                && Objects.equals(mNotFilterSet, eventTrigger.mNotFilterSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTriggerData,
                mTriggerPriority,
                mTriggerValue,
                mDedupKey,
                mFilterSet,
                mNotFilterSet);
    }

    /** Returns trigger_data for the event. */
    public UnsignedLong getTriggerData() {
        return mTriggerData;
    }

    /** Trigger priority. */
    public long getTriggerPriority() {
        return mTriggerPriority;
    }

    /** Trigger value. */
    public long getTriggerValue() {
        return mTriggerValue;
    }

    /** De-deuplication key.. */
    public UnsignedLong getDedupKey() {
        return mDedupKey;
    }

    /** Filters that should match with source's. */
    public Optional<List<FilterMap>> getFilterSet() {
        return mFilterSet;
    }

    /** Filters that should not match with source's. */
    public Optional<List<FilterMap>> getNotFilterSet() {
        return mNotFilterSet;
    }

    /** Builder for {@link AggregatableAttributionTrigger}. */
    public static final class Builder {
        private final EventTrigger mBuilding;

        public Builder(@NonNull UnsignedLong triggerData) {
            Validation.validateNonNull(triggerData);
            mBuilding = new EventTrigger();
            mBuilding.mTriggerData = triggerData;
        }

        /** See {@link EventTrigger#getTriggerPriority()}. */
        public EventTrigger.Builder setTriggerPriority(Long triggerPriority) {
            mBuilding.mTriggerPriority = triggerPriority;
            return this;
        }

        /** See {@link EventTrigger#getTriggerValue()}. */
        public EventTrigger.Builder setTriggerValue(Long value) {
            mBuilding.mTriggerValue = value;
            return this;
        }

        /** See {@link EventTrigger#getDedupKey()}. */
        public EventTrigger.Builder setDedupKey(UnsignedLong dedupKey) {
            mBuilding.mDedupKey = dedupKey;
            return this;
        }

        /** See {@link EventTrigger#getFilterSet()}. */
        public EventTrigger.Builder setFilterSet(List<FilterMap> filterSet) {
            mBuilding.mFilterSet = Optional.ofNullable(filterSet);
            return this;
        }

        /** See {@link EventTrigger#getNotFilterSet()}. */
        public EventTrigger.Builder setNotFilterSet(List<FilterMap> notFilterSet) {
            mBuilding.mNotFilterSet = Optional.ofNullable(notFilterSet);
            return this;
        }

        /** Build the {@link EventTrigger}. */
        public EventTrigger build() {
            return mBuilding;
        }
    }
}
