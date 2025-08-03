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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.auto.value.AutoValue;

import java.util.Objects;

/**
 * Information related to Ad bidding that is used for reporting
 */
@AutoValue
public abstract class CustomAudienceBiddingInfo {

    /** @return Uri that is used for getting Bidding logic */
    public abstract Uri getBiddingLogicUri();

    /**
     * @return logic for buyer decision used in bidding
     */
    public abstract String getBuyerDecisionLogicJs();

    /**
     * @return CA data used for bidding and later reporting
     */
    public abstract CustomAudienceSignals getCustomAudienceSignals();

    /**
     * @return Buyer contextual signals that are used for reporting
     */
    @Nullable
    public abstract BuyerContextualSignals getBuyerContextualSignals();

    /**
     * @return generic builder
     */
    public static Builder builder() {
        return new AutoValue_CustomAudienceBiddingInfo.Builder();
    }

    /**
     * Creates an object of CustomAudienceBiddingInfo
     *
     * @param biddingLogicUri URI that fetches bidding logic
     * @param buyerDecisionLogicJs JS that helps in making bidding decision
     * @param customAudienceSignals signals for CA
     * @param buyerContextualSignals contextual signals used in {@code reportWin}
     * @return an instance of CustomAudienceBiddingInfo
     */
    public static CustomAudienceBiddingInfo create(
            @NonNull Uri biddingLogicUri,
            @NonNull String buyerDecisionLogicJs,
            @NonNull CustomAudienceSignals customAudienceSignals,
            @Nullable BuyerContextualSignals buyerContextualSignals) {
        Objects.requireNonNull(biddingLogicUri);
        Objects.requireNonNull(buyerDecisionLogicJs);
        Objects.requireNonNull(customAudienceSignals);

        return builder()
                .setBiddingLogicUri(biddingLogicUri)
                .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                .setCustomAudienceSignals(customAudienceSignals)
                .setBuyerContextualSignals(buyerContextualSignals)
                .build();
    }

    /**
     * Creates an object of CustomAudienceBiddingInfo
     *
     * @param customAudience CA data
     * @param buyerDecisionLogicJs JS that helps in making bidding decision
     * @param buyerContextualSignals contextual signals used in {@code reportWin}
     * @return an instance of CustomAudienceBiddingInfo
     */
    public static CustomAudienceBiddingInfo create(
            @NonNull DBCustomAudience customAudience,
            @NonNull String buyerDecisionLogicJs,
            @Nullable BuyerContextualSignals buyerContextualSignals) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(buyerDecisionLogicJs);

        Uri biddingLogicUri = customAudience.getBiddingLogicUri();
        CustomAudienceSignals customAudienceSignals =
                new CustomAudienceSignals(
                        customAudience.getOwner(),
                        customAudience.getBuyer(),
                        customAudience.getName(),
                        customAudience.getActivationTime(),
                        customAudience.getExpirationTime(),
                        customAudience.getUserBiddingSignals());

        return builder()
                .setBiddingLogicUri(biddingLogicUri)
                .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                .setCustomAudienceSignals(customAudienceSignals)
                .setBuyerContextualSignals(buyerContextualSignals)
                .build();
    }

    /** Builder for {@link CustomAudienceBiddingInfo} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the BiddingLogicUri. */
        public abstract Builder setBiddingLogicUri(Uri biddingUri);

        /** Sets the BuyerDecisionLogicJs. */
        public abstract Builder setBuyerDecisionLogicJs(String buyerDecisionLogicJs);

        /** Sets the CustomAudienceSignals. */
        public abstract Builder setCustomAudienceSignals(
                CustomAudienceSignals customAudienceSignals);

        /** Sets the BuyerContextualSignals. */
        public abstract Builder setBuyerContextualSignals(BuyerContextualSignals value);

        /** Build a CustomAudienceBiddingInfo object. */
        public abstract CustomAudienceBiddingInfo build();
    }
}
