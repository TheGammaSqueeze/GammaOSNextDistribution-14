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

package android.adservices.test.scenario.adservices.utils;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;
import android.util.Log;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StaticAdTechServerUtils {
    private static final String TAG = "StaticAdTechServerUtils";

    private static final String SERVER_BASE_ADDRESS_FORMAT = "https://%s";
    private static final List<String> BUYER_BASE_DOMAINS =
            ImmutableList.of(
                    "performance-fledge-static-5jyy5ulagq-uc.a.run.app",
                    "performance-fledge-static-2-5jyy5ulagq-uc.a.run.app",
                    "performance-fledge-static-3-5jyy5ulagq-uc.a.run.app",
                    "performance-fledge-static-4-5jyy5ulagq-uc.a.run.app",
                    "performance-fledge-static-5-5jyy5ulagq-uc.a.run.app");

    // All details needed to create AdSelectionConfig
    private static final String SELLER_BASE_DOMAIN =
            "performance-fledge-static-5jyy5ulagq-uc.a.run.app";
    private static final AdTechIdentifier SELLER = AdTechIdentifier.fromString(SELLER_BASE_DOMAIN);
    private static final String DECISION_LOGIC_PATH = "/seller/decision/simple_logic";
    private static final String TRUSTED_SCORING_SIGNALS_PATH = "/trusted/scoringsignals/simple";
    private static final AdSelectionSignals AD_SELECTION_SIGNALS =
            AdSelectionSignals.fromString("{\"ad_selection_signals\":1}");
    private static final AdSelectionSignals SELLER_SIGNALS =
            AdSelectionSignals.fromString("{\"test_seller_signals\":1}");

    // All details needed to create custom audiences
    private static final AdSelectionSignals VALID_USER_BIDDING_SIGNALS =
            AdSelectionSignals.fromString("{'valid': 'yep', 'opaque': 'definitely'}");
    private static final String AD_URI_PATH_FORMAT = "/render/%s/%s";
    private static final String DAILY_UPDATE_PATH_FORMAT = "/dailyupdate/%s";
    private static final String BIDDING_LOGIC_PATH = "/buyer/bidding/simple_logic";
    private static final String TRUSTED_BIDDING_SIGNALS_PATH = "/trusted/biddingsignals/simple";
    private static final String CUSTOM_AUDIENCE_PREFIX = "GENERIC_CA_";
    private static final Duration CUSTOM_AUDIENCE_EXPIRE_IN = Duration.ofDays(1);
    private static final Instant VALID_ACTIVATION_TIME = Instant.now();
    private static final Instant VALID_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(CUSTOM_AUDIENCE_EXPIRE_IN);
    private static final ArrayList<String> VALID_TRUSTED_BIDDING_KEYS =
            new ArrayList<>(Arrays.asList("example", "valid", "list", "of", "keys"));

    private final List<AdTechIdentifier> mCustomAudienceBuyers;
    private final Map<AdTechIdentifier, AdSelectionSignals> mPerBuyerSignals;
    private final int mNumberOfBuyers;

    private StaticAdTechServerUtils(
            int numberOfBuyers,
            List<AdTechIdentifier> customAudienceBuyers,
            Map<AdTechIdentifier, AdSelectionSignals> perBuyerSignals) {
        this.mNumberOfBuyers = numberOfBuyers;
        this.mCustomAudienceBuyers = customAudienceBuyers;
        this.mPerBuyerSignals = perBuyerSignals;
    }

    /**
     * Makes a warmup call to all the servers so that servers don't have cold start latency during
     * test runs.
     */
    public static void warmupServers() {
        for (String domain : BUYER_BASE_DOMAINS) {
            String buyerBaseAddress = String.format("https://%s", domain);
            try {
                URL url = new URL(buyerBaseAddress);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.getInputStream();
            } catch (MalformedURLException e) {
                Log.e(TAG, "Parsing ad render url failed", e);
            } catch (ProtocolException e) {
                Log.e(TAG, "Invalid protocol for http call", e);
            } catch (IOException e) {
                Log.e(TAG, "Ad rendering call failed with exception", e);
            }
        }
    }

    public static StaticAdTechServerUtils withNumberOfBuyers(int numberOfBuyers) {
        if (numberOfBuyers > BUYER_BASE_DOMAINS.size()) {
            throw new IllegalArgumentException(
                    "Number of buyers should be less than available domains : "
                            + BUYER_BASE_DOMAINS.size());
        }

        List<AdTechIdentifier> customAudienceBuyers = new ArrayList<>(numberOfBuyers);
        Map<AdTechIdentifier, AdSelectionSignals> perBuyerSignals = new HashMap<>(numberOfBuyers);
        for (int i = 0; i < numberOfBuyers; i++) {
            AdTechIdentifier buyer = AdTechIdentifier.fromString(BUYER_BASE_DOMAINS.get(i));
            customAudienceBuyers.add(buyer);
            perBuyerSignals.put(buyer, AdSelectionSignals.fromString("{\"buyer_signals\":1}"));
        }

        return new StaticAdTechServerUtils(numberOfBuyers, customAudienceBuyers, perBuyerSignals);
    }

    public AdSelectionConfig createAndGetAdSelectionConfig() {
        String sellerBaseAddress = String.format(SERVER_BASE_ADDRESS_FORMAT, SELLER_BASE_DOMAIN);

        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(Uri.parse(sellerBaseAddress + DECISION_LOGIC_PATH))
                .setCustomAudienceBuyers(mCustomAudienceBuyers)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(mPerBuyerSignals)
                .setTrustedScoringSignalsUri(
                        Uri.parse(sellerBaseAddress + TRUSTED_SCORING_SIGNALS_PATH))
                .build();
    }

    public List<CustomAudience> createAndGetCustomAudiences(
            int numberOfCustomAudiencesPerBuyer, int numberOfAdsPerCustomAudiences) {
        List<CustomAudience> customAudiences = new ArrayList<>();
        List<Double> bidsForBuyer = new ArrayList<>();

        for (int i = 1; i <= numberOfAdsPerCustomAudiences; i++) {
            bidsForBuyer.add(i + 0.1);
        }

        for (int buyerIndex = 0; buyerIndex < mNumberOfBuyers; buyerIndex++) {
            for (int customAudienceIndex = 1;
                    customAudienceIndex <= numberOfCustomAudiencesPerBuyer;
                    customAudienceIndex++) {
                CustomAudience customAudience =
                        createCustomAudience(
                                buyerIndex,
                                customAudienceIndex,
                                bidsForBuyer,
                                VALID_ACTIVATION_TIME,
                                VALID_EXPIRATION_TIME);
                customAudiences.add(customAudience);
            }
        }

        return customAudiences;
    }

    private CustomAudience createCustomAudience(
            int buyerIndex,
            int customAudienceIndex,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();
        String customAudienceName = CUSTOM_AUDIENCE_PREFIX + customAudienceIndex;

        // Create ads with the custom audience name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            String adRenderUri = getAdRenderUri(buyerIndex, customAudienceName, i + 1);

            ads.add(
                    new AdData.Builder()
                            .setRenderUri(Uri.parse(adRenderUri))
                            .setMetadata("{\"bid\":" + (bids.get(i) + buyerIndex * 0.01) + "}")
                            .build());
        }

        AdTechIdentifier buyerIdentifier = mCustomAudienceBuyers.get(buyerIndex);
        String dailyUpdatePath = String.format(DAILY_UPDATE_PATH_FORMAT, customAudienceName);
        String buyerBaseAddress = String.format("https://%s", BUYER_BASE_DOMAINS.get(buyerIndex));
        return new CustomAudience.Builder()
                .setBuyer(buyerIdentifier)
                .setName(customAudienceName)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setDailyUpdateUri(Uri.parse(buyerBaseAddress + dailyUpdatePath))
                .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        getValidTrustedBiddingDataByBuyer(
                                Uri.parse(buyerBaseAddress + TRUSTED_BIDDING_SIGNALS_PATH)))
                .setBiddingLogicUri(Uri.parse(buyerBaseAddress + BIDDING_LOGIC_PATH))
                .setAds(ads)
                .build();
    }

    public static String getAdRenderUri(int buyerIndex, String ca, int adId) {
        String adPath = String.format(AD_URI_PATH_FORMAT, ca, adId);
        String adRenderUri =
                String.format(SERVER_BASE_ADDRESS_FORMAT, BUYER_BASE_DOMAINS.get(buyerIndex))
                        + adPath;
        return adRenderUri;
    }

    private static TrustedBiddingData getValidTrustedBiddingDataByBuyer(
            Uri validTrustedBiddingUri) {
        return new TrustedBiddingData.Builder()
                .setTrustedBiddingKeys(getValidTrustedBiddingKeys())
                .setTrustedBiddingUri(validTrustedBiddingUri)
                .build();
    }

    private static ImmutableList<String> getValidTrustedBiddingKeys() {
        return ImmutableList.copyOf(VALID_TRUSTED_BIDDING_KEYS);
    }
}
