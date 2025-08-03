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

package com.android.adservices.data.adselection.datahandlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.CustomAudienceSignals;

import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ReportingDataTest {
    public static final Duration CUSTOM_AUDIENCE_EXPIRE_IN = Duration.ofDays(1);
    public static final Instant VALID_ACTIVATION_TIME =
            Instant.now().truncatedTo(ChronoUnit.MILLIS);
    public static final Instant VALID_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(CUSTOM_AUDIENCE_EXPIRE_IN);
    public static final String VALID_NAME = "testCustomAudienceName";
    public static final AdTechIdentifier VALID_BUYER = CommonFixture.VALID_BUYER_1;
    public static final AdSelectionSignals VALID_USER_BIDDING_SIGNALS =
            AdSelectionSignals.fromString("{'valid': 'yep', 'opaque': 'definitely'}");
    public static final CustomAudienceSignals VALID_CUSTOM_AUDIENCE_SIGNALS =
            CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                    .setName(VALID_NAME)
                    .setBuyer(VALID_BUYER)
                    .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                    .setActivationTime(VALID_ACTIVATION_TIME)
                    .setExpirationTime(VALID_EXPIRATION_TIME)
                    .build();

    public static final AdSelectionSignals SELLER_SIGNALS =
            AdSelectionSignals.fromString("{\"test_seller_signals\":1}");
    public static final AdSelectionSignals BUYER_SIGNALS =
            AdSelectionSignals.fromString("{\"buyer_signals\":1}");
    private static final String REPORTING_FRAGMENT = "/reporting";
    private static final String TEST_BUYER_DECISION_LOGIC_JS = "fooJs";
    private static final Uri BUYER_REPORTING_URI_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.BUYER, REPORTING_FRAGMENT);
    private static final Uri SELLER_REPORTING_URI_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.SELLER, REPORTING_FRAGMENT);
    private static final Uri RENDER_URI = Uri.parse("http://www.domain.com/advert");
    private static final double BID = 5;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuild_bothComputationDataAndUriSet_throwsIAE() {
        ReportingComputationData reportingComputationData =
                ReportingComputationData.builder()
                        .setBuyerDecisionLogicJs(TEST_BUYER_DECISION_LOGIC_JS)
                        .setBuyerDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                        .setSellerContextualSignals(SELLER_SIGNALS)
                        .setBuyerContextualSignals(BUYER_SIGNALS)
                        .setWinningCustomAudienceSignals(VALID_CUSTOM_AUDIENCE_SIGNALS)
                        .setWinningRenderUri(RENDER_URI)
                        .setWinningBid(BID)
                        .build();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ReportingData.builder()
                                .setBuyerWinReportingUri(BUYER_REPORTING_URI_1)
                                .setSellerWinReportingUri(SELLER_REPORTING_URI_1)
                                .setReportingComputationData(reportingComputationData)
                                .build());
    }

    @Test
    public void testBuild_withReportingUris_success() {
        ReportingData reportingData =
                ReportingData.builder()
                        .setBuyerWinReportingUri(BUYER_REPORTING_URI_1)
                        .setSellerWinReportingUri(SELLER_REPORTING_URI_1)
                        .build();

        assertEquals(BUYER_REPORTING_URI_1, reportingData.getBuyerWinReportingUri());
        assertEquals(SELLER_REPORTING_URI_1, reportingData.getSellerWinReportingUri());
    }

    @Test
    public void testBuild_withReportingComputationData_success() {
        ReportingComputationData reportingComputationData =
                ReportingComputationData.builder()
                        .setBuyerDecisionLogicJs(TEST_BUYER_DECISION_LOGIC_JS)
                        .setBuyerDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                        .setSellerContextualSignals(SELLER_SIGNALS)
                        .setBuyerContextualSignals(BUYER_SIGNALS)
                        .setWinningCustomAudienceSignals(VALID_CUSTOM_AUDIENCE_SIGNALS)
                        .setWinningRenderUri(RENDER_URI)
                        .setWinningBid(BID)
                        .build();

        ReportingData reportingData =
                ReportingData.builder()
                        .setReportingComputationData(reportingComputationData)
                        .build();

        assertEquals(reportingComputationData, reportingData.getReportingComputationData());
    }
}
