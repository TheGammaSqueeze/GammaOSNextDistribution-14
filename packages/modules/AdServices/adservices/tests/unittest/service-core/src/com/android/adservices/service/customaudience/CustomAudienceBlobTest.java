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

package com.android.adservices.service.customaudience;

import static android.adservices.common.AdDataFixture.getValidFilterAdsWithAdRenderIdByBuyer;
import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.common.CommonFixture.VALID_BUYER_2;
import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_NAME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_OWNER;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS;
import static android.adservices.customaudience.CustomAudienceFixture.getValidBiddingLogicUriByBuyer;
import static android.adservices.customaudience.CustomAudienceFixture.getValidDailyUpdateUriByBuyer;
import static android.adservices.customaudience.TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer;

import static com.android.adservices.service.customaudience.CustomAudienceBlob.AUCTION_SERVER_REQUEST_FLAGS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceBlob.OMIT_ADS_VALUE;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CUSTOM_AUDIENCE_1;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.customaudience.DBPartialCustomAudience;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CustomAudienceBlobTest {
    private FetchAndJoinCustomAudienceInput.Builder mFetchAndJoinCustomAudienceInput =
            new FetchAndJoinCustomAudienceInput.Builder(
                            CustomAudienceFixture.getValidFetchUriByBuyer(VALID_BUYER_1),
                            VALID_OWNER)
                    .setName(VALID_NAME)
                    .setActivationTime(VALID_ACTIVATION_TIME)
                    .setExpirationTime(VALID_EXPIRATION_TIME)
                    .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS);
    private String mFetchAndJoinCustomAudienceInputAsJSONObjectString =
            CustomAudienceBlobFixture.asJSONObjectString(
                    VALID_OWNER,
                    VALID_BUYER_1,
                    VALID_NAME,
                    VALID_ACTIVATION_TIME,
                    VALID_EXPIRATION_TIME,
                    null,
                    null,
                    VALID_USER_BIDDING_SIGNALS.toString(),
                    null,
                    null);
    private JSONObject mJSONObject =
            CustomAudienceBlobFixture.asJSONObject(
                    VALID_OWNER,
                    VALID_BUYER_1,
                    VALID_NAME,
                    VALID_ACTIVATION_TIME,
                    VALID_EXPIRATION_TIME,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1),
                    getValidBiddingLogicUriByBuyer(VALID_BUYER_1),
                    VALID_USER_BIDDING_SIGNALS.toString(),
                    DBTrustedBiddingDataFixture.getValidBuilderByBuyer(VALID_BUYER_1).build(),
                    DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(VALID_BUYER_1),
                    false);

    private CustomAudienceBlob mCustomAudienceBlob;

    public CustomAudienceBlobTest() throws JSONException {}

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() throws JSONException {
        mCustomAudienceBlob = new CustomAudienceBlob();
    }

    @Test
    public void testOverrideFromFetchAndJoinCustomAudienceInput_validValues() {
        mCustomAudienceBlob.overrideFromFetchAndJoinCustomAudienceInput(
                mFetchAndJoinCustomAudienceInput.build());

        assertEquals(mCustomAudienceBlob.getOwner(), VALID_OWNER);
        assertEquals(mCustomAudienceBlob.getBuyer(), VALID_BUYER_1);
        assertEquals(mCustomAudienceBlob.getName(), VALID_NAME);
        assertEquals(mCustomAudienceBlob.getActivationTime(), VALID_ACTIVATION_TIME);
        assertEquals(mCustomAudienceBlob.getExpirationTime(), VALID_EXPIRATION_TIME);
        assertEquals(mCustomAudienceBlob.getUserBiddingSignals(), VALID_USER_BIDDING_SIGNALS);
    }

    @Test
    public void testOverrideFromFetchAndJoinCustomAudienceInput_validJSONObject() {
        mCustomAudienceBlob.overrideFromFetchAndJoinCustomAudienceInput(
                mFetchAndJoinCustomAudienceInput.build());

        JSONObject asJSONObject = mCustomAudienceBlob.asJSONObject();

        assertThat(asJSONObject.toString())
                .isEqualTo(mFetchAndJoinCustomAudienceInputAsJSONObjectString);
    }

    @Test
    public void testOverrideFromPartialCustomAudience_validValues() {
        mCustomAudienceBlob.overrideFromPartialCustomAudience(
                VALID_OWNER,
                VALID_BUYER_1,
                DBPartialCustomAudience.getPartialCustomAudience(PARTIAL_CUSTOM_AUDIENCE_1));

        assertEquals(mCustomAudienceBlob.getOwner(), VALID_OWNER);
        assertEquals(mCustomAudienceBlob.getBuyer(), VALID_BUYER_1);
        assertEquals(mCustomAudienceBlob.getName(), PARTIAL_CUSTOM_AUDIENCE_1.getName());
        assertEquals(
                mCustomAudienceBlob.getActivationTime(),
                PARTIAL_CUSTOM_AUDIENCE_1.getActivationTime());
        assertEquals(
                mCustomAudienceBlob.getExpirationTime(),
                PARTIAL_CUSTOM_AUDIENCE_1.getExpirationTime());
        assertEquals(
                mCustomAudienceBlob.getUserBiddingSignals(),
                PARTIAL_CUSTOM_AUDIENCE_1.getUserBiddingSignals());
    }

    @Test
    public void testOverrideFromJSONObject_validValues() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertEquals(mCustomAudienceBlob.getOwner(), VALID_OWNER);
        assertEquals(mCustomAudienceBlob.getBuyer(), VALID_BUYER_1);
        assertEquals(mCustomAudienceBlob.getName(), VALID_NAME);
        assertEquals(mCustomAudienceBlob.getActivationTime(), VALID_ACTIVATION_TIME);
        assertEquals(mCustomAudienceBlob.getExpirationTime(), VALID_EXPIRATION_TIME);
        assertEquals(
                mCustomAudienceBlob.getDailyUpdateUri(),
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1));
        assertEquals(
                mCustomAudienceBlob.getBiddingLogicUri(),
                getValidBiddingLogicUriByBuyer(VALID_BUYER_1));
        assertEquals(mCustomAudienceBlob.getUserBiddingSignals(), VALID_USER_BIDDING_SIGNALS);
        assertEquals(
                mCustomAudienceBlob.getTrustedBiddingData().toString(),
                getValidTrustedBiddingDataByBuyer(VALID_BUYER_1).toString());
        assertEquals(
                mCustomAudienceBlob.getAds().toString(),
                getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1).toString());
    }

    @Test
    public void testOverrideFromJSONObject_validJSONObject() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        JSONObject asJSONObject = mCustomAudienceBlob.asJSONObject();

        assertThat(asJSONObject.toString()).isEqualTo(mJSONObject.toString());
    }

    @Test
    public void testOverrideFromJSONObject_validValuesWithAuctionServerRequestFlagsEnabled()
            throws JSONException {
        CustomAudienceBlob blob = new CustomAudienceBlob(true, true, 12L, true);

        JSONObject jsonObject =
                CustomAudienceBlobFixture.asJSONObject(
                        VALID_OWNER,
                        VALID_BUYER_1,
                        VALID_NAME,
                        VALID_ACTIVATION_TIME,
                        VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1),
                        getValidBiddingLogicUriByBuyer(VALID_BUYER_1),
                        VALID_USER_BIDDING_SIGNALS.toString(),
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(VALID_BUYER_1).build(),
                        DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(VALID_BUYER_1),
                        /* shouldAddHarmlessJunk= */ false);

        JSONObject jsonObjectWithAuctionServerRequestFlags =
                CustomAudienceBlobFixture.addAuctionServerRequestFlags(
                        jsonObject, ImmutableList.of(OMIT_ADS_VALUE), false);

        blob.overrideFromJSONObject(jsonObjectWithAuctionServerRequestFlags);

        assertEquals(blob.getOwner(), VALID_OWNER);
        assertEquals(blob.getBuyer(), VALID_BUYER_1);
        assertEquals(blob.getName(), VALID_NAME);
        assertEquals(blob.getActivationTime(), VALID_ACTIVATION_TIME);
        assertEquals(blob.getExpirationTime(), VALID_EXPIRATION_TIME);
        assertEquals(
                blob.getDailyUpdateUri(),
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1));
        assertEquals(blob.getBiddingLogicUri(), getValidBiddingLogicUriByBuyer(VALID_BUYER_1));
        assertEquals(blob.getUserBiddingSignals(), VALID_USER_BIDDING_SIGNALS);
        assertEquals(
                blob.getTrustedBiddingData().toString(),
                getValidTrustedBiddingDataByBuyer(VALID_BUYER_1).toString());
        assertEquals(
                blob.getAds().toString(),
                getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1).toString());
        assertEquals(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS, blob.getAuctionServerRequestFlags());
    }

    @Test
    public void testOverrideFromJSONObject_invalidValuesWithAuctionServerRequestFlagsEnabled()
            throws JSONException {
        CustomAudienceBlob blob = new CustomAudienceBlob(true, true, 12L, true);

        JSONObject jsonObject =
                CustomAudienceBlobFixture.asJSONObject(
                        VALID_OWNER,
                        VALID_BUYER_1,
                        VALID_NAME,
                        VALID_ACTIVATION_TIME,
                        VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1),
                        getValidBiddingLogicUriByBuyer(VALID_BUYER_1),
                        VALID_USER_BIDDING_SIGNALS.toString(),
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(VALID_BUYER_1).build(),
                        DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(VALID_BUYER_1),
                        /* shouldAddHarmlessJunk= */ false);

        // Add a value instead of json array, making this invalid
        jsonObject.put(AUCTION_SERVER_REQUEST_FLAGS_KEY, "not array");

        // Assert no exception is thrown
        blob.overrideFromJSONObject(jsonObject);

        // Assert that all the values were propagated properly, and auction server request flags are
        // set to 0
        assertEquals(blob.getOwner(), VALID_OWNER);
        assertEquals(blob.getBuyer(), VALID_BUYER_1);
        assertEquals(blob.getName(), VALID_NAME);
        assertEquals(blob.getActivationTime(), VALID_ACTIVATION_TIME);
        assertEquals(blob.getExpirationTime(), VALID_EXPIRATION_TIME);
        assertEquals(
                blob.getDailyUpdateUri(),
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1));
        assertEquals(blob.getBiddingLogicUri(), getValidBiddingLogicUriByBuyer(VALID_BUYER_1));
        assertEquals(blob.getUserBiddingSignals(), VALID_USER_BIDDING_SIGNALS);
        assertEquals(
                blob.getTrustedBiddingData().toString(),
                getValidTrustedBiddingDataByBuyer(VALID_BUYER_1).toString());
        assertEquals(
                blob.getAds().toString(),
                getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1).toString());
        assertEquals(0, blob.getAuctionServerRequestFlags());
    }

    @Test
    public void testOverrideFromJSONObject_UnexpectedValuesWithAuctionServerRequestFlagsEnabled()
            throws JSONException {
        CustomAudienceBlob blob = new CustomAudienceBlob(true, true, 12L, true);

        JSONObject jsonObject =
                CustomAudienceBlobFixture.asJSONObject(
                        VALID_OWNER,
                        VALID_BUYER_1,
                        VALID_NAME,
                        VALID_ACTIVATION_TIME,
                        VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1),
                        getValidBiddingLogicUriByBuyer(VALID_BUYER_1),
                        VALID_USER_BIDDING_SIGNALS.toString(),
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(VALID_BUYER_1).build(),
                        DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(VALID_BUYER_1),
                        /* shouldAddHarmlessJunk= */ false);

        // Add a json array with both valid and invalid values
        JSONArray array = new JSONArray();
        array.put(1);
        array.put(OMIT_ADS_VALUE);
        array.put(3);
        jsonObject.put(AUCTION_SERVER_REQUEST_FLAGS_KEY, array);

        // Assert no exception is thrown
        blob.overrideFromJSONObject(jsonObject);

        // Assert that all the values were propagated properly, and auction server request flags are
        // set to 0
        assertEquals(blob.getOwner(), VALID_OWNER);
        assertEquals(blob.getBuyer(), VALID_BUYER_1);
        assertEquals(blob.getName(), VALID_NAME);
        assertEquals(blob.getActivationTime(), VALID_ACTIVATION_TIME);
        assertEquals(blob.getExpirationTime(), VALID_EXPIRATION_TIME);
        assertEquals(
                blob.getDailyUpdateUri(),
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1));
        assertEquals(blob.getBiddingLogicUri(), getValidBiddingLogicUriByBuyer(VALID_BUYER_1));
        assertEquals(blob.getUserBiddingSignals(), VALID_USER_BIDDING_SIGNALS);
        assertEquals(
                blob.getTrustedBiddingData().toString(),
                getValidTrustedBiddingDataByBuyer(VALID_BUYER_1).toString());
        assertEquals(
                blob.getAds().toString(),
                getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1).toString());
        assertEquals(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS, blob.getAuctionServerRequestFlags());
    }

    @Test
    public void testOverrideFromJSONObject_validValuesWithAuctionServerRequestFlagsDisabled()
            throws JSONException {
        JSONObject JsonObject =
                CustomAudienceBlobFixture.asJSONObject(
                        VALID_OWNER,
                        VALID_BUYER_1,
                        VALID_NAME,
                        VALID_ACTIVATION_TIME,
                        VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1),
                        getValidBiddingLogicUriByBuyer(VALID_BUYER_1),
                        VALID_USER_BIDDING_SIGNALS.toString(),
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(VALID_BUYER_1).build(),
                        DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(VALID_BUYER_1),
                        false);

        JSONObject jsonObjectWithAuctionServerRequestFlags =
                CustomAudienceBlobFixture.addAuctionServerRequestFlags(
                        JsonObject,
                        ImmutableList.of(OMIT_ADS_VALUE),
                        /* shouldAddHarmlessJunk= */ false);

        mCustomAudienceBlob.overrideFromJSONObject(jsonObjectWithAuctionServerRequestFlags);

        assertEquals(mCustomAudienceBlob.getOwner(), VALID_OWNER);
        assertEquals(mCustomAudienceBlob.getBuyer(), VALID_BUYER_1);
        assertEquals(mCustomAudienceBlob.getName(), VALID_NAME);
        assertEquals(mCustomAudienceBlob.getActivationTime(), VALID_ACTIVATION_TIME);
        assertEquals(mCustomAudienceBlob.getExpirationTime(), VALID_EXPIRATION_TIME);
        assertEquals(
                mCustomAudienceBlob.getDailyUpdateUri(),
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1));
        assertEquals(
                mCustomAudienceBlob.getBiddingLogicUri(),
                getValidBiddingLogicUriByBuyer(VALID_BUYER_1));
        assertEquals(mCustomAudienceBlob.getUserBiddingSignals(), VALID_USER_BIDDING_SIGNALS);
        assertEquals(
                mCustomAudienceBlob.getTrustedBiddingData().toString(),
                getValidTrustedBiddingDataByBuyer(VALID_BUYER_1).toString());
        assertEquals(
                mCustomAudienceBlob.getAds().toString(),
                getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1).toString());
        assertEquals(0, mCustomAudienceBlob.getAuctionServerRequestFlags());
    }

    @Test
    public void testHasOwner_true() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertTrue(mCustomAudienceBlob.hasOwner());
    }

    @Test
    public void testHasOwner_false() {
        assertFalse(mCustomAudienceBlob.hasOwner());
    }

    @Test
    public void testGetOwner_valid() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertEquals(mCustomAudienceBlob.getOwner(), VALID_OWNER);
    }

    @Test
    public void testGetOwner_unsetThrows() {
        assertThrows(NullPointerException.class, mCustomAudienceBlob::getOwner);
    }

    @Test
    public void testSetOwner_valid() {
        mCustomAudienceBlob.setOwner(VALID_OWNER);

        assertEquals(mCustomAudienceBlob.getOwner(), VALID_OWNER);
    }

    @Test
    public void testSetOwner_override() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        String overriddenOwner = "overriddenOwner";
        mCustomAudienceBlob.setOwner(overriddenOwner);

        assertEquals(mCustomAudienceBlob.getOwner(), overriddenOwner);
    }

    @Test
    public void testHasBuyer_true() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertTrue(mCustomAudienceBlob.hasBuyer());
    }

    @Test
    public void testHasBuyer_false() {
        assertFalse(mCustomAudienceBlob.hasBuyer());
    }

    @Test
    public void testGetBuyer_valid() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertEquals(mCustomAudienceBlob.getBuyer(), VALID_BUYER_1);
    }

    @Test
    public void testGetBuyer_unsetThrows() {
        assertThrows(NullPointerException.class, mCustomAudienceBlob::getBuyer);
    }

    @Test
    public void testSetBuyer_valid() {
        mCustomAudienceBlob.setBuyer(VALID_BUYER_1);

        assertEquals(mCustomAudienceBlob.getBuyer(), VALID_BUYER_1);
    }

    @Test
    public void testSetBuyer_override() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        AdTechIdentifier overriddenBuyer = VALID_BUYER_2;
        mCustomAudienceBlob.setBuyer(overriddenBuyer);

        assertEquals(mCustomAudienceBlob.getBuyer(), overriddenBuyer);
    }

    @Test
    public void testHasName_true() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertTrue(mCustomAudienceBlob.hasName());
    }

    @Test
    public void testHasName_false() {
        assertFalse(mCustomAudienceBlob.hasName());
    }

    @Test
    public void testGetName_valid() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertEquals(mCustomAudienceBlob.getName(), VALID_NAME);
    }

    @Test
    public void testGetName_unsetThrows() {
        assertThrows(NullPointerException.class, mCustomAudienceBlob::getName);
    }

    @Test
    public void testSetName_valid() {
        mCustomAudienceBlob.setName(VALID_NAME);

        assertEquals(mCustomAudienceBlob.getName(), VALID_NAME);
    }

    @Test
    public void testSetName_override() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        String overriddenName = "overriddenName";
        mCustomAudienceBlob.setName(overriddenName);

        assertEquals(mCustomAudienceBlob.getName(), overriddenName);
    }

    @Test
    public void testHasActivationTime_true() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertTrue(mCustomAudienceBlob.hasActivationTime());
    }

    @Test
    public void testHasActivationTime_false() {
        assertFalse(mCustomAudienceBlob.hasActivationTime());
    }

    @Test
    public void testGetActivationTime_valid() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertEquals(mCustomAudienceBlob.getActivationTime(), VALID_ACTIVATION_TIME);
    }

    @Test
    public void testGetActivationTime_unsetThrows() {
        assertThrows(NullPointerException.class, mCustomAudienceBlob::getActivationTime);
    }

    @Test
    public void testSetActivationTime_valid() {
        mCustomAudienceBlob.setActivationTime(VALID_ACTIVATION_TIME);

        assertEquals(mCustomAudienceBlob.getActivationTime(), VALID_ACTIVATION_TIME);
    }

    @Test
    public void testSetActivationTime_override() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        Instant overriddenActivationTime = Instant.now();
        mCustomAudienceBlob.setActivationTime(overriddenActivationTime);

        assertEquals(mCustomAudienceBlob.getActivationTime(), overriddenActivationTime);
    }

    @Test
    public void testHasExpirationTime_true() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertTrue(mCustomAudienceBlob.hasExpirationTime());
    }

    @Test
    public void testHasExpirationTime_false() {
        assertFalse(mCustomAudienceBlob.hasExpirationTime());
    }

    @Test
    public void tesGetExpirationTime_valid() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertEquals(mCustomAudienceBlob.getExpirationTime(), VALID_EXPIRATION_TIME);
    }

    @Test
    public void tesGetExpirationTime_unsetThrows() {
        assertThrows(NullPointerException.class, mCustomAudienceBlob::getExpirationTime);
    }

    @Test
    public void tesSetExpirationTime_valid() {
        mCustomAudienceBlob.setExpirationTime(VALID_EXPIRATION_TIME);

        assertEquals(mCustomAudienceBlob.getExpirationTime(), VALID_EXPIRATION_TIME);
    }

    @Test
    public void tesSetExpirationTime_override() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        Instant overriddenExpirationTime = Instant.now();
        mCustomAudienceBlob.setExpirationTime(overriddenExpirationTime);

        assertEquals(mCustomAudienceBlob.getExpirationTime(), overriddenExpirationTime);
    }

    @Test
    public void testHasDailyUpdateUri_true() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertTrue(mCustomAudienceBlob.hasDailyUpdateUri());
    }

    @Test
    public void testHasDailyUpdateUri_false() {
        assertFalse(mCustomAudienceBlob.hasDailyUpdateUri());
    }

    @Test
    public void testGetDailyUpdateUri_valid() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertEquals(
                mCustomAudienceBlob.getDailyUpdateUri(),
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_1));
    }

    @Test
    public void testGetDailyUpdateUri_unsetThrows() {
        assertThrows(NullPointerException.class, mCustomAudienceBlob::getDailyUpdateUri);
    }

    @Test
    public void testSetDailyUpdateUri_valid() {
        Uri validDailyUpdateUri = getValidDailyUpdateUriByBuyer(VALID_BUYER_1);
        mCustomAudienceBlob.setDailyUpdateUri(validDailyUpdateUri);

        assertEquals(mCustomAudienceBlob.getDailyUpdateUri(), validDailyUpdateUri);
    }

    @Test
    public void testGetDailyUpdateUri_override() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        Uri overriddenDailyUpdateUri =
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(VALID_BUYER_2);
        mCustomAudienceBlob.setDailyUpdateUri(overriddenDailyUpdateUri);

        assertEquals(mCustomAudienceBlob.getDailyUpdateUri(), overriddenDailyUpdateUri);
    }

    @Test
    public void testHasBiddingLogicUri_true() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertTrue(mCustomAudienceBlob.hasBiddingLogicUri());
    }

    @Test
    public void testHasBiddingLogicUri_false() {
        assertFalse(mCustomAudienceBlob.hasBiddingLogicUri());
    }

    @Test
    public void testGetBiddingLogicUri_valid() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertEquals(
                mCustomAudienceBlob.getBiddingLogicUri(),
                getValidBiddingLogicUriByBuyer(VALID_BUYER_1));
    }

    @Test
    public void testGetBiddingLogicUri_unsetThrows() {
        assertThrows(NullPointerException.class, mCustomAudienceBlob::getBiddingLogicUri);
    }

    @Test
    public void testSetBiddingLogicUri_valid() {
        Uri validBiddingLogicUri = getValidBiddingLogicUriByBuyer(VALID_BUYER_1);
        mCustomAudienceBlob.setBiddingLogicUri(validBiddingLogicUri);

        assertEquals(mCustomAudienceBlob.getBiddingLogicUri(), validBiddingLogicUri);
    }

    @Test
    public void testSetBiddingLogicUri_override() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        Uri overriddenBiddingLogicUri = getValidBiddingLogicUriByBuyer(VALID_BUYER_2);
        mCustomAudienceBlob.setBiddingLogicUri(overriddenBiddingLogicUri);

        assertEquals(mCustomAudienceBlob.getBiddingLogicUri(), overriddenBiddingLogicUri);
    }

    @Test
    public void testHasUserBiddingSignals_true() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertTrue(mCustomAudienceBlob.hasUserBiddingSignals());
    }

    @Test
    public void testHasUserBiddingSignals_false() {
        assertFalse(mCustomAudienceBlob.hasUserBiddingSignals());
    }

    @Test
    public void testGetUserBiddingSignals_valid() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        String expectedJSONString =
                new JSONObject(VALID_USER_BIDDING_SIGNALS.toString()).toString();

        assertEquals(
                mCustomAudienceBlob.getUserBiddingSignals().toString(),
                AdSelectionSignals.fromString(expectedJSONString).toString());
    }

    @Test
    public void testGetUserBiddingSignals_unsetThrows() {
        assertThrows(NullPointerException.class, mCustomAudienceBlob::getUserBiddingSignals);
    }

    @Test
    public void testSetUserBiddingSignals_valid() {
        mCustomAudienceBlob.setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS);

        assertEquals(
                mCustomAudienceBlob.getUserBiddingSignals().toString(),
                VALID_USER_BIDDING_SIGNALS.toString());
    }

    @Test
    public void testSetUserBiddingSignals_override() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        AdSelectionSignals overriddenUserBiddingSignals = AdSelectionSignals.fromString("k:v");
        mCustomAudienceBlob.setUserBiddingSignals(overriddenUserBiddingSignals);

        assertEquals(
                mCustomAudienceBlob.getUserBiddingSignals().toString(),
                overriddenUserBiddingSignals.toString());
    }

    @Test
    public void testHasTrustedBiddingData_true() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertTrue(mCustomAudienceBlob.hasTrustedBiddingData());
    }

    @Test
    public void testHasTrustedBiddingData_false() {
        assertFalse(mCustomAudienceBlob.hasTrustedBiddingData());
    }

    @Test
    public void testGetTrustedBiddingData_valid() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertEquals(
                mCustomAudienceBlob.getTrustedBiddingData().toString(),
                getValidTrustedBiddingDataByBuyer(VALID_BUYER_1).toString());
    }

    @Test
    public void testGetTrustedBiddingData_unsetThrows() {
        assertThrows(NullPointerException.class, mCustomAudienceBlob::getTrustedBiddingData);
    }

    @Test
    public void testSetTrustedBiddingData_valid() {
        TrustedBiddingData validTrustedBiddingData =
                getValidTrustedBiddingDataByBuyer(VALID_BUYER_1);
        mCustomAudienceBlob.setTrustedBiddingData(validTrustedBiddingData);

        assertEquals(
                mCustomAudienceBlob.getTrustedBiddingData().toString(),
                validTrustedBiddingData.toString());
    }

    @Test
    public void testSetTrustedBiddingData_override() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        TrustedBiddingData overriddenTrustedBiddingData =
                getValidTrustedBiddingDataByBuyer(VALID_BUYER_2);
        mCustomAudienceBlob.setTrustedBiddingData(overriddenTrustedBiddingData);

        assertEquals(
                mCustomAudienceBlob.getTrustedBiddingData().toString(),
                overriddenTrustedBiddingData.toString());
    }

    @Test
    public void testHasAds_true() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertTrue(mCustomAudienceBlob.hasAds());
    }

    @Test
    public void testHasAds_false() {
        assertFalse(mCustomAudienceBlob.hasAds());
    }

    @Test
    public void testGetAds_valid() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        assertEquals(
                mCustomAudienceBlob.getAds().toString(),
                getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1).toString());
    }

    @Test
    public void testGetAds_unsetThrows() {
        assertThrows(NullPointerException.class, mCustomAudienceBlob::getAds);
    }

    @Test
    public void testSetAds_valid() {
        List<AdData> validAds = getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_1);
        mCustomAudienceBlob.setAds(validAds);

        assertEquals(mCustomAudienceBlob.getAds().toString(), validAds.toString());
    }

    @Test
    public void testSetAds_override() throws JSONException {
        mCustomAudienceBlob.overrideFromJSONObject(mJSONObject);

        List<AdData> overriddenAds = getValidFilterAdsWithAdRenderIdByBuyer(VALID_BUYER_2);
        mCustomAudienceBlob.setAds(overriddenAds);

        assertEquals(mCustomAudienceBlob.getAds().toString(), overriddenAds.toString());
    }
}
