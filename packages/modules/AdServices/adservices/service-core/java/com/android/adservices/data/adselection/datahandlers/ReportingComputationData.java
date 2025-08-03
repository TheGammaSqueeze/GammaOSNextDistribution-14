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

package com.android.adservices.data.adselection.datahandlers;

import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.data.adselection.CustomAudienceSignals;

import com.google.auto.value.AutoValue;


/** Data class representing the data required to computing reporting URIs. */
@AutoValue
public abstract class ReportingComputationData {

    /** The buyer decision logic JS containing the reporting computation logic. */
    @NonNull
    public abstract String getBuyerDecisionLogicJs();

    /** The URI from where to fetch the buyer decision logic JS. */
    @Nullable
    public abstract Uri getBuyerDecisionLogicUri();

    /** The contextual signals related to the buyer used in the ad selection run. */
    @Nullable
    public abstract AdSelectionSignals getBuyerContextualSignals();

    /** The contextual signals related to the seller used in the ad selection run. */
    @NonNull
    public abstract AdSelectionSignals getSellerContextualSignals();

    /** The Custom Audience signals of the ad which won the ad selection run. */
    @NonNull
    public abstract CustomAudienceSignals getWinningCustomAudienceSignals();

    /** The render uri of the ad which won the ad selection run */
    @NonNull
    public abstract Uri getWinningRenderUri();

    /** The bid of the ad which won the ad selection run */
    public abstract double getWinningBid();

    /** Returns a builder for {@link ReportingComputationData}. */
    @NonNull
    public static Builder builder() {
        return new AutoValue_ReportingComputationData.Builder();
    }

    /** Builder for {@link ReportingComputationData}. */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the buyer decision logic JS containing the reporting computation logic. */
        public abstract Builder setBuyerDecisionLogicJs(@NonNull String buyerDecisionLogicJs);

        /** Sets the URI from where to fetch the buyer decision logic JS. */
        public abstract Builder setBuyerDecisionLogicUri(@Nullable Uri buyerDecisionLogicUri);

        /** Sets the contextual signals related to the buyer used in the ad selection run. */
        public abstract Builder setBuyerContextualSignals(
                @Nullable AdSelectionSignals buyerContextualSignals);

        /** Sets the contextual signals related to the seller used in the ad selection run. */
        public abstract Builder setSellerContextualSignals(
                @NonNull AdSelectionSignals sellerContextualSignals);

        /** Sets the Custom Audience signals of the ad which won the ad selection run */
        public abstract Builder setWinningCustomAudienceSignals(
                @NonNull CustomAudienceSignals customAudienceSignals);

        /** Sets the render uri of the ad which won the ad selection run. */
        public abstract Builder setWinningRenderUri(@NonNull Uri winningRenderUri);

        /** Sets the bid of the ad which won the ad selection run */
        public abstract Builder setWinningBid(double winningBid);

        /** Builds a {@link ReportingComputationData} object. */
        @NonNull
        public abstract ReportingComputationData build();
    }
}
