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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.AttributionConfig;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.util.Filter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Class facilitates creation of derived source for XNA. */
public class XnaSourceCreator {
    private static final String HEX_PREFIX = "0x";
    private final Flags mFlags;
    private final Filter mFilter;

    public XnaSourceCreator(@NonNull Flags flags) {
        mFlags = flags;
        mFilter = new Filter(flags);
    }

    /**
     * Generates derived sources using the trigger and parent sources.
     *
     * @param trigger trigger for override and filtering for derived sources
     * @param parentSources parent sources to generate derived sources from
     * @return derived sources collection
     */
    public List<Source> generateDerivedSources(
            @NonNull Trigger trigger, @NonNull List<Source> parentSources) {
        List<AttributionConfig> attributionConfigs = new ArrayList<>();
        try {
            JSONArray attributionConfigsJsonArray = new JSONArray(trigger.getAttributionConfig());
            for (int i = 0; i < attributionConfigsJsonArray.length(); i++) {
                attributionConfigs.add(
                        new AttributionConfig.Builder(
                                        attributionConfigsJsonArray.getJSONObject(i), mFlags)
                                .build());
            }
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger().d(e, "Failed to parse attribution configs.");
            return Collections.emptyList();
        }

        Map<String, List<Source>> sourcesByEnrollmentId =
                parentSources.stream().collect(Collectors.groupingBy(Source::getEnrollmentId));
        HashSet<String> alreadyConsumedSourceIds = new HashSet<>();
        return attributionConfigs.stream()
                .map(
                        attributionConfig ->
                                generateDerivedSources(
                                        attributionConfig,
                                        sourcesByEnrollmentId.get(
                                                attributionConfig.getSourceAdtech()),
                                        trigger,
                                        alreadyConsumedSourceIds))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<Source> generateDerivedSources(
            AttributionConfig attributionConfig,
            List<Source> parentSources,
            Trigger trigger,
            Set<String> alreadyConsumedSourceIds) {
        if (parentSources == null) {
            return Collections.emptyList();
        }
        Pair<Long, Long> sourcePriorityRange = attributionConfig.getSourcePriorityRange();
        List<FilterMap> sourceFilters = attributionConfig.getSourceFilters();
        List<FilterMap> sourceNotFilters = attributionConfig.getSourceNotFilters();
        return parentSources.stream()
                // Should not already be used to create a derived source with another
                // attributionConfig
                .filter(source -> !alreadyConsumedSourceIds.contains(source.getId()))
                // Source's priority should fall within the range
                .filter(createSourcePriorityRangePredicate(sourcePriorityRange))
                // Trigger time was before (source event time + attribution config expiry override)
                .filter(createSourceExpiryOverridePredicate(attributionConfig, trigger))
                // Source's filter data should match the provided attributionConfig filters
                .filter(createFilterMatchPredicate(sourceFilters, trigger, true))
                // Source's filter data should not coincide with the provided attributionConfig
                // not_filters
                .filter(createFilterMatchPredicate(sourceNotFilters, trigger, false))
                .map(
                        parentSource -> {
                            alreadyConsumedSourceIds.add(parentSource.getId());
                            return generateDerivedSource(attributionConfig, parentSource, trigger);
                        })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Predicate<Source> createSourceExpiryOverridePredicate(
            AttributionConfig attributionConfig, Trigger trigger) {
        return source ->
                Optional.ofNullable(attributionConfig.getSourceExpiryOverride())
                        .map(TimeUnit.SECONDS::toMillis)
                        .map(
                                expiryOverride ->
                                        (source.getEventTime() + expiryOverride)
                                                >= trigger.getTriggerTime())
                        .orElse(true);
    }

    private Predicate<Source> createSourcePriorityRangePredicate(
            @Nullable Pair<Long, Long> sourcePriorityRange) {
        return (source) ->
                Optional.ofNullable(sourcePriorityRange)
                        .map(
                                range ->
                                        (source.getPriority() >= range.first
                                                && source.getPriority() <= range.second))
                        .orElse(true);
    }

    private Predicate<Source> createFilterMatchPredicate(
            @Nullable List<FilterMap> filterSet, Trigger trigger, boolean match) {
        return (source) ->
                Optional.ofNullable(filterSet)
                        .map(
                                filter -> {
                                    try {
                                        return mFilter.isFilterMatch(
                                                source.getFilterData(trigger, mFlags),
                                                filter,
                                                match);
                                    } catch (JSONException e) {
                                        LoggerFactory.getMeasurementLogger()
                                                .d(e, "Failed to parse source filterData.");
                                        return false;
                                    }
                                })
                        .orElse(true);
    }

    private Optional<Source> generateDerivedSource(
            AttributionConfig attributionConfig, Source parentSource, Trigger trigger) {
        Source.Builder builder = Source.Builder.from(parentSource);
        // A derived source will not be persisted in the DB. Generated reports should be related to
        // a persisted source, so the ID needs to be null to satisfy FK constraint from
        // report -> source table.
        builder.setId(null);
        builder.setParentId(parentSource.getId());
        builder.setStatus(Source.Status.ACTIVE);
        setIfPresent(attributionConfig.getPriority(), builder::setPriority);
        setIfPresent(
                attributionConfig.getPostInstallExclusivityWindow(),
                builder::setInstallCooldownWindow);
        Optional.ofNullable(attributionConfig.getFilterData())
                .map(filterData -> filterData.serializeAsJson(mFlags))
                .map(JSONObject::toString)
                .ifPresent(builder::setFilterDataString);
        builder.setExpiryTime(calculateDerivedSourceExpiry(attributionConfig, parentSource));
        builder.setAggregateSource(createAggregatableSourceWithSharedKeys(parentSource, trigger));

        boolean isInstallAttributed =
                Optional.ofNullable(parentSource.getInstallTime())
                        .map(installTime -> installTime < trigger.getTriggerTime())
                        .orElse(false);
        builder.setInstallAttributed(isInstallAttributed);
        builder.setSharedDebugKey(null);
        if (mFlags.getMeasurementEnableSharedSourceDebugKey()) {
            builder.setDebugKey(parentSource.getSharedDebugKey());
        } else {
            builder.setDebugKey(null);
        }
        // Don't let the serving Ad-tech share the AdId and join key with the derived source
        builder.setDebugAdId(null);
        builder.setDebugJoinKey(null);
        builder.setAggregateReportDedupKeys(new ArrayList<>());
        builder.setEventReportDedupKeys(new ArrayList<>());
        if (mFlags.getMeasurementEnableSharedFilterDataKeysXNA()
                && parentSource.getSharedFilterDataKeys() != null) {
            try {
                builder.setFilterDataString(
                        parentSource
                                .getSharedFilterData(trigger, mFlags)
                                .serializeAsJson(mFlags)
                                .toString());
            } catch (JSONException e) {
                LoggerFactory.getMeasurementLogger().d(e, "Failed to parse shared filter keys.");
                return Optional.empty();
            }
            builder.setSharedFilterDataKeys(null);
        }
        return Optional.of(builder.build());
    }

    private String createAggregatableSourceWithSharedKeys(Source parentSource, Trigger trigger) {
        String sharedAggregationKeysString = parentSource.getSharedAggregationKeys();
        try {
            Optional<AggregatableAttributionSource> aggregateAttributionSource =
                    parentSource.getAggregatableAttributionSource(trigger, mFlags);
            if (sharedAggregationKeysString == null || !aggregateAttributionSource.isPresent()) {
                return null;
            }

            JSONArray sharedAggregationKeysArray = new JSONArray(sharedAggregationKeysString);
            AggregatableAttributionSource baseAggregatableAttributionSource =
                    aggregateAttributionSource.get();
            Map<String, BigInteger> baseAggregatableSource =
                    baseAggregatableAttributionSource.getAggregatableSource();
            Map<String, String> derivedAggregatableSource = new HashMap<>();
            for (int i = 0; i < sharedAggregationKeysArray.length(); i++) {
                String key = sharedAggregationKeysArray.getString(i);
                if (baseAggregatableSource.containsKey(key)) {
                    String hexString = baseAggregatableSource.get(key).toString(16);
                    derivedAggregatableSource.put(key, HEX_PREFIX + hexString);
                }
            }

            return new JSONObject(derivedAggregatableSource).toString();
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "Failed to set AggregatableAttributionSource for derived source.");
            return null;
        }
    }

    private long calculateDerivedSourceExpiry(
            AttributionConfig attributionConfig, Source parentSource) {
        long parentSourceExpiry = parentSource.getExpiryTime();
        long attributionConfigExpiry =
                Optional.ofNullable(attributionConfig.getExpiry())
                        .map(TimeUnit.SECONDS::toMillis)
                        .map(expiry -> (parentSource.getEventTime() + expiry))
                        .orElse(Long.MAX_VALUE);
        return Math.min(parentSourceExpiry, attributionConfigExpiry);
    }

    private <T> void setIfPresent(T nullableValue, Consumer<T> setter) {
        Optional.ofNullable(nullableValue).ifPresent(setter);
    }
}
