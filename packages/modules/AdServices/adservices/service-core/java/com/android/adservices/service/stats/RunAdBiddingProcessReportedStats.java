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

/** Class for runAdBidding process reported stats. */
@AutoValue
public abstract class RunAdBiddingProcessReportedStats {
    /** @return getBuyersCustomAudienceLatencyInMills. */
    public abstract int getGetBuyersCustomAudienceLatencyInMills();

    /** @return getBuyersCustomAudience result code. */
    public abstract int getGetBuyersCustomAudienceResultCode();

    /** @return num of buyers requests. */
    public abstract int getNumBuyersRequested();

    /** @return num of buyers fetched. */
    public abstract int getNumBuyersFetched();

    /** @return num of ads entered bidding. */
    public abstract int getNumOfAdsEnteringBidding();

    /** @return num of CAs entered bidding. */
    public abstract int getNumOfCasEnteringBidding();

    /** @return num of CAs post bidding. */
    public abstract int getNumOfCasPostBidding();

    /** @return ratio of CAs selected rmkt ads. */
    public abstract float getRatioOfCasSelectingRmktAds();

    /** @return runAdBidding latency in milliseconds. */
    public abstract int getRunAdBiddingLatencyInMillis();

    /** @return runAdBidding result code. */
    public abstract int getRunAdBiddingResultCode();

    /** @return total ad bidding stage latency in milliseconds. */
    public abstract int getTotalAdBiddingStageLatencyInMillis();

    static Builder builder() {
        return new AutoValue_RunAdBiddingProcessReportedStats.Builder();
    }

    /** Builder class for RunAdBiddingProcessReportedStats. */
    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setGetBuyersCustomAudienceLatencyInMills(int value);

        abstract Builder setGetBuyersCustomAudienceResultCode(int value);

        abstract Builder setNumBuyersRequested(int value);

        abstract Builder setNumBuyersFetched(int value);

        abstract Builder setNumOfAdsEnteringBidding(int value);

        abstract Builder setNumOfCasEnteringBidding(int value);

        abstract Builder setNumOfCasPostBidding(int value);

        abstract Builder setRatioOfCasSelectingRmktAds(float value);

        abstract Builder setRunAdBiddingLatencyInMillis(int value);

        abstract Builder setRunAdBiddingResultCode(int value);

        abstract Builder setTotalAdBiddingStageLatencyInMillis(int value);

        abstract RunAdBiddingProcessReportedStats build();
    }
}
