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

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.ADS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_COUNTERS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_FILTERS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_RENDER_ID_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AUCTION_SERVER_REQUEST_FLAGS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.METADATA_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.RENDER_URI_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_DATA_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_KEYS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_URI_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.JsonFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class CustomAudienceUpdatableDataFixture {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public static String getEmptyJsonResponseString() throws JSONException {
        return toJsonResponseString(null, null, null);
    }

    public static String getFullSuccessfulJsonResponseString() throws JSONException {
        return toJsonResponseString(
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(),
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .build(),
                DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1));
    }

    /**
     * Converts the input user bidding signals, trusted bidding data, and list of ads to a valid
     * JSON object, adds harmless junk fields, and returns it as a serialized string.
     */
    public static String toJsonResponseStringWithHarmlessJunk(
            String userBiddingSignals, DBTrustedBiddingData trustedBiddingData, List<DBAdData> ads)
            throws JSONException {
        return toJsonResponseString(userBiddingSignals, trustedBiddingData, ads, true);
    }

    /**
     * Gets a valid JSON object with keys for user bidding signals, trusted bidding data, and a list
     * of ads, malforms the expected schema, and returns it as a serialized string.
     */
    public static String getMalformedJsonResponseString() throws JSONException {
        return getMalformedJsonObject().toString();
    }

    /**
     * Gets a valid JSON object with keys for user bidding signals, trusted bidding data, and a list
     * of ads, malforms the expected schema, and returns it.
     */
    public static JSONObject getMalformedJsonObject() throws JSONException {
        JSONObject jsonResponse = new JSONObject();

        jsonResponse.put(
                USER_BIDDING_SIGNALS_KEY,
                "user bidding signals but as a string and not a JSON object");
        jsonResponse.put(TRUSTED_BIDDING_DATA_KEY, 0);
        jsonResponse.put(ADS_KEY, "mismatched schema");

        return jsonResponse;
    }

    /**
     * Gets a valid JSON object with keys for user bidding signals, trusted bidding data, and a list
     * of ads, malforms the expected schema at a deeper level, and returns it.
     */
    public static JSONObject getDeeperMalformedJsonObject() throws JSONException {
        JSONObject jsonResponse = new JSONObject();

        jsonResponse.put(USER_BIDDING_SIGNALS_KEY, 0);

        JSONObject trustedBiddingDataObject = new JSONObject();
        trustedBiddingDataObject.put(TRUSTED_BIDDING_URI_KEY, 0);
        trustedBiddingDataObject.put(TRUSTED_BIDDING_KEYS_KEY, 0);
        jsonResponse.put(TRUSTED_BIDDING_DATA_KEY, trustedBiddingDataObject);

        JSONArray adsArray = new JSONArray();
        JSONObject adObject0 = new JSONObject();
        adObject0.put(RENDER_URI_KEY, 0);
        adObject0.put(METADATA_KEY, 0);
        adsArray.put(adObject0);
        JSONObject adObject1 = new JSONObject();
        adObject1.put(RENDER_URI_KEY, 1);
        adObject1.put(METADATA_KEY, 1);
        adsArray.put(adObject1);
        jsonResponse.put(ADS_KEY, adsArray);

        return jsonResponse;
    }

    /**
     * Gets a valid JSON object with keys for user bidding signals, trusted bidding data, and a list
     * of ads, malforms the expected schema to null, and returns it.
     */
    public static JSONObject getMalformedNullJsonObject() throws JSONException {
        JSONObject jsonResponse = new JSONObject();

        jsonResponse.put(USER_BIDDING_SIGNALS_KEY, JSONObject.NULL);
        jsonResponse.put(TRUSTED_BIDDING_DATA_KEY, JSONObject.NULL);
        jsonResponse.put(ADS_KEY, JSONObject.NULL);

        return jsonResponse;
    }

    /**
     * Converts the input user bidding signals, trusted bidding data, and list of ads to a valid
     * JSON object and returns it as a serialized string.
     */
    public static String toJsonResponseString(
            String userBiddingSignals, DBTrustedBiddingData trustedBiddingData, List<DBAdData> ads)
            throws JSONException {
        return toJsonResponseString(userBiddingSignals, trustedBiddingData, ads, false);
    }

    /**
     * Converts the input user bidding signals, trusted bidding data, and list of ads to a valid
     * JSON object and returns it as a serialized string.
     *
     * <p>Optionally adds harmless junk to the response by adding unexpected fields.
     */
    private static String toJsonResponseString(
            String userBiddingSignals,
            DBTrustedBiddingData trustedBiddingData,
            List<DBAdData> ads,
            boolean shouldAddHarmlessJunk)
            throws JSONException {
        JSONObject jsonResponse = new JSONObject();

        jsonResponse = addToJsonObject(jsonResponse, userBiddingSignals, shouldAddHarmlessJunk);
        jsonResponse = addToJsonObject(jsonResponse, trustedBiddingData, shouldAddHarmlessJunk);
        jsonResponse = addToJsonObject(jsonResponse, ads, shouldAddHarmlessJunk);

        return jsonResponse.toString();
    }
    /**
     * Converts the input user bidding signals, trusted bidding data, list of ads, and
     * auctionServerRequestFlags to a valid JSON object and returns it as a serialized string.
     */
    public static String toJsonResponseString(
            String userBiddingSignals,
            DBTrustedBiddingData trustedBiddingData,
            List<DBAdData> ads,
            List<String> auctionServerFlags)
            throws JSONException {
        return toJsonResponseString(
                userBiddingSignals, trustedBiddingData, ads, auctionServerFlags, false);
    }

    /**
     * Converts the input user bidding signals, trusted bidding data, list of ads, and
     * auctionServerRequestFlags to a valid JSON object and returns it as a serialized string.
     *
     * <p>Optionally adds harmless junk to the response by adding unexpected fields.
     */
    public static String toJsonResponseString(
            String userBiddingSignals,
            DBTrustedBiddingData trustedBiddingData,
            List<DBAdData> ads,
            List<String> auctionServerFlags,
            boolean shouldAddHarmlessJunk)
            throws JSONException {
        JSONObject jsonResponse = new JSONObject();

        jsonResponse = addToJsonObject(jsonResponse, userBiddingSignals, shouldAddHarmlessJunk);
        jsonResponse = addToJsonObject(jsonResponse, trustedBiddingData, shouldAddHarmlessJunk);
        jsonResponse = addToJsonObject(jsonResponse, ads, shouldAddHarmlessJunk);
        jsonResponse =
                addAuctionServerRequestFlagsToJsonObject(
                        jsonResponse, auctionServerFlags, shouldAddHarmlessJunk);

        return jsonResponse.toString();
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * user bidding signals.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addToJsonObject(
            JSONObject jsonResponse, String userBiddingSignals, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (jsonResponse == null) {
            jsonResponse = new JSONObject();
        }

        if (shouldAddHarmlessJunk) {
            JsonFixture.addHarmlessJunkValues(jsonResponse);
        }

        if (userBiddingSignals != null) {
            JSONObject userBiddingSignalsJson = new JSONObject(userBiddingSignals);
            jsonResponse.put(USER_BIDDING_SIGNALS_KEY, userBiddingSignalsJson);
        }

        return jsonResponse;
    }

    /**
     * Converts {@link DBTrustedBiddingData} into a JSONObject with a keyed field for trusted
     * bidding data.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addToJsonObject(
            JSONObject jsonResponse,
            DBTrustedBiddingData trustedBiddingData,
            boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (jsonResponse == null) {
            jsonResponse = new JSONObject();
        }

        if (trustedBiddingData != null) {
            JSONObject trustedBiddingDataJson = new JSONObject();
            if (shouldAddHarmlessJunk) {
                JsonFixture.addHarmlessJunkValues(trustedBiddingDataJson);
            }

            trustedBiddingDataJson.put(
                    TRUSTED_BIDDING_URI_KEY, trustedBiddingData.getUri().toString());
            JSONArray trustedBiddingKeysJson = new JSONArray(trustedBiddingData.getKeys());
            if (shouldAddHarmlessJunk) {
                JsonFixture.addHarmlessJunkValues(trustedBiddingKeysJson);
            }
            trustedBiddingDataJson.put(TRUSTED_BIDDING_KEYS_KEY, trustedBiddingKeysJson);

            jsonResponse.put(TRUSTED_BIDDING_DATA_KEY, trustedBiddingDataJson);
        }

        return jsonResponse;
    }

    /**
     * Converts a list of {@link DBAdData} into a JSONObject with a keyed field for ads.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addToJsonObject(
            JSONObject jsonResponse, List<DBAdData> ads, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (jsonResponse == null) {
            jsonResponse = new JSONObject();
        }

        if (ads != null) {
            JSONArray adsJson = new JSONArray();
            if (shouldAddHarmlessJunk) {
                JsonFixture.addHarmlessJunkValues(adsJson);
            }

            for (DBAdData ad : ads) {
                JSONObject adJson = new JSONObject();
                if (shouldAddHarmlessJunk) {
                    JsonFixture.addHarmlessJunkValues(adJson);
                }

                adJson.put(RENDER_URI_KEY, ad.getRenderUri().toString());
                try {
                    adJson.put(METADATA_KEY, new JSONObject(ad.getMetadata()));
                } catch (JSONException exception) {
                    sLogger.v(
                            "Trying to add invalid JSON to test object (%s); inserting as String"
                                    + " instead",
                            exception.getMessage());
                    adJson.put(METADATA_KEY, ad.getMetadata());
                }
                if (!ad.getAdCounterKeys().isEmpty()) {
                    adJson.put(AD_COUNTERS_KEY, new JSONArray(ad.getAdCounterKeys()));
                }
                if (ad.getAdFilters() != null) {
                    adJson.put(AD_FILTERS_KEY, ad.getAdFilters().toJson());
                }
                if (ad.getAdRenderId() != null) {
                    adJson.put(AD_RENDER_ID_KEY, ad.getAdRenderId());
                }
                adsJson.put(adJson);
            }

            jsonResponse.put(ADS_KEY, adsJson);
        }

        return jsonResponse;
    }

    /**
     * Converts a list of {@link String} containing auction server request flags into a JSONObject
     * keyed on {@link CustomAudienceUpdatableDataReader#AUCTION_SERVER_REQUEST_FLAGS_KEY}.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addAuctionServerRequestFlagsToJsonObject(
            JSONObject jsonResponse,
            List<String> auctionServerRequestFlags,
            boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (jsonResponse == null) {
            jsonResponse = new JSONObject();
        }
        JSONArray array = new JSONArray(auctionServerRequestFlags);
        if (shouldAddHarmlessJunk) {
            JsonFixture.addHarmlessJunkValues(array);
        }
        jsonResponse.put(AUCTION_SERVER_REQUEST_FLAGS_KEY, array);
        return jsonResponse;
    }

    public static CustomAudienceUpdatableData.Builder getValidBuilderFullSuccessfulResponse()
            throws JSONException {
        return CustomAudienceUpdatableData.builder()
                .setUserBiddingSignals(
                        AdSelectionSignals.fromString(
                                JsonFixture.formatAsOrgJsonJSONObjectString(
                                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS
                                                .toString())))
                .setTrustedBiddingData(
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                        CommonFixture.VALID_BUYER_1)
                                .build())
                .setAds(DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1))
                .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                .setContainsSuccessfulUpdate(true);
    }

    public static CustomAudienceUpdatableData.Builder getValidBuilderEmptySuccessfulResponse() {
        return CustomAudienceUpdatableData.builder()
                .setUserBiddingSignals(null)
                .setTrustedBiddingData(null)
                .setAds(null)
                .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                .setContainsSuccessfulUpdate(true);
    }

    public static CustomAudienceUpdatableData.Builder getValidBuilderEmptyFailedResponse() {
        return CustomAudienceUpdatableData.builder()
                .setUserBiddingSignals(null)
                .setTrustedBiddingData(null)
                .setAds(null)
                .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                .setContainsSuccessfulUpdate(false);
    }
}
