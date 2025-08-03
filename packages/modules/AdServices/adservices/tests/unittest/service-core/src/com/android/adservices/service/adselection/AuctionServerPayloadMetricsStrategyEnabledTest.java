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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import com.google.common.base.Strings;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuctionServerPayloadMetricsStrategyEnabledTest {
    @Spy private GetAdSelectionDataApiCalledStats.Builder mBuilder;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategy;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mBuilder = Mockito.spy(GetAdSelectionDataApiCalledStats.builder());
        mAuctionServerPayloadMetricsStrategy =
                new AuctionServerPayloadMetricsStrategyEnabled(mAdServicesLoggerMock);
    }

    @Test
    public void testSetNumBuyersSetsNumBuyers() {
        mAuctionServerPayloadMetricsStrategy.setNumBuyers(mBuilder, 2);
        verify(mBuilder).setNumBuyers(2);
    }

    @Test
    public void testLogGetAdSelectionDataApiCalledStatsDoesLog() {
        int payloadSize = 2000;
        mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataApiCalledStats(
                mBuilder.setNumBuyers(2), payloadSize, AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mBuilder).setStatusCode(AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mBuilder).setPayloadSizeKb(payloadSize);

        verify(mAdServicesLoggerMock).logGetAdSelectionDataApiCalledStats(any());
    }

    @Test
    public void testLogGetAdSelectionDataBuyerInputGeneratedStatsDoesLog() {
        Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> buyerStats = new HashMap<>();
        BuyerInputGeneratorIntermediateStats stats1 = new BuyerInputGeneratorIntermediateStats();
        stats1.incrementNumCustomAudiences();
        BuyerInputGeneratorIntermediateStats stats2 = new BuyerInputGeneratorIntermediateStats();
        stats2.incrementNumCustomAudiences();
        buyerStats.put(AdTechIdentifier.fromString("hello"), stats1);
        buyerStats.put(AdTechIdentifier.fromString("hello2"), stats2);
        mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataBuyerInputGeneratedStats(
                buyerStats);
        verify(mAdServicesLoggerMock, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(any());
    }

    @Test
    public void testAddToBuyerIntermediateStatsDoesAdd() {
        AdTechIdentifier buyer = AdTechIdentifier.fromString("buyer");
        Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats = new HashMap<>();
        DBCustomAudience dbCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithOmitAdsEnabled(buyer).build();
        BiddingAuctionServers.BuyerInput.CustomAudience customAudience =
                buildCustomAudienceProtoFrom(dbCustomAudience);

        mAuctionServerPayloadMetricsStrategy.addToBuyerIntermediateStats(
                perBuyerStats, dbCustomAudience, customAudience);

        assertThat(perBuyerStats).containsKey(buyer);
        BuyerInputGeneratorIntermediateStats stats = perBuyerStats.get(buyer);
        assertThat(stats.getNumCustomAudiences()).isEqualTo(1);
        assertThat(stats.getNumCustomAudiencesOmitAds()).isEqualTo(1);
    }

    private BiddingAuctionServers.BuyerInput.CustomAudience buildCustomAudienceProtoFrom(
            DBCustomAudience customAudience) {
        BiddingAuctionServers.BuyerInput.CustomAudience.Builder customAudienceBuilder =
                BiddingAuctionServers.BuyerInput.CustomAudience.newBuilder();

        customAudienceBuilder.setName(customAudience.getName()).setOwner(customAudience.getOwner());

        if ((customAudience.getAuctionServerRequestFlags() & FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                == 0) {
            customAudienceBuilder.addAllAdRenderIds(getAdRenderIds(customAudience));
        }
        return customAudienceBuilder.build();
    }

    private List<String> getAdRenderIds(DBCustomAudience dbCustomAudience) {
        return dbCustomAudience.getAds().stream()
                .filter(ad -> !Strings.isNullOrEmpty(ad.getAdRenderId()))
                .map(ad -> ad.getAdRenderId())
                .collect(Collectors.toList());
    }
}
