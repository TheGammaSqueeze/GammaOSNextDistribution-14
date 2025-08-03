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

package com.android.adservices.service.adselection;

import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_OWNER;
import static android.adservices.customaudience.TrustedBiddingDataFixture.getValidTrustedBiddingKeys;
import static android.adservices.customaudience.TrustedBiddingDataFixture.getValidTrustedBiddingUriByBuyer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AuctionServerCustomAudienceFiltererTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void test_isValidCustomAudience_nullBiddingData_returnsFalse() {
        DBCustomAudience customAudience =
                new DBCustomAudience.Builder()
                        .setOwner(VALID_OWNER)
                        .setBuyer(VALID_BUYER_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setLastAdsAndBiddingDataUpdatedTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setBiddingLogicUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                        .setTrustedBiddingData(null)
                        .build();

        assertFalse(
                AuctionServerCustomAudienceFilterer.isValidCustomAudienceForServerSideAuction(
                        customAudience));
    }

    @Test
    public void test_isValidCustomAudience_emptyBiddingKeyList_returnsFalse() {
        DBCustomAudience customAudience =
                new DBCustomAudience.Builder()
                        .setOwner(VALID_OWNER)
                        .setBuyer(VALID_BUYER_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setLastAdsAndBiddingDataUpdatedTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setBiddingLogicUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                        .setTrustedBiddingData(
                                new DBTrustedBiddingData(Uri.EMPTY, new ArrayList<>()))
                        .build();

        assertFalse(
                AuctionServerCustomAudienceFilterer.isValidCustomAudienceForServerSideAuction(
                        customAudience));
    }

    @Test
    public void test_isValidCustomAudience_nullBiddingKeys_returnsFalse() {
        DBCustomAudience customAudience =
                new DBCustomAudience.Builder()
                        .setOwner(VALID_OWNER)
                        .setBuyer(VALID_BUYER_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setLastAdsAndBiddingDataUpdatedTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setBiddingLogicUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                        .setTrustedBiddingData(
                                new DBTrustedBiddingData.Builder()
                                        .setUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                                        .setKeys(Collections.singletonList(null))
                                        .build())
                        .build();

        assertFalse(
                AuctionServerCustomAudienceFilterer.isValidCustomAudienceForServerSideAuction(
                        customAudience));
    }

    @Test
    public void test_isValidCustomAudience_emptyBiddingKeys_returnsFalse() {
        DBCustomAudience customAudience =
                new DBCustomAudience.Builder()
                        .setOwner(VALID_OWNER)
                        .setBuyer(VALID_BUYER_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setLastAdsAndBiddingDataUpdatedTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setBiddingLogicUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                        .setTrustedBiddingData(
                                new DBTrustedBiddingData.Builder()
                                        .setUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                                        .setKeys(Collections.singletonList(""))
                                        .build())
                        .build();

        assertFalse(
                AuctionServerCustomAudienceFilterer.isValidCustomAudienceForServerSideAuction(
                        customAudience));
    }

    @Test
    public void test_isValidCustomAudience_noAds_returnsFalse() {
        DBCustomAudience customAudience =
                new DBCustomAudience.Builder()
                        .setOwner(VALID_OWNER)
                        .setBuyer(VALID_BUYER_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setLastAdsAndBiddingDataUpdatedTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setTrustedBiddingData(
                                new DBTrustedBiddingData.Builder()
                                        .setUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                                        .setKeys(getValidTrustedBiddingKeys())
                                        .build())
                        .setBiddingLogicUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                        .setAds(new ArrayList<>())
                        .build();

        assertFalse(
                AuctionServerCustomAudienceFilterer.isValidCustomAudienceForServerSideAuction(
                        customAudience));
    }

    @Test
    public void test_isValidCustomAudience_emptyAdRenderId_returnsFalse() {
        DBCustomAudience customAudience =
                new DBCustomAudience.Builder()
                        .setOwner(VALID_OWNER)
                        .setBuyer(VALID_BUYER_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setLastAdsAndBiddingDataUpdatedTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setTrustedBiddingData(
                                new DBTrustedBiddingData.Builder()
                                        .setUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                                        .setKeys(getValidTrustedBiddingKeys())
                                        .build())
                        .setBiddingLogicUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                        .setAds(
                                ImmutableList.of(
                                        new DBAdData.Builder()
                                                .setRenderUri(
                                                        AdDataFixture.getValidRenderUriByBuyer(
                                                                VALID_BUYER_1, 1))
                                                .setMetadata(AdDataFixture.VALID_METADATA)
                                                .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                                                .setAdRenderId("")
                                                .build()))
                        .build();

        assertFalse(
                AuctionServerCustomAudienceFilterer.isValidCustomAudienceForServerSideAuction(
                        customAudience));
    }

    @Test
    public void test_isValidCustomAudience_nullAdRenderId_returnsFalse() {
        DBCustomAudience customAudience =
                new DBCustomAudience.Builder()
                        .setOwner(VALID_OWNER)
                        .setBuyer(VALID_BUYER_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setLastAdsAndBiddingDataUpdatedTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setTrustedBiddingData(
                                new DBTrustedBiddingData.Builder()
                                        .setUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                                        .setKeys(getValidTrustedBiddingKeys())
                                        .build())
                        .setBiddingLogicUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                        .setAds(
                                ImmutableList.of(
                                        new DBAdData.Builder()
                                                .setRenderUri(
                                                        AdDataFixture.getValidRenderUriByBuyer(
                                                                VALID_BUYER_1, 1))
                                                .setMetadata(AdDataFixture.VALID_METADATA)
                                                .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                                                .setAdRenderId(null)
                                                .build()))
                        .build();

        assertFalse(
                AuctionServerCustomAudienceFilterer.isValidCustomAudienceForServerSideAuction(
                        customAudience));
    }

    @Test
    public void test_isValidCustomAudience_atLeastOneNonNullNonEmptyKeyAndAdRenderId_returnsTrue() {
        List<String> mixOfValidAndInvalidKeys = getValidTrustedBiddingKeys();
        mixOfValidAndInvalidKeys.addAll(Arrays.asList("", null));
        List<DBAdData> mixOfValidAndInValidAds =
                ImmutableList.of(
                        new DBAdData.Builder()
                                .setRenderUri(
                                        AdDataFixture.getValidRenderUriByBuyer(VALID_BUYER_1, 1))
                                .setMetadata(AdDataFixture.VALID_METADATA)
                                .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                                .setAdRenderId(String.valueOf(1))
                                .build(),
                        new DBAdData.Builder()
                                .setRenderUri(
                                        AdDataFixture.getValidRenderUriByBuyer(VALID_BUYER_1, 1))
                                .setMetadata(AdDataFixture.VALID_METADATA)
                                .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                                .setAdRenderId("")
                                .build(),
                        new DBAdData.Builder()
                                .setRenderUri(
                                        AdDataFixture.getValidRenderUriByBuyer(VALID_BUYER_1, 1))
                                .setMetadata(AdDataFixture.VALID_METADATA)
                                .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                                .setAdRenderId(null)
                                .build());

        DBCustomAudience customAudience =
                new DBCustomAudience.Builder()
                        .setOwner(VALID_OWNER)
                        .setBuyer(VALID_BUYER_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setLastAdsAndBiddingDataUpdatedTime(
                                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setBiddingLogicUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                        .setTrustedBiddingData(
                                new DBTrustedBiddingData.Builder()
                                        .setUri(getValidTrustedBiddingUriByBuyer(VALID_BUYER_1))
                                        .setKeys(mixOfValidAndInvalidKeys)
                                        .build())
                        .setAds(mixOfValidAndInValidAds)
                        .build();

        assertTrue(
                AuctionServerCustomAudienceFilterer.isValidCustomAudienceForServerSideAuction(
                        customAudience));
    }
}
