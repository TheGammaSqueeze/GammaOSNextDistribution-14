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

import android.adservices.adselection.AdWithBid;
import android.annotation.Nullable;

import com.google.auto.value.AutoValue;

/**
 * Results from Ad Bidding, combined with the Custom Audience Bidding info to be able to map CA data
 * related to an Ad for reporting
 */
@AutoValue
public abstract class AdBiddingOutcome {

    /**
     * @return Ad data object with bid value
     */
    public abstract AdWithBid getAdWithBid();

    /**
     * @return CA Bidding info that is used for reporting
     */
    public abstract CustomAudienceBiddingInfo getCustomAudienceBiddingInfo();

    /**
     * @return data for event-level debug reporting.
     */
    @Nullable
    public abstract DebugReport getDebugReport();

    /**
     * @return Generic builder
     */
    public static Builder builder() {
        return new AutoValue_AdBiddingOutcome.Builder();
    }

    /** The Builder for {@link AdBiddingOutcome} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the AdWithBid */
        public abstract Builder setAdWithBid(AdWithBid adWithBid);

        /** Sets the CustomAudienceBiddingInfo */
        public abstract Builder setCustomAudienceBiddingInfo(
                CustomAudienceBiddingInfo customAudienceBiddingInfo);

        /** Sets the DebugReport. */
        abstract Builder setDebugReport(DebugReport debugReport);

        /** Build an AdBiddingOutcome object. */
        public abstract AdBiddingOutcome build();
    }
}
