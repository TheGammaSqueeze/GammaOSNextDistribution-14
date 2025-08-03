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

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.FIELD_FOUND_LOG_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.FIELD_NOT_FOUND_LOG_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.STRING_ERROR_FORMAT;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.common.JsonUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

// TODO(b/283857101): Delete and use CustomAudienceBlob instead.
/** A parser and validator for a JSON representation of a Custom Audience. */
public class FetchCustomAudienceReader {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String NAME_KEY = "name";
    public static final String ACTIVATION_TIME_KEY = "activation_time";
    public static final String EXPIRATION_TIME_KEY = "expiration_time";
    public static final String DAILY_UPDATE_URI_KEY = "daily_update_uri";
    public static final String BIDDING_LOGIC_URI_KEY = "bidding_logic_uri";
    private final JSONObject mResponseObject;
    private final String mResponseHash;
    private final AdTechIdentifier mBuyer;
    private final CustomAudienceUpdatableDataReader mCustomAudienceUpdatableDataReader;

    /**
     * Creates a {@link FetchCustomAudienceReader} that will read updatable data from a given {@link
     * JSONObject} and log with the given identifying {@code responseHash}.
     *
     * @param responseObject a {@link JSONObject} that may contain user bidding signals, trusted
     *     bidding data, and/or a list of ads
     * @param responseHash a String that uniquely identifies the response which is used in logging
     * @param buyer the buyer ad tech's eTLD+1
     * @param maxUserBiddingSignalsSizeB the configured maximum size in bytes allocated for user
     *     bidding signals
     * @param maxTrustedBiddingDataSizeB the configured maximum size in bytes allocated for trusted
     *     bidding data
     * @param maxAdsSizeB the configured maximum size in bytes allocated for ads
     * @param maxNumAds the configured maximum number of ads allowed per update
     * @param filteringEnabled whether or not ad selection filtering fields should be read
     */
    protected FetchCustomAudienceReader(
            @NonNull JSONObject responseObject,
            @NonNull String responseHash,
            @NonNull AdTechIdentifier buyer,
            int maxUserBiddingSignalsSizeB,
            int maxTrustedBiddingDataSizeB,
            int maxAdsSizeB,
            int maxNumAds,
            boolean filteringEnabled,
            boolean adRenderIdEnabled,
            long adRenderIdMaxLength) {
        Objects.requireNonNull(responseObject);
        Objects.requireNonNull(responseHash);
        Objects.requireNonNull(buyer);

        mResponseObject = responseObject;
        mResponseHash = responseHash;
        mBuyer = buyer;
        mCustomAudienceUpdatableDataReader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        responseHash,
                        buyer,
                        maxUserBiddingSignalsSizeB,
                        maxTrustedBiddingDataSizeB,
                        maxAdsSizeB,
                        maxNumAds,
                        filteringEnabled,
                        adRenderIdEnabled,
                        adRenderIdMaxLength);
    }

    /**
     * Returns the user bidding signals extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted signals fail data validation
     */
    @Nullable
    public String getNameFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        if (mResponseObject.has(NAME_KEY)) {
            sLogger.v(FIELD_FOUND_LOG_FORMAT, mResponseHash, NAME_KEY);

            String name =
                    JsonUtils.getStringFromJson(
                            mResponseObject,
                            NAME_KEY,
                            String.format(STRING_ERROR_FORMAT, NAME_KEY, mResponseHash));

            // TODO(b/282018172): Validate name field
            // sLogger.v(VALIDATED_FIELD_LOG_FORMAT, mResponseHash, NAME_KEY);
            return name;
        } else {
            sLogger.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, NAME_KEY);
            return null;
        }
    }

    /**
     * Returns the user bidding signals extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted signals fail data validation
     */
    @Nullable
    public Instant getActivationTimeFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        if (mResponseObject.has(ACTIVATION_TIME_KEY)) {
            sLogger.v(FIELD_FOUND_LOG_FORMAT, mResponseHash, ACTIVATION_TIME_KEY);

            Instant activationTime =
                    Instant.ofEpochMilli(mResponseObject.getLong(ACTIVATION_TIME_KEY));

            // TODO(b/282018172): Validate activation_time field
            // sLogger.v(VALIDATED_FIELD_LOG_FORMAT, mResponseHash, ACTIVATION_TIME_KEY);
            return activationTime;
        } else {
            sLogger.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, ACTIVATION_TIME_KEY);
            return null;
        }
    }

    /**
     * Returns the user bidding signals extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted signals fail data validation
     */
    @Nullable
    public Instant getExpirationTimeFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        if (mResponseObject.has(EXPIRATION_TIME_KEY)) {
            sLogger.v(FIELD_FOUND_LOG_FORMAT, mResponseHash, EXPIRATION_TIME_KEY);
            Instant expirationTime =
                    Instant.ofEpochMilli(mResponseObject.getLong(EXPIRATION_TIME_KEY));

            // TODO(b/282018172): Validate expiration_time field
            // sLogger.v(VALIDATED_FIELD_LOG_FORMAT, mResponseHash, EXPIRATION_TIME_KEY);
            return expirationTime;
        } else {
            sLogger.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, EXPIRATION_TIME_KEY);
            return null;
        }
    }

    /**
     * Returns the user bidding signals extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted signals fail data validation
     */
    @Nullable
    public Uri getDailyUpdateUriFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        if (mResponseObject.has(DAILY_UPDATE_URI_KEY)) {
            sLogger.v(FIELD_FOUND_LOG_FORMAT, mResponseHash, DAILY_UPDATE_URI_KEY);

            String uri =
                    JsonUtils.getStringFromJson(
                            mResponseObject,
                            DAILY_UPDATE_URI_KEY,
                            String.format(
                                    STRING_ERROR_FORMAT, DAILY_UPDATE_URI_KEY, mResponseHash));
            Uri parsedUri = Uri.parse(uri);

            // TODO(b/282018172): Validate daily_update_uri field
            // sLogger.v(VALIDATED_FIELD_LOG_FORMAT, mResponseHash, DAILY_UPDATE_URI_KEY);
            return parsedUri;
        } else {
            sLogger.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, DAILY_UPDATE_URI_KEY);
            return null;
        }
    }

    /**
     * Returns the user bidding signals extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted signals fail data validation
     */
    @Nullable
    public Uri getBiddingLogicUriFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        if (mResponseObject.has(BIDDING_LOGIC_URI_KEY)) {
            sLogger.v(FIELD_FOUND_LOG_FORMAT, mResponseHash, BIDDING_LOGIC_URI_KEY);

            String uri =
                    JsonUtils.getStringFromJson(
                            mResponseObject,
                            BIDDING_LOGIC_URI_KEY,
                            String.format(
                                    STRING_ERROR_FORMAT, BIDDING_LOGIC_URI_KEY, mResponseHash));
            Uri parsedUri = Uri.parse(uri);

            // TODO(b/282018172): Validate bidding_logic_uri field
            // sLogger.v(VALIDATED_FIELD_LOG_FORMAT, mResponseHash, BIDDING_LOGIC_URI_KEY);
            return parsedUri;
        } else {
            sLogger.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, BIDDING_LOGIC_URI_KEY);
            return null;
        }
    }

    /**
     * Returns the user bidding signals extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted signals fail data validation
     */
    @Nullable
    public AdSelectionSignals getUserBiddingSignalsFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        return mCustomAudienceUpdatableDataReader.getUserBiddingSignalsFromJsonObject();
    }

    /**
     * Returns the trusted bidding data extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted data fails data validation
     */
    @Nullable
    public DBTrustedBiddingData getTrustedBiddingDataFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        return mCustomAudienceUpdatableDataReader.getTrustedBiddingDataFromJsonObject();
    }

    /**
     * Returns the list of ads extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted ads fail data validation
     */
    @Nullable
    public List<DBAdData> getAdsFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        return mCustomAudienceUpdatableDataReader.getAdsFromJsonObject();
    }
}
