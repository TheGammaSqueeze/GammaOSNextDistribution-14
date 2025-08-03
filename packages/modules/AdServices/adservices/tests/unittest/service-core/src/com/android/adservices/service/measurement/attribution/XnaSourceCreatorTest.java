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

package com.android.adservices.service.measurement.attribution;

import static com.android.adservices.service.Flags.MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.Flags.MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.AttributionConfig;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.util.Filter;
import com.android.adservices.service.measurement.util.UnsignedLong;

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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class XnaSourceCreatorTest {
    private static final UnsignedLong SHARED_DEBUG_KEY_1 = new UnsignedLong(1786463L);
    private static final String AD_ID = "abc-def-ghi";
    private static final String JOIN_KEY = "join-abc-def-ghi";
    private static final long LOOKBACK_WINDOW_VALUE = 10L;

    @Mock private Flags mFlags;
    private Filter mFilter;

    @Before
    public void setup() {
        doReturn(true).when(mFlags).getMeasurementEnableSharedSourceDebugKey();
        doReturn(true).when(mFlags).getMeasurementEnableSharedFilterDataKeysXNA();
        when(mFlags.getMeasurementMinReportingRegisterSourceExpirationInSeconds())
                .thenReturn(MEASUREMENT_MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        when(mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds())
                .thenReturn(MEASUREMENT_MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        mFilter = new Filter(mFlags);
    }

    @Test
    public void generateDerivedSources_withVarietyOfSources_filtersAndGeneratesSources()
            throws JSONException {
        // Setup
        String enrollment1 = "enrollment1";
        JSONArray filters =
                new JSONArray(
                        Collections.singletonList(new JSONObject(buildMatchingFilterData(false))));
        AttributionConfig attributionConfig1 =
                new AttributionConfig.Builder()
                        .setSourceAdtech(enrollment1)
                        .setSourceFilters(mFilter.deserializeFilterSet(filters))
                        .setSourceNotFilters(null)
                        .setFilterData(
                                new FilterMap.Builder()
                                        .buildFilterData(
                                                new JSONObject(buildMatchingFilterData(false)))
                                        .build())
                        .setExpiry(50L)
                        .setPriority(50L)
                        .setPostInstallExclusivityWindow(5L)
                        .setSourcePriorityRange(new Pair<>(1L, 100L))
                        .build();
        String enrollment2 = "enrollment2";
        AttributionConfig attributionConfig2 =
                new AttributionConfig.Builder()
                        .setSourceAdtech(enrollment2)
                        .setSourceFilters(null)
                        .setSourceNotFilters(
                                mFilter.deserializeFilterSet(
                                        new JSONArray(
                                                Collections.singletonList(
                                                        new JSONObject(
                                                                buildNonMatchingFilterData())))))
                        .setSourcePriorityRange(new Pair<>(101L, 200L))
                        .setSourceExpiryOverride(TimeUnit.DAYS.toSeconds(10L))
                        .build();

        AttributionConfig attributionConfig1_copy =
                new AttributionConfig.Builder()
                        .setSourceAdtech(enrollment1)
                        .setSourceFilters(mFilter.deserializeFilterSet(filters))
                        .setSourceNotFilters(null)
                        .setFilterData(
                                new FilterMap.Builder()
                                        .buildFilterData(
                                                new JSONObject(buildMatchingFilterData(false)))
                                        .build())
                        .setExpiry(60L)
                        .setPriority(70L)
                        .setPostInstallExclusivityWindow(50L)
                        .setSourcePriorityRange(new Pair<>(1L, 100L))
                        .build();

        String attributionConfigsArray =
                new JSONArray(
                                Arrays.asList(
                                        attributionConfig1.serializeAsJson(mFlags),
                                        attributionConfig2.serializeAsJson(mFlags),
                                        // This ensures that already consumed sources aren't reused
                                        attributionConfig1_copy.serializeAsJson(mFlags)))
                        .toString();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionConfig(attributionConfigsArray)
                        .build();

        // Aggregate source
        JSONObject aggregatableSource = new JSONObject();
        aggregatableSource.put("key1", "0x159");
        aggregatableSource.put("key2", "0x1");
        aggregatableSource.put("key3", "0x2");

        // enrollment1 sources
        Source source1Matches =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(1L)
                        .setAggregateSource(aggregatableSource.toString())
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("key2", "key3")).toString())
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .setDebugAdId(AD_ID)
                        .setDebugJoinKey(JOIN_KEY)
                        .build();

        JSONObject derivedAggregatableSource1 = new JSONObject();
        derivedAggregatableSource1.put("key2", "0x1");
        derivedAggregatableSource1.put("key3", "0x2");
        Source expectedDerivedSource1 =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(attributionConfig1.getPriority())
                        .setFilterDataString(
                                attributionConfig1
                                        .getFilterData()
                                        .serializeAsJson(mFlags)
                                        .toString())
                        .setExpiryTime(source1Matches.getExpiryTime())
                        .setInstallCooldownWindow(
                                attributionConfig1.getPostInstallExclusivityWindow())
                        .setParentId(source1Matches.getId())
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("key2", "key3")).toString())
                        .setAggregateSource(derivedAggregatableSource1.toString())
                        // shared_debug_key is shared as debug_key on the derived source
                        .setDebugKey(SHARED_DEBUG_KEY_1)
                        // adId isn't shared
                        .setDebugAdId(null)
                        // join key isn't shared
                        .setDebugJoinKey(null)
                        .build();
        Source source2Matches =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(100L)
                        .setFilterDataString(buildMatchingFilterData(false))
                        .setAggregateSource(aggregatableSource.toString())
                        .setSharedAggregationKeys(
                                new JSONArray(Collections.singletonList("key1")).toString())
                        .build();

        JSONObject derivedAggregatableSource2 = new JSONObject();
        derivedAggregatableSource2.put("key1", "0x159");
        Source expectedDerivedSource2;
        expectedDerivedSource2 =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(attributionConfig1.getPriority())
                        .setFilterDataString(
                                attributionConfig1
                                        .getFilterData()
                                        .serializeAsJson(mFlags)
                                        .toString())
                        .setExpiryTime(source2Matches.getExpiryTime())
                        .setInstallCooldownWindow(
                                attributionConfig1.getPostInstallExclusivityWindow())
                        .setParentId(source2Matches.getId())
                        .setAggregateSource(derivedAggregatableSource2.toString())
                        .setSharedAggregationKeys(
                                new JSONArray(Collections.singletonList("key1")).toString())
                        .build();
        Source source3OutOfPriorityRange =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(101L)
                        .setFilterDataString(buildMatchingFilterData(false))
                        .build();
        Source source4NonMatchingFilter =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(50L)
                        .setFilterDataString(buildNonMatchingFilterData())
                        .build();

        // enrollment2 sources
        Source source5Matches =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment2)
                        .setPriority(120L)
                        .setInstallCooldownWindow(0L)
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .build();
        Source expectedDerivedSource5 =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment2)
                        .setPriority(120L)
                        .setFilterDataString(source5Matches.getFilterDataString())
                        .setExpiryTime(source5Matches.getExpiryTime())
                        .setInstallCooldownWindow(0L)
                        .setParentId(source5Matches.getId())
                        .setAggregateSource(new JSONObject().toString())
                        .setDebugKey(SHARED_DEBUG_KEY_1)
                        .build();
        Source source6NoFiltersIssue =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment2)
                        .setPriority(130L)
                        .setFilterDataString(buildNonMatchingFilterData())
                        .build();
        Source source7ExpiresBeforeTriggerTime =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment2)
                        .setPriority(120L)
                        .setEventTime(
                                trigger.getTriggerTime()
                                        - TimeUnit.DAYS.toMillis(10L)
                                        - 50L /* random value so that eventTime+override is before
                                              trigger time*/)
                        .build();

        // enrollmentX source - is not considered since to attribution config is present for this
        Source source8NoAttributionConfig =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId("enrollmentX")
                        .setPriority(120L)
                        .setInstallCooldownWindow(0L)
                        .build();

        List<Source> expectedDerivedSources =
                Arrays.asList(
                        expectedDerivedSource1, expectedDerivedSource2, expectedDerivedSource5);
        Comparator<Source> sorter = Comparator.comparing(Source::getParentId);
        expectedDerivedSources.sort(sorter);

        // Execution
        XnaSourceCreator xnaSourceCreator = new XnaSourceCreator(mFlags);
        List<Source> parentSources =
                Arrays.asList(
                        source1Matches,
                        source2Matches,
                        source3OutOfPriorityRange,
                        source4NonMatchingFilter,
                        source5Matches,
                        source6NoFiltersIssue,
                        source7ExpiresBeforeTriggerTime,
                        source8NoAttributionConfig);
        List<Source> actualDerivedSources =
                xnaSourceCreator.generateDerivedSources(trigger, parentSources);
        actualDerivedSources.sort(sorter);

        // Assertion
        assertEquals(expectedDerivedSources, actualDerivedSources);
    }

    @Test
    public void generateDerivedSources_sharedDebugKeyDisabled_doesntAddDebugKeyToDerivedSource()
            throws JSONException {
        // Setup
        doReturn(false).when(mFlags).getMeasurementEnableSharedSourceDebugKey();
        String enrollment1 = "enrollment1";
        JSONArray filters =
                new JSONArray(
                        Collections.singletonList(new JSONObject(buildMatchingFilterData(false))));
        AttributionConfig attributionConfig1 =
                new AttributionConfig.Builder()
                        .setSourceAdtech(enrollment1)
                        .setSourceFilters(mFilter.deserializeFilterSet(filters))
                        .setSourceNotFilters(null)
                        .setFilterData(
                                new FilterMap.Builder()
                                        .buildFilterData(
                                                new JSONObject(buildMatchingFilterData(false)))
                                        .build())
                        .setExpiry(50L)
                        .setPriority(50L)
                        .setPostInstallExclusivityWindow(5L)
                        .setSourcePriorityRange(new Pair<>(1L, 100L))
                        .build();
        String attributionConfigsArray =
                new JSONArray(Collections.singletonList(attributionConfig1.serializeAsJson(mFlags)))
                        .toString();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionConfig(attributionConfigsArray)
                        .build();

        // Aggregate source
        JSONObject aggregatableSource = new JSONObject();
        aggregatableSource.put("key1", "0x159");
        aggregatableSource.put("key2", "0x1");
        aggregatableSource.put("key3", "0x2");

        // enrollment1 sources
        Source source1Matches =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(1L)
                        .setAggregateSource(aggregatableSource.toString())
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("key2", "key3")).toString())
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .setDebugAdId(AD_ID)
                        .setDebugJoinKey(JOIN_KEY)
                        .build();

        JSONObject derivedAggregatableSource1 = new JSONObject();
        derivedAggregatableSource1.put("key2", "0x1");
        derivedAggregatableSource1.put("key3", "0x2");
        Source expectedDerivedSource1 =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(attributionConfig1.getPriority())
                        .setFilterDataString(
                                attributionConfig1
                                        .getFilterData()
                                        .serializeAsJson(mFlags)
                                        .toString())
                        .setExpiryTime(source1Matches.getExpiryTime())
                        .setInstallCooldownWindow(
                                attributionConfig1.getPostInstallExclusivityWindow())
                        .setParentId(source1Matches.getId())
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("key2", "key3")).toString())
                        .setAggregateSource(derivedAggregatableSource1.toString())
                        // shared_debug_key is not shared when the feature is disabled
                        .setDebugKey(null)
                        // adId isn't shared
                        .setDebugAdId(null)
                        // join key isn't shared
                        .setDebugJoinKey(null)
                        .build();

        List<Source> expectedDerivedSources = Collections.singletonList(expectedDerivedSource1);

        // Execution
        XnaSourceCreator xnaSourceCreator = new XnaSourceCreator(mFlags);
        List<Source> parentSources = Collections.singletonList(source1Matches);
        List<Source> actualDerivedSources =
                xnaSourceCreator.generateDerivedSources(trigger, parentSources);

        // Assertion
        assertEquals(expectedDerivedSources, actualDerivedSources);
    }

    @Test
    public void generateDerivedSources_withSharedFilterDataKeys_filtersAndGeneratesSources()
            throws JSONException {
        // Setup
        doReturn(false).when(mFlags).getMeasurementEnableSharedSourceDebugKey();
        String enrollment1 = "enrollment1";

        JSONArray filters =
                new JSONArray(
                        Collections.singletonList(new JSONObject(buildMatchingSharedFilterData())));
        AttributionConfig attributionConfig1 =
                new AttributionConfig.Builder()
                        .setSourceAdtech(enrollment1)
                        .setSourceFilters(mFilter.deserializeFilterSet(filters))
                        .setSourceNotFilters(null)
                        .setFilterData(
                                new FilterMap.Builder()
                                        .buildFilterData(
                                                new JSONObject(buildMatchingSharedFilterData()))
                                        .build())
                        .setExpiry(50L)
                        .setPriority(50L)
                        .setPostInstallExclusivityWindow(5L)
                        .setSourcePriorityRange(new Pair<>(1L, 100L))
                        .build();
        String attributionConfigsArray =
                new JSONArray(Collections.singletonList(attributionConfig1.serializeAsJson(mFlags)))
                        .toString();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionConfig(attributionConfigsArray)
                        .build();

        // Aggregate source
        JSONObject aggregatableSource = new JSONObject();
        aggregatableSource.put("key1", "0x159");
        aggregatableSource.put("key2", "0x1");
        aggregatableSource.put("key3", "0x2");

        // enrollment1 sources
        Source source1Matches =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(1L)
                        .setAggregateSource(aggregatableSource.toString())
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("key2", "key3")).toString())
                        .setFilterDataString(buildMatchingSharedFilterData())
                        .setSharedFilterDataKeys(new JSONArray(Arrays.asList("product")).toString())
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .setDebugAdId(AD_ID)
                        .setDebugJoinKey(JOIN_KEY)
                        .build();

        JSONObject derivedAggregatableSource1 = new JSONObject();
        derivedAggregatableSource1.put("key2", "0x1");
        derivedAggregatableSource1.put("key3", "0x2");
        Source expectedDerivedSource1 =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(attributionConfig1.getPriority())
                        .setFilterDataString(
                                attributionConfig1
                                        .getFilterData()
                                        .serializeAsJson(mFlags)
                                        .toString())
                        .setExpiryTime(source1Matches.getExpiryTime())
                        .setInstallCooldownWindow(
                                attributionConfig1.getPostInstallExclusivityWindow())
                        .setParentId(source1Matches.getId())
                        .setFilterDataString("{\"product\":[\"123\"]}")
                        .setSharedFilterDataKeys(null)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("key2", "key3")).toString())
                        .setAggregateSource(derivedAggregatableSource1.toString())
                        // shared_debug_key is not shared when the feature is disabled
                        .setDebugKey(null)
                        // adId isn't shared
                        .setDebugAdId(null)
                        // join key isn't shared
                        .setDebugJoinKey(null)
                        .build();

        List<Source> expectedDerivedSources = Collections.singletonList(expectedDerivedSource1);

        // Execution
        XnaSourceCreator xnaSourceCreator = new XnaSourceCreator(mFlags);
        List<Source> parentSources = Collections.singletonList(source1Matches);
        List<Source> actualDerivedSources =
                xnaSourceCreator.generateDerivedSources(trigger, parentSources);

        // Assertion
        assertEquals(expectedDerivedSources, actualDerivedSources);
    }

    @Test
    public void generateDerivedSources_lookbackWindow_filtersAndGeneratesSources()
            throws JSONException {
        // Setup
        doReturn(true).when(mFlags).getMeasurementEnableLookbackWindowFilter();
        String enrollment1 = "enrollment1";

        JSONArray filters =
                new JSONArray(
                        Collections.singletonList(new JSONObject(buildMatchingFilterData(true))));
        JSONArray sourceFilters =
                new JSONArray(
                        Collections.singletonList(new JSONObject(buildMatchingFilterData(false))));
        AttributionConfig attributionConfig1 =
                new AttributionConfig.Builder()
                        .setSourceAdtech(enrollment1)
                        .setSourceFilters(mFilter.deserializeFilterSet(filters))
                        .setSourceNotFilters(null)
                        .setFilterData(
                                new FilterMap.Builder()
                                        .buildFilterDataV2(
                                                new JSONObject(buildMatchingFilterData(false)))
                                        .build())
                        .setExpiry(50L)
                        .setPriority(50L)
                        .setPostInstallExclusivityWindow(5L)
                        .setSourcePriorityRange(new Pair<>(1L, 100L))
                        .build();
        String attributionConfigsArray =
                new JSONArray(Collections.singletonList(attributionConfig1.serializeAsJson(mFlags)))
                        .toString();
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionConfig(attributionConfigsArray)
                        .setTriggerTime(20000L)
                        .build();

        // Aggregate source
        JSONObject aggregatableSource = new JSONObject();
        aggregatableSource.put("key1", "0x159");
        aggregatableSource.put("key2", "0x1");
        aggregatableSource.put("key3", "0x2");

        // enrollment1 sources
        Source sourceWithinLookbackWindow =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEventTime(
                                trigger.getTriggerTime()
                                        - TimeUnit.SECONDS.toMillis(LOOKBACK_WINDOW_VALUE - 1))
                        .setEnrollmentId(enrollment1)
                        .setPriority(1L)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("key2", "key3")).toString())
                        .setAggregateSource(aggregatableSource.toString())
                        .setFilterDataString(buildMatchingFilterData(false))
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .setDebugAdId(AD_ID)
                        .setDebugJoinKey(JOIN_KEY)
                        .build();

        Source sourceOutsideLookbackWindow =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEventTime(
                                trigger.getTriggerTime()
                                        - TimeUnit.SECONDS.toMillis(LOOKBACK_WINDOW_VALUE + 1))
                        .setEnrollmentId(enrollment1)
                        .setPriority(1L)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("key2", "key3")).toString())
                        .setAggregateSource(aggregatableSource.toString())
                        .setFilterDataString(buildMatchingFilterData(false))
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .setDebugAdId(AD_ID)
                        .setDebugJoinKey(JOIN_KEY)
                        .build();

        JSONObject derivedAggregatableSource1 = new JSONObject();
        derivedAggregatableSource1.put("key2", "0x1");
        derivedAggregatableSource1.put("key3", "0x2");
        Source expectedDerivedSource1 =
                createValidSourceBuilder()
                        .setId(UUID.randomUUID().toString())
                        .setEnrollmentId(enrollment1)
                        .setPriority(attributionConfig1.getPriority())
                        .setEventTime(sourceWithinLookbackWindow.getEventTime())
                        .setFilterDataString(buildMatchingFilterData(false))
                        .setExpiryTime(86411000L)
                        .setInstallCooldownWindow(
                                attributionConfig1.getPostInstallExclusivityWindow())
                        .setParentId(sourceWithinLookbackWindow.getId())
                        .setSharedFilterDataKeys(null)
                        .setSharedAggregationKeys(
                                new JSONArray(Arrays.asList("key2", "key3")).toString())
                        .setInstallAttributed(true)
                        .setAggregateSource(derivedAggregatableSource1.toString())
                        // shared_debug_key is not shared when the feature is disabled
                        .setDebugKey(SHARED_DEBUG_KEY_1)
                        // adId isn't shared
                        .setDebugAdId(null)
                        // join key isn't shared
                        .setDebugJoinKey(null)
                        .build();

        List<Source> expectedDerivedSources = Collections.singletonList(expectedDerivedSource1);

        // Execution
        XnaSourceCreator xnaSourceCreator = new XnaSourceCreator(mFlags);
        List<Source> parentSources =
                Arrays.asList(sourceWithinLookbackWindow, sourceOutsideLookbackWindow);
        List<Source> actualDerivedSources =
                xnaSourceCreator.generateDerivedSources(trigger, parentSources);

        // Assertion
        assertEquals(expectedDerivedSources, actualDerivedSources);
    }

    private Source.Builder createValidSourceBuilder() {
        return new Source.Builder()
                .setEventId(SourceFixture.ValidSourceParams.SOURCE_EVENT_ID)
                .setPublisher(SourceFixture.ValidSourceParams.PUBLISHER)
                .setAppDestinations(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS)
                .setWebDestinations(SourceFixture.ValidSourceParams.WEB_DESTINATIONS)
                .setEnrollmentId(SourceFixture.ValidSourceParams.ENROLLMENT_ID)
                .setRegistrant(SourceFixture.ValidSourceParams.REGISTRANT)
                .setEventTime(SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME)
                .setExpiryTime(SourceFixture.ValidSourceParams.EXPIRY_TIME)
                .setPriority(SourceFixture.ValidSourceParams.PRIORITY)
                .setSourceType(SourceFixture.ValidSourceParams.SOURCE_TYPE)
                .setInstallAttributionWindow(
                        SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW)
                .setInstallCooldownWindow(SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
                .setAttributionMode(SourceFixture.ValidSourceParams.ATTRIBUTION_MODE)
                .setAggregateSource(SourceFixture.ValidSourceParams.buildAggregateSource())
                .setFilterDataString(buildMatchingFilterData(false))
                .setIsDebugReporting(true)
                .setRegistrationId(SourceFixture.ValidSourceParams.REGISTRATION_ID)
                .setSharedAggregationKeys(SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS)
                .setInstallTime(SourceFixture.ValidSourceParams.INSTALL_TIME)
                .setRegistrationOrigin(SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    }

    private String buildMatchingFilterData(boolean addLookbackWindowFilter) {
        try {
            JSONObject filterMap = new JSONObject();
            filterMap.put(
                    "conversion_subdomain",
                    new JSONArray(Collections.singletonList("electronics.megastore")));
            if (addLookbackWindowFilter) {
                filterMap.put(FilterMap.LOOKBACK_WINDOW, LOOKBACK_WINDOW_VALUE);
            }
            return filterMap.toString();
        } catch (JSONException e) {
            LogUtil.e("JSONException when building aggregate filter data.");
        }
        return null;
    }

    private String buildNonMatchingFilterData() {
        try {
            JSONObject filterMap = new JSONObject();
            filterMap.put(
                    "conversion_subdomain",
                    new JSONArray(Collections.singletonList("non.matching")));
            return filterMap.toString();
        } catch (JSONException e) {
            LogUtil.e("JSONException when building aggregate filter data.");
        }
        return null;
    }

    private String buildMatchingSharedFilterData() {
        try {
            JSONObject filterMap = new JSONObject();
            filterMap.put(
                    "conversion_subdomain",
                    new JSONArray(Collections.singletonList("electronics.megastore")));
            filterMap.put("product", new JSONArray(Collections.singletonList("123")));
            return filterMap.toString();
        } catch (JSONException e) {
            LogUtil.e("JSONException when building aggregate filter data.");
        }
        return null;
    }
}
