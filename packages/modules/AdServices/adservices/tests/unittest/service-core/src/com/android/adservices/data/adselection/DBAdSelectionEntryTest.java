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

package com.android.adservices.data.adselection;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class DBAdSelectionEntryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Uri BUYER_DECISION_LOGIC_URI = Uri.parse("http://www.domain.com/logic");
    private static final String BUYER_DECISION_LOGIC_JS =
            "function test() { return \"hello world\"; }";
    private static final Uri RENDER_URI = Uri.parse("http://www.domain.com/advert");
    private static final Instant ACTIVATION_TIME = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);
    private static final long AD_SELECTION_ID = 1;
    private static final String BUYER_CONTEXTUAL_SIGNALS = "buyer_contextual_signals";
    private static final String SELLER_CONTEXTUAL_SIGNALS = "seller_contextual_signals";
    private static final double BID = 5;

    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS =
            CustomAudienceSignalsFixture.aCustomAudienceSignals();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildDBAdSelectionEntry() {
        DBAdSelectionEntry dbAdSelectionEntry =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setBiddingLogicUri(BUYER_DECISION_LOGIC_URI)
                        .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .setSellerContextualSignals(SELLER_CONTEXTUAL_SIGNALS)
                        .build();

        assertEquals(AD_SELECTION_ID, dbAdSelectionEntry.getAdSelectionId());
        assertEquals(CUSTOM_AUDIENCE_SIGNALS, dbAdSelectionEntry.getCustomAudienceSignals());
        assertEquals(BUYER_DECISION_LOGIC_URI, dbAdSelectionEntry.getBiddingLogicUri());
        assertEquals(BUYER_CONTEXTUAL_SIGNALS, dbAdSelectionEntry.getBuyerContextualSignals());
        assertEquals(RENDER_URI, dbAdSelectionEntry.getWinningAdRenderUri());
        assertEquals(BID, dbAdSelectionEntry.getWinningAdBid(), 0);
        assertEquals(ACTIVATION_TIME, dbAdSelectionEntry.getCreationTimestamp());
        assertEquals(BUYER_DECISION_LOGIC_JS, dbAdSelectionEntry.getBuyerDecisionLogicJs());
        assertEquals(SELLER_CONTEXTUAL_SIGNALS, dbAdSelectionEntry.getSellerContextualSignals());
    }

    @Test
    public void testFailsToBuildContextualAdWithNonNullBuyerDecisionLogicJs() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DBAdSelectionEntry.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS)
                            .setWinningAdRenderUri(RENDER_URI)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                            .setSellerContextualSignals(SELLER_CONTEXTUAL_SIGNALS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildContextualAdWithNonNullCustomAudienceSignals() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DBAdSelectionEntry.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                            .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS)
                            .setWinningAdRenderUri(RENDER_URI)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .setSellerContextualSignals(SELLER_CONTEXTUAL_SIGNALS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildDBAdSelectionEntryWithUnsetAdSelectionId() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DBAdSelectionEntry.Builder()
                            .setAdSelectionId(0)
                            .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                            .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS)
                            .setWinningAdRenderUri(RENDER_URI)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                            .setSellerContextualSignals(SELLER_CONTEXTUAL_SIGNALS)
                            .build();
                });
    }

    @Test
    public void testEqualDBAdSelectionEntryObjectsHaveSameHashCode() {
        DBAdSelectionEntry obj1 =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setBiddingLogicUri(BUYER_DECISION_LOGIC_URI)
                        .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .setSellerContextualSignals(SELLER_CONTEXTUAL_SIGNALS)
                        .build();

        DBAdSelectionEntry obj2 =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setBiddingLogicUri(BUYER_DECISION_LOGIC_URI)
                        .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .setSellerContextualSignals(SELLER_CONTEXTUAL_SIGNALS)
                        .build();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualDBAdSelectionEntryObjectsHaveDifferentHashCodes() {
        DBAdSelectionEntry obj1 =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setBiddingLogicUri(BUYER_DECISION_LOGIC_URI)
                        .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .setSellerContextualSignals(SELLER_CONTEXTUAL_SIGNALS)
                        .build();
        DBAdSelectionEntry obj2 =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(2)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setBiddingLogicUri(BUYER_DECISION_LOGIC_URI)
                        .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .setSellerContextualSignals(SELLER_CONTEXTUAL_SIGNALS)
                        .build();
        DBAdSelectionEntry obj3 =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setBiddingLogicUri(BUYER_DECISION_LOGIC_URI)
                        .setBuyerContextualSignals("different-signals")
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .setSellerContextualSignals(SELLER_CONTEXTUAL_SIGNALS)
                        .build();

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3);
    }

    @Test
    public void testBuildDBAdSelectionEntryWithNullSellerContextualSignals() {
        DBAdSelectionEntry dbAdSelectionEntry =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setBiddingLogicUri(BUYER_DECISION_LOGIC_URI)
                        .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .setSellerContextualSignals(null)
                        .build();

        assertEquals(AD_SELECTION_ID, dbAdSelectionEntry.getAdSelectionId());
        assertEquals(CUSTOM_AUDIENCE_SIGNALS, dbAdSelectionEntry.getCustomAudienceSignals());
        assertEquals(BUYER_DECISION_LOGIC_URI, dbAdSelectionEntry.getBiddingLogicUri());
        assertEquals(BUYER_CONTEXTUAL_SIGNALS, dbAdSelectionEntry.getBuyerContextualSignals());
        assertEquals(RENDER_URI, dbAdSelectionEntry.getWinningAdRenderUri());
        assertEquals(BID, dbAdSelectionEntry.getWinningAdBid(), 0);
        assertEquals(ACTIVATION_TIME, dbAdSelectionEntry.getCreationTimestamp());
        assertEquals(BUYER_DECISION_LOGIC_JS, dbAdSelectionEntry.getBuyerDecisionLogicJs());
        assertNull(dbAdSelectionEntry.getSellerContextualSignals());
    }

    @Test
    public void testBuildDBAdSelectionEntryWithUnsetSellerContextualSignals() {
        DBAdSelectionEntry dbAdSelectionEntry =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(CUSTOM_AUDIENCE_SIGNALS)
                        .setBiddingLogicUri(BUYER_DECISION_LOGIC_URI)
                        .setBuyerContextualSignals(BUYER_CONTEXTUAL_SIGNALS)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();

        assertEquals(AD_SELECTION_ID, dbAdSelectionEntry.getAdSelectionId());
        assertEquals(CUSTOM_AUDIENCE_SIGNALS, dbAdSelectionEntry.getCustomAudienceSignals());
        assertEquals(BUYER_DECISION_LOGIC_URI, dbAdSelectionEntry.getBiddingLogicUri());
        assertEquals(BUYER_CONTEXTUAL_SIGNALS, dbAdSelectionEntry.getBuyerContextualSignals());
        assertEquals(RENDER_URI, dbAdSelectionEntry.getWinningAdRenderUri());
        assertEquals(BID, dbAdSelectionEntry.getWinningAdBid(), 0);
        assertEquals(ACTIVATION_TIME, dbAdSelectionEntry.getCreationTimestamp());
        assertEquals(BUYER_DECISION_LOGIC_JS, dbAdSelectionEntry.getBuyerDecisionLogicJs());
        assertNull(dbAdSelectionEntry.getSellerContextualSignals());
    }
}
