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

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.ADS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_RENDER_ID_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AUCTION_SERVER_REQUEST_FLAGS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.OMIT_ADS_VALUE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
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
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

public class CustomAudienceUpdatableDataReaderTest {
    private static final String RESPONSE_IDENTIFIER = "[1]";
    private static final DBTrustedBiddingData VALID_TRUSTED_BIDDING_DATA =
            DBTrustedBiddingDataFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
    private static final List<DBAdData> VALID_DB_AD_DATA_LIST =
            DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1);
    private static final List<DBAdData> INVALID_DB_AD_DATA_LIST =
            DBAdDataFixture.getInvalidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1);

    private final Flags mFlags = FlagsFactory.getFlagsForTest();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testGetUserBiddingSignalsFromFullJsonObjectSuccess() throws JSONException {
        String validUserBiddingSignalsAsJsonObjectString =
                JsonFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(
                AdSelectionSignals.fromString(validUserBiddingSignalsAsJsonObjectString),
                reader.getUserBiddingSignalsFromJsonObject());
    }

    @Test
    public void testGetAuctionServerRequestFlagsSuccess() throws JSONException {
        List<String> flagsList = ImmutableList.of(OMIT_ADS_VALUE);

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addAuctionServerRequestFlagsToJsonObject(
                        null, flagsList, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS, reader.getAuctionServerRequestFlags());
    }

    @Test
    public void testGetAuctionServerRequestFlagsNoMatchInFlags() throws JSONException {
        List<String> flagsList = ImmutableList.of();

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addAuctionServerRequestFlagsToJsonObject(
                        null, flagsList, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(0, reader.getAuctionServerRequestFlags());
    }

    @Test
    public void testGetAuctionServerRequestFlagsNoFlagsField() throws JSONException {
        JSONObject responseObject = new JSONObject();
        JsonFixture.addHarmlessJunkValues(responseObject);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(0, reader.getAuctionServerRequestFlags());
    }

    @Test
    public void testGetAuctionServerRequestFlagsFlagsNotArray() throws JSONException {
        JSONObject responseObject =
                new JSONObject().put(AUCTION_SERVER_REQUEST_FLAGS_KEY, OMIT_ADS_VALUE);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertThrows(JSONException.class, reader::getAuctionServerRequestFlags);
    }

    @Test
    public void testGetUserBiddingSignalsFromFullJsonObjectWithHarmlessJunkSuccess()
            throws JSONException {
        String validUserBiddingSignalsAsJsonObjectString =
                JsonFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(
                AdSelectionSignals.fromString(validUserBiddingSignalsAsJsonObjectString),
                reader.getUserBiddingSignalsFromJsonObject());
    }

    @Test
    public void testGetUserBiddingSignalsFromEmptyJsonObject() throws JSONException {
        String missingUserBiddingSignalsAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingUserBiddingSignalsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertNull(reader.getUserBiddingSignalsFromJsonObject());
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectMismatchedSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());
        assertThrows(JSONException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectMismatchedNullSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());
        assertThrows(JSONException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectDeeperMismatchedSchema()
            throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getDeeperMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());
        assertThrows(JSONException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectInvalidSize() throws JSONException {
        String validUserBiddingSignalsAsJsonObjectString =
                JsonFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        1,
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertThrows(IllegalArgumentException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromFullJsonObjectSuccess() throws JSONException {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(expectedTrustedBiddingData, reader.getTrustedBiddingDataFromJsonObject());
    }

    @Test
    public void testGetTrustedBiddingDataFromFullJsonObjectWithHarmlessJunkSuccess()
            throws JSONException {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(
                "responseObject = " + responseObject.toString(4),
                expectedTrustedBiddingData,
                reader.getTrustedBiddingDataFromJsonObject());
    }

    @Test
    public void testGetTrustedBiddingDataFromEmptyJsonObject() throws JSONException {
        String missingTrustedBiddingDataAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingTrustedBiddingDataAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertNull(reader.getTrustedBiddingDataFromJsonObject());
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());
        assertThrows(JSONException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedNullSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());
        assertThrows(JSONException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectDeeperMismatchedSchema()
            throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getDeeperMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());
        assertThrows(JSONException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedUri() throws JSONException {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        AdTechIdentifier.fromString("other.domain"),
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertThrows(IllegalArgumentException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectInvalidSize() throws JSONException {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        1,
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertThrows(IllegalArgumentException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetAdsFromFullJsonObjectSuccess() throws JSONException {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(expectedAds, reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromFullJsonObjectFilteringOffSuccess() throws JSONException {
        List<DBAdData> inputAds =
                DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1);
        List<DBAdData> expectedAds =
                DBAdDataFixture.getValidDbAdDataListByBuyerNoFilters(CommonFixture.VALID_BUYER_1);
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, inputAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        false,
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(expectedAds, reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromFullJsonObjectAdRenderIdOnSuccess() throws JSONException {
        List<DBAdData> inputAds =
                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                        CommonFixture.VALID_BUYER_1);
        List<DBAdData> expectedAds =
                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                        CommonFixture.VALID_BUYER_1);
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, inputAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        true,
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(expectedAds, reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromFullJsonObjectAdRenderIdOff_Success_dropAdRenderId()
            throws JSONException {
        List<DBAdData> inputAds =
                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                        CommonFixture.VALID_BUYER_1);
        List<DBAdData> expectedAds =
                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                CommonFixture.VALID_BUYER_1)
                        .stream()
                        .map(
                                adData ->
                                        new DBAdData(
                                                adData.getRenderUri(),
                                                adData.getMetadata(),
                                                adData.getAdCounterKeys(),
                                                adData.getAdFilters(),
                                                null))
                        .collect(Collectors.toList());
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, inputAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        false,
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(expectedAds, reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromJsonObjectWithMalformedAdRenderId() throws JSONException {
        List<DBAdData> inputAds =
                ImmutableList.of(DBAdDataFixture.getValidDbAdDataBuilder().build());
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, inputAds, false);
        JSONObject wrongAdRenderIdJsonObject = new JSONObject().put("ik", "v");
        responseObject
                .getJSONArray(ADS_KEY)
                .getJSONObject(0)
                .put(AD_RENDER_ID_KEY, wrongAdRenderIdJsonObject);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        true,
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());
        assertTrue(reader.getAdsFromJsonObject().isEmpty());
    }

    @Test
    public void testGetAdsFromJsonObjectWithTooLongAdRenderId() throws JSONException {
        List<DBAdData> inputAds =
                ImmutableList.of(
                        DBAdDataFixture.getValidDbAdDataBuilder()
                                .setAdRenderId("IamExtremelyLongLongLong")
                                .build());
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, inputAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        true,
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());
        assertTrue(reader.getAdsFromJsonObject().isEmpty());
    }

    @Test
    public void testGetAdsFromFullJsonObjectWithHarmlessJunkSuccess() throws JSONException {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertEquals(
                "responseObject = " + responseObject.toString(4),
                expectedAds,
                reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromEmptyJsonObject() throws JSONException {
        String missingAdsAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingAdsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertNull(reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromJsonObjectMismatchedSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());
        assertThrows(JSONException.class, reader::getAdsFromJsonObject);
    }

    @Test
    public void testGetAdsFromJsonObjectMismatchedNullSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());
        assertThrows(JSONException.class, reader::getAdsFromJsonObject);
    }

    @Test
    public void testGetAdsFromJsonObjectDeeperMismatchedSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getDeeperMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        List<DBAdData> extractedAds = reader.getAdsFromJsonObject();
        assertNotNull(extractedAds);
        assertTrue(extractedAds.isEmpty());
    }

    @Test
    public void testGetAdsFromJsonObjectWithInvalidAdsMetadata() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.addToJsonObject(
                                null, INVALID_DB_AD_DATA_LIST, false),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        List<DBAdData> extractedAds = reader.getAdsFromJsonObject();
        assertNotNull(extractedAds);
        assertTrue(extractedAds.isEmpty());
    }

    @Test
    public void testGetAdsFromJsonObjectInvalidTotalSize() throws JSONException {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        1,
                        mFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertThrows(IllegalArgumentException.class, reader::getAdsFromJsonObject);
    }

    @Test
    public void testGetAdsFromJsonObjectInvalidNumAds() throws JSONException {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        1,
                        mFlags.getFledgeAdSelectionFilteringEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFlags.getFledgeAuctionServerAdRenderIdMaxLength());

        assertThrows(IllegalArgumentException.class, reader::getAdsFromJsonObject);
    }
}
