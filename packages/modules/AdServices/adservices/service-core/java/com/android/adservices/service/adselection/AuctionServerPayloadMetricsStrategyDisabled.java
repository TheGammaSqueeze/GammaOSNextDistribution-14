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

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import java.util.Map;

public class AuctionServerPayloadMetricsStrategyDisabled
        implements AuctionServerPayloadMetricsStrategy {
    @Override
    public void setNumBuyers(GetAdSelectionDataApiCalledStats.Builder builder, int numBuyers) {
        // do nothing
    }

    @Override
    public void logGetAdSelectionDataApiCalledStats(
            GetAdSelectionDataApiCalledStats.Builder builder, int payloadSize, int statusCode) {
        // do nothing
    }

    @Override
    public void logGetAdSelectionDataBuyerInputGeneratedStats(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> statsMap) {
        // do nothing
    }

    @Override
    public void addToBuyerIntermediateStats(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats,
            DBCustomAudience dbCustomAudience,
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience) {
        // do nothing
    }
}
