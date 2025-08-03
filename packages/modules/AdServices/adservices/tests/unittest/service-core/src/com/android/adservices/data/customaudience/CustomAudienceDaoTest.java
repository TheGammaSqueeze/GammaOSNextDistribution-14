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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.PartialCustomAudience;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.util.Pair;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.common.DecisionLogic;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.JsVersionHelper;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;
import com.android.adservices.service.customaudience.CustomAudienceUpdatableData;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CustomAudienceDaoTest {
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();

    @Mock private EnrollmentDao mEnrollmentDaoMock;

    private static final Uri DAILY_UPDATE_URI_1 = Uri.parse("https://www.example.com/d1");
    private static final AdSelectionSignals USER_BIDDING_SIGNALS_1 =
            AdSelectionSignals.fromString("{\"ExampleBiddingSignal1\":1}");
    private static final Uri AD_DATA_RENDER_URI_1 = Uri.parse("https://www.example.com/a1");
    private static final String AD_DATA_METADATA_1 = "meta1";
    private static final DBAdData ADS_1 =
            new DBAdData.Builder()
                    .setRenderUri(AD_DATA_RENDER_URI_1)
                    .setMetadata(AD_DATA_METADATA_1)
                    .build();
    private static final Uri DAILY_UPDATE_URI_2 = Uri.parse("https://www.example.com/d2");
    private static final AdSelectionSignals USER_BIDDING_SIGNALS_2 =
            AdSelectionSignals.fromString("ExampleBiddingSignal2");
    private static final Uri AD_DATA_RENDER_URI_2 = Uri.parse("https://www.example.com/a2");
    private static final String AD_DATA_METADATA_2 = "meta2";
    private static final DBAdData ADS_2 =
            new DBAdData.Builder()
                    .setRenderUri(AD_DATA_RENDER_URI_2)
                    .setMetadata(AD_DATA_METADATA_2)
                    .build();
    private static final Uri TRUSTED_BIDDING_DATA_URI_2 = Uri.parse("https://www.example.com/t1");
    private static final List<String> TRUSTED_BIDDING_DATA_KEYS_2 =
            Collections.singletonList("key2");
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Instant CURRENT_TIME = CLOCK.instant();
    private static final Instant CREATION_TIME_1 = CURRENT_TIME.truncatedTo(ChronoUnit.MILLIS);
    private static final Instant ACTIVATION_TIME_1 =
            CURRENT_TIME.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant EXPIRATION_TIME_1 =
            CURRENT_TIME.plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant LAST_UPDATED_TIME_1 = CURRENT_TIME.truncatedTo(ChronoUnit.MILLIS);
    private static final Instant LAST_UPDATED_TIME_36_HRS =
            CURRENT_TIME.minus(Duration.ofHours(36)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant LAST_UPDATED_TIME_72_DAYS =
            CURRENT_TIME.minus(Duration.ofDays(72)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant CREATION_TIME_2 =
            CURRENT_TIME.plus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant ACTIVATION_TIME_2 =
            CURRENT_TIME
                    .plus(Duration.ofDays(1))
                    .plus(Duration.ofMinutes(1))
                    .truncatedTo(ChronoUnit.MILLIS);
    private static final Instant EXPIRATION_TIME_2 =
            CURRENT_TIME
                    .plus(Duration.ofDays(3))
                    .plus(Duration.ofMinutes(1))
                    .truncatedTo(ChronoUnit.MILLIS);
    private static final Instant LAST_UPDATED_TIME_2 =
            CURRENT_TIME.plus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant ACTIVATION_TIME_MINUS_ONE_HOUR =
            CURRENT_TIME.minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant CREATION_TIME_MINUS_THREE_DAYS =
            CURRENT_TIME.minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MILLIS);
    private static final Instant EXPIRATION_TIME_MINUS_ONE_DAY =
            CURRENT_TIME.minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS);

    private static final String OWNER_1 = "owner1";
    private static final String OWNER_2 = "owner2";
    private static final String OWNER_3 = "owner3";
    private static final String OWNER_4 = "owner4";

    private static final AdTechIdentifier BUYER_1 = AdTechIdentifier.fromString("buyer1");
    private static final AdTechIdentifier BUYER_2 = AdTechIdentifier.fromString("buyer2");
    private static final AdTechIdentifier BUYER_3 = AdTechIdentifier.fromString("buyer3");
    private static final AdTechIdentifier BUYER_4 = AdTechIdentifier.fromString("buyer4");

    private static final String NAME_1 = "name1";
    private static final String NAME_2 = "name2";
    private static final String NAME_3 = "name3";
    private static final String NAME_4 = "name4";

    private static final Uri BIDDING_LOGIC_URI_1 = Uri.parse("https://www.example.com/e1");
    private static final Uri BIDDING_LOGIC_URI_2 = Uri.parse("https://www.example.com/e2");
    private static final DBTrustedBiddingData TRUSTED_BIDDING_DATA_2 =
            new DBTrustedBiddingData.Builder()
                    .setUri(TRUSTED_BIDDING_DATA_URI_2)
                    .setKeys(TRUSTED_BIDDING_DATA_KEYS_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_1 =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_1)
                    .setCreationTime(CREATION_TIME_1)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(
                            DBTrustedBiddingDataFixture.getValidBuilderByBuyer(BUYER_1).build())
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_1 =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setDailyUpdateUri(DAILY_UPDATE_URI_1)
                    .setEligibleUpdateTime(
                            DBCustomAudienceBackgroundFetchData
                                    .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                            CREATION_TIME_1, TEST_FLAGS))
                    .setIsDebuggable(false)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_1_1 =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_1)
                    .setCreationTime(CREATION_TIME_2)
                    .setExpirationTime(EXPIRATION_TIME_2)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_2)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_2)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(
                            DBTrustedBiddingDataFixture.getValidBuilderByBuyer(BUYER_1).build())
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_1_1 =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setDailyUpdateUri(DAILY_UPDATE_URI_1)
                    .setEligibleUpdateTime(
                            DBCustomAudienceBackgroundFetchData
                                    .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                            CREATION_TIME_2, TEST_FLAGS))
                    .setIsDebuggable(false)
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_1_UPDATED =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setDailyUpdateUri(DAILY_UPDATE_URI_1)
                    .setEligibleUpdateTime(
                            DBCustomAudienceBackgroundFetchData
                                    .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                            LAST_UPDATED_TIME_2, TEST_FLAGS))
                    .setIsDebuggable(false)
                    .build();

    private static final CustomAudienceUpdatableData CUSTOM_AUDIENCE_UPDATABLE_DATA =
            CustomAudienceUpdatableData.builder()
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_2)
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .setAds(List.of(ADS_2))
                    .setAttemptedUpdateTime(LAST_UPDATED_TIME_2)
                    .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                    .setContainsSuccessfulUpdate(true)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_1_UPDATED_FROM_UPDATABLE_DATA =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_1)
                    .setCreationTime(CREATION_TIME_1)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_2)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_2)
                    .setAds(List.of(ADS_2))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_2 =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setActivationTime(ACTIVATION_TIME_2)
                    .setCreationTime(CREATION_TIME_2)
                    .setExpirationTime(EXPIRATION_TIME_2)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_2)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_2)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_2)
                    .setAds(List.of(ADS_2))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_2 =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setDailyUpdateUri(DAILY_UPDATE_URI_2)
                    .setEligibleUpdateTime(
                            DBCustomAudienceBackgroundFetchData
                                    .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                            CREATION_TIME_2, TEST_FLAGS))
                    .setIsDebuggable(true)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_INACTIVE =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_1)
                    .setCreationTime(CREATION_TIME_1)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_ACTIVE =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_EXPIRED =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_3)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_MINUS_ONE_DAY)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_2)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_2)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_2)
                    .setAds(List.of(ADS_2))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_EXPIRED =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_3)
                    .setDailyUpdateUri(DAILY_UPDATE_URI_2)
                    .setEligibleUpdateTime(
                            DBCustomAudienceBackgroundFetchData
                                    .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                            CREATION_TIME_MINUS_THREE_DAYS, TEST_FLAGS))
                    .setIsDebuggable(false)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_UPDATED =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_3)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_36_HRS)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_UPDATED =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_3)
                    .setDailyUpdateUri(DAILY_UPDATE_URI_1)
                    .setEligibleUpdateTime(
                            DBCustomAudienceBackgroundFetchData
                                    .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                            CREATION_TIME_MINUS_THREE_DAYS, TEST_FLAGS))
                    .setIsDebuggable(false)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_OUTDATED =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_72_DAYS)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setUserBiddingSignals(null)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setActivationTime(ACTIVATION_TIME_1)
                    .setCreationTime(CREATION_TIME_1)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(
                            DBTrustedBiddingDataFixture.getValidBuilderByBuyer(BUYER_1).build())
                    .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                    .build();

    private static final DBCustomAudienceBackgroundFetchData
            CUSTOM_AUDIENCE_BGF_DATA_NO_USER_BIDDING_SIGNALS =
                    DBCustomAudienceBackgroundFetchData.builder()
                            .setOwner(OWNER_1)
                            .setBuyer(BUYER_1)
                            .setName(NAME_1)
                            .setDailyUpdateUri(DAILY_UPDATE_URI_1)
                            .setEligibleUpdateTime(Instant.EPOCH)
                            .setIsDebuggable(false)
                            .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(List.of(ADS_1))
                    .setTrustedBiddingData(null)
                    .build();

    private static final DBCustomAudienceBackgroundFetchData
            CUSTOM_AUDIENCE_BGF_DATA_NO_TRUSTED_BIDDING_DATA =
                    DBCustomAudienceBackgroundFetchData.builder()
                            .setOwner(OWNER_2)
                            .setBuyer(BUYER_2)
                            .setName(NAME_2)
                            .setDailyUpdateUri(DAILY_UPDATE_URI_1)
                            .setEligibleUpdateTime(Instant.EPOCH)
                            .setIsDebuggable(false)
                            .build();

    private static final DBCustomAudience CUSTOM_AUDIENCE_NO_ADS =
            new DBCustomAudience.Builder()
                    .setOwner(OWNER_3)
                    .setBuyer(BUYER_3)
                    .setName(NAME_3)
                    .setActivationTime(ACTIVATION_TIME_MINUS_ONE_HOUR)
                    .setCreationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .setExpirationTime(EXPIRATION_TIME_1)
                    .setLastAdsAndBiddingDataUpdatedTime(LAST_UPDATED_TIME_1)
                    .setBiddingLogicUri(BIDDING_LOGIC_URI_1)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                    .setAds(null)
                    .setTrustedBiddingData(TRUSTED_BIDDING_DATA_2)
                    .build();

    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BGF_DATA_NO_ADS =
            DBCustomAudienceBackgroundFetchData.builder()
                    .setOwner(OWNER_3)
                    .setBuyer(BUYER_3)
                    .setName(NAME_3)
                    .setDailyUpdateUri(DAILY_UPDATE_URI_1)
                    .setEligibleUpdateTime(Instant.EPOCH)
                    .setIsDebuggable(false)
                    .build();

    private static final String APP_PACKAGE_NAME_1 = "appPackageName1";
    private static final String BIDDING_LOGIC_JS_1 =
            "function test() { return \"hello world_1\"; }";
    private static final Long BIDDING_LOGIC_JS_VERSION_1 = 1L;
    private static final String TRUSTED_BIDDING_OVERRIDE_DATA_1 = "{\"trusted_bidding_data\":1}";
    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_1 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setAppPackageName(APP_PACKAGE_NAME_1)
                    .setBiddingLogicJS(BIDDING_LOGIC_JS_1)
                    .setBiddingLogicJsVersion(BIDDING_LOGIC_JS_VERSION_1)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_1)
                    .build();

    private static final String APP_PACKAGE_NAME_2 = "appPackageName2";
    private static final String BIDDING_LOGIC_JS_2 =
            "function test() { return \"hello world_2\"; }";
    private static final Long BIDDING_LOGIC_JS_VERSION_2 = 2L;
    private static final String TRUSTED_BIDDING_OVERRIDE_DATA_2 = "{\"trusted_bidding_data\":2}";
    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_2 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setName(NAME_2)
                    .setAppPackageName(APP_PACKAGE_NAME_2)
                    .setBiddingLogicJS(BIDDING_LOGIC_JS_2)
                    .setBiddingLogicJsVersion(BIDDING_LOGIC_JS_VERSION_2)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_2)
                    .build();

    private static final String BIDDING_LOGIC_JS_3 =
            "function test() { return \"hello world_3\"; }";
    private static final String TRUSTED_BIDDING_OVERRIDE_DATA_3 = "{\"trusted_bidding_data\":3}";
    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_3 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setName(NAME_1)
                    .setAppPackageName(APP_PACKAGE_NAME_1)
                    .setBiddingLogicJS(BIDDING_LOGIC_JS_3)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_3)
                    .build();

    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE_4 =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER_4)
                    .setBuyer(BUYER_4)
                    .setName(NAME_4)
                    .setAppPackageName(APP_PACKAGE_NAME_1)
                    .setBiddingLogicJS(BIDDING_LOGIC_JS_1)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA_1)
                    .build();

    private static final DBCustomAudienceQuarantine DB_CUSTOM_AUDIENCE_QUARANTINE_1 =
            DBCustomAudienceQuarantine.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setQuarantineExpirationTime(ACTIVATION_TIME_1)
                    .build();

    private static final DBCustomAudienceQuarantine DB_CUSTOM_AUDIENCE_QUARANTINE_2 =
            DBCustomAudienceQuarantine.builder()
                    .setOwner(OWNER_2)
                    .setBuyer(BUYER_2)
                    .setQuarantineExpirationTime(ACTIVATION_TIME_1)
                    .build();

    private static final DBCustomAudienceQuarantine DB_CUSTOM_AUDIENCE_QUARANTINE_EXPIRED_1 =
            DBCustomAudienceQuarantine.builder()
                    .setOwner(OWNER_1)
                    .setBuyer(BUYER_1)
                    .setQuarantineExpirationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .build();

    private static final DBCustomAudienceQuarantine DB_CUSTOM_AUDIENCE_QUARANTINE_EXPIRED_2 =
            DBCustomAudienceQuarantine.builder()
                    .setOwner(OWNER_3)
                    .setBuyer(BUYER_3)
                    .setQuarantineExpirationTime(CREATION_TIME_MINUS_THREE_DAYS)
                    .build();

    private static final DBScheduledCustomAudienceUpdate.Builder
            DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER =
                    DBScheduledCustomAudienceUpdate.builder()
                            .setBuyer(BUYER_1)
                            .setOwner(OWNER_1)
                            .setUpdateUri(CommonFixture.getUri(BUYER_1, "/updateUri"))
                            .setScheduledTime(CommonFixture.FIXED_NEXT_ONE_DAY)
                            .setCreationTime(CommonFixture.FIXED_NOW);

    private static final DBPartialCustomAudience.Builder DB_PARTIAL_CUSTOM_AUDIENCE_BUILDER =
            DBPartialCustomAudience.builder()
                    .setUpdateId(1L)
                    .setName("partial_audience_one")
                    .setActivationTime(CommonFixture.FIXED_NOW)
                    .setExpirationTime(CommonFixture.FIXED_NEXT_ONE_DAY)
                    .setUserBiddingSignals(USER_BIDDING_SIGNALS_1);

    private MockitoSession mStaticMockSession = null;
    private CustomAudienceDao mCustomAudienceDao;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(PackageManagerCompatUtils.class)
                        .initMocks(this)
                        .startMocking();

        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testPersistCustomAudienceWithAuctionServerFlags() {
        // Assert table is empty
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.persistCustomAudience(CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS);

        // Assert only first object is persisted

        DBCustomAudience customAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1);

        assertNotNull(customAudience);
        assertEquals(
                CUSTOM_AUDIENCE_SERVER_AUCTION_FLAGS.getAuctionServerRequestFlags(),
                customAudience.getAuctionServerRequestFlags());
    }

    @Test
    public void testPersistCustomAudienceQuarantine() {
        // Assert table is empty
        assertFalse(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceQuarantineExist(
                        OWNER_2, CommonFixture.VALID_BUYER_2));

        mCustomAudienceDao.persistCustomAudienceQuarantineData(DB_CUSTOM_AUDIENCE_QUARANTINE_1);

        // Assert only first object is persisted
        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceQuarantineExist(
                        OWNER_2, CommonFixture.VALID_BUYER_2));
    }

    @Test
    public void testGetTotalNumberCustomAudienceQuarantine() {
        // Assert table is empty
        assertEquals(0, mCustomAudienceDao.getTotalNumCustomAudienceQuarantineEntries());

        mCustomAudienceDao.persistCustomAudienceQuarantineData(DB_CUSTOM_AUDIENCE_QUARANTINE_1);
        assertEquals(1, mCustomAudienceDao.getTotalNumCustomAudienceQuarantineEntries());

        mCustomAudienceDao.persistCustomAudienceQuarantineData(DB_CUSTOM_AUDIENCE_QUARANTINE_2);
        assertEquals(2, mCustomAudienceDao.getTotalNumCustomAudienceQuarantineEntries());
    }

    @Test
    public void testSafelyInsertCustomAudienceQuarantineEntries() {
        // Assert table is empty
        assertFalse(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceQuarantineExist(
                        OWNER_2, CommonFixture.VALID_BUYER_2));

        mCustomAudienceDao.safelyInsertCustomAudienceQuarantine(DB_CUSTOM_AUDIENCE_QUARANTINE_1, 1);
        assertThrows(
                IllegalStateException.class,
                () ->
                        mCustomAudienceDao.safelyInsertCustomAudienceQuarantine(
                                DB_CUSTOM_AUDIENCE_QUARANTINE_2, 1));

        // Assert only first object is persisted
        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceQuarantineExist(
                        OWNER_2, CommonFixture.VALID_BUYER_2));
    }

    @Test
    public void testClearExpiredCustomAudienceQuarantineEntries() {
        mCustomAudienceDao.persistCustomAudienceQuarantineData(DB_CUSTOM_AUDIENCE_QUARANTINE_1);
        mCustomAudienceDao.persistCustomAudienceQuarantineData(
                DB_CUSTOM_AUDIENCE_QUARANTINE_EXPIRED_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_3, BUYER_3));

        int numCleared = mCustomAudienceDao.deleteAllExpiredQuarantineEntries(CREATION_TIME_1);

        // Assert only second object was cleared
        assertEquals(1, numCleared);
        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));
        assertFalse(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_3, BUYER_3));
    }

    @Test
    public void testOverwritesCustomAudienceQuarantineEntry() {
        // Persist expired entry
        mCustomAudienceDao.persistCustomAudienceQuarantineData(
                DB_CUSTOM_AUDIENCE_QUARANTINE_EXPIRED_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));

        int numCleared1 = mCustomAudienceDao.deleteAllExpiredQuarantineEntries(CREATION_TIME_1);
        // Assert entry was cleared
        assertEquals(1, numCleared1);
        assertFalse(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));

        // Persist expired entry again
        mCustomAudienceDao.persistCustomAudienceQuarantineData(
                DB_CUSTOM_AUDIENCE_QUARANTINE_EXPIRED_1);

        // Overwrite expired entry with non expired time
        mCustomAudienceDao.persistCustomAudienceQuarantineData(DB_CUSTOM_AUDIENCE_QUARANTINE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));

        int numCleared2 = mCustomAudienceDao.deleteAllExpiredQuarantineEntries(CREATION_TIME_1);
        // Assert entry was not cleared
        assertEquals(0, numCleared2);
        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));
    }

    @Test
    public void getCustomAudienceQuarantineExpiration() {
        assertNull(mCustomAudienceDao.getCustomAudienceQuarantineExpiration(OWNER_1, BUYER_1));

        mCustomAudienceDao.persistCustomAudienceQuarantineData(DB_CUSTOM_AUDIENCE_QUARANTINE_1);

        assertEquals(
                ACTIVATION_TIME_1,
                mCustomAudienceDao.getCustomAudienceQuarantineExpiration(OWNER_1, BUYER_1));
    }

    @Test
    public void testDeletesSingleCustomAudienceQuarantineEntry() {
        mCustomAudienceDao.persistCustomAudienceQuarantineData(DB_CUSTOM_AUDIENCE_QUARANTINE_1);
        mCustomAudienceDao.persistCustomAudienceQuarantineData(DB_CUSTOM_AUDIENCE_QUARANTINE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_2, BUYER_2));

        int numDeleted = mCustomAudienceDao.deleteQuarantineEntry(OWNER_1, BUYER_1);

        assertEquals(1, numDeleted);

        assertFalse(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_1, BUYER_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceQuarantineExist(OWNER_2, BUYER_2));
    }

    @Test
    public void testReturnsTrueIfCustomAudienceOverrideExists() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void testDeletesCustomAudienceOverridesByPrimaryKey() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));

        mCustomAudienceDao.removeCustomAudienceOverrideByPrimaryKeyAndPackageName(
                OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        assertFalse(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void testGetCustomAudiencesByBuyerAndName() {
        String anotherOwner = "anotherowner.com";
        DBCustomAudience caWithOwner1AndBuyer1 =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
        mCustomAudienceDao.persistCustomAudience(caWithOwner1AndBuyer1);
        DBCustomAudience caWithOwner2AndBuyer1 =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setOwner(anotherOwner)
                        .build();
        mCustomAudienceDao.persistCustomAudience(caWithOwner2AndBuyer1);
        DBCustomAudience caWithOwner1AndBuyer2 =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_2).build();
        mCustomAudienceDao.persistCustomAudience(caWithOwner1AndBuyer2);

        List<DBCustomAudience> caWithBuyer1AndName =
                mCustomAudienceDao.getCustomAudiencesForBuyerAndName(
                        CommonFixture.VALID_BUYER_1, CustomAudienceFixture.VALID_NAME);
        assertEquals(2, caWithBuyer1AndName.size());
        assertTrue(caWithBuyer1AndName.contains(caWithOwner1AndBuyer1));
        assertTrue(caWithBuyer1AndName.contains(caWithOwner2AndBuyer1));
        List<DBCustomAudience> caWithBuyer2AndName =
                mCustomAudienceDao.getCustomAudiencesForBuyerAndName(
                        CommonFixture.VALID_BUYER_2, CustomAudienceFixture.VALID_NAME);
        assertEquals(1, caWithBuyer2AndName.size());
        assertTrue(caWithBuyer2AndName.contains(caWithOwner1AndBuyer2));
    }

    @Test
    public void testListDebuggableCustomAudiences_happyPath() {
        DBCustomAudience.Builder ca =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1);
        DBCustomAudience ca1 = ca.setName("ca1").build();
        DBCustomAudience ca2 = ca.setName("ca2").build();
        DBCustomAudienceBackgroundFetchData.Builder builder =
                DBCustomAudienceBackgroundFetchData.builder()
                        .setBuyer(ca1.getBuyer())
                        .setOwner(ca1.getOwner())
                        .setIsDebuggable(true)
                        .setDailyUpdateUri(ca1.getBiddingLogicUri())
                        .setEligibleUpdateTime(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        DBCustomAudienceBackgroundFetchData backgroundFetchData1 =
                builder.setName(ca1.getName()).build();
        DBCustomAudienceBackgroundFetchData backgroundFetchData2 =
                builder.setName(ca2.getName()).build();
        mCustomAudienceDao.persistCustomAudience(ca1);
        mCustomAudienceDao.persistCustomAudience(ca2);
        mCustomAudienceDao.updateCustomAudienceAndBackgroundFetchData(
                backgroundFetchData1, CUSTOM_AUDIENCE_UPDATABLE_DATA);
        mCustomAudienceDao.updateCustomAudienceAndBackgroundFetchData(
                backgroundFetchData2, CUSTOM_AUDIENCE_UPDATABLE_DATA);

        List<DBCustomAudienceBackgroundFetchData> caList =
                mCustomAudienceDao.listDebuggableCustomAudienceBackgroundFetchData(
                        ca1.getOwner(), ca1.getBuyer());

        assertThat(caList).containsExactly(backgroundFetchData1, backgroundFetchData2);
    }

    @Test
    public void testListDebuggableCustomAudiences_withNoResult_returnsEmpty() {
        List<DBCustomAudience> caList =
                mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        "", AdTechIdentifier.fromString(""));

        assertThat(caList).isEmpty();
    }

    @Test
    public void testListBackgroundDebuggableCustomAudiences_happyPath() {
        DBCustomAudience ca =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setName("ca1")
                        .setDebuggable(true)
                        .build();
        mCustomAudienceDao.persistCustomAudience(ca);
        DBCustomAudience ca2 =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setName("ca2")
                        .setDebuggable(true)
                        .build();
        mCustomAudienceDao.persistCustomAudience(ca2);

        List<DBCustomAudience> caList =
                mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        ca.getOwner(), ca.getBuyer());

        assertThat(caList).containsExactly(ca, ca2);
    }

    @Test
    public void testListBackgroundDebuggableCustomAudiences_withNoResult_returnsEmpty() {
        List<DBCustomAudienceBackgroundFetchData> caList =
                mCustomAudienceDao.listDebuggableCustomAudienceBackgroundFetchData(
                        "", AdTechIdentifier.fromString(""));

        assertThat(caList).isEmpty();
    }

    @Test
    public void testViewDebuggableCustomAudiences_happyPath() {
        DBCustomAudience expected =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setDebuggable(true)
                        .build();
        mCustomAudienceDao.persistCustomAudience(expected);

        DBCustomAudience actual =
                mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(
                        expected.getOwner(), expected.getBuyer(), expected.getName());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testViewBackgroundDebuggableCustomAudiences_happyPath() {
        DBCustomAudience ca =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setDebuggable(true)
                        .build();
        DBCustomAudienceBackgroundFetchData expected =
                DBCustomAudienceBackgroundFetchData.builder()
                        .setBuyer(ca.getBuyer())
                        .setName(ca.getName())
                        .setOwner(ca.getOwner())
                        .setIsDebuggable(true)
                        .setDailyUpdateUri(ca.getBiddingLogicUri())
                        .setEligibleUpdateTime(Instant.now().truncatedTo(ChronoUnit.SECONDS))
                        .build();
        mCustomAudienceDao.persistCustomAudience(ca);
        mCustomAudienceDao.updateCustomAudienceAndBackgroundFetchData(
                expected, CUSTOM_AUDIENCE_UPDATABLE_DATA);

        DBCustomAudienceBackgroundFetchData actual =
                mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        ca.getOwner(), ca.getBuyer(), ca.getName());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testViewDebuggableCustomAudiences_withNoResult_returnsNull() {
        DBCustomAudience ca =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
        mCustomAudienceDao.persistCustomAudience(ca);

        DBCustomAudience actual =
                mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(
                        ca.getOwner(), ca.getBuyer(), ca.getName());

        assertThat(actual).isNull();
    }

    @Test
    public void testViewBackgroundDebuggableCustomAudiences_withNoResult_throwsException() {
        DBCustomAudience ca =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
        mCustomAudienceDao.persistCustomAudience(ca);

        DBCustomAudienceBackgroundFetchData actual =
                mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        ca.getOwner(), ca.getBuyer(), ca.getName());

        assertThat(actual).isNull();
    }

    @Test
    public void testDoesNotDeleteCustomAudienceOverrideWithIncorrectPackageName() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.removeCustomAudienceOverrideByPrimaryKeyAndPackageName(
                OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
    }

    @Test
    public void testDeletesAllCustomAudienceOverridesThatMatchPackageName() {
        // Adding with same package name
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_4);

        // Adding with different package name
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_4, BUYER_4, NAME_4));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));

        mCustomAudienceDao.removeCustomAudienceOverridesByPackageName(APP_PACKAGE_NAME_1);

        assertFalse(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertFalse(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_4, BUYER_4, NAME_4));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void testGetCustomAudienceOverrideExists() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));

        DecisionLogic biddingLogicJS =
                mCustomAudienceDao.getBiddingLogicUriOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        assertEquals(
                DecisionLogic.create(
                        BIDDING_LOGIC_JS_1,
                        ImmutableMap.of(
                                JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS,
                                BIDDING_LOGIC_JS_VERSION_1)),
                biddingLogicJS);
    }

    @Test
    public void testCorrectlyOverridesCustomAudienceOverride() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));

        DecisionLogic biddingLogicJs1 =
                mCustomAudienceDao.getBiddingLogicUriOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        String trustedBiddingData_1 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        assertEquals(
                DecisionLogic.create(
                        BIDDING_LOGIC_JS_1,
                        ImmutableMap.of(
                                JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS,
                                BIDDING_LOGIC_JS_VERSION_1)),
                biddingLogicJs1);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_1, trustedBiddingData_1);

        // Persisting with same primary key
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_3);

        DecisionLogic biddingLogicJs3 =
                mCustomAudienceDao.getBiddingLogicUriOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        String trustedBiddingData_3 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        assertEquals(DecisionLogic.create(BIDDING_LOGIC_JS_3, ImmutableMap.of()), biddingLogicJs3);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_3, trustedBiddingData_3);
    }

    @Test
    public void testCorrectlyGetsBothCustomAudienceOverrides() {
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);

        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_1, BUYER_1, NAME_1));
        assertTrue(mCustomAudienceDao.doesCustomAudienceOverrideExist(OWNER_2, BUYER_2, NAME_2));

        DecisionLogic biddingLogicJs1 =
                mCustomAudienceDao.getBiddingLogicUriOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        String trustedBiddingData_1 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(
                        OWNER_1, BUYER_1, NAME_1, APP_PACKAGE_NAME_1);

        assertEquals(
                DecisionLogic.create(
                        BIDDING_LOGIC_JS_1,
                        ImmutableMap.of(
                                JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS,
                                BIDDING_LOGIC_JS_VERSION_1)),
                biddingLogicJs1);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_1, trustedBiddingData_1);

        DecisionLogic biddingLogicJs2 =
                mCustomAudienceDao.getBiddingLogicUriOverride(
                        OWNER_2, BUYER_2, NAME_2, APP_PACKAGE_NAME_2);

        String trustedBiddingData_2 =
                mCustomAudienceDao.getTrustedBiddingDataOverride(
                        OWNER_2, BUYER_2, NAME_2, APP_PACKAGE_NAME_2);

        assertEquals(
                DecisionLogic.create(
                        BIDDING_LOGIC_JS_2,
                        ImmutableMap.of(
                                JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS,
                                BIDDING_LOGIC_JS_VERSION_2)),
                biddingLogicJs2);
        assertEquals(TRUSTED_BIDDING_OVERRIDE_DATA_2, trustedBiddingData_2);
    }

    @Test(expected = NullPointerException.class)
    public void testPersistCustomAudienceOverride() {
        mCustomAudienceDao.persistCustomAudienceOverride(null);
    }

    @Test
    public void getByPrimaryKey_keyExistOrNotExist() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void deleteByPrimaryKey_keyExist() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));

        mCustomAudienceDao.deleteAllCustomAudienceDataByPrimaryKey(OWNER_1, BUYER_1, NAME_1);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void deleteByPrimaryKey_keyNotExist() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));

        mCustomAudienceDao.deleteAllCustomAudienceDataByPrimaryKey(OWNER_1, BUYER_2, NAME_1);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_2, BUYER_2, NAME_2));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_2, BUYER_2, NAME_2));
    }

    @Test
    public void testGetCustomAudienceStats_nullOwner() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mCustomAudienceDao.getCustomAudienceStats(null);
                });
    }

    @Test
    public void testCustomAudienceStats_nonnullOwner() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        verifyCustomAudienceStats(
                mCustomAudienceDao.getCustomAudienceStats(OWNER_1), OWNER_1, 0, 0, 0);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        verifyCustomAudienceStats(
                mCustomAudienceDao.getCustomAudienceStats(OWNER_1), OWNER_1, 1, 1, 1);
        verifyCustomAudienceStats(
                mCustomAudienceDao.getCustomAudienceStats(OWNER_2), OWNER_2, 1, 0, 1);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_1, false);
        verifyCustomAudienceStats(
                mCustomAudienceDao.getCustomAudienceStats(OWNER_1), OWNER_1, 2, 1, 2);
        verifyCustomAudienceStats(
                mCustomAudienceDao.getCustomAudienceStats(OWNER_2), OWNER_2, 2, 1, 2);
        verifyCustomAudienceStats(
                mCustomAudienceDao.getCustomAudienceStats(OWNER_3), OWNER_3, 2, 0, 2);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateOrUpdate_nullCustomAudience() {
        mCustomAudienceDao.persistCustomAudience(null);
    }

    @Test(expected = NullPointerException.class)
    public void testCreateOrUpdate_nullCustomAudienceBackgroundFetchData() {
        mCustomAudienceDao.persistCustomAudienceBackgroundFetchData(null);
    }

    @Test
    public void testCreateOrUpdate_UpdateExist() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1_1, DAILY_UPDATE_URI_1, false);
        assertEquals(
                CUSTOM_AUDIENCE_1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
    }

    @Test
    public void testCreateOrUpdate_immediatelyEligibleForUpdate() {
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_NO_ADS, DAILY_UPDATE_URI_1, false);

        assertEquals(
                CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS.getOwner(),
                        CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS.getBuyer(),
                        CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_NO_USER_BIDDING_SIGNALS,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS.getOwner(),
                        CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS.getBuyer(),
                        CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS.getName()));

        assertEquals(
                CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA.getOwner(),
                        CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA.getBuyer(),
                        CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_NO_TRUSTED_BIDDING_DATA,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA.getOwner(),
                        CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA.getBuyer(),
                        CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA.getName()));

        assertEquals(
                CUSTOM_AUDIENCE_NO_ADS,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_NO_ADS.getOwner(),
                        CUSTOM_AUDIENCE_NO_ADS.getBuyer(),
                        CUSTOM_AUDIENCE_NO_ADS.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_NO_ADS,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_NO_ADS.getOwner(),
                        CUSTOM_AUDIENCE_NO_ADS.getBuyer(),
                        CUSTOM_AUDIENCE_NO_ADS.getName()));
    }

    @Test
    public void testCreateOrUpdate_UpdateExistingBackgroundFetchData() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.persistCustomAudienceBackgroundFetchData(
                CUSTOM_AUDIENCE_BGF_DATA_1_UPDATED);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1_UPDATED,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
    }

    @Test
    public void testCreateOrUpdate_UpdateExistingCustomAudienceAndBackgroundFetchData() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        mCustomAudienceDao.updateCustomAudienceAndBackgroundFetchData(
                CUSTOM_AUDIENCE_BGF_DATA_1_1, CUSTOM_AUDIENCE_UPDATABLE_DATA);
        assertEquals(
                CUSTOM_AUDIENCE_1_UPDATED_FROM_UPDATABLE_DATA,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1_UPDATED,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
    }

    @Test
    public void testUpdateMissingCustomAudienceAndBackgroundFetchData() {
        mCustomAudienceDao.persistCustomAudienceBackgroundFetchData(CUSTOM_AUDIENCE_BGF_DATA_1);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        // If a custom audience does not exist when we try to update the CA with its background
        // fetch data, we assume it was cleaned up while the CA was being updated.  In this case, we
        // should not persist the CA again, and the operation is aborted.
        mCustomAudienceDao.updateCustomAudienceAndBackgroundFetchData(
                CUSTOM_AUDIENCE_BGF_DATA_1_1, CUSTOM_AUDIENCE_UPDATABLE_DATA);
        assertNull(mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));
    }

    @Test
    public void testGetActiveCustomAudienceByBuyersInactiveCAs() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        List<AdTechIdentifier> buyers = Arrays.asList(BUYER_1);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_INACTIVE, DAILY_UPDATE_URI_1, false);
        assertEquals(
                CUSTOM_AUDIENCE_INACTIVE,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(OWNER_1, BUYER_1, NAME_1));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER_1, BUYER_1, NAME_1));

        assertTrue(
                mCustomAudienceDao
                        .getActiveCustomAudienceByBuyers(
                                buyers,
                                CURRENT_TIME,
                                TEST_FLAGS.getFledgeCustomAudienceActiveTimeWindowInMs())
                        .isEmpty());
    }

    @Test
    public void testGetAllActiveCustomAudienceForServerSideAuctionInactiveCAs() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        DBCustomAudience caWithNullUserBiddingSignals =
                CUSTOM_AUDIENCE_ACTIVE
                        .cloneToBuilder()
                        .setOwner(OWNER_2)
                        .setBuyer(BUYER_2)
                        .setName(NAME_2)
                        .setUserBiddingSignals(null)
                        .build();

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_ACTIVE, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                caWithNullUserBiddingSignals, DAILY_UPDATE_URI_2, false);
        List<DBCustomAudience> result =
                mCustomAudienceDao.getAllActiveCustomAudienceForServerSideAuction(
                        CURRENT_TIME, TEST_FLAGS.getFledgeCustomAudienceActiveTimeWindowInMs());
        assertThat(result).containsExactly(CUSTOM_AUDIENCE_ACTIVE, caWithNullUserBiddingSignals);
    }

    @Test
    public void testGetActiveCustomAudienceByBuyersActivatedCAs() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        List<AdTechIdentifier> buyers = Arrays.asList(BUYER_1, BUYER_2);
        List<DBCustomAudience> expectedCAs = Arrays.asList(CUSTOM_AUDIENCE_ACTIVE);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_ACTIVE, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_EXPIRED, DAILY_UPDATE_URI_2, false);
        List<DBCustomAudience> result =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        buyers,
                        CURRENT_TIME,
                        TEST_FLAGS.getFledgeCustomAudienceActiveTimeWindowInMs());
        assertThat(result).containsExactlyElementsIn(expectedCAs);
    }

    @Test
    public void testGetActiveCustomAudienceByBuyersUpdatedCAs() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        List<AdTechIdentifier> buyers = Arrays.asList(BUYER_1, BUYER_2);
        List<DBCustomAudience> expectedCAs = Arrays.asList(CUSTOM_AUDIENCE_UPDATED);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_UPDATED, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_OUTDATED, DAILY_UPDATE_URI_2, false);
        List<DBCustomAudience> result =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        buyers,
                        CURRENT_TIME,
                        TEST_FLAGS.getFledgeCustomAudienceActiveTimeWindowInMs());
        assertThat(result).containsExactlyElementsIn(expectedCAs);
    }

    @Test
    public void testGetActiveCustomAudienceByBuyersInvalidCAs() {
        List<AdTechIdentifier> buyers = Arrays.asList(BUYER_1, BUYER_2, BUYER_3);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_NO_TRUSTED_BIDDING_DATA, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_NO_USER_BIDDING_SIGNALS, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_NO_ADS, DAILY_UPDATE_URI_1, false);
        List<DBCustomAudience> result =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        buyers,
                        CURRENT_TIME,
                        TEST_FLAGS.getFledgeCustomAudienceActiveTimeWindowInMs());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetNumActiveEligibleCustomAudienceBackgroundFetchData() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // Prepopulate with three CAs, only one of which is eligible for update
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_UPDATED, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_EXPIRED, DAILY_UPDATE_URI_2, false);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_UPDATED,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_UPDATED.getOwner(),
                        CUSTOM_AUDIENCE_UPDATED.getBuyer(),
                        CUSTOM_AUDIENCE_UPDATED.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_UPDATED,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_UPDATED.getOwner(),
                        CUSTOM_AUDIENCE_UPDATED.getBuyer(),
                        CUSTOM_AUDIENCE_UPDATED.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_EXPIRED,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_EXPIRED,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));

        assertEquals(
                1,
                mCustomAudienceDao.getNumActiveEligibleCustomAudienceBackgroundFetchData(
                        CURRENT_TIME));
        assertThat(
                        mCustomAudienceDao.getActiveEligibleCustomAudienceBackgroundFetchData(
                                CURRENT_TIME, 10))
                .containsExactly(CUSTOM_AUDIENCE_BGF_DATA_UPDATED);
    }

    @Test
    public void testGetAllCustomAudienceOwners() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // Prepopulate with three CAs belonging to two owners
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_EXPIRED, DAILY_UPDATE_URI_2, false);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_EXPIRED,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_EXPIRED,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));

        List<String> owners = mCustomAudienceDao.getAllCustomAudienceOwners();
        assertThat(owners).hasSize(2);
        assertThat(owners)
                .containsExactly(CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_2.getOwner());
    }

    @Test
    public void testDeleteAllExpiredCustomAudienceData() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // Prepopulate with three CAs, only one of which is expired
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_EXPIRED, DAILY_UPDATE_URI_2, false);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_EXPIRED,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_EXPIRED,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));

        assertEquals(1, mCustomAudienceDao.deleteAllExpiredCustomAudienceData(CURRENT_TIME));

        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
    }

    @Test
    public void testDeleteUninstalledOwnerCustomAudienceData() {
        // All owners are allowed
        class FlagsThatAllowAllApps implements Flags {
            @Override
            public String getPpapiAppAllowList() {
                return AllowLists.ALLOW_ALL;
            }
        }
        Flags flagsThatAllowAllApps = new FlagsThatAllowAllApps();

        doReturn(flagsThatAllowAllApps).when(FlagsFactory::getFlags);

        // Only CA1's owner is installed
        ApplicationInfo installed_owner_1 = new ApplicationInfo();
        installed_owner_1.packageName = CUSTOM_AUDIENCE_1.getOwner();
        doReturn(Arrays.asList(installed_owner_1))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        // Prepopulate with data
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));

        // Clear the uninstalled app data
        CustomAudienceStats expectedDisallowedOwnerStats =
                CustomAudienceStats.builder()
                        .setTotalCustomAudienceCount(1)
                        .setTotalOwnerCount(1)
                        .build();
        assertEquals(
                expectedDisallowedOwnerStats,
                mCustomAudienceDao.deleteAllDisallowedOwnerCustomAudienceData(
                        CONTEXT.getPackageManager(), flagsThatAllowAllApps));

        // Verify only the uninstalled app data is deleted
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
    }

    @Test
    public void testDeleteNotAllowedOwnerCustomAudienceData() {
        // Only CA1's owner is allowed
        class FlagsThatAllowOneApp implements Flags {
            @Override
            public String getPpapiAppAllowList() {
                return CUSTOM_AUDIENCE_1.getOwner();
            }
        }
        Flags flagsThatAllowOneApp = new FlagsThatAllowOneApp();

        doReturn(flagsThatAllowOneApp).when(FlagsFactory::getFlags);

        // Both owners are installed
        ApplicationInfo installed_owner_1 = new ApplicationInfo();
        installed_owner_1.packageName = CUSTOM_AUDIENCE_1.getOwner();
        ApplicationInfo installed_owner_2 = new ApplicationInfo();
        installed_owner_2.packageName = CUSTOM_AUDIENCE_2.getOwner();
        doReturn(Arrays.asList(installed_owner_1, installed_owner_2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        // Prepopulate with data
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));

        // Clear the data for the app not in the allowlist
        CustomAudienceStats expectedDisallowedOwnerStats =
                CustomAudienceStats.builder()
                        .setTotalCustomAudienceCount(1)
                        .setTotalOwnerCount(1)
                        .build();
        assertEquals(
                expectedDisallowedOwnerStats,
                mCustomAudienceDao.deleteAllDisallowedOwnerCustomAudienceData(
                        CONTEXT.getPackageManager(), flagsThatAllowOneApp));

        // Verify only the uninstalled app data is deleted
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
    }

    @Test
    public void testDeleteUninstalledAndNotAllowedOwnerCustomAudienceData() {
        // Only CA1's owner is allowed
        class FlagsThatAllowOneApp implements Flags {
            @Override
            public String getPpapiAppAllowList() {
                return CUSTOM_AUDIENCE_1.getOwner();
            }
        }
        Flags flagsThatAllowOneApp = new FlagsThatAllowOneApp();

        doReturn(flagsThatAllowOneApp).when(FlagsFactory::getFlags);

        // Only CA2's owner is installed
        ApplicationInfo installed_owner_2 = new ApplicationInfo();
        installed_owner_2.packageName = CUSTOM_AUDIENCE_2.getOwner();
        doReturn(Arrays.asList(installed_owner_2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        // Prepopulate with data
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));

        // All data should be cleared because neither owner is both allowlisted and installed
        CustomAudienceStats expectedDisallowedOwnerStats =
                CustomAudienceStats.builder()
                        .setTotalCustomAudienceCount(2)
                        .setTotalOwnerCount(2)
                        .build();
        assertEquals(
                expectedDisallowedOwnerStats,
                mCustomAudienceDao.deleteAllDisallowedOwnerCustomAudienceData(
                        CONTEXT.getPackageManager(), flagsThatAllowOneApp));

        // Verify both owners' app data are deleted
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
    }

    @Test
    public void testDeleteAllDisallowedBuyerCustomAudienceData_enrollmentEnabled() {
        class FlagsThatEnforceEnrollment implements Flags {
            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return false;
            }
        }
        Flags flagsThatEnforceEnrollment = new FlagsThatEnforceEnrollment();

        doReturn(flagsThatEnforceEnrollment).when(FlagsFactory::getFlags);

        // Only CA2's buyer is enrolled
        doReturn(Stream.of(CUSTOM_AUDIENCE_2.getBuyer()).collect(Collectors.toSet()))
                .when(mEnrollmentDaoMock)
                .getAllFledgeEnrolledAdTechs();

        // Prepopulate with data
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));

        // CA1's data should be deleted because it is not enrolled
        CustomAudienceStats expectedDisallowedBuyerStats =
                CustomAudienceStats.builder()
                        .setTotalCustomAudienceCount(1)
                        .setTotalBuyerCount(1)
                        .build();
        assertEquals(
                expectedDisallowedBuyerStats,
                mCustomAudienceDao.deleteAllDisallowedBuyerCustomAudienceData(
                        mEnrollmentDaoMock, flagsThatEnforceEnrollment));

        // Verify only CA1's app data is deleted
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
    }

    @Test
    public void testDeleteAllDisallowedBuyerCustomAudienceData_enrollmentDisabledNoDeletion() {
        class FlagsThatDisableEnrollment implements Flags {
            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return true;
            }
        }
        Flags flagsThatDisableEnrollment = new FlagsThatDisableEnrollment();

        doReturn(flagsThatDisableEnrollment).when(FlagsFactory::getFlags);

        // Prepopulate with data
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));

        // CA1's data should be deleted because it is not enrolled, but enrollment is disabled, so
        // nothing is cleared
        CustomAudienceStats expectedDisallowedBuyerStats =
                CustomAudienceStats.builder()
                        .setTotalCustomAudienceCount(0)
                        .setTotalBuyerCount(0)
                        .build();
        assertEquals(
                expectedDisallowedBuyerStats,
                mCustomAudienceDao.deleteAllDisallowedBuyerCustomAudienceData(
                        mEnrollmentDaoMock, flagsThatDisableEnrollment));

        // Verify no data is deleted
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));

        verify(mEnrollmentDaoMock, never()).getAllFledgeEnrolledAdTechs();
    }

    @Test
    public void testDeleteAllCustomAudienceData() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // Prepopulate with data
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_EXPIRED, DAILY_UPDATE_URI_2, false);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_EXPIRED,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_EXPIRED,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getOwner(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getBuyer(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getName()));

        // Clear all data
        mCustomAudienceDao.deleteAllCustomAudienceData();

        // Verify all data is deleted
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getOwner(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getBuyer(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getName()));

        // Clear all data once empty to verify nothing crashes when we persist again
        mCustomAudienceDao.deleteAllCustomAudienceData();

        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_EXPIRED, DAILY_UPDATE_URI_2, false);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_EXPIRED,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_EXPIRED,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_EXPIRED.getOwner(),
                        CUSTOM_AUDIENCE_EXPIRED.getBuyer(),
                        CUSTOM_AUDIENCE_EXPIRED.getName()));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getOwner(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getBuyer(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getName()));
    }

    @Test
    public void testDeleteCustomAudienceDataByOwner() {
        doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // Prepopulate with data
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_1, DAILY_UPDATE_URI_1, false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                CUSTOM_AUDIENCE_2, DAILY_UPDATE_URI_2, /*debuggable=*/ true);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_1);
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE_2);
        assertEquals(
                CUSTOM_AUDIENCE_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_1,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getOwner(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getBuyer(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getName()));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        DB_CUSTOM_AUDIENCE_OVERRIDE_2.getOwner(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_2.getBuyer(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_2.getName()));

        // Clear all data
        mCustomAudienceDao.deleteCustomAudienceDataByOwner(CUSTOM_AUDIENCE_1.getOwner());

        // Verify data for the owner is deleted
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertNull(
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertEquals(
                CUSTOM_AUDIENCE_BGF_DATA_2,
                mCustomAudienceDao.getCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_2.getOwner(),
                        CUSTOM_AUDIENCE_2.getBuyer(),
                        CUSTOM_AUDIENCE_2.getName()));
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getOwner(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getBuyer(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_1.getName()));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        DB_CUSTOM_AUDIENCE_OVERRIDE_2.getOwner(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_2.getBuyer(),
                        DB_CUSTOM_AUDIENCE_OVERRIDE_2.getName()));
    }

    @Test
    public void testInsertAndQueryScheduledCustomAudienceUpdate_Success() {
        DBScheduledCustomAudienceUpdate anUpdate =
                DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER.setUpdateId(null).build();

        List<DBScheduledCustomAudienceUpdate> updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertThat(updates).isEmpty();

        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(anUpdate);

        updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertEquals("There should have been 1 entry", 1, updates.size());
        assertNotNull("UpdateId should not have been null", updates.get(0).getUpdateId());
        assertUpdateEqualsExceptId(updates.get(0), anUpdate);
    }

    @Test
    public void testDeleteScheduledCustomAudienceUpdate_Success() {
        DBScheduledCustomAudienceUpdate anUpdate =
                DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER.setUpdateId(null).build();

        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(anUpdate);

        List<DBScheduledCustomAudienceUpdate> updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertEquals("There should have been 1 entry", 1, updates.size());

        for (DBScheduledCustomAudienceUpdate updatesToDelete : updates) {
            mCustomAudienceDao.deleteScheduledCustomAudienceUpdate(updatesToDelete);
        }

        assertTrue(
                "All updates should have been deleted",
                mCustomAudienceDao
                        .getCustomAudienceUpdatesScheduledBeforeTime(
                                anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES))
                        .isEmpty());
    }

    @Test
    public void testInsertAndQueryScheduledCustomAudienceUpdate_SimilarUpdateReplaces() {
        DBScheduledCustomAudienceUpdate anUpdate =
                DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER.setUpdateId(null).build();

        List<DBScheduledCustomAudienceUpdate> updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertThat(updates).isEmpty();

        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(anUpdate);

        updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        Long previousUpdateId = updates.get(0).getUpdateId();

        Instant differentScheduleTime = CommonFixture.FIXED_EARLIER_ONE_DAY;

        DBScheduledCustomAudienceUpdate similarUpdate =
                DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER
                        .setUpdateId(null)
                        .setScheduledTime(differentScheduleTime)
                        .build();

        assertNotEquals(
                "Both times in updates should be different",
                updates.get(0).getScheduledTime().getEpochSecond(),
                differentScheduleTime.getEpochSecond());

        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(similarUpdate);
        updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));

        assertEquals(
                "Both times in updates should be same after being replaced",
                updates.get(0).getScheduledTime().getEpochSecond(),
                differentScheduleTime.getEpochSecond());
        assertNotEquals(
                "UpdateId should have been updated for new entry",
                previousUpdateId,
                updates.get(0).getUpdateId());
    }

    @Test
    public void testInsertAndQueryScheduledCustomAudienceUpdateInFuture_Empty() {
        DBScheduledCustomAudienceUpdate anUpdateInFuture =
                DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER
                        .setUpdateId(null)
                        .setScheduledTime(CommonFixture.FIXED_NEXT_ONE_DAY)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(anUpdateInFuture);

        List<DBScheduledCustomAudienceUpdate> updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        CommonFixture.FIXED_NOW);
        assertThat(updates).isEmpty();
    }

    @Test
    public void testInsertPartialCustomAudienceSucceeds() {
        DBScheduledCustomAudienceUpdate anUpdate =
                DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER.setUpdateId(null).build();
        long updateId = mCustomAudienceDao.insertScheduledCustomAudienceUpdate(anUpdate);

        DBPartialCustomAudience partialCustomAudience_1 =
                DB_PARTIAL_CUSTOM_AUDIENCE_BUILDER
                        .setUpdateId(updateId)
                        .setName("partial_ca_1")
                        .build();
        DBPartialCustomAudience partialCustomAudience_2 =
                DB_PARTIAL_CUSTOM_AUDIENCE_BUILDER
                        .setUpdateId(updateId)
                        .setName("partial_ca_2")
                        .build();

        List<DBScheduledCustomAudienceUpdate> updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertEquals("There should have been 1 entry", 1, updates.size());

        mCustomAudienceDao.insertPartialCustomAudiencesForUpdate(
                List.of(partialCustomAudience_1, partialCustomAudience_2));

        List<DBPartialCustomAudience> partialCustomAudienceList =
                mCustomAudienceDao.getPartialAudienceListForUpdateId(updateId);
        assertThat(
                        partialCustomAudienceList.stream()
                                .map(entry -> entry.getName())
                                .collect(Collectors.toList()))
                .containsExactly(
                        partialCustomAudience_1.getName(), partialCustomAudience_2.getName());
    }

    @Test
    public void testDeleteScheduledUpdate_ForeignKeyCascades() {
        DBScheduledCustomAudienceUpdate anUpdate =
                DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER.setUpdateId(null).build();
        long updateId = mCustomAudienceDao.insertScheduledCustomAudienceUpdate(anUpdate);

        DBPartialCustomAudience partialCustomAudience_1 =
                DB_PARTIAL_CUSTOM_AUDIENCE_BUILDER
                        .setUpdateId(updateId)
                        .setName("partial_ca_1")
                        .build();
        DBPartialCustomAudience partialCustomAudience_2 =
                DB_PARTIAL_CUSTOM_AUDIENCE_BUILDER
                        .setUpdateId(updateId)
                        .setName("partial_ca_2")
                        .build();

        mCustomAudienceDao.insertPartialCustomAudiencesForUpdate(
                List.of(partialCustomAudience_1, partialCustomAudience_2));

        List<DBPartialCustomAudience> partialCustomAudienceList =
                mCustomAudienceDao.getPartialAudienceListForUpdateId(updateId);
        assertThat(
                        partialCustomAudienceList.stream()
                                .map(entry -> entry.getName())
                                .collect(Collectors.toList()))
                .containsExactly(
                        partialCustomAudience_1.getName(), partialCustomAudience_2.getName());

        mCustomAudienceDao.deleteScheduledCustomAudienceUpdatesCreatedBeforeTime(
                anUpdate.getCreationTime().plus(10, ChronoUnit.MINUTES));

        List<DBScheduledCustomAudienceUpdate> updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertEquals("Entries should have been deleted", 0, updates.size());
        partialCustomAudienceList = mCustomAudienceDao.getPartialAudienceListForUpdateId(updateId);
        assertEquals(
                "Entries with foreign keys should have also been deleted",
                0,
                partialCustomAudienceList.size());
    }

    @Test
    public void testInsertPartialCustomAudience_ForeignKeyViolationFails() {
        DBPartialCustomAudience partialCustomAudience_1 =
                DB_PARTIAL_CUSTOM_AUDIENCE_BUILDER
                        .setUpdateId(500L)
                        .setName("partial_ca_1")
                        .build();

        assertThrows(
                SQLiteConstraintException.class,
                () ->
                        mCustomAudienceDao.insertPartialCustomAudiencesForUpdate(
                                List.of(partialCustomAudience_1)));
    }

    @Test
    public void testInsertAndQueryScheduledCustomAudienceUpdateInFuture_SingleTransaction() {
        DBScheduledCustomAudienceUpdate anUpdate =
                DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER.setUpdateId(null).build();
        DBPartialCustomAudience dbPartialCustomAudience =
                DB_PARTIAL_CUSTOM_AUDIENCE_BUILDER.build();
        String partialCaName1 = "partial_ca_1";
        String partialCaName2 = "partial_ca_2";

        PartialCustomAudience partialCustomAudience1 =
                new PartialCustomAudience.Builder(partialCaName1)
                        .setActivationTime(dbPartialCustomAudience.getActivationTime())
                        .setExpirationTime(dbPartialCustomAudience.getExpirationTime())
                        .setUserBiddingSignals(dbPartialCustomAudience.getUserBiddingSignals())
                        .build();

        PartialCustomAudience partialCustomAudience2 =
                new PartialCustomAudience.Builder(partialCaName2)
                        .setActivationTime(dbPartialCustomAudience.getActivationTime())
                        .setExpirationTime(dbPartialCustomAudience.getExpirationTime())
                        .setUserBiddingSignals(dbPartialCustomAudience.getUserBiddingSignals())
                        .build();

        List<DBScheduledCustomAudienceUpdate> updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertThat(updates).isEmpty();

        mCustomAudienceDao.insertScheduledUpdateAndPartialCustomAudienceList(
                anUpdate, List.of(partialCustomAudience1, partialCustomAudience2));
        updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertEquals("1 entry should have been inserted", 1, updates.size());

        long updateId = updates.get(0).getUpdateId();
        List<DBPartialCustomAudience> partialCustomAudienceList =
                mCustomAudienceDao.getPartialAudienceListForUpdateId(updateId);
        assertThat(
                        partialCustomAudienceList.stream()
                                .map(entry -> entry.getName())
                                .collect(Collectors.toList()))
                .containsExactly(partialCaName1, partialCaName2);

        List<Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>>
                updateAndOverridesPair =
                        mCustomAudienceDao.getScheduledUpdatesAndOverridesBeforeTime(
                                anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertThat(updateAndOverridesPair.get(0).first.getUpdateId()).isEqualTo(updateId);
        assertThat(
                        updateAndOverridesPair.get(0).second.stream()
                                .map(entry -> entry.getName())
                                .collect(Collectors.toList()))
                .containsExactly(partialCaName1, partialCaName2);
    }

    @Test
    public void testInsertAndQueryScheduledCustomAudienceUpdateInFuture_PartialCAsReplaced() {
        DBScheduledCustomAudienceUpdate anUpdate =
                DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER.setUpdateId(null).build();
        DBPartialCustomAudience dbPartialCustomAudience =
                DB_PARTIAL_CUSTOM_AUDIENCE_BUILDER.build();
        String partialCaName1 = "partial_ca_1";

        PartialCustomAudience partialCustomAudience1 =
                new PartialCustomAudience.Builder(partialCaName1)
                        .setActivationTime(dbPartialCustomAudience.getActivationTime())
                        .setExpirationTime(dbPartialCustomAudience.getExpirationTime())
                        .setUserBiddingSignals(dbPartialCustomAudience.getUserBiddingSignals())
                        .build();

        List<DBScheduledCustomAudienceUpdate> updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertThat(updates).isEmpty();

        mCustomAudienceDao.insertScheduledUpdateAndPartialCustomAudienceList(
                anUpdate, List.of(partialCustomAudience1));
        updates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));

        long previousUpdateId = updates.get(0).getUpdateId();
        List<DBPartialCustomAudience> partialCustomAudienceList =
                mCustomAudienceDao.getPartialAudienceListForUpdateId(previousUpdateId);
        assertThat(
                        partialCustomAudienceList.stream()
                                .map(entry -> entry.getName())
                                .collect(Collectors.toList()))
                .containsExactly(partialCaName1);

        DBScheduledCustomAudienceUpdate similarUpdate =
                DB_SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BUILDER.setUpdateId(null).build();
        String partialCaName2 = "partial_ca_2";

        PartialCustomAudience partialCustomAudience_2 =
                new PartialCustomAudience.Builder(partialCaName2)
                        .setActivationTime(dbPartialCustomAudience.getActivationTime())
                        .setExpirationTime(dbPartialCustomAudience.getExpirationTime())
                        .setUserBiddingSignals(dbPartialCustomAudience.getUserBiddingSignals())
                        .build();
        mCustomAudienceDao.insertScheduledUpdateAndPartialCustomAudienceList(
                similarUpdate, List.of(partialCustomAudience_2));
        List<DBScheduledCustomAudienceUpdate> newUpdates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(
                        anUpdate.getScheduledTime().plus(10, ChronoUnit.MINUTES));
        assertEquals("Only 1 entry should have been inserted", 1, newUpdates.size());

        long newUpdateId = newUpdates.get(0).getUpdateId();
        List<DBPartialCustomAudience> newPartialCustomAudienceList =
                mCustomAudienceDao.getPartialAudienceListForUpdateId(newUpdateId);
        assertThat(
                        newPartialCustomAudienceList.stream()
                                .map(entry -> entry.getName())
                                .collect(Collectors.toList()))
                .containsExactly(partialCaName2);
        partialCustomAudienceList =
                mCustomAudienceDao.getPartialAudienceListForUpdateId(previousUpdateId);
        assertEquals(
                "Partial CAs for previous update should have been cleared",
                0,
                partialCustomAudienceList.size());
    }

    private void assertUpdateEqualsExceptId(
            DBScheduledCustomAudienceUpdate expected, DBScheduledCustomAudienceUpdate actual) {
        assertEquals(expected.getBuyer(), actual.getBuyer());
        assertEquals(expected.getOwner(), actual.getOwner());
        assertEquals(expected.getUpdateUri(), actual.getUpdateUri());
        assertEquals(
                expected.getCreationTime().getEpochSecond(),
                actual.getCreationTime().getEpochSecond());
        assertEquals(
                expected.getScheduledTime().getEpochSecond(),
                actual.getScheduledTime().getEpochSecond());
    }

    private void verifyCustomAudienceStats(
            CustomAudienceStats customAudienceStats,
            String owner,
            int totalCount,
            int perOwnerCount,
            int ownerCount) {
        assertEquals(owner, customAudienceStats.getOwner());
        assertEquals(totalCount, customAudienceStats.getTotalCustomAudienceCount());
        assertEquals(perOwnerCount, customAudienceStats.getPerOwnerCustomAudienceCount());
        assertEquals(ownerCount, customAudienceStats.getTotalOwnerCount());
    }
}
