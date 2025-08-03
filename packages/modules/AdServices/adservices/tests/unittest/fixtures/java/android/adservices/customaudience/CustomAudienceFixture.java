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

package android.adservices.customaudience;

import static android.adservices.common.AdDataFixture.getValidFilterAdDataWithAdRenderIdByBuyer;
import static android.adservices.customaudience.TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Utility class supporting custom audience API unit tests */
public final class CustomAudienceFixture {

    public static final Duration CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN =
            Duration.ofMillis(
                    CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxActivationDelayInMs());
    public static final Duration CUSTOM_AUDIENCE_MAX_EXPIRE_IN =
            Duration.ofMillis(CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxExpireInMs());
    public static final Duration CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN =
            Duration.ofMillis(
                    CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceDefaultExpireInMs());
    public static final long DAY_IN_SECONDS = 60 * 60 * 24;

    public static final String VALID_OWNER = CommonFixture.TEST_PACKAGE_NAME;
    public static final String VALID_NAME = "testCustomAudienceName";

    public static final Instant VALID_ACTIVATION_TIME =
            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
    public static final Instant VALID_DELAYED_ACTIVATION_TIME =
            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plus(
                    CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN.dividedBy(2));
    public static final Instant INVALID_DELAYED_ACTIVATION_TIME =
            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plus(
                    CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN.multipliedBy(2));

    public static final Instant VALID_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN);
    public static final Instant VALID_DELAYED_EXPIRATION_TIME =
            VALID_DELAYED_ACTIVATION_TIME.plusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_BEFORE_NOW_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.minusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_NOW_EXPIRATION_TIME =
            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_BEFORE_DELAYED_EXPIRATION_TIME =
            VALID_DELAYED_ACTIVATION_TIME.minusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_BEYOND_MAX_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(CUSTOM_AUDIENCE_MAX_EXPIRE_IN.multipliedBy(2));
    public static final Instant VALID_LAST_UPDATE_TIME_24_HRS_BEFORE =
            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_LAST_UPDATE_TIME_72_DAYS_BEFORE =
            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(DAY_IN_SECONDS * 72);

    public static final AdSelectionSignals VALID_USER_BIDDING_SIGNALS =
            AdSelectionSignals.fromString("{\"valid\":\"yep\",\"opaque\":\"definitely\"}");

    public static Uri getValidFetchUriByBuyer(AdTechIdentifier buyer, String token) {
        boolean hasToken = token != null && !token.isEmpty();
        return CommonFixture.getUri(buyer, "/ca" + (hasToken ? "?token=" + token : ""));
    }

    public static Uri getValidFetchUriByBuyer(AdTechIdentifier buyer) {
        return getValidFetchUriByBuyer(buyer, null);
    }

    public static Uri getValidDailyUpdateUriByBuyer(AdTechIdentifier buyer) {
        return CommonFixture.getUri(buyer, "/update");
    }

    public static Uri getValidBiddingLogicUriByBuyer(AdTechIdentifier buyer) {
        return CommonFixture.getUri(buyer, "/bidding/logic/here/");
    }

    public static CustomAudience.Builder getValidBuilderForBuyer(AdTechIdentifier buyer) {
        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUri(CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUri(CustomAudienceFixture.getValidBiddingLogicUriByBuyer(buyer))
                .setAds(AdDataFixture.getValidAdsByBuyer(buyer));
    }

    /** Build valid CA with server auction flags */
    public static CustomAudience.Builder getValidBuilderByBuyerWithAuctionServerRequestFlags(
            AdTechIdentifier buyer,
            @CustomAudience.AuctionServerRequestFlag int auctionServerRequestFlags) {
        return getValidBuilderForBuyer(buyer)
                .setAuctionServerRequestFlags(auctionServerRequestFlags);
    }

    public static CustomAudience.Builder getValidBuilderWithSubdomainsForBuyer(
            AdTechIdentifier buyer) {
        return getValidBuilderForBuyer(buyer)
                .setBiddingLogicUri(
                        CommonFixture.getUriWithValidSubdomain(buyer.toString(), "/bidding/logic"))
                .setDailyUpdateUri(
                        CommonFixture.getUriWithValidSubdomain(buyer.toString(), "/dailyupdate"))
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingUri(
                                        CommonFixture.getUriWithValidSubdomain(
                                                buyer.toString(), "/trustedbidding"))
                                .setTrustedBiddingKeys(
                                        TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_KEYS)
                                .build())
                .setAds(
                        Arrays.asList(
                                AdDataFixture.getValidAdDataWithSubdomainBuilderByBuyer(buyer, 0)
                                        .build(),
                                AdDataFixture.getValidAdDataWithSubdomainBuilderByBuyer(buyer, 1)
                                        .build()));
    }

    // TODO(b/266837113) Merge with getValidBuilderForBuyer once filters are unhidden
    public static CustomAudience.Builder getValidBuilderForBuyerFilters(AdTechIdentifier buyer) {
        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUri(CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUri(CustomAudienceFixture.getValidBiddingLogicUriByBuyer(buyer))
                .setAds(AdDataFixture.getValidFilterAdsByBuyer(buyer));
    }

    /** Build valid CA with filters and render id */
    public static CustomAudience.Builder getValidBuilderForBuyerFiltersWithAdRenderId(
            AdTechIdentifier buyer) {
        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUri(CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUri(CustomAudienceFixture.getValidBiddingLogicUriByBuyer(buyer))
                .setAds(AdDataFixture.getValidFilterAdsWithAdRenderIdByBuyer(buyer));
    }

    /** Build N valid CAs with filters and render id */
    public static List<CustomAudience> getNValidCustomAudiences(
            int nBuyers, int nCAsPerBuyer, int nAdsPerCA) {
        List<CustomAudience> customAudiences = new ArrayList<>();
        for (int b = 0; b < nBuyers; b++) {
            AdTechIdentifier buyer = AdTechIdentifier.fromString("buyer%d.com".formatted(b));
            for (int c = 0; c < nCAsPerBuyer; c++) {
                List<AdData> ads = new ArrayList<>();
                for (int a = 0; a < nAdsPerCA; a++) {
                    ads.add(
                            getValidFilterAdDataWithAdRenderIdByBuyer(
                                    buyer, /* sequenceString= */ generateHash(a, b, c)));
                }
                CustomAudience customAudience =
                        new CustomAudience.Builder()
                                .setBuyer(buyer)
                                .setName("testCustomAudience_%s".formatted(generateHash(b, c)))
                                .setActivationTime(VALID_ACTIVATION_TIME)
                                .setExpirationTime(VALID_EXPIRATION_TIME)
                                .setDailyUpdateUri(getValidDailyUpdateUriByBuyer(buyer))
                                .setBiddingLogicUri(getValidBiddingLogicUriByBuyer(buyer))
                                .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                                .setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(buyer))
                                .setAds(ads)
                                .build();
                customAudiences.add(customAudience);
            }
        }
        return customAudiences;
    }

    private static String generateHash(int... vargs) {
        return Arrays.stream(vargs).mapToObj(String::valueOf).collect(Collectors.joining("-"));
    }
}
