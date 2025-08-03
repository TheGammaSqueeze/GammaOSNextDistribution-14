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

package com.android.adservices.service.adselection;

import android.adservices.common.AdTechIdentifier;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.data.adselection.CustomAudienceSignals;

import com.google.auto.value.AutoValue;

/**
 * Represents outcome of the scoring, with Ads their score and their Custom Audience information or
 * Contextual Information gathered during the selection process.
 *
 * <p>The ads and their scores are used to decide the winner for Ad Selection. The Custom audience
 * information and contextual information is used during reporting
 */
@AutoValue
public abstract class AdScoringOutcome {
    /**
     * @return Ad with score based on seller scoring logic
     */
    public abstract AdWithScore getAdWithScore();

    /** @return signals associated with Custom Audience */
    @Nullable
    public abstract CustomAudienceSignals getCustomAudienceSignals();

    /**
     * @return data for event-level debug reporting.
     */
    @Nullable
    public abstract DebugReport getDebugReport();

    /** @return uri that corresponds to the logic for Ad bidding, reporting */
    public abstract Uri getBiddingLogicUri();

    /** @return the downloaded decision logic JS */
    @Nullable
    public abstract String getBiddingLogicJs();

    /**
     * @return boolean if decision logic has been downloaded or not. Helps optimize network calls by
     *     downloading the logic only when needed
     */
    public abstract boolean isBiddingLogicJsDownloaded();

    /** @return buyer associated with the ad */
    public abstract AdTechIdentifier getBuyer();

    /**
     * @return Buyer contextual signals that are used for reporting
     */
    @Nullable
    public abstract BuyerContextualSignals getBuyerContextualSignals();

    /**
     * @return Seller contextual signals that are used for reporting
     */
    @Nullable
    public abstract SellerContextualSignals getSellerContextualSignals();

    /**
     * @return generic builder
     */
    static Builder builder() {
        return new AutoValue_AdScoringOutcome.Builder()
                .setCustomAudienceSignals(null)
                .setBiddingLogicJs("")
                .setBiddingLogicJsDownloaded(false)
                .setBuyerContextualSignals(null)
                .setSellerContextualSignals(null);
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setAdWithScore(AdWithScore adWithScore);

        abstract Builder setCustomAudienceSignals(CustomAudienceSignals customAudienceSignals);

        abstract Builder setDebugReport(DebugReport debugReport);

        abstract Builder setBiddingLogicUri(Uri decisionLogicUri);

        abstract Builder setBiddingLogicJs(String decisionLogicJs);

        abstract Builder setBiddingLogicJsDownloaded(boolean decisionLogicJsDownloaded);

        abstract Builder setBuyer(AdTechIdentifier buyer);

        abstract Builder setBuyerContextualSignals(BuyerContextualSignals buyerContextualSignals);

        abstract Builder setSellerContextualSignals(
                SellerContextualSignals sellerContextualSignals);

        abstract AdScoringOutcome build();
    }
}
