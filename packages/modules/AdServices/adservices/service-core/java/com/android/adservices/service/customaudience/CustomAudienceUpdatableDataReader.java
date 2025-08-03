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

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.JsonUtils;
import com.android.adservices.service.common.ValidatorUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// TODO(b/283857101): Delete and use CustomAudienceBlob instead.
/**
 * A parser and validator for a JSON response that is fetched during the Custom Audience background
 * fetch process.
 */
public class CustomAudienceUpdatableDataReader {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String USER_BIDDING_SIGNALS_KEY = "user_bidding_signals";
    public static final String TRUSTED_BIDDING_DATA_KEY = "trusted_bidding_data";
    public static final String TRUSTED_BIDDING_URI_KEY = "trusted_bidding_uri";
    public static final String TRUSTED_BIDDING_KEYS_KEY = "trusted_bidding_keys";
    public static final String ADS_KEY = "ads";
    public static final String RENDER_URI_KEY = "render_uri";
    public static final String METADATA_KEY = "metadata";
    public static final String AD_COUNTERS_KEY = "ad_counter_keys";
    public static final String AD_FILTERS_KEY = "ad_filters";
    public static final String AD_RENDER_ID_KEY = "ad_render_id";
    public static final String STRING_ERROR_FORMAT = "Unexpected format parsing %s in %s";
    public static final String AUCTION_SERVER_REQUEST_FLAGS_KEY = "auction_server_request_flags";
    public static final String OMIT_ADS_VALUE = "omit_ads";

    public static final String FIELD_FOUND_LOG_FORMAT = "%s Found %s in JSON response";
    public static final String VALIDATED_FIELD_LOG_FORMAT =
            "%s Validated %s found in JSON response";
    public static final String FIELD_NOT_FOUND_LOG_FORMAT = "%s %s not found in JSON response";
    public static final String SKIP_INVALID_JSON_TYPE_LOG_FORMAT =
            "%s Invalid JSON type while parsing a single item in the %s found in JSON response;"
                    + " ignoring and continuing.  Error message: %s";

    private final JSONObject mResponseObject;
    private final String mResponseHash;
    private final AdTechIdentifier mBuyer;
    private final int mMaxUserBiddingSignalsSizeB;
    private final int mMaxTrustedBiddingDataSizeB;
    private final int mMaxAdsSizeB;
    private final int mMaxNumAds;
    private final ReadFiltersFromJsonStrategy mGetFiltersFromJsonObjectStrategy;
    private final ReadAdRenderIdFromJsonStrategy mReadAdRenderIdFromJsonStrategy;

    /**
     * Creates a {@link CustomAudienceUpdatableDataReader} that will read updatable data from a
     * given {@link JSONObject} and log with the given identifying {@code responseHash}.
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
     * @param adRenderIdEnabled whether ad render id field should be read
     * @param adRenderIdMaxLength the max length of the ad render id
     */
    protected CustomAudienceUpdatableDataReader(
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
        mMaxUserBiddingSignalsSizeB = maxUserBiddingSignalsSizeB;
        mMaxTrustedBiddingDataSizeB = maxTrustedBiddingDataSizeB;
        mMaxAdsSizeB = maxAdsSizeB;
        mMaxNumAds = maxNumAds;
        mGetFiltersFromJsonObjectStrategy =
                ReadFiltersFromJsonStrategyFactory.getStrategy(filteringEnabled);
        mReadAdRenderIdFromJsonStrategy =
                ReadAdRenderIdFromJsonStrategyFactory.getStrategy(
                        adRenderIdEnabled, adRenderIdMaxLength);
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
        if (mResponseObject.has(USER_BIDDING_SIGNALS_KEY)) {
            sLogger.v(FIELD_FOUND_LOG_FORMAT, mResponseHash, USER_BIDDING_SIGNALS_KEY);

            // Note that because the user bidding signals are stored in the response as a full JSON
            // object already, the signals do not need to be validated further; the JSON must have
            // been valid to be extracted successfully
            JSONObject signalsJsonObj =
                    Objects.requireNonNull(mResponseObject.getJSONObject(USER_BIDDING_SIGNALS_KEY));
            String signalsString = signalsJsonObj.toString();

            if (signalsString.length() > mMaxUserBiddingSignalsSizeB) {
                throw new IllegalArgumentException();
            }

            sLogger.v(VALIDATED_FIELD_LOG_FORMAT, mResponseHash, USER_BIDDING_SIGNALS_KEY);
            return AdSelectionSignals.fromString(signalsString);
        } else {
            sLogger.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, USER_BIDDING_SIGNALS_KEY);
            return null;
        }
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
        if (mResponseObject.has(TRUSTED_BIDDING_DATA_KEY)) {
            sLogger.v(FIELD_FOUND_LOG_FORMAT, mResponseHash, TRUSTED_BIDDING_DATA_KEY);

            JSONObject dataJsonObj = mResponseObject.getJSONObject(TRUSTED_BIDDING_DATA_KEY);

            String uri =
                    JsonUtils.getStringFromJson(
                            dataJsonObj,
                            TRUSTED_BIDDING_URI_KEY,
                            String.format(
                                    STRING_ERROR_FORMAT,
                                    TRUSTED_BIDDING_URI_KEY,
                                    TRUSTED_BIDDING_DATA_KEY));
            Uri parsedUri = Uri.parse(uri);

            JSONArray keysJsonArray = dataJsonObj.getJSONArray(TRUSTED_BIDDING_KEYS_KEY);
            int keysListLength = keysJsonArray.length();
            List<String> keysList = new ArrayList<>(keysListLength);
            for (int i = 0; i < keysListLength; i++) {
                try {
                    keysList.add(
                            JsonUtils.getStringFromJsonArrayAtIndex(
                                    keysJsonArray,
                                    i,
                                    String.format(
                                            STRING_ERROR_FORMAT,
                                            TRUSTED_BIDDING_KEYS_KEY,
                                            TRUSTED_BIDDING_DATA_KEY)));
                } catch (JSONException | NullPointerException exception) {
                    // Skip any keys that are malformed and continue to the next in the list; note
                    // that if the entire given list of keys is junk, then any existing trusted
                    // bidding keys are cleared from the custom audience
                    sLogger.v(
                            SKIP_INVALID_JSON_TYPE_LOG_FORMAT,
                            mResponseHash,
                            TRUSTED_BIDDING_KEYS_KEY,
                            Optional.ofNullable(exception.getMessage()).orElse("<null>"));
                }
            }

            AdTechUriValidator uriValidator =
                    new AdTechUriValidator(
                            ValidatorUtil.AD_TECH_ROLE_BUYER,
                            mBuyer.toString(),
                            this.getClass().getSimpleName(),
                            TrustedBiddingDataValidator.TRUSTED_BIDDING_URI_FIELD_NAME);
            uriValidator.validate(parsedUri);

            DBTrustedBiddingData trustedBiddingData =
                    new DBTrustedBiddingData.Builder().setUri(parsedUri).setKeys(keysList).build();

            if (trustedBiddingData.size() > mMaxTrustedBiddingDataSizeB) {
                throw new IllegalArgumentException();
            }

            sLogger.v(VALIDATED_FIELD_LOG_FORMAT, mResponseHash, TRUSTED_BIDDING_DATA_KEY);
            return trustedBiddingData;
        } else {
            sLogger.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, TRUSTED_BIDDING_DATA_KEY);
            return null;
        }
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
        if (mResponseObject.has(ADS_KEY)) {
            sLogger.v(FIELD_FOUND_LOG_FORMAT, mResponseHash, ADS_KEY);

            JSONArray adsJsonArray = mResponseObject.getJSONArray(ADS_KEY);
            int adsSize = 0;
            int adsListLength = adsJsonArray.length();
            List<DBAdData> adsList = new ArrayList<>();
            for (int i = 0; i < adsListLength; i++) {
                try {
                    JSONObject adDataJsonObj = adsJsonArray.getJSONObject(i);

                    // Note: getString() coerces values to be strings; use get() instead
                    Object uri = adDataJsonObj.get(RENDER_URI_KEY);
                    if (!(uri instanceof String)) {
                        throw new JSONException(
                                "Unexpected format parsing " + RENDER_URI_KEY + " in " + ADS_KEY);
                    }
                    Uri parsedUri = Uri.parse(Objects.requireNonNull((String) uri));

                    // By passing in an empty ad tech identifier string, ad tech identifier host
                    // matching is skipped
                    AdTechUriValidator uriValidator =
                            new AdTechUriValidator(
                                    ValidatorUtil.AD_TECH_ROLE_BUYER,
                                    "",
                                    this.getClass().getSimpleName(),
                                    RENDER_URI_KEY);
                    uriValidator.validate(parsedUri);

                    String metadata =
                            Objects.requireNonNull(adDataJsonObj.getJSONObject(METADATA_KEY))
                                    .toString();

                    DBAdData.Builder adDataBuilder =
                            new DBAdData.Builder().setRenderUri(parsedUri).setMetadata(metadata);

                    mGetFiltersFromJsonObjectStrategy.readFilters(adDataBuilder, adDataJsonObj);
                    mReadAdRenderIdFromJsonStrategy.readId(adDataBuilder, adDataJsonObj);
                    DBAdData adData = adDataBuilder.build();
                    adsList.add(adData);
                    adsSize += adData.size();
                } catch (JSONException
                        | NullPointerException
                        | IllegalArgumentException exception) {
                    // Skip any ads that are malformed and continue to the next in the list;
                    // note
                    // that if the entire given list of ads is junk, then any existing ads are
                    // cleared from the custom audience
                    sLogger.v(
                            SKIP_INVALID_JSON_TYPE_LOG_FORMAT,
                            mResponseHash,
                            ADS_KEY,
                            Optional.ofNullable(exception.getMessage()).orElse("<null>"));
                }
            }

            if (adsSize > mMaxAdsSizeB) {
                throw new IllegalArgumentException();
            }

            if (adsList.size() > mMaxNumAds) {
                throw new IllegalArgumentException();
            }

            sLogger.v(VALIDATED_FIELD_LOG_FORMAT, mResponseHash, ADS_KEY);
            return adsList;
        } else {
            sLogger.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, ADS_KEY);
            return null;
        }
    }

    /**
     * Returns the server auction request bitfield extracted from the response, if found.
     *
     * @throws JSONException if the value found at the key is not a {@link JSONArray}
     */
    @CustomAudience.AuctionServerRequestFlag
    public int getAuctionServerRequestFlags() throws JSONException {
        @CustomAudience.AuctionServerRequestFlag int result = 0;
        if (mResponseObject.has(AUCTION_SERVER_REQUEST_FLAGS_KEY)) {
            sLogger.v(FIELD_FOUND_LOG_FORMAT, mResponseHash, AUCTION_SERVER_REQUEST_FLAGS_KEY);
            JSONArray array = mResponseObject.getJSONArray(AUCTION_SERVER_REQUEST_FLAGS_KEY);
            for (int i = 0; i < array.length(); i++) {
                if (OMIT_ADS_VALUE.equals(array.getString(i))) {
                    if ((result & FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS) == 0) {
                        // Only set the flag and print the log once
                        sLogger.v(VALIDATED_FIELD_LOG_FORMAT, mResponseHash, OMIT_ADS_VALUE);
                        result = result | FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;
                    }
                }
            }
        } else {
            sLogger.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, AUCTION_SERVER_REQUEST_FLAGS_KEY);
        }
        return result;
    }
}
