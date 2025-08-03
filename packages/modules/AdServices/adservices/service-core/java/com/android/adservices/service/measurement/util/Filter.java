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

package com.android.adservices.service.measurement.util;

import android.annotation.NonNull;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.FilterValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Filtering utilities for measurement. */
public final class Filter {
    private final Flags mFlags;

    public Filter(@NonNull Flags flags) {
        mFlags = flags;
    }

    /**
     * Checks whether source filter and trigger filter are matched. When a key is only present in
     * source or trigger, ignore that key. When a key is present both in source and trigger, the key
     * matches if the intersection of values is not empty.
     *
     * @param sourceFilter the {@code FilterMap} in attribution source.
     * @param triggerFilters a list of {@code FilterMap}, the trigger filter set.
     * @param isFilter true for filters, false for not_filters.
     * @return return true when all keys shared by source filter and trigger filter are matched.
     */
    public boolean isFilterMatch(
            FilterMap sourceFilter, List<FilterMap> triggerFilters, boolean isFilter) {
        if (sourceFilter.isEmpty(mFlags) || triggerFilters.isEmpty()) {
            return true;
        }
        for (FilterMap filterMap : triggerFilters) {
            if (isFilterMatch(sourceFilter, filterMap, isFilter)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFilterMatch(
            FilterMap sourceFilter, FilterMap triggerFilter, boolean isFilter) {
        return mFlags.getMeasurementEnableLookbackWindowFilter()
                ? isFilterMatchWithLookbackWindow(sourceFilter, triggerFilter, isFilter)
                : isFilterMatchV1(sourceFilter, triggerFilter, isFilter);
    }

    private boolean isFilterMatchV1(
            FilterMap sourceFilter, FilterMap triggerFilter, boolean isFilter) {
        for (String key : triggerFilter.getAttributionFilterMap().keySet()) {
            if (!sourceFilter.getAttributionFilterMap().containsKey(key)) {
                continue;
            }
            // Finds the intersection of two value lists.
            List<String> sourceValues = sourceFilter.getAttributionFilterMap().get(key);
            List<String> triggerValues = triggerFilter.getAttributionFilterMap().get(key);
            if (!matchFilterValues(sourceValues, triggerValues, isFilter)) {
                return false;
            }
        }
        return true;
    }

    private boolean isFilterMatchWithLookbackWindow(
            FilterMap sourceFilter, FilterMap triggerFilter, boolean isFilter) {
        for (String key : triggerFilter.getAttributionFilterMapWithLongValue().keySet()) {
            if (!sourceFilter.getAttributionFilterMapWithLongValue().containsKey(key)) {
                continue;
            }
            FilterValue filterValue = triggerFilter.getAttributionFilterMapWithLongValue().get(key);
            switch (filterValue.kind()) {
                case STRING_LIST_VALUE:
                    // Finds the intersection of two value lists.
                    List<String> sourceValues =
                            sourceFilter
                                    .getAttributionFilterMapWithLongValue()
                                    .get(key)
                                    .stringListValue();
                    List<String> triggerValues =
                            triggerFilter
                                    .getAttributionFilterMapWithLongValue()
                                    .get(key)
                                    .stringListValue();
                    if (!matchFilterValues(sourceValues, triggerValues, isFilter)) {
                        return false;
                    }
                    break;
                case LONG_VALUE:
                    if (!sourceFilter.getAttributionFilterMapWithLongValue().containsKey(key)
                            || !FilterMap.LOOKBACK_WINDOW.equals(key)) {
                        continue;
                    }
                    long lookbackWindow = triggerFilter.getLongValue(key);
                    long durationFromSource = sourceFilter.getLongValue(key);
                    boolean lessOrEqual = durationFromSource <= lookbackWindow;
                    if (lessOrEqual != isFilter) {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    private boolean matchFilterValues(
            List<String> sourceValues, List<String> triggerValues, boolean isFilter) {
        if (triggerValues.isEmpty()) {
            return isFilter ? sourceValues.isEmpty() : !sourceValues.isEmpty();
        }
        Set<String> intersection = new HashSet<>(sourceValues);
        intersection.retainAll(triggerValues);
        return isFilter ? !intersection.isEmpty() : intersection.isEmpty();
    }

    /**
     * Deserializes the provided {@link JSONArray} of filters into filter set.
     *
     * @param filters serialized filter set
     * @return deserialized filter set
     * @throws JSONException if the deserialization fails
     */
    @NonNull
    public List<FilterMap> deserializeFilterSet(@NonNull JSONArray filters) throws JSONException {
        List<FilterMap> filterSet = new ArrayList<>();
        for (int i = 0; i < filters.length(); i++) {
            FilterMap filterMap =
                    new FilterMap.Builder()
                            .buildFilterData(filters.getJSONObject(i), mFlags)
                            .build();
            filterSet.add(filterMap);
        }
        return filterSet;
    }

    /**
     * Builds {@link JSONArray} our of the list of {@link List<FilterMap>} provided by serializing
     * it recursively.
     *
     * @param filterMaps to be serialized
     * @return serialized filter maps
     */
    @NonNull
    public JSONArray serializeFilterSet(@NonNull List<FilterMap> filterMaps) {
        JSONArray serializedFilterMaps = new JSONArray();
        for (FilterMap filter : filterMaps) {
            serializedFilterMaps.put(filter.serializeAsJson(mFlags));
        }
        return serializedFilterMaps;
    }

    /**
     * Filters can be available in either {@link JSONObject} format or {@link JSONArray} format. For
     * consistency across the board, this method wraps the {@link JSONObject} into {@link
     * JSONArray}.
     *
     * @param json json where to look for the filter object
     * @param key key with which the filter object is associated
     * @return wrapped {@link JSONArray}
     * @throws JSONException when creation of {@link JSONArray} fails
     */
    @NonNull
    public static JSONArray maybeWrapFilters(@NonNull JSONObject json, @NonNull String key)
            throws JSONException {
        JSONObject maybeFilterMap = json.optJSONObject(key);
        if (maybeFilterMap != null) {
            JSONArray filterSet = new JSONArray();
            filterSet.put(maybeFilterMap);
            return filterSet;
        }
        return json.getJSONArray(key);
    }
}
