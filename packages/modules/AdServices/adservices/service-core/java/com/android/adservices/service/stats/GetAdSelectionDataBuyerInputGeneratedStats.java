/*
 * Copyright (C) 2024 The Android Open Source Project
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

/** Class for buyer input generated for getAdSelectionData api stats. */
@AutoValue
public abstract class GetAdSelectionDataBuyerInputGeneratedStats {
    /** Returns the number of custom audiences in this buyer input */
    public abstract int getNumCustomAudiences();

    /** Returns the number of custom audiences opting into the omit-ads feature */
    public abstract int getNumCustomAudiencesOmitAds();

    /** Returns the mean of the size of a custom audience for this buyer input */
    public abstract float getCustomAudienceSizeMeanB();

    /** Returns the variance of the size of a custom audience for this buyer input */
    public abstract float getCustomAudienceSizeVarianceB();

    /** Returns the mean of the size of the trusted bidding signals keys for this buyer input */
    public abstract float getTrustedBiddingSignalsKeysSizeMeanB();

    /** Returns the variance of the size of the trusted bidding signals keys for this buyer input */
    public abstract float getTrustedBiddingSignalsKeysSizeVarianceB();

    /** Returns the mean of the size of the user bidding signals for this buyer input */
    public abstract float getUserBiddingSignalsSizeMeanB();

    /** Returns the variance of the size of the user bidding signals for this buyer input */
    public abstract float getUserBiddingSignalsSizeVarianceB();

    /** Returns a generic builder. */
    public static Builder builder() {
        return new AutoValue_GetAdSelectionDataBuyerInputGeneratedStats.Builder();
    }

    /** Builder class for GetAdSelectionDataBuyerInputGeneratedStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the number of custom audiences. */
        public abstract Builder setNumCustomAudiences(int numCustomAudiences);

        /** Sets the number of custom audiences omitting ads. */
        public abstract Builder setNumCustomAudiencesOmitAds(int numCustomAudiencesOmitAds);

        /** Sets the mean of the size of a custom audience for this buyer input. */
        public abstract Builder setCustomAudienceSizeMeanB(float customAudienceSizeMeanB);

        /** Sets the variance of the size of a custom audience for this buyer input. */
        public abstract Builder setCustomAudienceSizeVarianceB(float customAudienceSizeVarianceB);

        /** Sets the mean of the size of the trusted bidding signals keys for this buyer input. */
        public abstract Builder setTrustedBiddingSignalsKeysSizeMeanB(
                float trustedBiddingSignalsKeysSizeMeanB);

        /**
         * Sets the variance of the size of the trusted bidding signals keys for this buyer input.
         */
        public abstract Builder setTrustedBiddingSignalsKeysSizeVarianceB(
                float trustedBiddingSignalsSizeVarianceB);

        /** Sets the mean of the size of the user bidding signals for this buyer input */
        public abstract Builder setUserBiddingSignalsSizeMeanB(float userBiddingSignalsSizeMeanB);

        /** Sets the variance of the size of the user bidding signals for this buyer input */
        public abstract Builder setUserBiddingSignalsSizeVarianceB(
                float userBiddingSignalsSizeVarianceB);

        /** Builds the {@link GetAdSelectionDataBuyerInputGeneratedStats} object. */
        public abstract GetAdSelectionDataBuyerInputGeneratedStats build();
    }
}
