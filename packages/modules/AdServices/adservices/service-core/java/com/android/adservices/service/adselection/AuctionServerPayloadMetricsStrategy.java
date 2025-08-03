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

package com.android.adservices.service.adselection;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import java.util.Map;

/** Strategy interface denoting how to log GetAdSelectionData payload size metrics */
public interface AuctionServerPayloadMetricsStrategy {
    /** Sets the number of buyers to the {@link GetAdSelectionDataApiCalledStats#builder()} */
    void setNumBuyers(GetAdSelectionDataApiCalledStats.Builder builder, int numBuyers);

    /** Invokes the logger to log {@link GetAdSelectionDataApiCalledStats} */
    void logGetAdSelectionDataApiCalledStats(
            GetAdSelectionDataApiCalledStats.Builder builder,
            int payloadSize,
            @AdServicesStatusUtils.StatusCode int statusCode);

    /**
     * Loops thorough each buyer and logs {@link
     * com.android.adservices.service.stats.GetAdSelectionDataBuyerInputGeneratedStats}
     */
    void logGetAdSelectionDataBuyerInputGeneratedStats(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> statsMap);

    /**
     * Adds this custom audiences stats to the map of buyer to {@link
     * BuyerInputGeneratorIntermediateStats}
     */
    void addToBuyerIntermediateStats(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats,
            DBCustomAudience dbCustomAudience,
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience);
}
