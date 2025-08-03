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

package com.android.tests.providers.sdkfledge;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.adselection.UpdateAdCounterHistogramRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.adselection.TestAdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SdkFledge extends SandboxedSdkProvider {
    private static final String TAG = "SdkFledge";
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final AdTechIdentifier SELLER = AdTechIdentifier.fromString("test.com");

    private static final AdTechIdentifier BUYER_1 = AdTechIdentifier.fromString("test2.com");
    private static final AdTechIdentifier BUYER_2 = AdTechIdentifier.fromString("test3.com");

    private static final String AD_URI_PREFIX = "/adverts/123/";

    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";

    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";

    // Interaction reporting constants
    private static final String CLICK_INTERACTION = "click";
    private static final String HOVER_INTERACTION = "hover";

    private static final String SELLER_CLICK_URI_PATH = "/click/seller";
    private static final String SELLER_HOVER_URI_PATH = "/hover/seller";

    private static final String BUYER_CLICK_URI_PATH = "/click/buyer";
    private static final String BUYER_HOVER_URI_PATH = "/hover/buyer";

    private static final String SELLER_CLICK_URI =
            String.format("https://%s%s", SELLER, SELLER_CLICK_URI_PATH);

    private static final String SELLER_HOVER_URI =
            String.format("https://%s%s", SELLER, SELLER_HOVER_URI_PATH);

    private static final String BUYER_1_CLICK_URI =
            String.format("https://%s%s", BUYER_1, BUYER_CLICK_URI_PATH);

    private static final String BUYER_1_HOVER_URI =
            String.format("https://%s%s", BUYER_1, BUYER_HOVER_URI_PATH);

    private static final String BUYER_2_CLICK_URI =
            String.format("https://%s%s", BUYER_2, BUYER_CLICK_URI_PATH);

    private static final String BUYER_2_HOVER_URI =
            String.format("https://%s%s", BUYER_2, BUYER_HOVER_URI_PATH);

    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");

    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");

    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            anAdSelectionConfigBuilder()
                    .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                    .setDecisionLogicUri(
                            Uri.parse(
                                    String.format(
                                            "https://%s%s",
                                            SELLER, SELLER_DECISION_LOGIC_URI_PATH)))
                    .setTrustedScoringSignalsUri(
                            Uri.parse(
                                    String.format(
                                            "https://%s%s",
                                            SELLER, SELLER_TRUSTED_SIGNAL_URI_PATH)))
                    .build();
    private static final String HTTPS_SCHEME = "https";

    private static final int BUYER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
    private static final int SELLER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

    private static final String INTERACTION_DATA = "{\"key\":\"value\"}";

    private static final long SLEEP_TIME_MS = (long) 1500 + 100L;

    private AdSelectionClient mAdSelectionClient;
    private TestAdSelectionClient mTestAdSelectionClient;
    private AdvertisingCustomAudienceClient mCustomAudienceClient;
    private TestAdvertisingCustomAudienceClient mTestCustomAudienceClient;

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException {
        try {
            setup();
        } catch (Exception e) {
            String errorMessage =
                    String.format("Error setting up the test: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            throw new LoadSdkException(e, new Bundle());
        }
        // TODO(b/274837158) Uncomment after API is un-hidden
//        String decisionLogicJs =
//                "function scoreAd(ad, bid, auction_config, seller_signals,"
//                        + " trusted_scoring_signals, contextual_signal, user_signal,"
//                        + " custom_audience_signal) { \n"
//                        + "  return {'status': 0, 'score': bid };\n"
//                        + "}\n"
//                        + "function reportResult(ad_selection_config, render_uri, bid,"
//                        + " contextual_signals) { \n"
//                        + "    registerAdBeacon('click', '"
//                        + SELLER_CLICK_URI
//                        + "');\n"
//                        + "    registerAdBeacon('hover', '"
//                        + SELLER_HOVER_URI
//                        + "');\n"
//                        + " return {'status': 0, 'results': {'signals_for_buyer':"
//                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
//                        + getUri(SELLER.toString(), SELLER_REPORTING_PATH).toString()
//                        + "' } };\n"
//                        + "}";

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_uri, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + getUri(SELLER.toString(), SELLER_REPORTING_PATH).toString()
                        + "' } };\n"
                        + "}";


        // TODO(b/274837158) Uncomment after API is un-hidden
//        String biddingLogicJsBuyer1 =
//                "function generateBid(ad, auction_signals, per_buyer_signals,"
//                        + " trusted_bidding_signals, contextual_signals,"
//                        + " custom_audience_signals) { \n"
//                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
//                        + "}\n"
//                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
//                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
//                        + "    registerAdBeacon('click', '"
//                        + BUYER_1_CLICK_URI
//                        + "');\n"
//                        + "    registerAdBeacon('hover', '"
//                        + BUYER_1_HOVER_URI
//                        + "');\n"
//                        + " return {'status': 0, 'results': {'reporting_uri': '"
//                        + getUri(BUYER_1.toString(), BUYER_REPORTING_PATH).toString()
//                        + "' } };\n"
//                        + "}";

        String biddingLogicJsBuyer1 =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + getUri(BUYER_1.toString(), BUYER_REPORTING_PATH).toString()
                        + "' } };\n"
                        + "}";

        // TODO(b/274837158) Uncomment after API is un-hidden
//        String biddingLogicJsBuyer2 =
//                "function generateBid(ad, auction_signals, per_buyer_signals,"
//                        + " trusted_bidding_signals, contextual_signals,"
//                        + " custom_audience_signals) { \n"
//                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
//                        + "}\n"
//                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
//                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
//                        + "    registerAdBeacon('click', '"
//                        + BUYER_2_CLICK_URI
//                        + "');\n"
//                        + "    registerAdBeacon('hover', '"
//                        + BUYER_2_HOVER_URI
//                        + "');\n"
//                        + " return {'status': 0, 'results': {'reporting_uri': '"
//                        + getUri(BUYER_2.toString(), BUYER_REPORTING_PATH).toString()
//                        + "' } };\n"
//                        + "}";

        String biddingLogicJsBuyer2 =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + getUri(BUYER_2.toString(), BUYER_REPORTING_PATH).toString()
                        + "' } };\n"
                        + "}";

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        CustomAudience customAudience1 = createCustomAudience(BUYER_1, bidsForBuyer1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_2, bidsForBuyer2);

        try {
            mCustomAudienceClient.joinCustomAudience(customAudience1).get(10, TimeUnit.SECONDS);
            mCustomAudienceClient.joinCustomAudience(customAudience2).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            String errorMessage =
                    String.format("Error setting up the test: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            throw new LoadSdkException(e, new Bundle());
        }

        try {
            AddAdSelectionOverrideRequest addAdSelectionOverrideRequest =
                    new AddAdSelectionOverrideRequest(
                            AD_SELECTION_CONFIG, decisionLogicJs, TRUSTED_SCORING_SIGNALS);
            mTestAdSelectionClient
                    .overrideAdSelectionConfigRemoteInfo(addAdSelectionOverrideRequest)
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            String errorMessage =
                    String.format(
                            "Error adding ad selection override: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            throw new LoadSdkException(e, new Bundle());
        }

        try {
            AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest1 =
                    new AddCustomAudienceOverrideRequest.Builder()
                            .setBuyer(customAudience1.getBuyer())
                            .setName(customAudience1.getName())
                            .setBiddingLogicJs(biddingLogicJsBuyer1)
                            .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                            .build();
            AddCustomAudienceOverrideRequest addCustomAudienceOverrideRequest2 =
                    new AddCustomAudienceOverrideRequest.Builder()
                            .setBuyer(customAudience2.getBuyer())
                            .setName(customAudience2.getName())
                            .setBiddingLogicJs(biddingLogicJsBuyer2)
                            .setTrustedBiddingSignals(TRUSTED_BIDDING_SIGNALS)
                            .build();

            mTestCustomAudienceClient
                    .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest1)
                    .get(10, TimeUnit.SECONDS);
            mTestCustomAudienceClient
                    .overrideCustomAudienceRemoteInfo(addCustomAudienceOverrideRequest2)
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            String errorMessage =
                    String.format(
                            "Error adding custom audience override: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            throw new LoadSdkException(e, new Bundle());
        }

        Log.i(
                TAG,
                "Running ad selection with logic URI " + AD_SELECTION_CONFIG.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + AD_SELECTION_CONFIG.getDecisionLogicUri().getHost());

        long adSelectionId = -1;
        try {
            // Running ad selection and asserting that the outcome is returned in < 10 seconds
            AdSelectionOutcome outcome =
                    mAdSelectionClient.selectAds(AD_SELECTION_CONFIG).get(10, TimeUnit.SECONDS);

            adSelectionId = outcome.getAdSelectionId();

            if (!outcome.getRenderUri()
                    .equals(getUri(BUYER_2.toString(), AD_URI_PREFIX + "/ad3"))) {
                String errorMessage =
                        String.format(
                                "Ad selection failed to select the correct ad, got %s instead",
                                outcome.getRenderUri().toString());
                Log.e(TAG, errorMessage);
                throw new LoadSdkException(new Exception(errorMessage), new Bundle());
            }
        } catch (Exception e) {
            String errorMessage =
                    String.format(
                            "Error encountered during ad selection: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            throw new LoadSdkException(e, new Bundle());
        }

        try {
            ReportImpressionRequest reportImpressionRequest =
                    new ReportImpressionRequest(adSelectionId, AD_SELECTION_CONFIG);

            // Performing reporting, and asserting that no exception is thrown
            mAdSelectionClient.reportImpression(reportImpressionRequest).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            String errorMessage =
                    String.format(
                            "Error encountered during reporting: message is %s", e.getMessage());
            Log.e(TAG, errorMessage);
            throw new LoadSdkException(e, new Bundle());
        }

        // TODO(b/274837158) Uncomment after API is un-hidden

        //        try {
        //            ReportEventRequest reportInteractionClickRequest =
        //                    new ReportEventRequest(
        //                            adSelectionId,
        //                            CLICK_INTERACTION,
        //                            INTERACTION_DATA,
        //                            BUYER_DESTINATION | SELLER_DESTINATION);
        //
        //            ReportEventRequest reportInteractionHoverRequest =
        //                    new ReportEventRequest(
        //                            adSelectionId,
        //                            HOVER_INTERACTION,
        //                            INTERACTION_DATA,
        //                            BUYER_DESTINATION | SELLER_DESTINATION);
        //
        //            // Performing interaction reporting, and asserting that no exception is thrown
        //            mAdSelectionClient
        //                    .reportInteraction(reportInteractionClickRequest)
        //                    .get(10, TimeUnit.SECONDS);
        //            mAdSelectionClient
        //                    .reportInteraction(reportInteractionHoverRequest)
        //                    .get(10, TimeUnit.SECONDS);
        //        } catch (Exception e) {
        //            String errorMessage =
        //                    String.format(
        //                            "Error encountered during interaction reporting: message is
        // %s", e.getMessage());
        //            Log.e(TAG, errorMessage);
        //            throw new LoadSdkException(e, new Bundle());
        //        }

        try {
            UpdateAdCounterHistogramRequest updateHistogramRequest =
                    new UpdateAdCounterHistogramRequest.Builder(
                                    adSelectionId,
                                    FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                    AD_SELECTION_CONFIG.getSeller())
                            .build();
            mAdSelectionClient
                    .updateAdCounterHistogram(updateHistogramRequest)
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            String errorMessage =
                    String.format(
                            "Error encountered during ad counter histogram update: message is %s",
                            exception.getMessage());
            Log.e(TAG, errorMessage);
            throw new LoadSdkException(exception, new Bundle());
        }

        // If we got this far, that means the test succeeded
        return new SandboxedSdk(new Binder());
    }

    @Override
    public View getView(
            @NonNull Context windowContext, @NonNull Bundle params, int width, int height) {
        return null;
    }

    private void setup() {
        mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(getContext())
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        mTestAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(getContext())
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        mCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(getContext())
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        mTestCustomAudienceClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(getContext())
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private CustomAudience createCustomAudience(final AdTechIdentifier buyer, List<Double> bids) {

        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(getUri(buyer.toString(), AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(buyer + "testCustomAudienceName")
                .setActivationTime(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .setExpirationTime(Instant.now().plus(Duration.ofDays(40)))
                .setDailyUpdateUri(getUri(buyer.toString(), "/update"))
                .setUserBiddingSignals(
                        AdSelectionSignals.fromString("{'valid': 'yep', 'opaque': 'definitely'}"))
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingKeys(
                                        Arrays.asList("example", "valid", "list", "of", "keys"))
                                .setTrustedBiddingUri(getUri(buyer.toString(), "/trusted/bidding"))
                                .build())
                .setBiddingLogicUri(getUri(buyer.toString(), BUYER_BIDDING_LOGIC_URI_PATH))
                .setAds(ads)
                .build();
    }

    public static AdSelectionConfig.Builder anAdSelectionConfigBuilder() {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(getUri(SELLER.toString(), "/update"))
                .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                .setAdSelectionSignals(AdSelectionSignals.EMPTY)
                .setSellerSignals(AdSelectionSignals.fromString("{\"test_seller_signals\":1}"))
                .setPerBuyerSignals(
                        Map.of(
                                BUYER_1,
                                AdSelectionSignals.fromString("{\"buyer_signals\":1}"),
                                BUYER_2,
                                AdSelectionSignals.fromString("{\"buyer_signals\":2}")))
                .setTrustedScoringSignalsUri(getUri(SELLER.toString(), "/trusted/scoring"));
    }

    private static Uri getUri(String host, String path) {
        return Uri.parse(HTTPS_SCHEME + "://" + host + path);
    }
}
