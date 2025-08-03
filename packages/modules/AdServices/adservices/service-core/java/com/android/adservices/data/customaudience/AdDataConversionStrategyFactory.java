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

package com.android.adservices.data.customaudience;

import android.adservices.common.AdData;
import android.adservices.common.AdFilters;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.common.FledgeRoomConverters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Factory for AdDataConversionStrategys */
public class AdDataConversionStrategyFactory {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final String RENDER_URI_FIELD_NAME = "renderUri";
    private static final String METADATA_FIELD_NAME = "metadata";
    private static final String AD_COUNTER_KEYS_FIELD_NAME = "adCounterKeys";
    private static final String AD_FILTERS_FIELD_NAME = "adFilters";
    private static final String AD_RENDER_ID_FIELD_NAME = "adRenderId";

    private static class FilteringEnabledConversionStrategy
            implements AdDataOptionalConversionStrategy {
        public void toJson(@NonNull DBAdData adData, @NonNull JSONObject toReturn)
                throws JSONException {
            if (!adData.getAdCounterKeys().isEmpty()) {
                JSONArray jsonCounterKeys = new JSONArray(adData.getAdCounterKeys());
                toReturn.put(AD_COUNTER_KEYS_FIELD_NAME, jsonCounterKeys);
            }
            if (adData.getAdFilters() != null) {
                toReturn.put(AD_FILTERS_FIELD_NAME, adData.getAdFilters().toJson());
            }
        }

        public void fromJson(@NonNull JSONObject json, @NonNull DBAdData.Builder adDataBuilder)
                throws JSONException {
            Set<Integer> adCounterKeys = new HashSet<>();
            if (json.has(AD_COUNTER_KEYS_FIELD_NAME)) {
                JSONArray counterKeys = json.getJSONArray(AD_COUNTER_KEYS_FIELD_NAME);
                for (int i = 0; i < counterKeys.length(); i++) {
                    adCounterKeys.add(counterKeys.getInt(i));
                }
            }
            AdFilters adFilters = null;
            if (json.has(AD_FILTERS_FIELD_NAME)) {
                adFilters = AdFilters.fromJson(json.getJSONObject(AD_FILTERS_FIELD_NAME));
            }
            adDataBuilder.setAdCounterKeys(adCounterKeys).setAdFilters(adFilters);
        }

        @Override
        public void fromServiceObject(
                @NonNull AdData parcelable, @NonNull DBAdData.Builder adDataBuilder) {
            adDataBuilder.setAdCounterKeys(parcelable.getAdCounterKeys());
            adDataBuilder.setAdFilters(parcelable.getAdFilters());
        }
    }

    private static class AdRenderIdEnabledConversionStrategy
            implements AdDataOptionalConversionStrategy {
        public void toJson(@NonNull DBAdData adData, @NonNull JSONObject toReturn)
                throws JSONException {
            if (adData.getAdRenderId() != null) {
                toReturn.put(AD_RENDER_ID_FIELD_NAME, adData.getAdRenderId());
            }
        }

        public void fromJson(@NonNull JSONObject json, @NonNull DBAdData.Builder adDataBuilder)
                throws JSONException {
            if (json.has(AD_RENDER_ID_FIELD_NAME)) {
                adDataBuilder.setAdRenderId(json.getString(AD_RENDER_ID_FIELD_NAME));
            }
        }

        @Override
        public void fromServiceObject(
                @NonNull AdData parcelable, @NonNull DBAdData.Builder adDataBuilder) {
            sLogger.v("Setting ad render id " + parcelable.getAdRenderId());
            adDataBuilder.setAdRenderId(parcelable.getAdRenderId());
        }
    }

    /**
     * Conversion strategy with no optional feature enabled. This is the baseline of all
     * conversions.
     */
    private static class BaseConversionStrategy implements AdDataConversionStrategy {
        /**
         * Serialize {@link DBAdData} to {@link JSONObject}, but ignore filter fields.
         *
         * @param adData the {@link DBAdData} object to serialize
         * @return the json serialization of the AdData object
         */
        public JSONObject toJson(DBAdData adData) throws JSONException {
            return new org.json.JSONObject()
                    .put(
                            RENDER_URI_FIELD_NAME,
                            FledgeRoomConverters.serializeUri(adData.getRenderUri()))
                    .put(METADATA_FIELD_NAME, adData.getMetadata());
        }

        /**
         * Deserialize {@link DBAdData} to {@link JSONObject} but ignore filter fields.
         *
         * @param json the {@link JSONObject} object to deserialize
         * @return the {@link DBAdData} deserialized from the json
         */
        public DBAdData.Builder fromJson(JSONObject json) throws JSONException {
            String renderUriString = json.getString(RENDER_URI_FIELD_NAME);
            String metadata = json.getString(METADATA_FIELD_NAME);
            Uri renderUri = FledgeRoomConverters.deserializeUri(renderUriString);
            return new DBAdData.Builder().setRenderUri(renderUri).setMetadata(metadata);
        }

        /**
         * Parse parcelable {@link AdData} to storage model {@link DBAdData}.
         *
         * @param parcelable the service model.
         * @return storage model
         */
        @NonNull
        @Override
        public DBAdData.Builder fromServiceObject(@NonNull AdData parcelable) {
            return new DBAdData.Builder()
                    .setRenderUri(parcelable.getRenderUri())
                    .setMetadata(parcelable.getMetadata())
                    .setAdCounterKeys(Collections.emptySet());
        }
    }

    private static class CompositeConversionStrategy implements AdDataConversionStrategy {
        private final AdDataConversionStrategy mBaseStrategy;
        private final List<AdDataOptionalConversionStrategy> mOptionalStrategies;

        CompositeConversionStrategy(AdDataConversionStrategy baseStrategy) {
            this.mBaseStrategy = baseStrategy;
            this.mOptionalStrategies = new ArrayList<>();
        }

        @Override
        public JSONObject toJson(DBAdData adData) throws JSONException {
            JSONObject result = mBaseStrategy.toJson(adData);
            for (AdDataOptionalConversionStrategy optionalStrategy : mOptionalStrategies) {
                optionalStrategy.toJson(adData, result);
            }
            return result;
        }

        @Override
        public DBAdData.Builder fromJson(JSONObject json) throws JSONException {
            DBAdData.Builder result = mBaseStrategy.fromJson(json);
            for (AdDataOptionalConversionStrategy optionalStrategy : mOptionalStrategies) {
                optionalStrategy.fromJson(json, result);
            }
            return result;
        }

        @NonNull
        @Override
        public DBAdData.Builder fromServiceObject(@NonNull AdData parcelable) {
            DBAdData.Builder result = mBaseStrategy.fromServiceObject(parcelable);
            for (AdDataOptionalConversionStrategy optionalStrategy : mOptionalStrategies) {
                optionalStrategy.fromServiceObject(parcelable, result);
            }
            return result;
        }

        @NonNull
        public CompositeConversionStrategy composeWith(AdDataOptionalConversionStrategy strategy) {
            mOptionalStrategies.add(strategy);
            return this;
        }
    }

    /**
     * Returns the appropriate AdDataConversionStrategy based whether filtering is enabled
     *
     * @param filteringEnabled Should be true if filtering is enabled.
     * @return An implementation of AdDataConversionStrategy
     */
    public static AdDataConversionStrategy getAdDataConversionStrategy(
            boolean filteringEnabled, boolean adRenderIdEnabled) {

        CompositeConversionStrategy result =
                new CompositeConversionStrategy(new BaseConversionStrategy());

        if (filteringEnabled) {
            sLogger.v("Adding Filtering Conversion Strategy to Composite Conversion Strategy");
            result.composeWith(new FilteringEnabledConversionStrategy());
        }
        if (adRenderIdEnabled) {
            sLogger.v("Adding Ad Render Conversion Strategy to Composite Conversion Strategy");
            result.composeWith(new AdRenderIdEnabledConversionStrategy());
        }
        return result;
    }
}
