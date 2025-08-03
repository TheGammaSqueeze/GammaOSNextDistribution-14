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

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.ADS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_COUNTERS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_FILTERS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_RENDER_ID_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.FIELD_FOUND_LOG_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.FIELD_NOT_FOUND_LOG_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.METADATA_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.RENDER_URI_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.SKIP_INVALID_JSON_TYPE_LOG_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.STRING_ERROR_FORMAT;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_DATA_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_KEYS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_URI_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.ACTIVATION_TIME_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.BIDDING_LOGIC_URI_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.DAILY_UPDATE_URI_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.EXPIRATION_TIME_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.NAME_KEY;

import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.customaudience.PartialCustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.common.JsonUtils;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Common representation of a custom audience.
 *
 * <p>A custom audience can be, partially or completely, represented in many ways:
 *
 * <ul>
 *   <li>{@link CustomAudience} or {@link FetchAndJoinCustomAudienceInput} as input from on-device
 *       callers.
 *   <li>{@link JSONObject} to/from a server.
 *   <li>{@link CustomAudienceUpdatableData} internally for daily fetch.
 *   <li>{@link DBCustomAudience} and {@link DBCustomAudienceBackgroundFetchData} to/from a DB.
 * </ul>
 *
 * Each of the above are use-case specific and their fields have different properties. For example,
 * a {@link CustomAudience#getAds()} may be malformed whereas {@link DBCustomAudience#getAds()} is
 * validated to be well-formed. In contrast, {@link CustomAudienceBlob} is a generalized
 * representation of a custom audience, that can be constructed to/from any of the above use-case
 * specific representations to aid testing and development of features.
 */
public class CustomAudienceBlob {
    // TODO(b/283857101): Remove the use functional interfaces and simplify by using individual
    //  named and typed fields instead.
    /**
     * Common representation of a custom audience's field.
     *
     * @param <T> the value of the field.
     */
    static class Field<T> {
        String mName;
        T mValue;
        Function<T, Object> mToJSONObject;
        BiFunction<JSONObject, String, T> mFromJSONObject;

        Field(Function<T, Object> toJSONObject, BiFunction<JSONObject, String, T> fromJSONObject) {
            this.mToJSONObject = toJSONObject;
            this.mFromJSONObject = fromJSONObject;
        }

        /**
         * @return {@link JSONObject} representation of the {@link Field}.
         */
        JSONObject toJSONObject() throws JSONException {
            JSONObject json = new JSONObject();
            json.put(mName, mToJSONObject.apply(mValue));
            return json;
        }

        /**
         * Populate the {@link Field#mName} and {@link Field#mValue} of the {@link Field} from its
         * {@link JSONObject} representation.
         */
        void fromJSONObject(JSONObject json, String key) throws JSONException {
            this.mName = key;
            this.mValue = mFromJSONObject.apply(json, key);
        }
    }

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String OWNER_KEY = "owner";
    public static final String BUYER_KEY = "buyer";
    public static final String AUCTION_SERVER_REQUEST_FLAGS_KEY = "auction_server_request_flags";
    public static final String OMIT_ADS_VALUE = "omit_ads";
    static final LinkedHashSet<String> mKeysSet =
            new LinkedHashSet<>(
                    Arrays.asList(
                            OWNER_KEY,
                            BUYER_KEY,
                            NAME_KEY,
                            ACTIVATION_TIME_KEY,
                            EXPIRATION_TIME_KEY,
                            DAILY_UPDATE_URI_KEY,
                            BIDDING_LOGIC_URI_KEY,
                            USER_BIDDING_SIGNALS_KEY,
                            TRUSTED_BIDDING_DATA_KEY,
                            ADS_KEY));
    final LinkedHashMap<String, Field<?>> mFieldsMap = new LinkedHashMap<>();
    private final ReadFiltersFromJsonStrategy mReadFiltersFromJsonStrategy;
    private final ReadAdRenderIdFromJsonStrategy mReadAdRenderIdFromJsonStrategy;
    private final boolean mAuctionServerRequestFlagsEnabled;

    public CustomAudienceBlob(
            boolean filteringEnabled,
            boolean adRenderIdEnabled,
            long adRenderIdMaxLength,
            boolean auctionServerRequestFlagsEnabled) {
        mReadFiltersFromJsonStrategy =
                ReadFiltersFromJsonStrategyFactory.getStrategy(filteringEnabled);
        mReadAdRenderIdFromJsonStrategy =
                ReadAdRenderIdFromJsonStrategyFactory.getStrategy(
                        adRenderIdEnabled, adRenderIdMaxLength);
        mAuctionServerRequestFlagsEnabled = auctionServerRequestFlagsEnabled;
    }

    @VisibleForTesting
    public CustomAudienceBlob() {
        // Filtering enabled by default.
        this(true, true, 12L, false);
    }

    /** Update fields of the {@link CustomAudienceBlob} from a {@link JSONObject}. */
    public void overrideFromJSONObject(JSONObject json) throws JSONException {
        LinkedHashSet<String> jsonKeySet = new LinkedHashSet<>(Lists.newArrayList(json.keys()));
        for (String key : mKeysSet) {
            if (jsonKeySet.contains(key)) {
                sLogger.v("Adding %s", key);
                switch (key) {
                    case OWNER_KEY:
                        this.setOwner(this.getStringFromJSONObject(json, OWNER_KEY));
                        break;
                    case BUYER_KEY:
                        this.setBuyer(
                                AdTechIdentifier.fromString(
                                        this.getStringFromJSONObject(json, BUYER_KEY)));
                        break;
                    case NAME_KEY:
                        this.setName(this.getStringFromJSONObject(json, NAME_KEY));
                        break;
                    case ACTIVATION_TIME_KEY:
                        this.setActivationTime(
                                this.getInstantFromJSONObject(json, ACTIVATION_TIME_KEY));
                        break;
                    case EXPIRATION_TIME_KEY:
                        this.setExpirationTime(
                                this.getInstantFromJSONObject(json, EXPIRATION_TIME_KEY));
                        break;
                    case DAILY_UPDATE_URI_KEY:
                        this.setDailyUpdateUri(
                                Uri.parse(
                                        this.getStringFromJSONObject(json, DAILY_UPDATE_URI_KEY)));
                        break;
                    case BIDDING_LOGIC_URI_KEY:
                        this.setBiddingLogicUri(
                                Uri.parse(
                                        this.getStringFromJSONObject(json, BIDDING_LOGIC_URI_KEY)));
                        break;
                    case USER_BIDDING_SIGNALS_KEY:
                        this.setUserBiddingSignals(
                                AdSelectionSignals.fromString(
                                        json.getJSONObject(USER_BIDDING_SIGNALS_KEY).toString()));
                        break;
                    case TRUSTED_BIDDING_DATA_KEY:
                        this.setTrustedBiddingData(
                                this.getTrustedBiddingDataFromJSONObject(
                                        json, TRUSTED_BIDDING_DATA_KEY));
                        break;
                    case ADS_KEY:
                        this.setAds(this.getAdsFromJSONObject(json, ADS_KEY));
                }
            }
        }
        // Set auction server flags if flag is enabled
        if (mAuctionServerRequestFlagsEnabled
                && jsonKeySet.contains(AUCTION_SERVER_REQUEST_FLAGS_KEY)) {
            this.setAuctionServerRequestFlags(
                    this.getAuctionServerRequestFlagsFromJSONObject(
                            json, AUCTION_SERVER_REQUEST_FLAGS_KEY));
        }
    }

    /**
     * Update fields of the {@link CustomAudienceBlob} from a {@link
     * FetchAndJoinCustomAudienceInput}.
     */
    public void overrideFromFetchAndJoinCustomAudienceInput(FetchAndJoinCustomAudienceInput input) {
        this.setOwner(input.getCallerPackageName());
        this.setBuyer(AdTechIdentifier.fromString(input.getFetchUri().getHost()));

        if (input.getName() != null) {
            this.setName(input.getName());
        }
        if (input.getActivationTime() != null) {
            this.setActivationTime(input.getActivationTime());
        }
        if (input.getExpirationTime() != null) {
            this.setExpirationTime(input.getExpirationTime());
        }
        if (input.getUserBiddingSignals() != null) {
            this.setUserBiddingSignals(input.getUserBiddingSignals());
        }
    }

    /**
     * Utility methods to override a {@link CustomAudienceBlob} from a {@link PartialCustomAudience}
     */
    public void overrideFromPartialCustomAudience(
            String owner, AdTechIdentifier buyer, PartialCustomAudience partialCustomAudience) {
        this.setOwner(owner);
        this.setBuyer(buyer);

        this.setName(partialCustomAudience.getName());

        if (partialCustomAudience.getActivationTime() != null) {
            this.setActivationTime(partialCustomAudience.getActivationTime());
        }

        if (partialCustomAudience.getExpirationTime() != null) {
            this.setExpirationTime(partialCustomAudience.getExpirationTime());
        }

        if (partialCustomAudience.getUserBiddingSignals() != null) {
            this.setUserBiddingSignals(partialCustomAudience.getUserBiddingSignals());
        }
    }

    /**
     * @return {@link JSONObject} representation of the {@link CustomAudienceBlob}.
     */
    public JSONObject asJSONObject() {
        JSONObject json = new JSONObject();
        mFieldsMap.forEach(
                (name, field) -> {
                    try {
                        json.put(name, field.toJSONObject().get(name));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
        return json;
    }

    /**
     * @return if the {@code owner} {@link Field} is set
     */
    public boolean hasOwner() {
        return mFieldsMap.get(OWNER_KEY) != null;
    }

    /**
     * @return the {@code owner} {@link Field}
     */
    public String getOwner() {
        return (String) mFieldsMap.get(OWNER_KEY).mValue;
    }

    /** set the {@code owner} {@link Field} */
    public void setOwner(String value) {
        if (mFieldsMap.containsKey(OWNER_KEY)) {
            Field<String> field = (Field<String>) mFieldsMap.get(OWNER_KEY);
            field.mValue = value;
        } else {
            Field<String> field = new Field<>((str) -> str, this::getStringFromJSONObject);

            field.mName = OWNER_KEY;
            field.mValue = value;

            mFieldsMap.put(OWNER_KEY, field);
        }
    }

    /**
     * @return if the {@code buyer} {@link Field} is set
     */
    public boolean hasBuyer() {
        return mFieldsMap.get(BUYER_KEY) != null;
    }

    /**
     * @return the {@code buyer} {@link Field}
     */
    public AdTechIdentifier getBuyer() {
        return (AdTechIdentifier) mFieldsMap.get(BUYER_KEY).mValue;
    }

    /** set the {@code buyer} {@link Field} */
    public void setBuyer(AdTechIdentifier value) {
        if (mFieldsMap.containsKey(BUYER_KEY)) {
            Field<AdTechIdentifier> field = (Field<AdTechIdentifier>) mFieldsMap.get(BUYER_KEY);
            field.mValue = value;
        } else {
            Field<AdTechIdentifier> field =
                    new Field<>(
                            Object::toString,
                            (json, key) ->
                                    AdTechIdentifier.fromString(
                                            this.getStringFromJSONObject(json, key)));

            field.mName = BUYER_KEY;
            field.mValue = value;

            mFieldsMap.put(BUYER_KEY, field);
        }
    }

    /** sets the {@code auctionServerRequestFlags} {@link Field} */
    public void setAuctionServerRequestFlags(List<String> auctionServerRequestFlags) {
        if (mFieldsMap.containsKey(AUCTION_SERVER_REQUEST_FLAGS_KEY)) {
            Field<List<String>> field =
                    (Field<List<String>>) mFieldsMap.get(AUCTION_SERVER_REQUEST_FLAGS_KEY);
            field.mValue = auctionServerRequestFlags;
        } else {
            Field<List<String>> field =
                    new Field<>(
                            this::getAuctionServerRequestFlagsAsJSONArray,
                            this::getAuctionServerRequestFlagsFromJSONObject);

            field.mName = AUCTION_SERVER_REQUEST_FLAGS_KEY;
            field.mValue = auctionServerRequestFlags;

            mFieldsMap.put(AUCTION_SERVER_REQUEST_FLAGS_KEY, field);
        }
    }

    /** Returns the server auction request bitfield extracted from the response, if found. */
    @CustomAudience.AuctionServerRequestFlag
    public int getAuctionServerRequestFlags() {
        @CustomAudience.AuctionServerRequestFlag int result = 0;
        if (mFieldsMap.containsKey(AUCTION_SERVER_REQUEST_FLAGS_KEY)) {
            sLogger.d("Fields map contains auction server key");
            List<String> list =
                    (List<String>) mFieldsMap.get(AUCTION_SERVER_REQUEST_FLAGS_KEY).mValue;
            for (String s : list) {
                if (OMIT_ADS_VALUE.equals(s)) {
                    if ((result & FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS) == 0) {
                        // Only set the flag once
                        result = result | FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;
                    }
                }
            }
        }
        return result;
    }

    /**
     * @return if the {@code name} {@link Field} is set
     */
    public boolean hasName() {
        return mFieldsMap.get(NAME_KEY) != null;
    }

    /**
     * @return the {@code name} {@link Field}
     */
    public String getName() {
        return (String) mFieldsMap.get(NAME_KEY).mValue;
    }

    /** set the {@code name} {@link Field} */
    public void setName(String value) {
        if (mFieldsMap.containsKey(NAME_KEY)) {
            Field<String> field = (Field<String>) mFieldsMap.get(NAME_KEY);
            field.mValue = value;
        } else {
            Field<String> field = new Field<>((str) -> str, this::getStringFromJSONObject);

            field.mName = NAME_KEY;
            field.mValue = value;

            mFieldsMap.put(NAME_KEY, field);
        }
    }

    /**
     * @return if the {@code activationTime} {@link Field} is set
     */
    public boolean hasActivationTime() {
        return mFieldsMap.get(ACTIVATION_TIME_KEY) != null;
    }

    /**
     * @return the {@code activationTime} {@link Field}
     */
    public Instant getActivationTime() {
        return (Instant) mFieldsMap.get(ACTIVATION_TIME_KEY).mValue;
    }

    /** set the {@code activationTime} {@link Field} */
    public void setActivationTime(Instant value) {
        if (mFieldsMap.containsKey(ACTIVATION_TIME_KEY)) {
            Field<Instant> field = (Field<Instant>) mFieldsMap.get(ACTIVATION_TIME_KEY);
            field.mValue = value;
        } else {
            Field<Instant> field =
                    new Field<>(Instant::toEpochMilli, this::getInstantFromJSONObject);

            field.mName = ACTIVATION_TIME_KEY;
            field.mValue = value;

            mFieldsMap.put(ACTIVATION_TIME_KEY, field);
        }
    }

    /**
     * @return if the {@code expirationTime} {@link Field} is set
     */
    public boolean hasExpirationTime() {
        return mFieldsMap.get(EXPIRATION_TIME_KEY) != null;
    }

    /**
     * @return the {@code expirationTime} {@link Field}
     */
    public Instant getExpirationTime() {
        return (Instant) mFieldsMap.get(EXPIRATION_TIME_KEY).mValue;
    }

    /** set the {@code expirationTime} {@link Field} */
    public void setExpirationTime(Instant value) {
        if (mFieldsMap.containsKey(EXPIRATION_TIME_KEY)) {
            Field<Instant> field = (Field<Instant>) mFieldsMap.get(EXPIRATION_TIME_KEY);
            field.mValue = value;
        } else {
            Field<Instant> field =
                    new Field<>(Instant::toEpochMilli, this::getInstantFromJSONObject);

            field.mName = EXPIRATION_TIME_KEY;
            field.mValue = value;

            mFieldsMap.put(EXPIRATION_TIME_KEY, field);
        }
    }

    /**
     * @return if the {@code dailyUpdateUri} {@link Field} is set
     */
    public boolean hasDailyUpdateUri() {
        return mFieldsMap.get(DAILY_UPDATE_URI_KEY) != null;
    }

    /**
     * @return the {@code dailyUpdateUri} {@link Field}
     */
    public Uri getDailyUpdateUri() {
        return (Uri) mFieldsMap.get(DAILY_UPDATE_URI_KEY).mValue;
    }

    /** set the {@code dailyUpdateUri} {@link Field} */
    public void setDailyUpdateUri(Uri value) {
        if (mFieldsMap.containsKey(DAILY_UPDATE_URI_KEY)) {
            Field<Uri> field = (Field<Uri>) mFieldsMap.get(DAILY_UPDATE_URI_KEY);
            field.mValue = value;
        } else {
            Field<Uri> field =
                    new Field<>(
                            Uri::toString,
                            (json, key) -> Uri.parse(this.getStringFromJSONObject(json, key)));

            field.mName = DAILY_UPDATE_URI_KEY;
            field.mValue = value;

            mFieldsMap.put(DAILY_UPDATE_URI_KEY, field);
        }
    }

    /**
     * @return if the {@code biddingLogicUri} {@link Field} is set
     */
    public boolean hasBiddingLogicUri() {
        return mFieldsMap.get(BIDDING_LOGIC_URI_KEY) != null;
    }

    /**
     * @return the {@code biddingLogicUri} {@link Field}
     */
    public Uri getBiddingLogicUri() {
        return (Uri) mFieldsMap.get(BIDDING_LOGIC_URI_KEY).mValue;
    }

    /** set the {@code biddingLogicUri} {@link Field} */
    public void setBiddingLogicUri(Uri value) {
        if (mFieldsMap.containsKey(BIDDING_LOGIC_URI_KEY)) {
            Field<Uri> field = (Field<Uri>) mFieldsMap.get(BIDDING_LOGIC_URI_KEY);
            field.mValue = value;
        } else {
            Field<Uri> field =
                    new Field<>(
                            Uri::toString,
                            (json, key) -> Uri.parse(this.getStringFromJSONObject(json, key)));

            field.mName = BIDDING_LOGIC_URI_KEY;
            field.mValue = value;

            mFieldsMap.put(BIDDING_LOGIC_URI_KEY, field);
        }
    }

    /**
     * @return if the {@code userBiddingSignals} {@link Field} is set
     */
    public boolean hasUserBiddingSignals() {
        return mFieldsMap.get(USER_BIDDING_SIGNALS_KEY) != null;
    }

    /**
     * @return the {@code userBiddingSignals} {@link Field}
     */
    public AdSelectionSignals getUserBiddingSignals() {
        return (AdSelectionSignals) mFieldsMap.get(USER_BIDDING_SIGNALS_KEY).mValue;
    }

    /** set the {@code userBiddingSignals} {@link Field} */
    public void setUserBiddingSignals(AdSelectionSignals value) {
        if (mFieldsMap.containsKey(USER_BIDDING_SIGNALS_KEY)) {
            Field<AdSelectionSignals> field =
                    (Field<AdSelectionSignals>) mFieldsMap.get(USER_BIDDING_SIGNALS_KEY);
            field.mValue = value;
        } else {
            Field<AdSelectionSignals> field =
                    new Field<>(
                            (adSelectionSignals) -> {
                                try {
                                    return new JSONObject(adSelectionSignals.toString());
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                            },
                            (json, key) -> {
                                try {
                                    return AdSelectionSignals.fromString(
                                            json.getJSONObject(key).toString());
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                            });

            field.mName = USER_BIDDING_SIGNALS_KEY;
            field.mValue = value;

            mFieldsMap.put(USER_BIDDING_SIGNALS_KEY, field);
        }
    }

    /**
     * @return if the {@code trustedBiddingData} {@link Field} is set
     */
    public boolean hasTrustedBiddingData() {
        return mFieldsMap.get(TRUSTED_BIDDING_DATA_KEY) != null;
    }

    /**
     * @return the {@code trustedBiddingData} {@link Field}
     */
    public TrustedBiddingData getTrustedBiddingData() {
        return (TrustedBiddingData) mFieldsMap.get(TRUSTED_BIDDING_DATA_KEY).mValue;
    }

    /** set the {@code trustedBiddingData} {@link Field} */
    public void setTrustedBiddingData(TrustedBiddingData value) {
        if (mFieldsMap.containsKey(TRUSTED_BIDDING_DATA_KEY)) {
            Field<TrustedBiddingData> field =
                    (Field<TrustedBiddingData>) mFieldsMap.get(TRUSTED_BIDDING_DATA_KEY);
            field.mValue = value;
        } else {
            Field<TrustedBiddingData> field =
                    new Field<>(
                            this::getTrustedBiddingDataAsJSONObject,
                            this::getTrustedBiddingDataFromJSONObject);

            field.mName = TRUSTED_BIDDING_DATA_KEY;
            field.mValue = value;

            mFieldsMap.put(TRUSTED_BIDDING_DATA_KEY, field);
        }
    }

    private JSONObject getTrustedBiddingDataAsJSONObject(TrustedBiddingData value) {
        try {
            JSONObject json = new JSONObject();
            json.put(TRUSTED_BIDDING_URI_KEY, value.getTrustedBiddingUri().toString());
            json.put(TRUSTED_BIDDING_KEYS_KEY, new JSONArray(value.getTrustedBiddingKeys()));
            return json;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private TrustedBiddingData getTrustedBiddingDataFromJSONObject(JSONObject json, String key) {
        return getValueFromJSONObject(
                json,
                key,
                (jsonObject, jsonKey) -> {
                    try {
                        JSONObject dataJsonObj = jsonObject.getJSONObject(jsonKey);

                        String uri =
                                JsonUtils.getStringFromJson(
                                        dataJsonObj,
                                        TRUSTED_BIDDING_URI_KEY,
                                        String.format(
                                                STRING_ERROR_FORMAT, TRUSTED_BIDDING_URI_KEY, key));
                        Uri parsedUri = Uri.parse(uri);

                        JSONArray keysJsonArray =
                                dataJsonObj.getJSONArray(TRUSTED_BIDDING_KEYS_KEY);
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
                                                        key)));
                            } catch (JSONException | NullPointerException exception) {
                                // Skip any keys that are malformed and continue to the next in the
                                // list; note that if the entire given list of keys is junk, then
                                // any existing trusted bidding keys are cleared from the custom
                                // audience
                                sLogger.v(
                                        SKIP_INVALID_JSON_TYPE_LOG_FORMAT,
                                        json.hashCode(),
                                        TRUSTED_BIDDING_KEYS_KEY,
                                        Optional.ofNullable(exception.getMessage())
                                                .orElse("<null>"));
                            }
                        }

                        TrustedBiddingData trustedBiddingData =
                                new TrustedBiddingData.Builder()
                                        .setTrustedBiddingUri(parsedUri)
                                        .setTrustedBiddingKeys(keysList)
                                        .build();

                        return trustedBiddingData;
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * @return if the {@code ads} {@link Field} is set
     */
    public boolean hasAds() {
        return mFieldsMap.get(ADS_KEY) != null;
    }

    /**
     * @return the {@code ads} {@link Field}
     */
    public List<AdData> getAds() {
        return (List<AdData>) mFieldsMap.get(ADS_KEY).mValue;
    }

    /** set the {@code ads} {@link Field} */
    public void setAds(List<AdData> value) {
        if (mFieldsMap.containsKey(ADS_KEY)) {
            Field<List<AdData>> field = (Field<List<AdData>>) mFieldsMap.get(ADS_KEY);
            field.mValue = value;
        } else {
            Field<List<AdData>> field =
                    new Field<>(this::getAdsAsJSONObject, this::getAdsFromJSONObject);

            field.mName = ADS_KEY;
            field.mValue = value;

            mFieldsMap.put(ADS_KEY, field);
        }
    }

    private JSONArray getAdsAsJSONObject(List<AdData> value) {
        try {
            JSONArray adsJson = new JSONArray();
            for (AdData ad : value) {
                JSONObject adJson = new JSONObject();

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
            return adsJson;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private JSONArray getAuctionServerRequestFlagsAsJSONArray(List<String> value) {
        return new JSONArray(value);
    }

    @SuppressWarnings("FormatStringAnnotation")
    private List<String> getAuctionServerRequestFlagsFromJSONObject(JSONObject json, String key) {
        return getValueFromJSONObject(
                json,
                key,
                (jsonObject, jsonKey) -> {
                    List<String> resultList = new ArrayList<>();
                    try {
                        JSONArray flagsJsonArray = jsonObject.getJSONArray(key);
                        for (int i = 0; i < flagsJsonArray.length(); i++) {
                            try {
                                resultList.add(
                                        JsonUtils.getStringFromJsonArrayAtIndex(
                                                flagsJsonArray,
                                                i,
                                                SKIP_INVALID_JSON_TYPE_LOG_FORMAT));
                            } catch (JSONException e) {
                                sLogger.v(
                                        SKIP_INVALID_JSON_TYPE_LOG_FORMAT,
                                        jsonObject.hashCode(),
                                        key,
                                        Optional.ofNullable(e.getMessage()).orElse("<null>"));
                            }
                        }
                    } catch (JSONException e) {
                        // Ignore since we don't want to fail if there is an issue with this
                        // optional field
                        sLogger.v(
                                FIELD_NOT_FOUND_LOG_FORMAT,
                                jsonObject.hashCode(),
                                key,
                                Optional.ofNullable(e.getMessage()).orElse("<null>"));
                    }
                    return resultList;
                });
    }

    private List<AdData> getAdsFromJSONObject(JSONObject json, String key) {
        return getValueFromJSONObject(
                json,
                key,
                (jsonObject, jsonKey) -> {
                    try {
                        JSONArray adsJsonArray = jsonObject.getJSONArray(key);
                        int adsListLength = adsJsonArray.length();
                        List<AdData> adsList = new ArrayList<>();
                        for (int i = 0; i < adsListLength; i++) {
                            try {
                                JSONObject adDataJsonObj = adsJsonArray.getJSONObject(i);

                                // Note: getString() coerces values to be strings; use get() instead
                                Object uri = adDataJsonObj.get(RENDER_URI_KEY);
                                if (!(uri instanceof String)) {
                                    throw new JSONException(
                                            "Unexpected format parsing "
                                                    + RENDER_URI_KEY
                                                    + " in "
                                                    + key);
                                }
                                Uri parsedUri = Uri.parse(Objects.requireNonNull((String) uri));

                                String metadata =
                                        Objects.requireNonNull(
                                                        adDataJsonObj.getJSONObject(METADATA_KEY))
                                                .toString();

                                DBAdData.Builder adDataBuilder =
                                        new DBAdData.Builder()
                                                .setRenderUri(parsedUri)
                                                .setMetadata(metadata);

                                mReadFiltersFromJsonStrategy.readFilters(
                                        adDataBuilder, adDataJsonObj);
                                mReadAdRenderIdFromJsonStrategy.readId(
                                        adDataBuilder, adDataJsonObj);

                                DBAdData dbAdData = adDataBuilder.build();
                                AdData adData =
                                        new AdData.Builder()
                                                .setMetadata(dbAdData.getMetadata())
                                                .setRenderUri(dbAdData.getRenderUri())
                                                .setAdCounterKeys(dbAdData.getAdCounterKeys())
                                                .setAdFilters(dbAdData.getAdFilters())
                                                .setAdRenderId(dbAdData.getAdRenderId())
                                                .build();

                                adsList.add(adData);
                            } catch (JSONException
                                    | NullPointerException
                                    | IllegalArgumentException exception) {
                                // Skip any ads that are malformed and continue to the next in the
                                // list; note that if the entire given list of ads is junk, then any
                                // existing ads are cleared from the custom audience
                                sLogger.v(
                                        SKIP_INVALID_JSON_TYPE_LOG_FORMAT,
                                        jsonObject.hashCode(),
                                        key,
                                        Optional.ofNullable(exception.getMessage())
                                                .orElse("<null>"));
                            }
                        }
                        return adsList;
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private String getStringFromJSONObject(JSONObject json, String key) {
        return getValueFromJSONObject(
                json,
                key,
                (jsonObject, jsonKey) -> {
                    try {
                        return JsonUtils.getStringFromJson(
                                json,
                                key,
                                String.format(STRING_ERROR_FORMAT, key, json.hashCode()));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private Instant getInstantFromJSONObject(JSONObject json, String key) {
        return getValueFromJSONObject(
                json,
                key,
                (jsonObject, jsonKey) -> {
                    try {
                        return Instant.ofEpochMilli(jsonObject.getLong(jsonKey));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private <T> T getValueFromJSONObject(
            JSONObject json, String key, BiFunction<JSONObject, String, T> fromJsonObject) {
        if (json.has(key)) {
            sLogger.v(FIELD_FOUND_LOG_FORMAT, json.hashCode(), key);
            return fromJsonObject.apply(json, key);
        } else {
            sLogger.v(FIELD_NOT_FOUND_LOG_FORMAT, json.hashCode(), ACTIVATION_TIME_KEY);
            return null;
        }
    }
}
