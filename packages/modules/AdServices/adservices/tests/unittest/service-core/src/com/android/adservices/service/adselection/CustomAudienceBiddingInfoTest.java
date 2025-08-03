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

package com.android.adservices.service.adselection;

import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.BUYER_CONTEXTUAL_SIGNALS_WITH_AD_COST;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.customaudience.DBCustomAudience;

import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

public class CustomAudienceBiddingInfoTest {
    private static final String BUYER_DECISION_LOGIC_JS = "buyer_decision_logic_javascript";
    private static final String NAME = "name";
    private static final Instant NOW = Instant.now();
    private static final Instant ACTIVATION_TIME = NOW;
    private static final Instant EXPIRATION_TIME = NOW.plus(Duration.ofDays(1));
    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS =
            new CustomAudienceSignals.Builder()
                    .setOwner(CustomAudienceFixture.VALID_OWNER)
                    .setBuyer(CommonFixture.VALID_BUYER_1)
                    .setName(NAME)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                    .build();
    private static final DBCustomAudience CUSTOM_AUDIENCE =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                    .setOwner(CustomAudienceFixture.VALID_OWNER)
                    .setBuyer(CommonFixture.VALID_BUYER_1)
                    .setName(NAME)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                    .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                    .build();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testCustomAudienceBiddingInfo() {
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1),
                        BUYER_DECISION_LOGIC_JS,
                        CUSTOM_AUDIENCE_SIGNALS,
                        null);
        assertEquals(
                customAudienceBiddingInfo.getBiddingLogicUri(),
                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(CommonFixture.VALID_BUYER_1));
        assertEquals(customAudienceBiddingInfo.getBuyerDecisionLogicJs(), BUYER_DECISION_LOGIC_JS);
        assertEquals(customAudienceBiddingInfo.getCustomAudienceSignals(), CUSTOM_AUDIENCE_SIGNALS);
        assertNull(customAudienceBiddingInfo.getBuyerContextualSignals());
    }

    @Test
    public void testCustomAudienceBiddingInfoFromCA() {
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(CUSTOM_AUDIENCE, BUYER_DECISION_LOGIC_JS, null);
        assertEquals(
                customAudienceBiddingInfo.getBiddingLogicUri(),
                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(CommonFixture.VALID_BUYER_1));
        assertEquals(customAudienceBiddingInfo.getBuyerDecisionLogicJs(), BUYER_DECISION_LOGIC_JS);
        assertEquals(customAudienceBiddingInfo.getCustomAudienceSignals(), CUSTOM_AUDIENCE_SIGNALS);
        assertNull(customAudienceBiddingInfo.getBuyerContextualSignals());
    }

    @Test
    public void testCustomAudienceBiddingInfoBuilder() {
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.builder()
                        .setBiddingLogicUri(
                                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                        CommonFixture.VALID_BUYER_1))
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .build();
        assertEquals(
                customAudienceBiddingInfo.getBiddingLogicUri(),
                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(CommonFixture.VALID_BUYER_1));
        assertEquals(customAudienceBiddingInfo.getBuyerDecisionLogicJs(), BUYER_DECISION_LOGIC_JS);
        assertEquals(customAudienceBiddingInfo.getCustomAudienceSignals(), CUSTOM_AUDIENCE_SIGNALS);
        assertNull(customAudienceBiddingInfo.getBuyerContextualSignals());
    }

    @Test
    public void testCustomAudienceBiddingInfoBuilderWithContextualSignals() {
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.builder()
                        .setBiddingLogicUri(
                                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                        CommonFixture.VALID_BUYER_1))
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS_WITH_AD_COST)
                        .build();
        assertEquals(
                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(CommonFixture.VALID_BUYER_1),
                customAudienceBiddingInfo.getBiddingLogicUri());
        assertEquals(BUYER_DECISION_LOGIC_JS, customAudienceBiddingInfo.getBuyerDecisionLogicJs());
        assertEquals(CUSTOM_AUDIENCE_SIGNALS, customAudienceBiddingInfo.getCustomAudienceSignals());
        assertEquals(
                BUYER_CONTEXTUAL_SIGNALS_WITH_AD_COST,
                customAudienceBiddingInfo.getBuyerContextualSignals());
    }
}
