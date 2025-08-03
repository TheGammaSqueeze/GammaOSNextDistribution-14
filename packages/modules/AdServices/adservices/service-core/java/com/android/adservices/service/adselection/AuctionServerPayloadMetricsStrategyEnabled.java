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

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;
import com.android.adservices.service.stats.GetAdSelectionDataBuyerInputGeneratedStats;

import java.util.Map;

public class AuctionServerPayloadMetricsStrategyEnabled
        implements AuctionServerPayloadMetricsStrategy {
    private final AdServicesLogger mAdServicesLogger;

    /** Constructs a {@link AuctionServerPayloadMetricsStrategyEnabled} instance. */
    public AuctionServerPayloadMetricsStrategyEnabled(AdServicesLogger adServicesLogger) {
        mAdServicesLogger = adServicesLogger;
    }

    @Override
    public void setNumBuyers(GetAdSelectionDataApiCalledStats.Builder builder, int numBuyers) {
        builder.setNumBuyers(numBuyers);
    }

    @Override
    public void logGetAdSelectionDataApiCalledStats(
            GetAdSelectionDataApiCalledStats.Builder builder, int payloadSize, int statusCode) {
        mAdServicesLogger.logGetAdSelectionDataApiCalledStats(
                builder.setPayloadSizeKb(payloadSize).setStatusCode(statusCode).build());
    }

    @Override
    public void logGetAdSelectionDataBuyerInputGeneratedStats(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> statsMap) {
        for (BuyerInputGeneratorIntermediateStats buyerStats : statsMap.values()) {
            GetAdSelectionDataBuyerInputGeneratedStats stats =
                    GetAdSelectionDataBuyerInputGeneratedStats.builder()
                            .setNumCustomAudiences(buyerStats.getNumCustomAudiences())
                            .setNumCustomAudiencesOmitAds(buyerStats.getNumCustomAudiencesOmitAds())
                            .setCustomAudienceSizeMeanB(buyerStats.getCustomAudienceSizeMeanB())
                            .setCustomAudienceSizeVarianceB(
                                    buyerStats.getCustomAudienceSizeVarianceB())
                            .setTrustedBiddingSignalsKeysSizeMeanB(
                                    buyerStats.getTrustedBiddingSignalsKeysSizeMeanB())
                            .setTrustedBiddingSignalsKeysSizeVarianceB(
                                    buyerStats.getTrustedBiddingSignalskeysSizeVarianceB())
                            .setUserBiddingSignalsSizeMeanB(
                                    buyerStats.getUserBiddingSignalsSizeMeanB())
                            .setUserBiddingSignalsSizeVarianceB(
                                    buyerStats.getUserBiddingSignalsSizeVarianceB())
                            .build();
            mAdServicesLogger.logGetAdSelectionDataBuyerInputGeneratedStats(stats);
        }
    }

    @Override
    public void addToBuyerIntermediateStats(
            Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats,
            DBCustomAudience dbCustomAudience,
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience) {
        AdTechIdentifier buyerName = dbCustomAudience.getBuyer();

        if (!perBuyerStats.containsKey(buyerName)) {
            perBuyerStats.put(buyerName, new BuyerInputGeneratorIntermediateStats());
        }
        updateInputFromCustomAudience(
                perBuyerStats.get(buyerName), customAudience, dbCustomAudience);
    }

    private void updateInputFromCustomAudience(
            BuyerInputGeneratorIntermediateStats stats,
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience,
            DBCustomAudience dbCustomAudience) {
        stats.incrementNumCustomAudiences();
        if (isCaOmittingAds(customAudience, dbCustomAudience)) {
            stats.incrementNumCustomAudiencesOmitAds();
        }
        stats.addCustomAudienceSize(customAudience.getSerializedSize());
        if (dbCustomAudience.getTrustedBiddingData() != null) {
            int trustedBiddingSignalsKeysSize =
                    dbCustomAudience.getTrustedBiddingData().size()
                            - dbCustomAudience
                                    .getTrustedBiddingData()
                                    .getUri()
                                    .toString()
                                    .getBytes()
                                    .length;
            stats.addTrustedBiddingSignalsKeysSize(trustedBiddingSignalsKeysSize);
        } else {
            stats.addTrustedBiddingSignalsKeysSize(0);
        }
        if (dbCustomAudience.getUserBiddingSignals() != null) {
            stats.addUserBiddingSignalsSize(
                    dbCustomAudience.getUserBiddingSignals().getSizeInBytes());
        } else {
            stats.addUserBiddingSignalsSize(0);
        }
    }

    private boolean isCaOmittingAds(
            BiddingAuctionServers.BuyerInput.CustomAudience customAudience,
            DBCustomAudience dbCustomAudience) {
        return customAudience.getAdRenderIdsCount() == 0
                && ((dbCustomAudience.getAuctionServerRequestFlags()
                                & FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        != 0);
    }
}
