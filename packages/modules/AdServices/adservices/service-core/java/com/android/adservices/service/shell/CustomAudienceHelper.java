/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.shell;

import static com.android.adservices.shared.util.Preconditions.checkState;

import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class CustomAudienceHelper {
    @VisibleForTesting public static final String NAME = "name";
    @VisibleForTesting public static final String OWNER = "owner";
    @VisibleForTesting public static final String BUYER = "buyer";
    @VisibleForTesting public static final String IS_DEBUGGABLE = "is_debuggable";
    @VisibleForTesting public static final String ACTIVATION_TIME = "activation_time";
    @VisibleForTesting public static final String CREATION_TIME = "creation_time";
    @VisibleForTesting public static final String EXPIRATION_TIME = "expiration_time";
    @VisibleForTesting public static final String UPDATED_TIME = "updated_time";
    @VisibleForTesting public static final String BIDDING_LOGIC_URI = "bidding_logic_uri";
    @VisibleForTesting public static final String USER_BIDDING_SIGNALS = "user_bidding_signals";
    @VisibleForTesting public static final String TRUSTED_BIDDING_DATA = "trusted_bidding_data";
    @VisibleForTesting public static final String ADS = "ads";
    @VisibleForTesting public static final String TRUSTED_BIDDING_DATA_URI = "uri";
    @VisibleForTesting public static final String ADS_AD_COUNTER_KEYS = "ad_counter_keys";
    @VisibleForTesting public static final String ADS_AD_FILTERS = "ad_filters";
    @VisibleForTesting public static final String ADS_KEYS = "keys";
    @VisibleForTesting public static final String AD_AD_RENDER_URI = "render_uri";
    @VisibleForTesting public static final String AD_METADATA = "metadata";
    @VisibleForTesting public static final String AD_AD_RENDER_ID = "ad_render_id";
    @VisibleForTesting public static final String DAILY_UPDATE = "daily_update";
    @VisibleForTesting public static final String DAILY_UPDATE_URI = "uri";

    @VisibleForTesting
    public static final String DAILY_UPDATE_ELIGIBLE_UPDATE_TIME = "eligible_update_time";

    @VisibleForTesting
    public static final String DAILY_UPDATE_NUM_VALIDATION_FAILURES = "num_validation_failures";

    @VisibleForTesting
    public static final String DAILY_UPDATE_NUM_TIMEOUT_FAILURES = "num_timeout_failures";

    static JSONObject toJson(
            @NonNull DBCustomAudience customAudience,
            @NonNull DBCustomAudienceBackgroundFetchData customAudienceBackgroundFetchData)
            throws JSONException {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(customAudienceBackgroundFetchData);
        return new JSONObject()
                .put(NAME, customAudience.getName())
                .put(OWNER, customAudience.getOwner())
                .put(BUYER, customAudience.getBuyer())
                .put(
                        DAILY_UPDATE,
                        new JSONObject()
                                .put(
                                        DAILY_UPDATE_URI,
                                        customAudienceBackgroundFetchData.getDailyUpdateUri())
                                .put(
                                        DAILY_UPDATE_ELIGIBLE_UPDATE_TIME,
                                        customAudienceBackgroundFetchData.getEligibleUpdateTime())
                                .put(
                                        DAILY_UPDATE_NUM_VALIDATION_FAILURES,
                                        customAudienceBackgroundFetchData
                                                .getNumValidationFailures())
                                .put(
                                        DAILY_UPDATE_NUM_TIMEOUT_FAILURES,
                                        customAudienceBackgroundFetchData.getNumTimeoutFailures()))
                .put(IS_DEBUGGABLE, customAudience.isDebuggable())
                .put(CREATION_TIME, customAudience.getCreationTime())
                .put(ACTIVATION_TIME, customAudience.getActivationTime())
                .put(EXPIRATION_TIME, customAudience.getExpirationTime())
                .put(UPDATED_TIME, customAudience.getLastAdsAndBiddingDataUpdatedTime())
                .put(BIDDING_LOGIC_URI, customAudience.getBiddingLogicUri().toString())
                .put(USER_BIDDING_SIGNALS, customAudience.getUserBiddingSignals())
                .put(DAILY_UPDATE_URI, Uri.EMPTY) // TODO(b/322976190): Remove call to Uri.EMPTY.
                .put(
                        TRUSTED_BIDDING_DATA,
                        getJsonFromTrustedBiddingData(customAudience.getTrustedBiddingData()))
                .put(ADS, getJsonArrayFromAdsList(customAudience.getAds()));
    }

    private static JSONObject getJsonFromTrustedBiddingData(
            @Nullable DBTrustedBiddingData trustedBiddingData) throws JSONException {
        if (Objects.isNull(trustedBiddingData)) {
            return null;
        }
        JSONObject jsonObject =
                new JSONObject()
                        .put(TRUSTED_BIDDING_DATA_URI, trustedBiddingData.getUri().toString());
        JSONArray keys = new JSONArray();
        trustedBiddingData.getKeys().forEach(keys::put);
        return jsonObject.put(ADS_KEYS, keys);
    }

    private static JSONArray getJsonArrayFromAdsList(@Nullable List<DBAdData> ads)
            throws JSONException {
        JSONArray array = new JSONArray();
        if (Objects.isNull(ads)) {
            return array;
        }
        for (DBAdData ad : ads) {
            array.put(getJsonFromAd(ad));
        }
        return array;
    }

    private static JSONObject getJsonFromAd(DBAdData adData) throws JSONException {
        JSONObject jsonObject =
                new JSONObject()
                        .put(AD_AD_RENDER_URI, adData.getRenderUri())
                        .put(AD_AD_RENDER_ID, adData.getAdRenderId())
                        .put(AD_METADATA, adData.getMetadata())
                        .put(
                                ADS_AD_COUNTER_KEYS,
                                getJsonArrayFromIntegerSet(adData.getAdCounterKeys()));
        if (adData.getAdFilters() != null) {
            jsonObject.put(ADS_AD_FILTERS, adData.getAdFilters().toJson().toString());
        }
        return jsonObject;
    }

    static DBCustomAudience getCustomAudienceFromJson(@NonNull JSONObject jsonObject)
            throws JSONException {
        return new DBCustomAudience.Builder()
                .setName(jsonObject.getString(NAME))
                .setOwner(jsonObject.getString(OWNER))
                .setBuyer(AdTechIdentifier.fromString(jsonObject.getString(BUYER)))
                .setDebuggable(jsonObject.getBoolean(IS_DEBUGGABLE))
                .setCreationTime(Instant.parse(jsonObject.getString(CREATION_TIME)))
                .setActivationTime(Instant.parse(jsonObject.getString(ACTIVATION_TIME)))
                .setExpirationTime(Instant.parse(jsonObject.getString(EXPIRATION_TIME)))
                .setLastAdsAndBiddingDataUpdatedTime(
                        Instant.parse(jsonObject.getString(UPDATED_TIME)))
                .setBiddingLogicUri(Uri.parse(jsonObject.getString(BIDDING_LOGIC_URI)))
                .setUserBiddingSignals(
                        AdSelectionSignals.fromString(jsonObject.getString(USER_BIDDING_SIGNALS)))
                .setTrustedBiddingData(
                        getTrustedBiddingDataFromJson(
                                jsonObject.getJSONObject(TRUSTED_BIDDING_DATA)))
                .setAds(getAdsFromJsonArray(jsonObject.getJSONArray(ADS)))
                .build();
    }

    static DBCustomAudienceBackgroundFetchData getCustomAudienceBackgroundFetchDataFromJson(
            @NonNull JSONObject jsonObject) throws JSONException {
        checkState(!jsonObject.isNull(DAILY_UPDATE), "`daily_update` field is not present.");
        JSONObject dailyUpdateJsonObject = jsonObject.getJSONObject(DAILY_UPDATE);
        return DBCustomAudienceBackgroundFetchData.builder()
                .setName(jsonObject.getString(NAME))
                .setOwner(jsonObject.getString(OWNER))
                .setBuyer(AdTechIdentifier.fromString(jsonObject.getString(BUYER)))
                .setIsDebuggable(jsonObject.getBoolean(IS_DEBUGGABLE))
                .setDailyUpdateUri(Uri.parse(dailyUpdateJsonObject.getString(DAILY_UPDATE_URI)))
                .setEligibleUpdateTime(
                        Instant.parse(
                                dailyUpdateJsonObject.getString(DAILY_UPDATE_ELIGIBLE_UPDATE_TIME)))
                .setNumTimeoutFailures(
                        dailyUpdateJsonObject.getInt(DAILY_UPDATE_NUM_TIMEOUT_FAILURES))
                .setNumValidationFailures(
                        dailyUpdateJsonObject.getInt(DAILY_UPDATE_NUM_VALIDATION_FAILURES))
                .build();
    }

    private static DBTrustedBiddingData getTrustedBiddingDataFromJson(JSONObject jsonObject)
            throws JSONException {
        return new DBTrustedBiddingData.Builder()
                .setUri(Uri.parse(jsonObject.getString(TRUSTED_BIDDING_DATA_URI)))
                .setKeys(getStringsFromJsonArray(jsonObject.getJSONArray(ADS_KEYS)))
                .build();
    }

    private static ImmutableList<DBAdData> getAdsFromJsonArray(JSONArray jsonArray)
            throws JSONException {
        ImmutableList.Builder<DBAdData> builder = ImmutableList.builder();
        for (int i = 0; i < jsonArray.length(); i++) {
            builder.add(getAdFromJson(jsonArray.getJSONObject(i)));
        }
        return builder.build();
    }

    private static DBAdData getAdFromJson(JSONObject jsonObject) throws JSONException {
        DBAdData.Builder builder =
                new DBAdData.Builder()
                        .setRenderUri(Uri.parse(jsonObject.getString(AD_AD_RENDER_URI)))
                        .setMetadata(jsonObject.getString(AD_METADATA));
        if (jsonObject.has(AD_AD_RENDER_ID)) {
            builder.setAdRenderId(jsonObject.getString(AD_AD_RENDER_ID));
        }
        if (jsonObject.has(ADS_AD_COUNTER_KEYS)) {
            builder.setAdCounterKeys(
                    getIntegersFromJsonArray(jsonObject.getJSONArray(ADS_AD_COUNTER_KEYS)));
        }
        if (jsonObject.has(ADS_AD_FILTERS)) {
            builder.setAdFilters(
                    AdFilters.fromJson(new JSONObject(jsonObject.getString(ADS_AD_FILTERS))));
        }
        return builder.build();
    }

    private static ImmutableList<String> getStringsFromJsonArray(JSONArray jsonArray)
            throws JSONException {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (int i = 0; i < jsonArray.length(); i++) {
            builder.add(jsonArray.getString(i));
        }
        return builder.build();
    }

    private static JSONArray getJsonArrayFromIntegerSet(Set<Integer> integerSet) {
        JSONArray jsonArray = new JSONArray();
        for (Integer i : integerSet) {
            jsonArray.put(i);
        }
        return jsonArray;
    }

    private static ImmutableSet<Integer> getIntegersFromJsonArray(@NonNull JSONArray jsonArray)
            throws JSONException {
        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        for (int i = 0; i < jsonArray.length(); i++) {
            builder.add(jsonArray.getInt(i));
        }
        return builder.build();
    }
}
