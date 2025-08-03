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

package android.adservices.adselection;

import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.adselection.DBAdSelectionInitialization;
import com.android.adservices.data.adselection.DBAdSelectionResult;
import com.android.adservices.data.adselection.DBReportingComputationInfo;
import com.android.adservices.data.adselection.DBReportingData;
import com.android.adservices.data.adselection.DBWinningCustomAudience;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.data.adselection.datahandlers.RegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.ReportingComputationData;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;

import com.google.common.collect.ImmutableSet;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;

public class DataHandlersFixture {
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);

    public static final long AD_SELECTION_ID_1 = 1L;
    public static final long AD_SELECTION_ID_2 = 2L;
    public static final AdTechIdentifier SELLER_1 = AdTechIdentifier.fromString("seller1test.com");
    public static final AdTechIdentifier BUYER_1 = AdTechIdentifier.fromString("buyer1test.com");
    public static final String TEST_PACKAGE_NAME_1 = "android.adservices.tests1";
    public static final Instant CREATION_INSTANT_1 = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);
    public static final Instant CREATION_INSTANT_2 =
            CLOCK.instant().plusSeconds(10).truncatedTo(ChronoUnit.MILLIS);

    public static final double WIN_BID_1 = 0.1;
    public static final Uri WIN_RENDER_URI_1 = AdDataFixture.getValidRenderUriByBuyer(BUYER_1, 1);
    public static final String TEST_WIN_CA_OWNER = "testOwner";
    public static final ImmutableSet<Integer> TEST_WIN_CA_COUNTER_KEYS =
            AdDataFixture.getAdCounterKeys();

    private static final String REPORTING_FRAGMENT = "/reporting";
    private static final Uri BUYER_REPORTING_URI_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.BUYER, REPORTING_FRAGMENT);
    private static final Uri SELLER_REPORTING_URI_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.SELLER, REPORTING_FRAGMENT);
    public static final Duration CUSTOM_AUDIENCE_EXPIRE_IN = Duration.ofDays(1);
    public static final Instant VALID_ACTIVATION_TIME =
            Instant.now().truncatedTo(ChronoUnit.MILLIS);
    public static final Instant VALID_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(CUSTOM_AUDIENCE_EXPIRE_IN);
    public static final String VALID_NAME = "testCustomAudienceName";
    public static final AdSelectionSignals VALID_USER_BIDDING_SIGNALS =
            AdSelectionSignals.fromString("{'valid': 'yep', 'opaque': 'definitely'}");
    public static final AdSelectionSignals SELLER_SIGNALS =
            AdSelectionSignals.fromString("{\"test_seller_signals\":1}");
    public static final AdSelectionSignals BUYER_SIGNALS =
            AdSelectionSignals.fromString("{\"buyer_signals\":1}");
    public static final String DUMMY_DECISION_LOGIC_JS = "someJs";

    private static final String CLICK_EVENT = "click";
    private static final String HOVER_EVENT = "hover";

    private static final Uri SELLER_1_BASE_URI = CommonFixture.getUri(SELLER_1, "/");
    private static final Uri BUYER_1_BASE_URI = CommonFixture.getUri(BUYER_1, "/");
    private static final Uri SELLER_1_CLICK_URI = Uri.parse(SELLER_1_BASE_URI + CLICK_EVENT);
    private static final Uri SELLER_1_HOVER_URI = Uri.parse(SELLER_1_BASE_URI + HOVER_EVENT);

    public static AdSelectionInitialization AD_SELECTION_INITIALIZATION_1 =
            getAdSelectionInitialization(SELLER_1, TEST_PACKAGE_NAME_1);
    public static DBAdSelectionInitialization DB_AD_SELECTION_INITIALIZATION_1 =
            DBAdSelectionInitialization.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setSeller(AD_SELECTION_INITIALIZATION_1.getSeller())
                    .setCallerPackageName(AD_SELECTION_INITIALIZATION_1.getCallerPackageName())
                    .setCreationInstant(CREATION_INSTANT_1)
                    .build();

    public static DBAdSelectionInitialization DB_AD_SELECTION_INITIALIZATION_2 =
            DBAdSelectionInitialization.builder()
                    .setAdSelectionId(AD_SELECTION_ID_2)
                    .setSeller(AD_SELECTION_INITIALIZATION_1.getSeller())
                    .setCallerPackageName(AD_SELECTION_INITIALIZATION_1.getCallerPackageName())
                    .setCreationInstant(CREATION_INSTANT_2)
                    .build();

    public static AdSelectionResultBidAndUri AD_SELECTION_RESULT_1 =
            getAdSelectionResultBidAndUri(AD_SELECTION_ID_1, WIN_BID_1, WIN_RENDER_URI_1);

    public static AdSelectionResultBidAndUri AD_SELECTION_RESULT_2 =
            getAdSelectionResultBidAndUri(AD_SELECTION_ID_2, WIN_BID_1, WIN_RENDER_URI_1);

    public static WinningCustomAudience WINNING_CUSTOM_AUDIENCE_ALL_FIELDS_SET =
            getWinningCustomAudience(TEST_WIN_CA_OWNER, "caAllFields", TEST_WIN_CA_COUNTER_KEYS);
    public static WinningCustomAudience WINNING_CUSTOM_AUDIENCE_ONLY_NAME =
            getWinningCustomAudience(null, "caOnlyName", null);
    public static ReportingComputationData REPORTING_COMPUTATION_DATA_1 =
            ReportingComputationData.builder()
                    .setBuyerDecisionLogicJs(DUMMY_DECISION_LOGIC_JS)
                    .setBuyerDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                    .setSellerContextualSignals(SELLER_SIGNALS)
                    .setBuyerContextualSignals(BUYER_SIGNALS)
                    .setWinningCustomAudienceSignals(
                            CustomAudienceSignalsFixture.aCustomAudienceSignals())
                    .setWinningBid(WIN_BID_1)
                    .setWinningRenderUri(WIN_RENDER_URI_1)
                    .build();

    public static DBAdSelectionEntry DB_AD_SELECTION_ENTRY =
            new DBAdSelectionEntry.Builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setSellerContextualSignals(SELLER_SIGNALS.toString())
                    .setBiddingLogicUri(BUYER_1_BASE_URI)
                    .setWinningAdBid(WIN_BID_1)
                    .setCustomAudienceSignals(CustomAudienceSignalsFixture.aCustomAudienceSignals())
                    .setCreationTimestamp(CREATION_INSTANT_1)
                    .setBuyerContextualSignals(BUYER_SIGNALS.toString())
                    .setBuyerDecisionLogicJs(DUMMY_DECISION_LOGIC_JS)
                    .setWinningAdRenderUri(WIN_RENDER_URI_1)
                    .build();

    public static DBReportingComputationInfo DB_REPORTING_COMPUTATION_INFO =
            DBReportingComputationInfo.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setSellerContextualSignals(SELLER_SIGNALS.toString())
                    .setBiddingLogicUri(BUYER_1_BASE_URI)
                    .setWinningAdBid(WIN_BID_1)
                    .setCustomAudienceSignals(CustomAudienceSignalsFixture.aCustomAudienceSignals())
                    .setBuyerContextualSignals(BUYER_SIGNALS.toString())
                    .setBuyerDecisionLogicJs(DUMMY_DECISION_LOGIC_JS)
                    .setWinningAdRenderUri(WIN_RENDER_URI_1)
                    .build();
    public static ReportingData REPORTING_DATA_WITH_URIS =
            getReportingData(BUYER_REPORTING_URI_1, SELLER_REPORTING_URI_1);

    public static RegisteredAdInteraction REGISTERED_AD_INTERACTIONS_SELLER_1_CLICK =
            RegisteredAdInteraction.builder()
                    .setInteractionKey(CLICK_EVENT)
                    .setInteractionReportingUri(SELLER_1_CLICK_URI)
                    .build();

    public static RegisteredAdInteraction REGISTERED_AD_INTERACTIONS_SELLER_1_HOVER =
            RegisteredAdInteraction.builder()
                    .setInteractionKey(HOVER_EVENT)
                    .setInteractionReportingUri(SELLER_1_HOVER_URI)
                    .build();

    public static DBWinningCustomAudience DB_WINNING_CA_ALL_FIELDS_SET =
            DBWinningCustomAudience.builder()
                    .setOwner(WINNING_CUSTOM_AUDIENCE_ALL_FIELDS_SET.getOwner())
                    .setName(WINNING_CUSTOM_AUDIENCE_ALL_FIELDS_SET.getName())
                    .setAdCounterIntKeys(WINNING_CUSTOM_AUDIENCE_ALL_FIELDS_SET.getAdCounterKeys())
                    .build();

    public static DBWinningCustomAudience DB_WINNING_CA_ONLY_NAME =
            DBWinningCustomAudience.builder()
                    .setName(WINNING_CUSTOM_AUDIENCE_ONLY_NAME.getName())
                    .build();
    public static DBAdSelectionResult.Builder DB_AD_SELECTION_RESULT_FOR_CA_ALL_FIELDS =
            DBAdSelectionResult.builder()
                    .setWinningAdBid(AD_SELECTION_RESULT_1.getWinningAdBid())
                    .setWinningBuyer(BUYER_1)
                    .setWinningAdRenderUri(AD_SELECTION_RESULT_1.getWinningAdRenderUri())
                    .setWinningCustomAudience(DB_WINNING_CA_ALL_FIELDS_SET);

    public static DBAdSelectionResult.Builder DB_AD_SELECTION_RESULT_FOR_CA_ONLY_NAME =
            DBAdSelectionResult.builder()
                    .setWinningAdBid(AD_SELECTION_RESULT_1.getWinningAdBid())
                    .setWinningBuyer(BUYER_1)
                    .setWinningAdRenderUri(AD_SELECTION_RESULT_1.getWinningAdRenderUri())
                    .setWinningCustomAudience(DB_WINNING_CA_ONLY_NAME);

    public static DBReportingData.Builder DB_REPORTING_DATA =
            DBReportingData.builder()
                    .setBuyerReportingUri(REPORTING_DATA_WITH_URIS.getBuyerWinReportingUri())
                    .setSellerReportingUri(REPORTING_DATA_WITH_URIS.getSellerWinReportingUri());

    public static DBAdSelectionResult getDBAdSelectionResultForCaAllFieldsWithId(
            long adSelectionId) {
        return DB_AD_SELECTION_RESULT_FOR_CA_ALL_FIELDS.setAdSelectionId(adSelectionId).build();
    }

    public static DBAdSelectionResult getDBAdSelectionResultForCaOnlyNameWithId(
            long adSelectionId) {
        return DB_AD_SELECTION_RESULT_FOR_CA_ONLY_NAME.setAdSelectionId(adSelectionId).build();
    }

    public static Uri getBuyerWinReportingUriForBuyer(AdTechIdentifier buyer) {
        return CommonFixture.getUri(buyer, REPORTING_FRAGMENT);
    }

    public static Uri getSellerWinReportingUriForBuyer(AdTechIdentifier seller) {
        return CommonFixture.getUri(seller, REPORTING_FRAGMENT);
    }

    public static DBReportingData getDBReportingDataWithId(long adSelectionId) {
        return DB_REPORTING_DATA.setAdSelectionId(adSelectionId).build();
    }

    public static AdSelectionInitialization getAdSelectionInitialization(
            AdTechIdentifier seller, String callerPackageName) {
        return AdSelectionInitialization.create(seller, callerPackageName, CREATION_INSTANT_1);
    }

    public static AdSelectionResultBidAndUri getAdSelectionResultBidAndUri(
            long adSelectionId, double bid, Uri adRenderUri) {
        return AdSelectionResultBidAndUri.builder()
                .setAdSelectionId(adSelectionId)
                .setWinningAdBid(bid)
                .setWinningAdRenderUri(adRenderUri)
                .build();
    }

    public static WinningCustomAudience getWinningCustomAudience(
            String owner, String name, Set<Integer> adCounterKeys) {
        return WinningCustomAudience.builder()
                .setOwner(owner)
                .setName(name)
                .setAdCounterKeys(adCounterKeys)
                .build();
    }

    public static ReportingData getReportingData(Uri buyerReportingUri, Uri sellerReportingUri) {
        return ReportingData.builder()
                .setBuyerWinReportingUri(buyerReportingUri)
                .setSellerWinReportingUri(sellerReportingUri)
                .build();
    }
}
