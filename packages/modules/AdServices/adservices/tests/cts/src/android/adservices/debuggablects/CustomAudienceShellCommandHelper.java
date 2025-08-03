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

package android.adservices.debuggablects;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdData;
import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;

final class CustomAudienceShellCommandHelper {
    private static final String NAME = "name";
    private static final String BUYER = "buyer";
    private static final String ACTIVATION_TIME = "activation_time";
    private static final String EXPIRATION_TIME = "expiration_time";
    private static final String BIDDING_LOGIC_URI = "bidding_logic_uri";
    private static final String USER_BIDDING_SIGNALS = "user_bidding_signals";
    private static final String TRUSTED_BIDDING_DATA = "trusted_bidding_data";
    private static final String DAILY_UPDATE = "daily_update";
    private static final String DAILY_UPDATE_URI = "uri";
    private static final String ADS = "ads";
    private static final String ADS_URI = "uri";
    private static final String ADS_KEYS = "keys";
    private static final String ADS_AD_COUNTER_KEYS = "ad_counter_keys";
    private static final String ADS_AD_FILTERS = "ad_filters";
    private static final String AD_AD_RENDER_URI = "render_uri";
    private static final String AD_METADATA = "metadata";
    private static final String AD_AD_RENDER_ID = "ad_render_id";
    private static final String ELIGIBLE_UPDATE_TIME = "eligible_update_time";
    private static final String NUM_VALIDATION_FAILURES = "num_validation_failures";
    private static final String NUM_TIMEOUT_FAILURES = "num_timeout_failures";

    static CustomAudience fromJson(@NonNull JSONObject jsonObject) throws JSONException {
        return new CustomAudience.Builder()
                .setName(jsonObject.getString(NAME))
                .setBuyer(AdTechIdentifier.fromString(jsonObject.getString(BUYER)))
                .setExpirationTime(Instant.parse(jsonObject.getString(EXPIRATION_TIME)))
                .setBiddingLogicUri(Uri.parse(jsonObject.getString(BIDDING_LOGIC_URI)))
                .setTrustedBiddingData(
                        getTrustedBiddingDataFromJson(
                                jsonObject.getJSONObject(TRUSTED_BIDDING_DATA)))
                .setUserBiddingSignals(
                        AdSelectionSignals.fromString(jsonObject.getString(USER_BIDDING_SIGNALS)))
                .setAds(getAdsFromJsonArray(jsonObject.getJSONArray(ADS)))
                .setDailyUpdateUri(
                        jsonObject.isNull(DAILY_UPDATE)
                                ? Uri.EMPTY
                                : Uri.parse(
                                        jsonObject
                                                .getJSONObject(DAILY_UPDATE)
                                                .getString(DAILY_UPDATE_URI)))
                .build();
    }

    // Activation time is inconsistent so only parse for format correctness.
    // TODO(b/327205505): Replace this code with a Truth matcher.
    static void verifyActivationTime(JSONObject customAudience) throws JSONException {
        Instant.parse(customAudience.getString(ACTIVATION_TIME));
    }

    // Background fetch data is not part of the public API except as exposed via CLI commands,
    // therefore specific assertions cannot be made as to the expected state beyond expecting a
    // certain format (valid date and integers).
    // TODO(b/327205505): Replace this code with a Truth matcher.
    static void verifyBackgroundFetchData(
            JSONObject jsonObject, int expectedTimeoutFailures, int expectedValidationFailures)
            throws JSONException {
        JSONObject customAudienceBackgroundFetchData = jsonObject.getJSONObject(DAILY_UPDATE);
        Instant.parse(customAudienceBackgroundFetchData.getString(ELIGIBLE_UPDATE_TIME));
        assertThat(customAudienceBackgroundFetchData.getInt(NUM_TIMEOUT_FAILURES))
                .isEqualTo(expectedTimeoutFailures);
        assertThat(customAudienceBackgroundFetchData.getInt(NUM_VALIDATION_FAILURES))
                .isEqualTo(expectedValidationFailures);
    }

    private static TrustedBiddingData getTrustedBiddingDataFromJson(JSONObject jsonObject)
            throws JSONException {
        return new TrustedBiddingData.Builder()
                .setTrustedBiddingUri(Uri.parse(jsonObject.getString(ADS_URI)))
                .setTrustedBiddingKeys(getStringsFromJsonArray(jsonObject.getJSONArray(ADS_KEYS)))
                .build();
    }

    private static ImmutableList<String> getStringsFromJsonArray(JSONArray jsonArray)
            throws JSONException {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (int i = 0; i < jsonArray.length(); i++) {
            builder.add(jsonArray.getString(i));
        }
        return builder.build();
    }

    private static ImmutableList<AdData> getAdsFromJsonArray(JSONArray jsonArray)
            throws JSONException {
        ImmutableList.Builder<AdData> builder = ImmutableList.builder();
        for (int i = 0; i < jsonArray.length(); i++) {
            builder.add(getAdFromJson(jsonArray.getJSONObject(i)));
        }
        return builder.build();
    }

    private static AdData getAdFromJson(JSONObject jsonObject) throws JSONException {
        AdData.Builder builder =
                new AdData.Builder()
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

    private static ImmutableSet<Integer> getIntegersFromJsonArray(JSONArray jsonArray)
            throws JSONException {
        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        for (int i = 0; i < jsonArray.length(); i++) {
            builder.add(jsonArray.getInt(i));
        }
        return builder.build();
    }
}
