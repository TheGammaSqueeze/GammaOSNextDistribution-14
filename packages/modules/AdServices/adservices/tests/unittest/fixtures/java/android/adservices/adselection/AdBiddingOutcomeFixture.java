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

package android.adservices.adselection;

import android.adservices.common.AdData;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.service.adselection.AdBiddingOutcome;
import com.android.adservices.service.adselection.BuyerContextualSignals;
import com.android.adservices.service.adselection.CustomAudienceBiddingInfo;

import java.util.List;
import java.util.stream.Collectors;

public class AdBiddingOutcomeFixture {

    public static AdBiddingOutcome.Builder anAdBiddingOutcomeBuilder(
            AdTechIdentifier buyer, Double bid) {

        final AdData adData =
                new AdData.Builder()
                        .setRenderUri(
                                new Uri.Builder()
                                        .path("valid.example.com/testing/hello/" + buyer.toString())
                                        .build())
                        .setMetadata("{'example': 'metadata', 'valid': true}")
                        .build();
        final double testBid = bid;

        return AdBiddingOutcome.builder()
                .setAdWithBid(new AdWithBid(adData, testBid))
                .setCustomAudienceBiddingInfo(
                        CustomAudienceBiddingInfo.builder()
                                .setBiddingLogicUri(
                                        CustomAudienceBiddingInfoFixture.getValidBiddingLogicUri(
                                                buyer))
                                .setBuyerDecisionLogicJs(
                                        CustomAudienceBiddingInfoFixture.BUYER_DECISION_LOGIC_JS)
                                .setCustomAudienceSignals(
                                        CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                                                .setBuyer(buyer)
                                                .build())
                                .build());
    }

    public static AdBiddingOutcome.Builder anAdBiddingOutcomeBuilderWithBuyerContextualSignals(
            AdTechIdentifier buyer, Double bid, BuyerContextualSignals buyerContextualSignals) {

        final AdData adData =
                new AdData.Builder()
                        .setRenderUri(
                                new Uri.Builder()
                                        .path("valid.example.com/testing/hello/" + buyer.toString())
                                        .build())
                        .setMetadata("{'example': 'metadata', 'valid': true}")
                        .build();
        final double testBid = bid;

        return AdBiddingOutcome.builder()
                .setAdWithBid(new AdWithBid(adData, testBid))
                .setCustomAudienceBiddingInfo(
                        CustomAudienceBiddingInfo.builder()
                                .setBiddingLogicUri(
                                        CustomAudienceBiddingInfoFixture.getValidBiddingLogicUri(
                                                buyer))
                                .setBuyerDecisionLogicJs(
                                        CustomAudienceBiddingInfoFixture.BUYER_DECISION_LOGIC_JS)
                                .setCustomAudienceSignals(
                                        CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                                                .setBuyer(buyer)
                                                .build())
                                .setBuyerContextualSignals(buyerContextualSignals)
                                .build());
    }

    public static List<AdBiddingOutcome> getListOfAdBiddingOutcomes(
            List<Pair<AdTechIdentifier, Double>> buyersAndBids) {
        return buyersAndBids.stream()
                .map(
                        a ->
                                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(a.first, a.second)
                                        .build())
                .collect(Collectors.toList());
    }
}
