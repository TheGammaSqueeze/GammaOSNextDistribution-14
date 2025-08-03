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
package com.android.adservices.service.stats;

import com.google.auto.value.AutoValue;

/** Class for AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS atom. */
@AutoValue
public abstract class MsmtAdIdMatchForDebugKeysStats {
    /**
     * @return Ad-tech enrollment ID.
     */
    public abstract String getAdTechEnrollmentId();

    /**
     * @return Attribution type.
     */
    public abstract int getAttributionType();

    /**
     * @return true, if the debug AdID provided by the Ad-tech on web registration matches the
     *     platform AdID value.
     */
    public abstract boolean isMatched();

    /**
     * @return Number of unique AdIDs an Ad-tech has provided for matching.
     */
    public abstract long getNumUniqueAdIds();

    /**
     * @return Limit on number of unique AdIDs an Ad-tech is allowed.
     */
    public abstract long getNumUniqueAdIdsLimit();

    /**
     * @return source registrant.
     */
    public abstract String getSourceRegistrant();

    /**
     * @return generic builder.
     */
    public static MsmtAdIdMatchForDebugKeysStats.Builder builder() {
        return new AutoValue_MsmtAdIdMatchForDebugKeysStats.Builder();
    }

    /** Builder class for {@link MsmtAdIdMatchForDebugKeysStats} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set Ad-tech enrollment ID. */
        public abstract Builder setAdTechEnrollmentId(String value);

        /** Set attribution type. */
        public abstract Builder setAttributionType(int value);

        /**
         * Set to true, if the debug AdID provided by the Ad-tech on web registration matches the
         * platform AdID value.
         */
        public abstract Builder setMatched(boolean value);

        /** Set number of unique AdIDs an Ad-tech has provided for matching. */
        public abstract Builder setNumUniqueAdIds(long value);

        /** Set limit on number of unique AdIDs an Ad-tech is allowed. */
        public abstract Builder setNumUniqueAdIdsLimit(long value);

        /** Set source registrant. */
        public abstract Builder setSourceRegistrant(String value);

        /** build for {@link MsmtAdIdMatchForDebugKeysStats} */
        public abstract MsmtAdIdMatchForDebugKeysStats build();
    }
}
