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

/** Class for updateCustomAudience process reported stats. */
@AutoValue
public abstract class UpdateCustomAudienceProcessReportedStats {
    /** @return latency in milliseconds. */
    public abstract int getLatencyInMills();

    /** @return update custom audience result code. */
    public abstract int getResultCode();

    /** @return data size of ads in bytes. */
    public abstract int getDataSizeOfAdsInBytes();

    /** @return num of ads. */
    public abstract int getNumOfAds();

    /** @return generic builder. */
    static Builder builder() {
        return new AutoValue_UpdateCustomAudienceProcessReportedStats.Builder();
    }

    /** Builder class for {@link UpdateCustomAudienceProcessReportedStats} object. */
    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setLatencyInMills(int value);

        abstract Builder setResultCode(int value);

        abstract Builder setDataSizeOfAdsInBytes(int value);

        abstract Builder setNumOfAds(int value);

        abstract UpdateCustomAudienceProcessReportedStats build();
    }
}
