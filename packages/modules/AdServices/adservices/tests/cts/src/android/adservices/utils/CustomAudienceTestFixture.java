/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.adservices.utils;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.content.Context;
import android.util.Log;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CustomAudienceTestFixture {
    private static final String TAG = CustomAudienceTestFixture.class.getPackageName();
    // Timeout for joining or leaving a custom audience.
    private static final int API_RESPONSE_TIMEOUT_SECONDS = 5;
    public static final String AD_URI_PREFIX = "/adverts/123/";
    public static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";

    private final ArrayList<CustomAudience> mCustomAudiencesToCleanUp = new ArrayList<>();
    private AdvertisingCustomAudienceClient mCustomAudienceClient;

    public CustomAudienceTestFixture(Context context) {
        ExecutorService executor = Executors.newCachedThreadPool();
        mCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(context)
                        .setExecutor(executor)
                        .build();
    }

    public CustomAudienceTestFixture(AdvertisingCustomAudienceClient customAudienceClient) {
        mCustomAudienceClient = customAudienceClient;
    }

    /** Return the custom audience client being used. */
    public AdvertisingCustomAudienceClient getClient() {
        return mCustomAudienceClient;
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    public CustomAudience createCustomAudience(final AdTechIdentifier buyer, List<Double> bids) {
        return createCustomAudience(
                buyer,
                bids,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME);
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    public CustomAudience createCustomAudienceWithAdCost(
            final AdTechIdentifier buyer, List<Double> bids, double adCost) {
        return createCustomAudienceWithAdCost(
                buyer,
                bids,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                adCost);
    }

    /** Create a custom audience. */
    public CustomAudience createCustomAudience(
            final AdTechIdentifier buyer,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime) {
        return createCustomAudience(
                buyer + CustomAudienceFixture.VALID_NAME,
                buyer,
                bids,
                activationTime,
                expirationTime);
    }

    /** Create a custom audience. */
    public CustomAudience createCustomAudience(
            String name,
            final AdTechIdentifier buyer,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(buyer, AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(name)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setDailyUpdateUri(CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUri(CommonFixture.getUri(buyer, BUYER_BIDDING_LOGIC_URI_PATH))
                .setAds(ads)
                .build();
    }

    /** Create a custom audience with a given ad cost. */
    public CustomAudience createCustomAudienceWithAdCost(
            final AdTechIdentifier buyer,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime,
            double adCost) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(buyer, AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata(
                                    "{\"result\":" + bids.get(i) + ",\"adCost\":" + adCost + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setDailyUpdateUri(CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUri(CommonFixture.getUri(buyer, BUYER_BIDDING_LOGIC_URI_PATH))
                .setAds(ads)
                .build();
    }

    /** Create a custom audience with given domains. */
    public CustomAudience createCustomAudienceWithSubdomains(
            final AdTechIdentifier buyer, List<Double> bids) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUriWithValidSubdomain(
                                            buyer.toString(), AD_URI_PREFIX + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return CustomAudienceFixture.getValidBuilderWithSubdomainsForBuyer(buyer)
                .setAds(ads)
                .build();
    }

    /** Join a custom audience. */
    public void joinCustomAudience(CustomAudience customAudience)
            throws ExecutionException, InterruptedException, TimeoutException {
        mCustomAudiencesToCleanUp.add(customAudience);
        Log.i(
                TAG,
                "Joining custom audience "
                        + customAudience.getName()
                        + " for buyer "
                        + customAudience.getBuyer());
        mCustomAudienceClient
                .joinCustomAudience(customAudience)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** Leave a custom audience. */
    public void leaveCustomAudience(CustomAudience customAudience)
            throws ExecutionException, InterruptedException, TimeoutException {
        mCustomAudienceClient
                .leaveCustomAudience(customAudience.getBuyer(), customAudience.getName())
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Log.d(TAG, "Left Custom Audience: " + customAudience.getName());
    }

    /** Leave a custom audience. */
    public void leaveJoinedCustomAudiences()
            throws ExecutionException, InterruptedException, TimeoutException {
        try {
            for (CustomAudience customAudience : mCustomAudiencesToCleanUp) {
                Log.i(
                        TAG,
                        "Cleanup: leaving custom audience "
                                + customAudience.getName()
                                + " for buyer"
                                + customAudience.getBuyer());
                mCustomAudienceClient
                        .leaveCustomAudience(customAudience.getBuyer(), customAudience.getName())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            mCustomAudiencesToCleanUp.clear();
        }
    }
}
