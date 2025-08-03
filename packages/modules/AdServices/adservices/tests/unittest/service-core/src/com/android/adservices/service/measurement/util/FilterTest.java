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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.FilterMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class FilterTest {
    @Mock private Flags mFlags;

    @Before
    public void setup() {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
    }

    @Test
    public void testIsFilterMatch_filterSet_nonEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore.one"));
        triggerFilterMap1.put("product", Arrays.asList("12345", "2345"));
        triggerFilterMap1.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertTrue(
                new Filter(mFlags)
                        .isFilterMatch(
                                sourceFilter, List.of(triggerFilter1, triggerFilter2), true));
    }

    @Test
    public void testIsFilterMatch_nonEmptyValues_returnsTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertTrue(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatchV2_insideLookbackWindow_returnsTrue() {
        FilterMap sourceFilter =
                new FilterMap.Builder()
                        .addStringListValue(
                                "conversion_subdomain", List.of("electronics.megastore"))
                        .addStringListValue("product", List.of("1234", "234"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 100L)
                        .build();

        FilterMap triggerFilter =
                new FilterMap.Builder()
                        .addStringListValue(
                                "conversion_subdomain", List.of("electronics.megastore"))
                        .addStringListValue("product", List.of("1234", "2345"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 200L)
                        .build();

        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertTrue(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatchV2_emptySource_returnsTrue() {
        FilterMap sourceFilter = new FilterMap.Builder().build();
        // Sets unmatching values in V1 map, should be ignored.
        sourceFilter.getAttributionFilterMap().put("conversion_subdomain", List.of("electronics"));

        FilterMap triggerFilter =
                new FilterMap.Builder()
                        .addStringListValue(
                                "conversion_subdomain", List.of("electronics.megastore"))
                        .addStringListValue("product", List.of("1234", "2345"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 200L)
                        .build();

        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertTrue(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatchV2_outsideLookbackWindow_returnsFalse() {
        FilterMap sourceFilter =
                new FilterMap.Builder()
                        .addStringListValue(
                                "conversion_subdomain", List.of("electronics.megastore"))
                        .addStringListValue("product", List.of("1234", "234"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 100L)
                        .build();

        FilterMap triggerFilter =
                new FilterMap.Builder()
                        .addStringListValue(
                                "conversion_subdomain", List.of("electronics.megastore"))
                        .addStringListValue("product", List.of("1234", "2345"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 10L)
                        .build();

        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertFalse(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatch_filterSet_nonEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap1.put("product", Arrays.asList("2", "3"));
        triggerFilterMap1.put("id", Arrays.asList("11", "22"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap2.put("product", Arrays.asList("1", "2"));
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertFalse(
                new Filter(mFlags)
                        .isFilterMatch(
                                sourceFilter, List.of(triggerFilter1, triggerFilter2), true));
    }

    @Test
    public void testIsFilterMatch_nonEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap.put("product", Arrays.asList("1", "2"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatch_filterSet_withEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Collections.emptyList());
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap1.put("product", Arrays.asList("2", "3"));
        triggerFilterMap1.put("id", Arrays.asList("11", "22"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Collections.emptyList());
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertTrue(
                new Filter(mFlags)
                        .isFilterMatch(
                                sourceFilter, List.of(triggerFilter1, triggerFilter2), true));
    }

    @Test
    public void testIsFilterMatch_withEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Collections.emptyList());
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertTrue(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatch_filterSet_withEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap1.put("product", Collections.emptyList());
        triggerFilterMap1.put("id", Arrays.asList("11", "22"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap2.put("product", Collections.emptyList());
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertFalse(
                new Filter(mFlags)
                        .isFilterMatch(
                                sourceFilter, List.of(triggerFilter1, triggerFilter2), true));
    }

    @Test
    public void testIsFilterMatch_withEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatch_filterSet_withNegation_nonEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        // Intersecting values
        triggerFilterMap1.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        triggerFilterMap1.put("product", Arrays.asList("1234", "2"));
        triggerFilterMap1.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        // Non-intersecting values
        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put("conversion_subdomain", Collections.singletonList("electronics"));
        triggerFilterMap2.put("product", Arrays.asList("1", "2"));
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertTrue(
                new Filter(mFlags)
                        .isFilterMatch(
                                sourceFilter, List.of(triggerFilter1, triggerFilter2), false));
    }

    @Test
    public void testIsFilterMatchV2_withNegation_outsideLookbackWindow_returnTrue() {
        FilterMap sourceFilter =
                new FilterMap.Builder()
                        .addStringListValue(
                                "conversion_subdomain", List.of("electronics.megastore"))
                        .addStringListValue("product", List.of("1234", "234"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 100L)
                        .build();

        FilterMap triggerFilter =
                new FilterMap.Builder()
                        .addStringListValue("conversion_subdomain", List.of("electronics"))
                        .addStringListValue("product", List.of("1", "2"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 10L)
                        .build();

        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertTrue(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), false));
    }

    @Test
    public void testIsFilterMatchV2_withNegation_insideLookbackWindow_returnFalse() {
        FilterMap sourceFilter =
                new FilterMap.Builder()
                        .addStringListValue(
                                "conversion_subdomain", List.of("electronics.megastore"))
                        .addStringListValue("product", List.of("1234", "234"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 100L)
                        .build();

        FilterMap triggerFilter =
                new FilterMap.Builder()
                        .addStringListValue("conversion_subdomain", List.of("electronics"))
                        .addStringListValue("product", List.of("1", "2"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 200L)
                        .build();

        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        assertFalse(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), false));
    }

    @Test
    public void testIsFilterMatch_filterSet_withNegation_nonEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put("product", Arrays.asList("abcd", "234"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertFalse(
                new Filter(mFlags)
                        .isFilterMatch(
                                sourceFilter, List.of(triggerFilter1, triggerFilter2), false));
    }

    @Test
    public void testIsFilterMatch_withNegation_nonEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), false));
    }

    @Test
    public void testIsFilterMatch_filterSet_withNegation_withEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put("conversion_subdomain", Collections.emptyList());
        triggerFilterMap1.put("product", Collections.emptyList());
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put("conversion_subdomain", Collections.singletonList("electronics"));
        triggerFilterMap2.put("product", Collections.emptyList());
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertTrue(
                new Filter(mFlags)
                        .isFilterMatch(
                                sourceFilter, List.of(triggerFilter1, triggerFilter2), false));
    }

    @Test
    public void testIsFilterMatch_withNegation_withEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put("conversion_subdomain", Collections.singletonList("electronics"));
        // Matches when negated
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertTrue(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), false));
    }

    @Test
    public void testIsFilterMatch_filterSet_withNegation_withEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("conversion_subdomain", Collections.emptyList());
        sourceFilterMap.put("product", Collections.emptyList());
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        // Doesn't match when negated
        triggerFilterMap1.put("conversion_subdomain", Collections.emptyList());
        triggerFilterMap1.put("product", Arrays.asList("3", "4"));
        triggerFilterMap1.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match when negated
        triggerFilterMap2.put("product", Collections.emptyList());
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertFalse(
                new Filter(mFlags)
                        .isFilterMatch(
                                sourceFilter, List.of(triggerFilter1, triggerFilter2), false));
    }

    @Test
    public void testIsFilterMatch_withNegation_withEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Collections.emptyList());
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match when negated
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(new Filter(mFlags).isFilterMatch(sourceFilter, List.of(triggerFilter), false));
    }

    @Test
    public void deserializeFilterSet_success() throws JSONException {
        // Setup
        Map<String, List<String>> map1 = new HashMap<>();
        map1.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
        map1.put("product", Arrays.asList("1234", "234"));
        map1.put("ctid", Collections.singletonList("id"));
        FilterMap filterMap1 = new FilterMap.Builder().setAttributionFilterMap(map1).build();
        JSONObject map1Json = new JSONObject(map1);

        Map<String, List<String>> map2 = new HashMap<>();
        map2.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        map2.put("product", Arrays.asList("2", "3"));
        map2.put("id", Arrays.asList("11", "22"));
        FilterMap filterMap2 = new FilterMap.Builder().setAttributionFilterMap(map2).build();
        JSONObject map2Json = new JSONObject(map2);

        // Execution
        JSONArray jsonArray = new JSONArray(Arrays.asList(map1Json, map2Json));
        List<FilterMap> actualFilterMaps = new Filter(mFlags).deserializeFilterSet(jsonArray);

        // Assertion
        assertEquals(Arrays.asList(filterMap1, filterMap2), actualFilterMaps);
    }

    @Test
    public void serializeAndDeserializeFilterSetV2_success() throws JSONException {
        // Setup
        FilterMap filterMap1 =
                new FilterMap.Builder()
                        .addStringListValue(
                                "conversion_subdomain", List.of("electronics.megastore"))
                        .addStringListValue("product", List.of("1234", "234"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 100L)
                        .build();

        FilterMap filterMap2 =
                new FilterMap.Builder()
                        .addStringListValue("conversion_subdomain", List.of("electronics"))
                        .addStringListValue("product", List.of("1", "2"))
                        .addLongValue(FilterMap.LOOKBACK_WINDOW, 200L)
                        .build();
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);

        JSONArray actualFilterMaps =
                new Filter(mFlags).serializeFilterSet(Arrays.asList(filterMap1, filterMap2));
        List<FilterMap> filterMaps = new Filter(mFlags).deserializeFilterSet(actualFilterMaps);

        // Assertion
        assertEquals(Arrays.asList(filterMap1, filterMap2), filterMaps);
    }

    @Test
    public void serializeAndDeserializeFilterSet_success() throws JSONException {
        // Setup
        Map<String, List<String>> map1 = new HashMap<>();
        map1.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
        map1.put("product", Arrays.asList("1234", "234"));
        map1.put("ctid", Collections.singletonList("id"));
        FilterMap filterMap1 = new FilterMap.Builder().setAttributionFilterMap(map1).build();

        Map<String, List<String>> map2 = new HashMap<>();
        map2.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        map2.put("product", Arrays.asList("2", "3"));
        map2.put("id", Arrays.asList("11", "22"));
        FilterMap filterMap2 = new FilterMap.Builder().setAttributionFilterMap(map2).build();

        // Execution
        JSONArray actualFilterMaps =
                new Filter(mFlags).serializeFilterSet(Arrays.asList(filterMap1, filterMap2));
        List<FilterMap> filterMaps = new Filter(mFlags).deserializeFilterSet(actualFilterMaps);

        // Assertion
        assertEquals(Arrays.asList(filterMap1, filterMap2), filterMaps);
    }

    @Test
    public void maybeWrapFilters_providedJsonObject_wrapsIntoJsonArray() throws JSONException {
        // Setup
        Map<String, List<String>> map1 = new HashMap<>();
        map1.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
        map1.put("product", Arrays.asList("1234", "234"));
        map1.put("ctid", Collections.singletonList("id"));
        JSONObject map1Json = new JSONObject(map1);
        JSONArray expectedWrappedJsonArray = new JSONArray(Collections.singletonList(map1));

        JSONObject topLevelObject = new JSONObject();
        String key = "key";
        topLevelObject.put(key, map1Json);

        // Execution
        JSONArray actualWrappedJsonArray = Filter.maybeWrapFilters(topLevelObject, key);

        // Assertion
        assertEquals(expectedWrappedJsonArray.toString(), actualWrappedJsonArray.toString());
    }

    @Test
    public void maybeWrapFilters_providedJsonArray_returnsAsIs() throws JSONException {
        // Setup
        Map<String, List<String>> map1 = new HashMap<>();
        map1.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
        map1.put("product", Arrays.asList("1234", "234"));
        map1.put("ctid", Collections.singletonList("id"));
        JSONObject map1Json = new JSONObject(map1);

        Map<String, List<String>> map2 = new HashMap<>();
        map2.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        map2.put("product", Arrays.asList("2", "3"));
        map2.put("id", Arrays.asList("11", "22"));
        JSONObject map2Json = new JSONObject(map2);

        JSONArray jsonArray = new JSONArray(Arrays.asList(map1Json, map2Json));
        JSONObject topLevelObject = new JSONObject();
        String key = "key";
        topLevelObject.put(key, jsonArray);

        // Execution
        JSONArray wrappedJsonArray = Filter.maybeWrapFilters(topLevelObject, key);

        // Assertion
        assertEquals(jsonArray.toString(), wrappedJsonArray.toString());
    }
}
