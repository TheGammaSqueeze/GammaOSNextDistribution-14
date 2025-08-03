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

/** Class for backgroundFetch process reported stats. */
@AutoValue
public abstract class BackgroundFetchProcessReportedStats {
    /** @return latency in milliseconds. */
    public abstract int getLatencyInMillis();

    /** @return num of eligible-to-update CAs. */
    public abstract int getNumOfEligibleToUpdateCas();

    /** @return background fetch process result code. */
    public abstract int getResultCode();

    /** @return generic builder. */
    static Builder builder() {
        return new AutoValue_BackgroundFetchProcessReportedStats.Builder();
    }

    /** Builder class for {@link BackgroundFetchProcessReportedStats} object. */
    @AutoValue.Builder
    abstract static class Builder {

        abstract Builder setLatencyInMillis(int value);

        abstract Builder setNumOfEligibleToUpdateCas(int value);

        abstract Builder setResultCode(int value);

        abstract BackgroundFetchProcessReportedStats build();
    }
}
