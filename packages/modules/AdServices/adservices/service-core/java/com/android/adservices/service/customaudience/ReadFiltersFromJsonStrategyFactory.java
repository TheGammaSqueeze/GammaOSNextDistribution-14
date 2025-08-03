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

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_COUNTERS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_FILTERS_KEY;

import android.adservices.common.AdFilters;

import com.android.adservices.data.common.DBAdData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
/** Factory for ReadFiltersFromJsonStrategys */
public class ReadFiltersFromJsonStrategyFactory {

    private static class FilteringEnabledStrategy implements ReadFiltersFromJsonStrategy {

        /**
         * Adds filtering fields to the provided AdData builder.
         *
         * @param adDataBuilder the AdData builder to modify.
         * @param adDataJsonObj the AdData JSON to extract from
         * @throws JSONException if the key is found but the schema is incorrect
         * @throws NullPointerException if the key found by the field is null
         */
        @Override
        public void readFilters(DBAdData.Builder adDataBuilder, JSONObject adDataJsonObj)
                throws JSONException, NullPointerException, IllegalArgumentException {
            Set<Integer> adCounterKeys = new HashSet<>();
            if (adDataJsonObj.has(AD_COUNTERS_KEY)) {
                JSONArray adCounterKeysJson = adDataJsonObj.getJSONArray(AD_COUNTERS_KEY);
                for (int j = 0; j < adCounterKeysJson.length(); j++) {
                    adCounterKeys.add(adCounterKeysJson.getInt(j));
                }
            }
            AdFilters adFilters = null;
            if (adDataJsonObj.has(AD_FILTERS_KEY)) {
                adFilters = AdFilters.fromJson(adDataJsonObj.getJSONObject(AD_FILTERS_KEY));
            }
            adDataBuilder.setAdCounterKeys(adCounterKeys).setAdFilters(adFilters);
        }
    }

    private static class FilteringDisabledStrategy implements ReadFiltersFromJsonStrategy {
        /**
         * Does nothing.
         *
         * @param adDataBuilder unused
         * @param adDataJsonObj unused
         */
        @Override
        public void readFilters(DBAdData.Builder adDataBuilder, JSONObject adDataJsonObj) {}
    }

    /**
     * Returns the appropriate ReadFiltersFromJsonStrategy based whether filtering is enabled
     *
     * @param filteringEnabled Should be true if filtering is enabled.
     * @return An implementation of ReadFiltersFromJsonStrategy
     */
    public static ReadFiltersFromJsonStrategy getStrategy(boolean filteringEnabled) {
        if (filteringEnabled) {
            return new FilteringEnabledStrategy();
        }
        return new FilteringDisabledStrategy();
    }
}
