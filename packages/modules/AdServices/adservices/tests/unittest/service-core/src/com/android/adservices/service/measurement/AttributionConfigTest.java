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

package com.android.adservices.service.measurement;

import static com.android.adservices.service.Flags.MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link AttributionConfig} */
@SmallTest
@RunWith(MockitoJUnitRunner.class)
public final class AttributionConfigTest {
    private static final String SOURCE_AD_TECH = "AdTech1-Ads";
    @Mock private Flags mFlags;

    @Before
    public void setup() {
        when(mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds())
                .thenReturn(MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
    }
    @Test
    public void testCreation() throws Exception {
        AttributionConfig attributionConfig = createExample();

        assertEquals("AdTech1-Ads", attributionConfig.getSourceAdtech());
        assertEquals(100L, attributionConfig.getSourcePriorityRange().first.longValue());
        assertEquals(1000L, attributionConfig.getSourcePriorityRange().second.longValue());
        List<FilterMap> sourceFilter = attributionConfig.getSourceFilters();
        assertEquals(1, sourceFilter.get(0).getAttributionFilterMap().get("campaign_type").size());
        assertEquals(1, sourceFilter.get(0).getAttributionFilterMap().get("source_type").size());
        List<FilterMap> sourceNotFilter = attributionConfig.getSourceNotFilters();
        assertEquals(
                1, sourceNotFilter.get(0).getAttributionFilterMap().get("campaign_type").size());
        assertEquals(600000L, attributionConfig.getSourceExpiryOverride().longValue());
        assertEquals(99L, attributionConfig.getPriority().longValue());
        assertEquals(604800L, attributionConfig.getExpiry().longValue());
        FilterMap filterData = attributionConfig.getFilterData();
        assertEquals(1, filterData.getAttributionFilterMap().get("campaign_type").size());
        assertEquals(100000L, attributionConfig.getPostInstallExclusivityWindow().longValue());
    }

    @Test
    public void testDefaults() throws Exception {
        AttributionConfig attributionConfig =
                new AttributionConfig.Builder().setSourceAdtech("AdTech1-Ads").build();
        assertNotNull(attributionConfig.getSourceAdtech());
        assertNull(attributionConfig.getSourcePriorityRange());
        assertNull(attributionConfig.getSourceFilters());
        assertNull(attributionConfig.getSourceNotFilters());
        assertNull(attributionConfig.getSourceExpiryOverride());
        assertNull(attributionConfig.getPriority());
        assertNull(attributionConfig.getExpiry());
        assertNull(attributionConfig.getFilterData());
        assertNull(attributionConfig.getPostInstallExclusivityWindow());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AttributionConfig config1 = createExample();
        final AttributionConfig config2 = createExample();
        final Set<AttributionConfig> configSet1 = Set.of(config1);
        final Set<AttributionConfig> configSet2 = Set.of(config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertEquals(config1, config2);
        assertEquals(configSet1, configSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final AttributionConfig config1 = createExample();

        Pair<Long, Long> sourcePriorityRange = new Pair<>(100L, 1000L);

        Map<String, List<String>> sourceFiltersMap = new HashMap<>();
        sourceFiltersMap.put("campaign_type", Collections.singletonList("install"));
        sourceFiltersMap.put("source_type", Collections.singletonList("navigation"));
        FilterMap sourceFilters =
                new FilterMap.Builder().setAttributionFilterMap(sourceFiltersMap).build();

        Map<String, List<String>> sourceNotFiltersMap = new HashMap<>();
        sourceNotFiltersMap.put("campaign_type", Collections.singletonList("product"));
        FilterMap sourceNotFilters =
                new FilterMap.Builder().setAttributionFilterMap(sourceNotFiltersMap).build();

        Map<String, List<String>> filterDataMap = new HashMap<>();
        filterDataMap.put("campaign_type", Collections.singletonList("install"));
        FilterMap filterData =
                new FilterMap.Builder().setAttributionFilterMap(filterDataMap).build();

        AttributionConfig config2 =
                new AttributionConfig.Builder()
                        .setSourceAdtech("AdTech2-Ads")
                        .setSourcePriorityRange(sourcePriorityRange)
                        .setSourceFilters(List.of(sourceFilters))
                        .setSourceNotFilters(List.of(sourceNotFilters))
                        .setSourceExpiryOverride(600000L)
                        .setPriority(99L)
                        .setExpiry(604800L)
                        .setFilterData(filterData)
                        .setPostInstallExclusivityWindow(100000L)
                        .build();

        final Set<AttributionConfig> configSet1 = Set.of(config1);
        final Set<AttributionConfig> configSet2 = Set.of(config2);
        assertNotEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1, config2);
        assertNotEquals(configSet1, configSet2);
    }

    @Test
    public void build_nonEmptyAttributionConfig_parseSuccess() throws JSONException {
        // Setup
        AttributionConfig expected = createExampleAttributionConfig();

        // Action
        AttributionConfig actual =
                new AttributionConfig.Builder(createExampleAttributionConfigJson(), mFlags).build();

        // Assertion
        assertEquals(expected, actual);
    }

    @Test
    public void build_missingSourceAdTech_throwsJsonException() throws JSONException {
        // Setup
        JSONObject attributionConfigJson = createExampleAttributionConfigJson();
        attributionConfigJson.remove(AttributionConfig.AttributionConfigContract.SOURCE_NETWORK);

        // Assertion
        assertThrows(
                JSONException.class,
                () -> new AttributionConfig.Builder(attributionConfigJson, mFlags).build());
    }

    @Test
    public void build_withOnlySourceAdTechField_success() throws JSONException {
        // Setup
        JSONObject attributionConfigJson = new JSONObject();
        attributionConfigJson.put("source_network", SOURCE_AD_TECH);

        // Assertion
        assertEquals(
                new AttributionConfig.Builder().setSourceAdtech(SOURCE_AD_TECH).build(),
                new AttributionConfig.Builder(attributionConfigJson, mFlags).build());
    }

    @Test
    public void serializeAsJson_success() throws JSONException {
        // Setup
        AttributionConfig attributionConfig = createExample();
        // Assertion
        assertEquals(
                attributionConfig,
                new AttributionConfig.Builder(attributionConfig.serializeAsJson(mFlags), mFlags)
                        .build());
    }

    private JSONObject createExampleAttributionConfigJson() throws JSONException {
        JSONObject attributionConfig = new JSONObject();
        attributionConfig.put("source_network", SOURCE_AD_TECH);
        JSONObject sourcePriorityRangeJson = new JSONObject();
        sourcePriorityRangeJson.put("start", 100L);
        sourcePriorityRangeJson.put("end", 1000L);
        attributionConfig.put("source_priority_range", sourcePriorityRangeJson);
        JSONObject sourceFiltersJson = new JSONObject();
        sourceFiltersJson.put(
                "source_type", new JSONArray(Collections.singletonList("navigation")));
        JSONArray sourceFilterSet = new JSONArray();
        sourceFilterSet.put(sourceFiltersJson);
        attributionConfig.put("source_filters", sourceFilterSet);
        JSONObject sourceNotFiltersJson = new JSONObject();
        sourceNotFiltersJson.put(
                "campaign_type", new JSONArray(Collections.singletonList("product")));
        JSONArray sourceNotFilterSet = new JSONArray();
        sourceNotFilterSet.put(sourceNotFiltersJson);
        attributionConfig.put("source_not_filters", sourceNotFilterSet);
        attributionConfig.put("source_expiry_override", 600000L);
        attributionConfig.put("priority", 99L);
        attributionConfig.put("expiry", 604800L);
        JSONObject filterDataJson = new JSONObject();
        filterDataJson.put("campaign_type", new JSONArray(Collections.singletonList("install")));
        attributionConfig.put("filter_data", filterDataJson);
        attributionConfig.put("post_install_exclusivity_window", 100000L);
        return attributionConfig;
    }

    private AttributionConfig createExampleAttributionConfig() throws JSONException {
        JSONObject sourceFiltersMap = new JSONObject();
        Pair<Long, Long> sourcePriorityRange = new Pair<>(100L, 1000L);
        sourceFiltersMap.put("source_type", new JSONArray(Collections.singletonList("navigation")));
        FilterMap sourceFilters = new FilterMap.Builder().buildFilterData(sourceFiltersMap).build();

        JSONObject sourceNotFiltersMap = new JSONObject();
        sourceNotFiltersMap.put(
                "campaign_type", new JSONArray(Collections.singletonList("product")));
        FilterMap sourceNotFilters =
                new FilterMap.Builder().buildFilterData(sourceNotFiltersMap).build();

        JSONObject filterDataMap = new JSONObject();
        filterDataMap.put("campaign_type", new JSONArray(Collections.singletonList("install")));
        FilterMap filterData = new FilterMap.Builder().buildFilterData(filterDataMap).build();

        return new AttributionConfig.Builder()
                .setSourceAdtech(SOURCE_AD_TECH)
                .setSourcePriorityRange(sourcePriorityRange)
                .setSourceFilters(List.of(sourceFilters))
                .setSourceNotFilters(List.of(sourceNotFilters))
                .setSourceExpiryOverride(600000L)
                .setPriority(99L)
                .setExpiry(604800L)
                .setFilterData(filterData)
                .setPostInstallExclusivityWindow(100000L)
                .build();
    }

    private AttributionConfig createExample() {
        Pair<Long, Long> sourcePriorityRange = new Pair<>(100L, 1000L);

        Map<String, List<String>> sourceFiltersMap = new HashMap<>();
        sourceFiltersMap.put("campaign_type", Collections.singletonList("install"));
        sourceFiltersMap.put("source_type", Collections.singletonList("navigation"));
        FilterMap sourceFilters =
                new FilterMap.Builder().setAttributionFilterMap(sourceFiltersMap).build();

        Map<String, List<String>> sourceNotFiltersMap = new HashMap<>();
        sourceNotFiltersMap.put("campaign_type", Collections.singletonList("product"));
        FilterMap sourceNotFilters =
                new FilterMap.Builder().setAttributionFilterMap(sourceNotFiltersMap).build();

        Map<String, List<String>> filterDataMap = new HashMap<>();
        filterDataMap.put("campaign_type", Collections.singletonList("install"));
        FilterMap filterData =
                new FilterMap.Builder().setAttributionFilterMap(filterDataMap).build();

        return new AttributionConfig.Builder()
                .setSourceAdtech(SOURCE_AD_TECH)
                .setSourcePriorityRange(sourcePriorityRange)
                .setSourceFilters(List.of(sourceFilters))
                .setSourceNotFilters(List.of(sourceNotFilters))
                .setSourceExpiryOverride(600000L)
                .setPriority(99L)
                .setExpiry(604800L)
                .setFilterData(filterData)
                .setPostInstallExclusivityWindow(100000L)
                .build();
    }
}
