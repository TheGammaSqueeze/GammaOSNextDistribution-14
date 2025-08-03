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

package com.android.adservices.service.adselection;

import android.adservices.common.AdTechIdentifier;
import android.annotation.Nullable;

import com.google.auto.value.AutoValue;

import java.util.Objects;

/** Captures the winning and second highest scored bids after ad selection is finished. */
@AutoValue
abstract class PostAuctionSignals {

    /** Gets the winning bid. */
    @Nullable
    abstract Double getWinningBid();

    /** Gets the winning buyer. */
    @Nullable
    abstract AdTechIdentifier getWinningBuyer();

    @Nullable
    abstract String getWinningCustomAudienceName();

    /** Gets the second highest scored bid. */
    @Nullable
    abstract Double getSecondHighestScoredBid();

    /** Gets the second highest scored buyer. */
    @Nullable
    abstract AdTechIdentifier getSecondHighestScoredBuyer();

    /** Builder */
    static PostAuctionSignals.Builder builder() {
        return new AutoValue_PostAuctionSignals.Builder();
    }

    /**
     * Generates post auction signals from winning ad and second highest ad.
     *
     * @param winningAd the winning ad outcome.
     * @param secondHighestAd the second highest scored ad outcome.
     * @return Post auction signals
     */
    public static PostAuctionSignals create(
            @Nullable AdScoringOutcome winningAd, @Nullable AdScoringOutcome secondHighestAd) {
        PostAuctionSignals.Builder builder = builder();
        if (Objects.nonNull(winningAd)) {
            builder.setWinningBid(winningAd.getAdWithScore().getAdWithBid().getBid())
                    .setWinningBuyer(winningAd.getCustomAudienceSignals().getBuyer())
                    .setWinningCustomAudienceName(winningAd.getCustomAudienceSignals().getName());
        }
        if (Objects.nonNull(secondHighestAd)) {
            builder.setSecondHighestScoredBid(
                            secondHighestAd.getAdWithScore().getAdWithBid().getBid())
                    .setSecondHighestScoredBuyer(
                            secondHighestAd.getCustomAudienceSignals().getBuyer());
        }
        return builder.build();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the winning bid. */
        public abstract PostAuctionSignals.Builder setWinningBid(Double winningBid);

        /** Sets the winning buyer. */
        public abstract PostAuctionSignals.Builder setWinningBuyer(AdTechIdentifier winningBuyer);

        /** Sets the winning buyer. */
        public abstract PostAuctionSignals.Builder setWinningCustomAudienceName(
                String customAudienceName);

        /** Sets the second highest scored bid. */
        public abstract PostAuctionSignals.Builder setSecondHighestScoredBid(
                Double secondHighestScoredBid);

        /** Sets the second highest scored buyer. */
        public abstract PostAuctionSignals.Builder setSecondHighestScoredBuyer(
                AdTechIdentifier secondHighestScoredBuyer);

        /** Build a CustomAudienceBiddingInfo object. */
        public abstract PostAuctionSignals build();
    }
}
