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

package com.android.adservices.customaudience;

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.data.customaudience.DBCustomAudience;

import java.util.List;
import java.util.stream.Collectors;

public class DBCustomAudienceFixture {
    private static final String AD_URI_PREFIX = "http://www.domain.com/adverts/123/";
    public static final DBCustomAudience VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS =
            getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1).build();

    public static DBCustomAudience.Builder getValidBuilderByBuyer(AdTechIdentifier buyer) {
        return new DBCustomAudience.Builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(buyer).build())
                .setBiddingLogicUri(CustomAudienceFixture.getValidBiddingLogicUriByBuyer(buyer))
                .setAds(DBAdDataFixture.getValidDbAdDataListByBuyer(buyer));
    }

    public static DBCustomAudience.Builder getValidBuilderByBuyer(
            AdTechIdentifier buyer, String name) {
        return new DBCustomAudience.Builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(name)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(buyer).build())
                .setBiddingLogicUri(CustomAudienceFixture.getValidBiddingLogicUriByBuyer(buyer))
                .setAds(DBAdDataFixture.getValidDbAdDataListByBuyer(buyer));
    }

    public static DBCustomAudience.Builder getValidBuilderByBuyerWithAdRenderId(
            AdTechIdentifier buyer, String name) {
        return getValidBuilderByBuyerWithAdRenderId(buyer, name, CustomAudienceFixture.VALID_OWNER);
    }

    public static DBCustomAudience.Builder getValidBuilderByBuyerWithAdRenderId(
            AdTechIdentifier buyer, String name, String owner) {
        return new DBCustomAudience.Builder()
                .setOwner(owner)
                .setBuyer(buyer)
                .setName(name)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(buyer).build())
                .setBiddingLogicUri(CustomAudienceFixture.getValidBiddingLogicUriByBuyer(buyer))
                .setAds(DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(buyer));
    }

    public static DBCustomAudience.Builder getValidBuilderByBuyerNoFilters(AdTechIdentifier buyer) {
        return new DBCustomAudience.Builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(buyer).build())
                .setBiddingLogicUri(CustomAudienceFixture.getValidBiddingLogicUriByBuyer(buyer))
                .setAds(DBAdDataFixture.getValidDbAdDataListByBuyerNoFilters(buyer));
    }

    public static List<DBCustomAudience> getListOfBuyersCustomAudiences(
            List<AdTechIdentifier> buyers) {
        return buyers.stream()
                .map(a -> DBCustomAudienceFixture.getValidBuilderByBuyer(a).build())
                .collect(Collectors.toList());
    }

    /** Build valid DB CA with server auction flags */
    public static DBCustomAudience.Builder getValidBuilderByBuyerWithOmitAdsEnabled(
            AdTechIdentifier buyer) {
        return getValidBuilderByBuyerNoFilters(buyer)
                .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS);
    }

    /** Build valid DB CA with server auction flags and name */
    public static DBCustomAudience.Builder getValidBuilderByBuyerWithOmitAdsEnabled(
            AdTechIdentifier buyer, String name) {
        return getValidBuilderByBuyerWithAdRenderId(buyer, name, CustomAudienceFixture.VALID_OWNER)
                .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS);
    }
}
