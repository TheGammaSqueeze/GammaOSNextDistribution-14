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

/** Class for runAdBiddingPerCA process reported stats. */
@AutoValue
public abstract class RunAdBiddingPerCAProcessReportedStats {
    /** @return num of ads for bidding. */
    public abstract int getNumOfAdsForBidding();

    /** @return runAdBiddingPerCA latency in milliseconds. */
    public abstract int getRunAdBiddingPerCaLatencyInMillis();

    /** @return runAdBiddingPerCA result code. */
    public abstract int getRunAdBiddingPerCaResultCode();

    /** @return getBuyerDecisionLogic script latency in milliseconds. */
    public abstract int getGetBuyerDecisionLogicLatencyInMillis();

    /** @return getBuyerDecisionLogic result code. */
    public abstract int getGetBuyerDecisionLogicResultCode();

    /** @return getBuyerDecisionLogic script type. */
    public abstract int getBuyerDecisionLogicScriptType();

    /** @return fetched buyer decision logic script size in bytes. */
    public abstract int getFetchedBuyerDecisionLogicScriptSizeInBytes();

    /** @return num of keys of trusted bidding signals. */
    public abstract int getNumOfKeysOfTrustedBiddingSignals();

    /** @return fetched trusted bidding signals data size in bytes. */
    public abstract int getFetchedTrustedBiddingSignalsDataSizeInBytes();

    /** @return getTrustedBiddingSignals latency in milliseconds. */
    public abstract int getGetTrustedBiddingSignalsLatencyInMillis();

    /** @return getTrustedBiddingSignals result code. */
    public abstract int getGetTrustedBiddingSignalsResultCode();

    /** @return the total generateBids script execution time when runBidding() is called. */
    public abstract int getGenerateBidsLatencyInMillis();

    /** @return the overall latency of runBidding(). */
    public abstract int getRunBiddingLatencyInMillis();

    /** @return the runBidding() result code. */
    public abstract int getRunBiddingResultCode();

    /** @return generic builder. */
    static Builder builder() {
        return new AutoValue_RunAdBiddingPerCAProcessReportedStats.Builder();
    }

    /** Builder class for {@link RunAdBiddingPerCAProcessReportedStats}. */
    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setNumOfAdsForBidding(int value);

        abstract Builder setRunAdBiddingPerCaLatencyInMillis(int value);

        abstract Builder setRunAdBiddingPerCaResultCode(int value);

        abstract Builder setGetBuyerDecisionLogicLatencyInMillis(int value);

        abstract Builder setGetBuyerDecisionLogicResultCode(int value);

        abstract Builder setBuyerDecisionLogicScriptType(int value);

        abstract Builder setFetchedBuyerDecisionLogicScriptSizeInBytes(int value);

        abstract Builder setNumOfKeysOfTrustedBiddingSignals(int value);

        abstract Builder setFetchedTrustedBiddingSignalsDataSizeInBytes(int value);

        abstract Builder setGetTrustedBiddingSignalsLatencyInMillis(int value);

        abstract Builder setGetTrustedBiddingSignalsResultCode(int value);

        abstract Builder setGenerateBidsLatencyInMillis(int value);

        abstract Builder setRunBiddingLatencyInMillis(int value);

        abstract Builder setRunBiddingResultCode(int value);

        abstract RunAdBiddingPerCAProcessReportedStats build();
    }
}
