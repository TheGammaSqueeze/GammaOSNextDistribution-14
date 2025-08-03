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

/** Class for AD_SERVICES_MEASUREMENT_DEBUG_KEYS_MATCH atom. */
@AutoValue
public abstract class MsmtDebugKeysMatchStats {

    /** @return Ad-tech enrollment ID. */
    public abstract String getAdTechEnrollmentId();

    /** @return Attribution type. */
    public abstract int getAttributionType();

    /** @return true, if debug join keys match between source and trigger. */
    public abstract boolean isMatched();

    /** @return Hashed value of debug join key. */
    public abstract long getDebugJoinKeyHashedValue();

    /** @return Hash limit used to hash the debug join key value. */
    public abstract long getDebugJoinKeyHashLimit();

    /**
     * @return source registrant.
     */
    public abstract String getSourceRegistrant();

    /** @return generic builder. */
    public static MsmtDebugKeysMatchStats.Builder builder() {
        return new AutoValue_MsmtDebugKeysMatchStats.Builder();
    }

    /** Builder class for {@link MsmtDebugKeysMatchStats}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set Ad-tech enrollment ID. */
        public abstract MsmtDebugKeysMatchStats.Builder setAdTechEnrollmentId(String value);

        /** Set attribution type. */
        public abstract MsmtDebugKeysMatchStats.Builder setAttributionType(int value);

        /** Set to true, if debug join keys match between source and trigger. */
        public abstract Builder setMatched(boolean value);

        /** Set debug join key hashed value. */
        public abstract MsmtDebugKeysMatchStats.Builder setDebugJoinKeyHashedValue(long value);

        /** Set limit of debug join key hashed value is calculated. */
        public abstract MsmtDebugKeysMatchStats.Builder setDebugJoinKeyHashLimit(long value);

        /** Set source registrant. */
        public abstract MsmtDebugKeysMatchStats.Builder setSourceRegistrant(String value);

        /** build for {@link MsmtDebugKeysMatchStats}. */
        public abstract MsmtDebugKeysMatchStats build();
    }
}
