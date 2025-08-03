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

import com.google.auto.value.AutoValue;

/**
 * Ads with score and bid data, based on Scoring logic provided by the seller
 * The scoring is done on Ads that have already been through Auction thus have bids
 */
@AutoValue
public abstract class AdWithScore {

    /**
     * @return score from scoring logic JS
     */
    public abstract Double getScore();

    /**
     * @return ad data with its bid value
     */
    public abstract AdWithBid getAdWithBid();

    /**
     * @return generic builder
     */
    static Builder builder() {
        return new AutoValue_AdWithScore.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setScore(Double score);
        abstract Builder setAdWithBid(AdWithBid adWithBid);
        abstract AdWithScore build();
    }
}
