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

import static com.android.adservices.service.customaudience.CustomAudienceBlob.AUCTION_SERVER_REQUEST_FLAGS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceBlob.BUYER_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceBlob.OWNER_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.ADS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_COUNTERS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_FILTERS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_RENDER_ID_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.METADATA_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.RENDER_URI_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_DATA_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_KEYS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_URI_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.ACTIVATION_TIME_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.BIDDING_LOGIC_URI_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.DAILY_UPDATE_URI_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.EXPIRATION_TIME_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.NAME_KEY;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.JsonFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.List;

public class CustomAudienceBlobFixture {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    /** Converts the input to a valid JSON object and returns it as a serialized string. */
    public static String asJSONObjectString(
            String owner,
            AdTechIdentifier buyer,
            String name,
            Instant activationTime,
            Instant expirationTime,
            Uri dailyUpdateUri,
            Uri biddingLogicUri,
            String userBiddingSignals,
            DBTrustedBiddingData trustedBiddingData,
            List<DBAdData> ads)
            throws JSONException {
        return asJSONObject(
                        owner,
                        buyer,
                        name,
                        activationTime,
                        expirationTime,
                        dailyUpdateUri,
                        biddingLogicUri,
                        userBiddingSignals,
                        trustedBiddingData,
                        ads,
                        false)
                .toString();
    }

    /**
     * Converts the inputs to a valid JSON object and returns it as a serialized string.
     *
     * <p>Optionally adds harmless junk to the response by adding unexpected fields.
     */
    public static JSONObject asJSONObject(
            String owner,
            AdTechIdentifier buyer,
            String name,
            Instant activationTime,
            Instant expirationTime,
            Uri dailyUpdateUri,
            Uri biddingLogicUri,
            String userBiddingSignals,
            DBTrustedBiddingData trustedBiddingData,
            List<DBAdData> ads,
            boolean shouldAddHarmlessJunk)
            throws JSONException {
        JSONObject json = new JSONObject();

        json = addOwner(json, owner, shouldAddHarmlessJunk);
        json = addBuyer(json, buyer, shouldAddHarmlessJunk);
        json = addName(json, name, shouldAddHarmlessJunk);
        json = addActivationTime(json, activationTime, shouldAddHarmlessJunk);
        json = addExpirationTime(json, expirationTime, shouldAddHarmlessJunk);
        json = addDailyUpdateUri(json, dailyUpdateUri, shouldAddHarmlessJunk);
        json = addBiddingLogicUri(json, biddingLogicUri, shouldAddHarmlessJunk);
        json = addUserBiddingSignals(json, userBiddingSignals, shouldAddHarmlessJunk);
        json = addTrustedBiddingData(json, trustedBiddingData, shouldAddHarmlessJunk);
        json = addAds(json, ads, shouldAddHarmlessJunk);

        return json;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * owner.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addOwner(JSONObject json, String owner, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (owner != null) {
            return addToJSONObject(json, OWNER_KEY, owner, shouldAddHarmlessJunk);
        }
        return json;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * buyer.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addBuyer(
            JSONObject json, AdTechIdentifier adTechIdentifier, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (adTechIdentifier != null) {
            return addToJSONObject(
                    json, BUYER_KEY, adTechIdentifier.toString(), shouldAddHarmlessJunk);
        }
        return json;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * name.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addName(JSONObject json, String name, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (name != null) {
            return addToJSONObject(json, NAME_KEY, name, shouldAddHarmlessJunk);
        }
        return json;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * activation time.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addActivationTime(
            JSONObject json, Instant activationTime, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (activationTime != null) {
            return addToJSONObject(
                    json,
                    ACTIVATION_TIME_KEY,
                    activationTime.toEpochMilli(),
                    shouldAddHarmlessJunk);
        }
        return json;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * expiration time.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addExpirationTime(
            JSONObject json, Instant expirationTime, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (expirationTime != null) {
            return addToJSONObject(
                    json,
                    EXPIRATION_TIME_KEY,
                    expirationTime.toEpochMilli(),
                    shouldAddHarmlessJunk);
        }
        return json;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * daily update uri.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addDailyUpdateUri(
            JSONObject json, Uri dailyUpdateUri, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (dailyUpdateUri != null) {
            return addToJSONObject(
                    json, DAILY_UPDATE_URI_KEY, dailyUpdateUri.toString(), shouldAddHarmlessJunk);
        }
        return json;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * bidding logic uri.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addBiddingLogicUri(
            JSONObject json, Uri biddingLogicUri, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (biddingLogicUri != null) {
            return addToJSONObject(
                    json, BIDDING_LOGIC_URI_KEY, biddingLogicUri.toString(), shouldAddHarmlessJunk);
        }
        return json;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * user bidding signals.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addUserBiddingSignals(
            JSONObject json, String userBiddingSignals, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (userBiddingSignals != null) {
            JSONObject userBiddingSignalsJson = new JSONObject(userBiddingSignals);
            return addToJSONObject(
                    json, USER_BIDDING_SIGNALS_KEY, userBiddingSignalsJson, shouldAddHarmlessJunk);
        }
        return json;
    }

    /**
     * Converts {@link DBTrustedBiddingData} into a JSONObject with a keyed field for trusted
     * bidding data.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addTrustedBiddingData(
            JSONObject json, DBTrustedBiddingData trustedBiddingData, boolean shouldAddHarmlessJunk)
            throws JSONException {
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

            return addToJSONObject(json, TRUSTED_BIDDING_DATA_KEY, trustedBiddingDataJson, false);
        }

        return json;
    }

    /**
     * Converts a list of {@link DBAdData} into a JSONObject with a keyed field for ads.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addAds(
            JSONObject json, List<DBAdData> ads, boolean shouldAddHarmlessJunk)
            throws JSONException {
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

            return addToJSONObject(json, ADS_KEY, adsJson, false);
        }

        return json;
    }

    /**
     * Converts a list of {@link String} into a JSONObject with a keyed field for auction server
     * request flags.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addAuctionServerRequestFlags(
            JSONObject json, List<String> auctionServerRequestFlags, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (auctionServerRequestFlags != null) {
            JSONArray auctionServerRequestFlagsArray = new JSONArray();
            for (String s : auctionServerRequestFlags) {
                auctionServerRequestFlagsArray.put(s);
            }
            if (shouldAddHarmlessJunk) {
                JsonFixture.addHarmlessJunkValues(auctionServerRequestFlagsArray);
            }
            return addToJSONObject(
                    json, AUCTION_SERVER_REQUEST_FLAGS_KEY, auctionServerRequestFlagsArray, false);
        }
        return json;
    }

    private static JSONObject addToJSONObject(
            JSONObject json, String key, Object value, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (json == null) {
            json = new JSONObject();
        }

        if (shouldAddHarmlessJunk) {
            JsonFixture.addHarmlessJunkValues(json);
        }

        json.put(key, value);
        return json;
    }
}
