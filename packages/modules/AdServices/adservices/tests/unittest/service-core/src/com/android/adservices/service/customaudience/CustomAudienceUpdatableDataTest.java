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

package com.android.adservices.service.customaudience;

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.OMIT_ADS_VALUE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.JsonFixture;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class CustomAudienceUpdatableDataTest {
    private static final DBTrustedBiddingData VALID_DB_TRUSTED_BIDDING_DATA =
            DBTrustedBiddingDataFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
    private static final List<DBAdData> VALID_DB_AD_DATA_LIST =
            DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildUpdatableDataSuccess() throws JSONException {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        FlagsFactory.getFlagsForTest());

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
    }

    @Test
    public void testBuildUpdatableDataSuccessWithAuctionServerFlagsEnabled() throws JSONException {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());
        assertEquals(
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                updatableDataFromBuilder.getAuctionServerRequestFlags());

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST,
                        ImmutableList.of(OMIT_ADS_VALUE));
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        new FlagsWithAuctionServerRequestFlagsSet(true));

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
    }

    @Test
    public void testBuildUpdatableDataSuccessWithJunkWithAuctionServerFlagsEnabled()
            throws JSONException {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());
        assertEquals(
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                updatableDataFromBuilder.getAuctionServerRequestFlags());

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST,
                        ImmutableList.of(OMIT_ADS_VALUE),
                        true);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        new FlagsWithAuctionServerRequestFlagsSet(true));

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
    }

    @Test
    public void testBuildUpdatableDataSuccessWithAuctionServerFlagsDisabled() throws JSONException {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST,
                        ImmutableList.of(OMIT_ADS_VALUE));
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        new FlagsWithAuctionServerRequestFlagsSet(false));

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
        assertEquals(0, updatableDataFromResponseString.getAuctionServerRequestFlags());
    }

    @Test
    public void testBuildEmptyUpdatableDataSuccess() throws JSONException {
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(null)
                        .setTrustedBiddingData(null)
                        .setAds(null)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertNull(updatableDataFromBuilder.getUserBiddingSignals());
        assertNull(updatableDataFromBuilder.getTrustedBiddingData());
        assertNull(updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        final String jsonResponse = CustomAudienceUpdatableDataFixture.getEmptyJsonResponseString();
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        FlagsFactory.getFlagsForTest());

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);

        CustomAudienceUpdatableData updatableDataFromEmptyString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        "",
                        FlagsFactory.getFlagsForTest());

        assertEquals(
                "Updatable data created with empty string does not match built from response"
                        + " string \""
                        + jsonResponse
                        + '"',
                updatableDataFromEmptyString,
                updatableDataFromResponseString);
    }

    @Test
    public void testBuildEmptyUpdatableDataWithNonEmptyResponseSuccess() throws JSONException {
        boolean expectedContainsSuccessfulUpdate = false;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(null)
                        .setTrustedBiddingData(null)
                        .setAds(null)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertNull(updatableDataFromBuilder.getUserBiddingSignals());
        assertNull(updatableDataFromBuilder.getTrustedBiddingData());
        assertNull(updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        // In this case, a non-empty response was parsed, but the units of data found were malformed
        // and not updatable
        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.getMalformedJsonResponseString();
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        FlagsFactory.getFlagsForTest());

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
    }

    @Test
    public void testHarmlessJunkIgnoredInUpdatableDataCreateFromResponse() throws JSONException {
        // In this case, a regular full response was parsed without any extra fields
        final String jsonResponseWithoutHarmlessJunk =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(),
                        null,
                        VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataWithoutHarmlessJunk =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponseWithoutHarmlessJunk,
                        FlagsFactory.getFlagsForTest());

        // Harmless junk was added to the same response
        final String jsonResponseWithHarmlessJunk =
                CustomAudienceUpdatableDataFixture.toJsonResponseStringWithHarmlessJunk(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(),
                        null,
                        VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataWithHarmlessJunk =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponseWithHarmlessJunk,
                        FlagsFactory.getFlagsForTest());

        assertNotEquals(
                "Harmless junk was not added to the response JSON",
                jsonResponseWithoutHarmlessJunk,
                jsonResponseWithHarmlessJunk);
        assertEquals(
                "Updatable data created without harmless junk \""
                        + jsonResponseWithoutHarmlessJunk
                        + "\" does not match created with harmless junk \""
                        + jsonResponseWithHarmlessJunk
                        + '"',
                updatableDataWithoutHarmlessJunk,
                updatableDataWithHarmlessJunk);
    }

    @Test
    public void testBuildNonEmptyUpdatableDataWithUnsuccessfulUpdateFailure() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CustomAudienceUpdatableData.builder()
                                .setUserBiddingSignals(
                                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                                .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                                .setAds(VALID_DB_AD_DATA_LIST)
                                .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                                .setInitialUpdateResult(
                                        BackgroundFetchRunner.UpdateResultType.SUCCESS)
                                .setContainsSuccessfulUpdate(false)
                                .build());
    }

    @Test
    public void testUnsuccessfulInitialUpdateResultCausesUnsuccessfulUpdate() throws JSONException {
        // If the initial update result is anything except for SUCCESS, the resulting updatableData
        // should not contain a successful update
        for (BackgroundFetchRunner.UpdateResultType initialUpdateResult :
                BackgroundFetchRunner.UpdateResultType.values()) {
            CustomAudienceUpdatableData updatableData =
                    CustomAudienceUpdatableData.createFromResponseString(
                            CommonFixture.FIXED_NOW,
                            CommonFixture.VALID_BUYER_1,
                            initialUpdateResult,
                            CustomAudienceUpdatableDataFixture.getEmptyJsonResponseString(),
                            FlagsFactory.getFlagsForTest());
            assertEquals(
                    "Incorrect update success when initial result is "
                            + initialUpdateResult.toString(),
                    initialUpdateResult == BackgroundFetchRunner.UpdateResultType.SUCCESS,
                    updatableData.getContainsSuccessfulUpdate());
        }
    }

    @Test
    public void testCreateFromNonJsonResponseStringCausesUnsuccessfulUpdate() {
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        "this (input ,string .is -not real json'",
                        FlagsFactory.getFlagsForTest());

        assertNull(updatableData.getUserBiddingSignals());
        assertNull(updatableData.getTrustedBiddingData());
        assertNull(updatableData.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableData.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableData.getInitialUpdateResult());
        assertFalse(updatableData.getContainsSuccessfulUpdate());
    }

    @Test
    public void testCreateFromFullJsonResponseStringWithSmallLimitStillSuccess()
            throws JSONException {
        class FlagsWithSmallLimits implements Flags {
            @Override
            public int getFledgeCustomAudienceMaxUserBiddingSignalsSizeB() {
                return 1;
            }

            @Override
            public boolean getFledgeAdSelectionFilteringEnabled() {
                return true;
            }
        }

        String validUserBiddingSignalsAsJsonObjectString =
                JsonFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());
        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString,
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        new FlagsWithSmallLimits());

        // Because *something* was updated, even a failed unit of data still creates a successful
        // update, but the failed unit is not updated
        assertTrue(updatableDataFromResponseString.getContainsSuccessfulUpdate());
        assertNull(updatableDataFromResponseString.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA,
                updatableDataFromResponseString.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromResponseString.getAds());
    }

    @Test
    public void testCreateFromResponseStringWithLargeFieldsCausesUnsuccessfulUpdate()
            throws JSONException {
        class FlagsWithSmallLimits implements Flags {
            @Override
            public int getFledgeCustomAudienceMaxUserBiddingSignalsSizeB() {
                return 1;
            }

            @Override
            public int getFledgeCustomAudienceMaxTrustedBiddingDataSizeB() {
                return 1;
            }

            @Override
            public int getFledgeCustomAudienceMaxAdsSizeB() {
                return 1;
            }
        }

        String validUserBiddingSignalsAsJsonObjectString =
                JsonFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());
        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString,
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        new FlagsWithSmallLimits());

        // All found fields in the response were too large, failing validation
        assertFalse(updatableDataFromResponseString.getContainsSuccessfulUpdate());
    }

    static class FlagsWithAuctionServerRequestFlagsSet extends FlagsFactory.TestFlags {
        private final boolean mAuctionServerRequestFlagsEnabled;

        FlagsWithAuctionServerRequestFlagsSet(boolean auctionServerRequestFlagsEnabled) {
            mAuctionServerRequestFlagsEnabled = auctionServerRequestFlagsEnabled;
        }

        @Override
        public boolean getFledgeAuctionServerRequestFlagsEnabled() {
            return mAuctionServerRequestFlagsEnabled;
        }
    }
}
