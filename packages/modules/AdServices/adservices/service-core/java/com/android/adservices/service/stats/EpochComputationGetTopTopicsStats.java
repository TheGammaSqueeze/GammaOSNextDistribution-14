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

package com.android.adservices.service.stats;

import com.google.auto.value.AutoValue;

/** Class for AdServicesEpochComputationGetTopTopicsReported atom. */
@AutoValue
public abstract class EpochComputationGetTopTopicsStats {

    /** @return number of top topics generated. */
    public abstract int getTopTopicCount();

    /** @return number of padded random topics generated. */
    public abstract int getPaddedRandomTopicsCount();

    /** @return number of apps considered for calculating top topics. */
    public abstract int getAppsConsideredCount();

    /** @return number of sdks that called Topics API in the epoch. */
    public abstract int getSdksConsideredCount();

    /** @return generic builder. */
    public static EpochComputationGetTopTopicsStats.Builder builder() {
        return new AutoValue_EpochComputationGetTopTopicsStats.Builder();
    }

    /** Builder class for {@link EpochComputationGetTopTopicsStats}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set number of top topics generated. */
        public abstract EpochComputationGetTopTopicsStats.Builder setTopTopicCount(int value);

        /** Set number of padded random topics generated. */
        public abstract EpochComputationGetTopTopicsStats.Builder setPaddedRandomTopicsCount(
                int value);

        /** Set number of apps considered for calculating top topics. */
        public abstract EpochComputationGetTopTopicsStats.Builder setAppsConsideredCount(int value);

        /** Set number of sdks that called Topics API in the epoch. */
        public abstract EpochComputationGetTopTopicsStats.Builder setSdksConsideredCount(int value);

        /** build for {@link EpochComputationGetTopTopicsStats}. */
        public abstract EpochComputationGetTopTopicsStats build();
    }
}
