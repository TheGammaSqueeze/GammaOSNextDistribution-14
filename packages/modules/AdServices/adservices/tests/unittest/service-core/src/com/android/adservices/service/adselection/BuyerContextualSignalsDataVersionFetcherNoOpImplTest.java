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

package com.android.adservices.service.adselection;

import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_1;

import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_BIDDING_KEY;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

import android.adservices.common.AdSelectionSignals;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import com.google.common.collect.ImmutableMap;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class BuyerContextualSignalsDataVersionFetcherNoOpImplTest {
    private static final Uri VALID_URI = Uri.parse("valid");

    private static final Map<String, Object> TRUSTED_BIDDING_SIGNALS_MAP =
            ImmutableMap.of("max_bid_limit", 20, "ad_type", "retail");

    private static final DBTrustedBiddingData TRUSTED_BIDDING_DATA =
            new DBTrustedBiddingData(VALID_URI, List.of("h1", "h2"));

    private static final Map<Uri, TrustedBiddingResponse> MAP_WITHOUT_DATA_VERSION_HEADER =
            ImmutableMap.of(
                    VALID_URI,
                    TrustedBiddingResponse.builder()
                            .setBody(new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP))
                            .setHeaders(new JSONObject())
                            .build());

    private static final Map<Uri, TrustedBiddingResponse> MAP_WITH_DATA_VERSION_HEADER =
            ImmutableMap.of(
                    VALID_URI,
                    TrustedBiddingResponse.builder()
                            .setBody(new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP))
                            .setHeaders(
                                    new JSONObject(
                                            ImmutableMap.of(
                                                    DATA_VERSION_HEADER_BIDDING_KEY,
                                                    List.of(DATA_VERSION_1))))
                            .build());

    private static final BuyerContextualSignalsDataVersionFetcher
            BUYER_CONTEXTUAL_SIGNALS_DATA_VERSION_FETCHER_NO_OP =
                    new BuyerContextualSignalsDataVersionFetcherNoOpImpl();

    private static final AdCost AD_COST = new AdCost(1.0, 8);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testGetContextualSignalsGenerateBidWithDataVersionHeaderReturnsEmpty() {
        assertEquals(
                AdSelectionSignals.EMPTY,
                BUYER_CONTEXTUAL_SIGNALS_DATA_VERSION_FETCHER_NO_OP
                        .getContextualSignalsForGenerateBid(
                                TRUSTED_BIDDING_DATA, MAP_WITH_DATA_VERSION_HEADER));
    }

    @Test
    public void testGetContextualSignalsGenerateBidWithoutDataVersionHeaderReturnsEmpty() {
        assertEquals(
                AdSelectionSignals.EMPTY,
                BUYER_CONTEXTUAL_SIGNALS_DATA_VERSION_FETCHER_NO_OP
                        .getContextualSignalsForGenerateBid(
                                TRUSTED_BIDDING_DATA, MAP_WITHOUT_DATA_VERSION_HEADER));
    }

    @Test
    public void
            testGetContextualSignalsReportWinWithAdCostReturnsBuyerContextualSignalsWithAdCost() {
        BuyerContextualSignals expected =
                BuyerContextualSignals.builder().setAdCost(AD_COST).build();

        assertEquals(
                expected,
                BUYER_CONTEXTUAL_SIGNALS_DATA_VERSION_FETCHER_NO_OP
                        .getContextualSignalsForReportWin(
                                TRUSTED_BIDDING_DATA, MAP_WITHOUT_DATA_VERSION_HEADER, AD_COST));
        assertEquals(
                expected,
                BUYER_CONTEXTUAL_SIGNALS_DATA_VERSION_FETCHER_NO_OP
                        .getContextualSignalsForReportWin(
                                TRUSTED_BIDDING_DATA, MAP_WITH_DATA_VERSION_HEADER, AD_COST));
    }

    @Test
    public void testGetContextualSignalsReportWinReturnsNullWithoutAdCost() {
        assertNull(
                BUYER_CONTEXTUAL_SIGNALS_DATA_VERSION_FETCHER_NO_OP
                        .getContextualSignalsForReportWin(
                                TRUSTED_BIDDING_DATA, MAP_WITHOUT_DATA_VERSION_HEADER, null));
        assertNull(
                BUYER_CONTEXTUAL_SIGNALS_DATA_VERSION_FETCHER_NO_OP
                        .getContextualSignalsForReportWin(
                                TRUSTED_BIDDING_DATA, MAP_WITH_DATA_VERSION_HEADER, null));
    }
}
