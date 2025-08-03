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

import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger;

import com.google.common.util.concurrent.FluentFuture;

import java.util.Map;

/** Defines the bid generator applied on the ads of each custom audience during the ad selection. */
interface AdBidGenerator {
    /**
     * This function uses the buyer-provided signals and javascript to generate bids for each of the
     * remarketing ads and pick a winner from each custom audience with the best bid.
     *
     * @param customAudience provides the ads and related information to run filtering and bidding.
     * @param adSelectionSignals includes any information the SSP would provide during for bidding.
     * @param buyerSignals contains any information the SDP would provide to the bidding stage.
     * @return a future contains either a {@link AdBiddingOutcome} containing the candidate ad with
     *     the best bid for this custom audience or null if no valid ads are available for scoring.
     */
    @Nullable
    FluentFuture<AdBiddingOutcome> runAdBiddingPerCA(
            @NonNull DBCustomAudience customAudience,
            @NonNull Map<Uri, TrustedBiddingResponse> trustedBiddingDataPerBaseUri,
            @NonNull AdSelectionSignals adSelectionSignals,
            @NonNull AdSelectionSignals buyerSignals,
            @NonNull RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger);
}
