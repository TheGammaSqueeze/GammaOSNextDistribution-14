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

import static org.mockito.Mockito.verifyZeroInteractions;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

public class AuctionServerPayloadMetricsStrategyDisabledTest {
    @Mock private GetAdSelectionDataApiCalledStats.Builder mBuilder;
    private final AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategy =
            new AuctionServerPayloadMetricsStrategyDisabled();

    @Mock private Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> mPerBuyerStatsMock;
    @Mock private DBCustomAudience mDBCustomAudienceMock;
    @Mock private BiddingAuctionServers.BuyerInput.CustomAudience mCustomAudienceMock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSetNumBuyerDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.setNumBuyers(mBuilder, 2);
        verifyZeroInteractions(mBuilder);
    }

    @Test
    public void testLogGetAdSelectionDataApiCalledStatsDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataApiCalledStats(
                mBuilder, 2000, AdServicesStatusUtils.STATUS_SUCCESS);
        verifyZeroInteractions(mBuilder);
    }

    @Test
    public void testLogGetAdSelectionDataBuyerInputGeneratedStatsDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataBuyerInputGeneratedStats(
                mPerBuyerStatsMock);
        verifyZeroInteractions(mPerBuyerStatsMock);
    }

    @Test
    public void testAddToBuyerIntermediateStatsDoesNothing() {
        mAuctionServerPayloadMetricsStrategy.addToBuyerIntermediateStats(
                mPerBuyerStatsMock, mDBCustomAudienceMock, mCustomAudienceMock);
        verifyZeroInteractions(mPerBuyerStatsMock, mDBCustomAudienceMock, mCustomAudienceMock);
    }
}
