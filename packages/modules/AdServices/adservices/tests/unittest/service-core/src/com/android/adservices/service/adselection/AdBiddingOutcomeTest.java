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
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.CustomAudienceSignals;

import org.junit.Rule;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

public class AdBiddingOutcomeTest {
    private static final AdData AD_DATA =
            AdDataFixture.getValidAdDataByBuyer(CommonFixture.VALID_BUYER_1, 0);
    private static final Double BID = 0.1;
    private static final AdWithBid AD_WITH_BID = new AdWithBid(AD_DATA, BID);

    private static final Uri BIDDING_LOGIC_URI = Uri.parse("https://www.example.com/test");
    private static final String BUYER_DECISION_LOGIC_JS = "buyer_decision_logic_javascript";
    private static final String NAME = "name";
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Instant ACTIVATION_TIME = CLOCK.instant();
    private static final Instant EXPIRATION_TIME = CLOCK.instant().plus(Duration.ofDays(1));
    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS =
            new CustomAudienceSignals.Builder()
                    .setOwner(CustomAudienceFixture.VALID_OWNER)
                    .setBuyer(CommonFixture.VALID_BUYER_1)
                    .setName(NAME)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                    .build();
    private static final CustomAudienceBiddingInfo CUSTOM_AUDIENCE_BIDDING_INFO =
            CustomAudienceBiddingInfo.create(
                    BIDDING_LOGIC_URI,
                    BUYER_DECISION_LOGIC_JS,
                    CUSTOM_AUDIENCE_SIGNALS,
                    BUYER_CONTEXTUAL_SIGNALS_WITH_AD_COST);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testAdSelectionBiddingOutcomeBuilder() {
        AdBiddingOutcome adBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BID)
                        .setCustomAudienceBiddingInfo(CUSTOM_AUDIENCE_BIDDING_INFO)
                        .build();
        assertEquals(adBiddingOutcome.getAdWithBid(), AD_WITH_BID);
        assertEquals(adBiddingOutcome.getCustomAudienceBiddingInfo(), CUSTOM_AUDIENCE_BIDDING_INFO);
    }

    @Test
    public void testAdBiddingOutcomeFailureMissingBiddingInfo() {
        assertThrows(IllegalStateException.class, () -> AdBiddingOutcome.builder().build());
    }
}
