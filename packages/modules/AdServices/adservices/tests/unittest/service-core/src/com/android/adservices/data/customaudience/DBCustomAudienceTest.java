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

package com.android.adservices.data.customaudience;

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.customaudience.CustomAudienceUpdatableData;
import com.android.adservices.service.customaudience.CustomAudienceUpdatableDataFixture;

import com.google.common.collect.ImmutableSet;

import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DBCustomAudienceTest {
    private static final Flags FLAGS = FlagsFactory.getFlagsForTest();
    private static final Duration DEFAULT_EXPIRE_IN =
            Duration.ofMillis(FLAGS.getFledgeCustomAudienceDefaultExpireInMs());

    private static final AdDataConversionStrategy AD_DATA_CONVERSION_STRATEGY =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testFromServiceObject_success() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyerFilters(
                                        CommonFixture.VALID_BUYER_1)
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN,
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_successNoFilters() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN,
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_successNoRenderId() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(
                                List.of(
                                        DBAdDataFixture.getValidDbAdDataBuilder()
                                                .setAdFilters(null)
                                                .setAdCounterKeys(ImmutableSet.of())
                                                .setAdRenderId(null)
                                                .build()))
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setAds(
                                        List.of(
                                                AdDataFixture.getValidAdDataBuilderByBuyer(
                                                                CommonFixture.VALID_BUYER_1, 0)
                                                        .setAdFilters(null)
                                                        .setAdCounterKeys(ImmutableSet.of())
                                                        .setAdRenderId("render-id")
                                                        .build()))
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN,
                        AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, false),
                        false,
                        false));
    }

    @Test
    public void testFromServiceObject_successWIthRenderId() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setAds(
                                List.of(
                                        DBAdDataFixture.getValidDbAdDataBuilder()
                                                .setAdFilters(null)
                                                .setAdCounterKeys(ImmutableSet.of())
                                                .setAdRenderId("render-id")
                                                .build()))
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setAds(
                                        List.of(
                                                AdDataFixture.getValidAdDataBuilderByBuyer(
                                                                CommonFixture.VALID_BUYER_1, 0)
                                                        .setAdFilters(null)
                                                        .setAdCounterKeys(ImmutableSet.of())
                                                        .setAdRenderId("render-id")
                                                        .build()))
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN,
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_successWithServerAuctionFlagsEnabled() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithOmitAdsEnabled(
                                CommonFixture.VALID_BUYER_1)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderByBuyerWithAuctionServerRequestFlags(
                                        CommonFixture.VALID_BUYER_1,
                                        FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN,
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        /* auctionServerRequestFlags = */ true));
    }

    @Test
    public void testFromServiceObject_withServerAuctionFlags_serverAuctionFlagsDisabled_success() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderByBuyerWithAuctionServerRequestFlags(
                                        CommonFixture.VALID_BUYER_1,
                                        FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN,
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_nullCustomAudience() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBCustomAudience.fromServiceObject(
                                null,
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                                DEFAULT_EXPIRE_IN,
                                AD_DATA_CONVERSION_STRATEGY,
                                false,
                                /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_nullCallerPackageName() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build(),
                                null,
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                                DEFAULT_EXPIRE_IN,
                                AD_DATA_CONVERSION_STRATEGY,
                                false,
                                /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_nullCurrentTime() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build(),
                                CustomAudienceFixture.VALID_OWNER,
                                null,
                                DEFAULT_EXPIRE_IN,
                                AD_DATA_CONVERSION_STRATEGY,
                                false,
                                /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_nullDefaultExpireIn() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build(),
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                                null,
                                AD_DATA_CONVERSION_STRATEGY,
                                false,
                                /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_nullConversionStrategy() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build(),
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                                DEFAULT_EXPIRE_IN,
                                null,
                                false,
                                /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_noAdsData_lastUpdatedSetToZero() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setLastAdsAndBiddingDataUpdatedTime(Instant.EPOCH)
                        .setAds(null)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyerFilters(
                                        CommonFixture.VALID_BUYER_1)
                                .setAds(null)
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN,
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_activationTimeBeforeCurrentTime_setToNow() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setActivationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyerFilters(
                                        CommonFixture.VALID_BUYER_1)
                                .setActivationTime(
                                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(
                                                200))
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN,
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_nullActivationTime() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setActivationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyerFilters(
                                        CommonFixture.VALID_BUYER_1)
                                .setActivationTime(null)
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN,
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testFromServiceObject_nullExpirationTime() {
        assertEquals(
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setExpirationTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plus(DEFAULT_EXPIRE_IN))
                        .build(),
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyerFilters(
                                        CommonFixture.VALID_BUYER_1)
                                .setExpirationTime(null)
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                        DEFAULT_EXPIRE_IN,
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        /* auctionServerRequestFlags = */ false));
    }

    @Test
    public void testCopyWithNullUpdatableDataThrowsException() {
        DBCustomAudience customAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();

        assertThrows(NullPointerException.class, () -> customAudience.copyWithUpdatableData(null));
    }

    @Test
    public void testCopyWithUnsuccessfulUpdatableDataDoesNotChange() {
        Instant originalUpdateTime = CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
        DBCustomAudience originalCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setLastAdsAndBiddingDataUpdatedTime(originalUpdateTime)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build())
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyer(
                                        CommonFixture.VALID_BUYER_1))
                        .build();

        Instant attemptedUpdateTime = originalUpdateTime.plusSeconds(10);
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptyFailedResponse()
                        .setAttemptedUpdateTime(attemptedUpdateTime)
                        .build();

        DBCustomAudience updatedCustomAudience =
                originalCustomAudience.copyWithUpdatableData(updatableData);

        assertEquals(originalCustomAudience, updatedCustomAudience);
    }

    @Test
    public void testCopyWithDebuggableWorksSuccessfully() {
        Instant originalUpdateTime = CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
        DBCustomAudience originalCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setLastAdsAndBiddingDataUpdatedTime(originalUpdateTime)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build())
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyer(
                                        CommonFixture.VALID_BUYER_1))
                        .setDebuggable(true)
                        .build();

        Instant attemptedUpdateTime = originalUpdateTime.plusSeconds(10);
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptyFailedResponse()
                        .setAttemptedUpdateTime(attemptedUpdateTime)
                        .build();

        DBCustomAudience updatedCustomAudience =
                originalCustomAudience.copyWithUpdatableData(updatableData);

        assertEquals(originalCustomAudience, updatedCustomAudience);
    }

    @Test
    public void testCopyWithSuccessfulEmptyUpdatableDataOnlyUpdatesTime() {
        Instant originalUpdateTime = CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
        DBCustomAudience originalCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setLastAdsAndBiddingDataUpdatedTime(originalUpdateTime)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build())
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyer(
                                        CommonFixture.VALID_BUYER_1))
                        .build();

        Instant attemptedUpdateTime = originalUpdateTime.plusSeconds(10);
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptySuccessfulResponse()
                        .setAttemptedUpdateTime(attemptedUpdateTime)
                        .build();

        DBCustomAudience expectedCustomAudience =
                new DBCustomAudience.Builder(originalCustomAudience)
                        .setLastAdsAndBiddingDataUpdatedTime(attemptedUpdateTime)
                        .build();

        DBCustomAudience updatedCustomAudience =
                originalCustomAudience.copyWithUpdatableData(updatableData);

        assertEquals(expectedCustomAudience, updatedCustomAudience);
    }

    @Test
    public void testCopyWithSuccessfulFullUpdatableDataUpdatesAll() {
        Instant originalUpdateTime = CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
        DBCustomAudience originalCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setLastAdsAndBiddingDataUpdatedTime(originalUpdateTime)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .setTrustedBiddingData(
                                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build())
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyer(
                                        CommonFixture.VALID_BUYER_1))
                        .build();

        Instant attemptedUpdateTime = originalUpdateTime.plusSeconds(10);
        AdSelectionSignals updatedUserBiddingSignals = AdSelectionSignals.fromString("{'new':1}");
        DBTrustedBiddingData updatedTrustedBiddingData =
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setKeys(Arrays.asList("new", "updated"))
                        .build();
        List<DBAdData> updatedAds = Collections.emptyList();
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptySuccessfulResponse()
                        .setAttemptedUpdateTime(attemptedUpdateTime)
                        .setUserBiddingSignals(updatedUserBiddingSignals)
                        .setTrustedBiddingData(updatedTrustedBiddingData)
                        .setAds(updatedAds)
                        .build();

        DBCustomAudience expectedCustomAudience =
                new DBCustomAudience.Builder(originalCustomAudience)
                        .setLastAdsAndBiddingDataUpdatedTime(attemptedUpdateTime)
                        .setUserBiddingSignals(updatedUserBiddingSignals)
                        .setTrustedBiddingData(updatedTrustedBiddingData)
                        .setAds(updatedAds)
                        .build();

        DBCustomAudience updatedCustomAudience =
                originalCustomAudience.copyWithUpdatableData(updatableData);

        assertEquals(expectedCustomAudience, updatedCustomAudience);
    }
}
