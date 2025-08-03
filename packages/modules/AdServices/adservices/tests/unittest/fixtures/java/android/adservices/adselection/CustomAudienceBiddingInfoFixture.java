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

import static com.android.adservices.service.adselection.AdSelectionScriptEngine.NUM_BITS_STOCHASTIC_ROUNDING;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import com.android.adservices.service.adselection.AdCost;
import com.android.adservices.service.adselection.BuyerContextualSignals;

public class CustomAudienceBiddingInfoFixture {

    public static final String VALID_BIDDING_LOGIC_URI_FORMAT = "https://%s/bidding/logic/here/";

    public static final int DATA_VERSION_1 = 1;
    public static final int DATA_VERSION_2 = 2;

    public static final String BUYER_DECISION_LOGIC_JS =
            "function runBidding(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                    + ") {;\n"
                    + "}";

    public static final BuyerContextualSignals BUYER_CONTEXTUAL_SIGNALS_WITH_AD_COST =
            BuyerContextualSignals.builder()
                    .setAdCost(new AdCost(1.0, NUM_BITS_STOCHASTIC_ROUNDING))
                    .build();

    public static final BuyerContextualSignals BUYER_CONTEXTUAL_SIGNALS_WITH_DATA_VERSION =
            BuyerContextualSignals.builder().setDataVersion(DATA_VERSION_1).build();

    public static final BuyerContextualSignals BUYER_CONTEXTUAL_SIGNALS_All =
            BuyerContextualSignals.builder()
                    .setAdCost(new AdCost(1.0, NUM_BITS_STOCHASTIC_ROUNDING))
                    .setDataVersion(DATA_VERSION_1)
                    .build();

    public static Uri getValidBiddingLogicUri(String buyer) {
        return Uri.parse(String.format(VALID_BIDDING_LOGIC_URI_FORMAT, buyer));
    }

    public static Uri getValidBiddingLogicUri(AdTechIdentifier buyer) {
        return getValidBiddingLogicUri(buyer.toString());
    }
}
