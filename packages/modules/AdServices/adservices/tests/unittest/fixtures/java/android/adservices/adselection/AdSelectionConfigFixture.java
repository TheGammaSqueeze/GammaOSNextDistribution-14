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

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** This is a static class meant to help with tests that involve creating an AdSelectionConfig. */
public class AdSelectionConfigFixture {
    public static final AdTechIdentifier SELLER = AdTechIdentifier.fromString("test.com");
    public static final AdTechIdentifier SELLER_1 = AdTechIdentifier.fromString("test2.com");

    // Uri Constants
    public static final String DECISION_LOGIC_FRAGMENT = "/decisionFragment";
    public static final String TRUSTED_SCORING_SIGNAL_FRAGMENT = "/trustedScoringSignalsFragment";

    public static final Uri DECISION_LOGIC_URI =
            CommonFixture.getUri(SELLER, DECISION_LOGIC_FRAGMENT);

    public static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("buyer.example.com");
    public static final AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static final AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    public static final AdTechIdentifier BUYER_3 = AdTechIdentifier.fromString("test3.com");
    public static final List<AdTechIdentifier> CUSTOM_AUDIENCE_BUYERS =
            Arrays.asList(BUYER_1, BUYER_2, BUYER_3);

    public static final AdSelectionSignals EMPTY_SIGNALS = AdSelectionSignals.EMPTY;

    public static final AdSelectionSignals AD_SELECTION_SIGNALS =
            AdSelectionSignals.fromString("{\"ad_selection_signals\":1}");

    public static final AdSelectionSignals SELLER_SIGNALS =
            AdSelectionSignals.fromString("{\"test_seller_signals\":1}");

    public static final Map<AdTechIdentifier, AdSelectionSignals> PER_BUYER_SIGNALS =
            Map.of(
                    BUYER_1,
                    AdSelectionSignals.fromString("{\"buyer_signals\":1}"),
                    BUYER_2,
                    AdSelectionSignals.fromString("{\"buyer_signals\":2}"),
                    BUYER_3,
                    AdSelectionSignals.fromString("{\"buyer_signals\":3}"),
                    BUYER,
                    AdSelectionSignals.fromString("{\"buyer_signals\":0}"));

    public static final Uri TRUSTED_SCORING_SIGNALS_URI =
            CommonFixture.getUri(SELLER, TRUSTED_SCORING_SIGNAL_FRAGMENT);

    /** Creates an AdSelectionConfig object to be used in unit and integration tests */
    public static AdSelectionConfig anAdSelectionConfig() {
        return anAdSelectionConfigBuilder().build();
    }

    /**
     * @return returns a pre-loaded builder, where the internal members of the object can be changed
     *     for the unit tests
     */
    public static AdSelectionConfig.Builder anAdSelectionConfigBuilder() {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(DECISION_LOGIC_URI)
                .setCustomAudienceBuyers(CUSTOM_AUDIENCE_BUYERS)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(PER_BUYER_SIGNALS)
                .setTrustedScoringSignalsUri(TRUSTED_SCORING_SIGNALS_URI);
    }

    /**
     * Creates an AdSelectionConfig object to be used in unit and integration tests Accepts a Uri
     * decisionLogicUri to be used instead of the default
     */
    public static AdSelectionConfig anAdSelectionConfig(@NonNull Uri decisionLogicUri) {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(decisionLogicUri)
                .setCustomAudienceBuyers(CUSTOM_AUDIENCE_BUYERS)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(PER_BUYER_SIGNALS)
                .setTrustedScoringSignalsUri(TRUSTED_SCORING_SIGNALS_URI)
                .build();
    }

    /**
     * Creates an AdSelectionConfig object to be used in unit and integration tests Accepts a Uri
     * decisionLogicUri to be used instead of the default
     */
    public static AdSelectionConfig anAdSelectionConfig(@NonNull AdTechIdentifier seller) {
        return new AdSelectionConfig.Builder()
                .setSeller(seller)
                .setDecisionLogicUri(DECISION_LOGIC_URI)
                .setCustomAudienceBuyers(CUSTOM_AUDIENCE_BUYERS)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(PER_BUYER_SIGNALS)
                .setTrustedScoringSignalsUri(TRUSTED_SCORING_SIGNALS_URI)
                .build();
    }

    /**
     * @return returns a pre-loaded builder, where the internal members of the object can be changed
     *     for the unit tests, this version of Ad Selection builder includes contextual Ads as well
     * @hide
     */
    public static AdSelectionConfig.Builder anAdSelectionConfigWithSignedContextualAdsBuilder() {
        return anAdSelectionConfigBuilder()
                .setPerBuyerSignedContextualAds(
                        SignedContextualAdsFixture.getBuyerSignedContextualAdsMap());
    }
}
