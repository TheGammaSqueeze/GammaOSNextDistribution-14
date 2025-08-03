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
package com.android.adservices.service.measurement.registration;

import static com.android.adservices.service.measurement.util.BaseUriExtractor.getBaseUri;
import static com.android.adservices.service.measurement.util.MathUtils.extractValidNumberInRange;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.MeasurementHttpClient;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.TriggerSpec;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.util.Enrollment;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Download and decode Response Based Registration
 *
 * @hide
 */
public class AsyncSourceFetcher {

    private static final long ONE_DAY_IN_SECONDS = TimeUnit.DAYS.toSeconds(1);
    private static final String DEFAULT_ANDROID_APP_SCHEME = "android-app";
    private static final String DEFAULT_ANDROID_APP_URI_PREFIX = DEFAULT_ANDROID_APP_SCHEME + "://";
    private final MeasurementHttpClient mNetworkConnection;
    private final EnrollmentDao mEnrollmentDao;
    private final Flags mFlags;
    private final Context mContext;

    public AsyncSourceFetcher(Context context) {
        this(
                context,
                EnrollmentDao.getInstance(context),
                FlagsFactory.getFlags());
    }

    @VisibleForTesting
    public AsyncSourceFetcher(Context context, EnrollmentDao enrollmentDao, Flags flags) {
        mContext = context;
        mEnrollmentDao = enrollmentDao;
        mFlags = flags;
        mNetworkConnection = new MeasurementHttpClient(context);
    }

    private boolean parseCommonSourceParams(
            JSONObject json,
            AsyncRegistration asyncRegistration,
            Source.Builder builder,
            String enrollmentId)
            throws JSONException {
        if (json.isNull(SourceHeaderContract.DESTINATION)
                && json.isNull(SourceHeaderContract.WEB_DESTINATION)) {
            throw new JSONException("Expected a destination");
        }
        long sourceEventTime = asyncRegistration.getRequestTime();
        UnsignedLong eventId = new UnsignedLong(0L);
        if (!json.isNull(SourceHeaderContract.SOURCE_EVENT_ID)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                Optional<UnsignedLong> maybeEventId =
                        FetcherUtil.extractUnsignedLong(json, SourceHeaderContract.SOURCE_EVENT_ID);
                if (!maybeEventId.isPresent()) {
                    return false;
                }
                eventId = maybeEventId.get();
            } else {
                try {
                    eventId = new UnsignedLong(
                            json.getString(SourceHeaderContract.SOURCE_EVENT_ID));
                } catch (NumberFormatException e) {
                    LoggerFactory.getMeasurementLogger()
                            .d(e, "parseCommonSourceParams: parsing source_event_id failed.");
                }
            }
        }
        builder.setEventId(eventId);
        long expiry;
        if (!json.isNull(SourceHeaderContract.EXPIRY)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                UnsignedLong expiryUnsigned =
                        extractValidNumberInRange(
                                new UnsignedLong(json.getString(SourceHeaderContract.EXPIRY)),
                                new UnsignedLong(
                                        mFlags.getMeasurementMinReportingRegisterSourceExpirationInSeconds()),
                                new UnsignedLong(
                                        mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds()));
                // Relies on expiryUnsigned not using the 64th bit.
                expiry = expiryUnsigned.getValue();
            } else {
                expiry =
                        extractValidNumberInRange(
                                json.getLong(SourceHeaderContract.EXPIRY),
                                mFlags.getMeasurementMinReportingRegisterSourceExpirationInSeconds(),
                                mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds());
            }
            if (asyncRegistration.getSourceType() == Source.SourceType.EVENT) {
                expiry = roundSecondsToWholeDays(expiry);
            }
        } else {
            expiry = mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds();
        }
        builder.setExpiryTime(sourceEventTime + TimeUnit.SECONDS.toMillis(expiry));
        long effectiveExpiry = expiry;
        if (!json.isNull(SourceHeaderContract.EVENT_REPORT_WINDOW)) {
            long eventReportWindow;
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                UnsignedLong eventReportWindowUnsigned =
                        extractValidNumberInRange(
                                new UnsignedLong(
                                        json.getString(SourceHeaderContract.EVENT_REPORT_WINDOW)),
                                new UnsignedLong(
                                        mFlags.getMeasurementMinimumEventReportWindowInSeconds()),
                                new UnsignedLong(
                                        mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds()));
                // Relies on eventReportWindowUnsigned not using the 64th bit.
                eventReportWindow = Math.min(expiry, eventReportWindowUnsigned.getValue());
            } else {
                eventReportWindow =
                        Math.min(
                                expiry,
                                extractValidNumberInRange(
                                        json.getLong(SourceHeaderContract.EVENT_REPORT_WINDOW),
                                        mFlags.getMeasurementMinimumEventReportWindowInSeconds(),
                                        mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds()));
            }
            effectiveExpiry = eventReportWindow;
            builder.setEventReportWindow(TimeUnit.SECONDS.toMillis(eventReportWindow));
        }
        long aggregateReportWindow;
        if (!json.isNull(SourceHeaderContract.AGGREGATABLE_REPORT_WINDOW)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                // Registration will be rejected if parsing unsigned long throws.
                UnsignedLong aggregateReportWindowUnsigned =
                        extractValidNumberInRange(
                                new UnsignedLong(
                                        json.getString(
                                                SourceHeaderContract.AGGREGATABLE_REPORT_WINDOW)),
                                new UnsignedLong(
                                        mFlags.getMeasurementMinimumAggregatableReportWindowInSeconds()),
                                new UnsignedLong(
                                        mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds()));
                // Relies on aggregateReportWindowUnsigned not using the 64th bit.
                aggregateReportWindow = Math.min(expiry, aggregateReportWindowUnsigned.getValue());
            } else {
                aggregateReportWindow = Math.min(expiry, extractValidNumberInRange(
                        json.getLong(
                                SourceHeaderContract.AGGREGATABLE_REPORT_WINDOW),
                                mFlags.getMeasurementMinReportingRegisterSourceExpirationInSeconds(),
                                mFlags.getMeasurementMaxReportingRegisterSourceExpirationInSeconds()));
            }
        } else {
            aggregateReportWindow = expiry;
        }
        builder.setAggregatableReportWindow(
                sourceEventTime + TimeUnit.SECONDS.toMillis(aggregateReportWindow));

        if (!json.isNull(SourceHeaderContract.PRIORITY)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                Optional<Long> maybePriority =
                        FetcherUtil.extractLongString(json, SourceHeaderContract.PRIORITY);
                if (!maybePriority.isPresent()) {
                    return false;
                }
                builder.setPriority(maybePriority.get());
            } else {
                builder.setPriority(json.getLong(SourceHeaderContract.PRIORITY));
            }
        }

        if (!json.isNull(SourceHeaderContract.DEBUG_REPORTING)) {
            builder.setIsDebugReporting(json.optBoolean(SourceHeaderContract.DEBUG_REPORTING));
        }
        if (!json.isNull(SourceHeaderContract.DEBUG_KEY)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                Optional<UnsignedLong> maybeDebugKey =
                        FetcherUtil.extractUnsignedLong(json, SourceHeaderContract.DEBUG_KEY);
                if (maybeDebugKey.isPresent()) {
                    builder.setDebugKey(maybeDebugKey.get());
                }
            } else {
                try {
                    builder.setDebugKey(
                            new UnsignedLong(json.getString(SourceHeaderContract.DEBUG_KEY)));
                } catch (NumberFormatException e) {
                    LoggerFactory.getMeasurementLogger()
                            .e(e, "parseCommonSourceParams: parsing debug key failed");
                }
            }
        }
        if (!json.isNull(SourceHeaderContract.INSTALL_ATTRIBUTION_WINDOW_KEY)) {
            long installAttributionWindow =
                    extractValidNumberInRange(
                            json.getLong(SourceHeaderContract.INSTALL_ATTRIBUTION_WINDOW_KEY),
                            mFlags.getMeasurementMinInstallAttributionWindow(),
                            mFlags.getMeasurementMaxInstallAttributionWindow());
            builder.setInstallAttributionWindow(
                    TimeUnit.SECONDS.toMillis(installAttributionWindow));
        } else {
            builder.setInstallAttributionWindow(
                    TimeUnit.SECONDS.toMillis(mFlags.getMeasurementMaxInstallAttributionWindow()));
        }
        if (!json.isNull(SourceHeaderContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY)) {
            long installCooldownWindow =
                    extractValidNumberInRange(
                            json.getLong(SourceHeaderContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY),
                            mFlags.getMeasurementMinPostInstallExclusivityWindow(),
                            mFlags.getMeasurementMaxPostInstallExclusivityWindow());
            builder.setInstallCooldownWindow(TimeUnit.SECONDS.toMillis(installCooldownWindow));
        } else {
            builder.setInstallCooldownWindow(
                    TimeUnit.SECONDS.toMillis(
                            mFlags.getMeasurementMinPostInstallExclusivityWindow()));
        }
        // This "filter_data" field is used to generate reports.
        if (!json.isNull(SourceHeaderContract.FILTER_DATA)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                JSONObject maybeFilterData = json.optJSONObject(SourceHeaderContract.FILTER_DATA);
                if (maybeFilterData != null && maybeFilterData.has("source_type")) {
                    LoggerFactory.getMeasurementLogger()
                            .d("Source filter-data includes 'source_type' key.");
                    return false;
                }
                if (!FetcherUtil.areValidAttributionFilters(
                        maybeFilterData,
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true)) {
                    LoggerFactory.getMeasurementLogger().d("Source filter-data is invalid.");
                    return false;
                }
                builder.setFilterDataString(maybeFilterData.toString());
            } else {
                if (!FetcherUtil.areValidAttributionFilters(
                        json.optJSONObject(SourceHeaderContract.FILTER_DATA),
                        mFlags,
                        /* canIncludeLookbackWindow= */ false,
                        /* shouldCheckFilterSize= */ true)) {
                    LoggerFactory.getMeasurementLogger().d("Source filter-data is invalid.");
                    return false;
                }
                builder.setFilterDataString(
                        json.getJSONObject(SourceHeaderContract.FILTER_DATA).toString());
            }
        }

        Uri appUri = null;
        if (!json.isNull(SourceHeaderContract.DESTINATION)) {
            appUri = Uri.parse(json.getString(SourceHeaderContract.DESTINATION));
            if (appUri.getScheme() == null) {
                LoggerFactory.getMeasurementLogger()
                        .d("App destination is missing app scheme, adding.");
                appUri = Uri.parse(DEFAULT_ANDROID_APP_URI_PREFIX + appUri);
            }
            if (!DEFAULT_ANDROID_APP_SCHEME.equals(appUri.getScheme())) {
                LoggerFactory.getMeasurementLogger()
                        .e(
                                "Invalid scheme for app destination: %s; dropping the source.",
                                appUri.getScheme());
                return false;
            }
        }

        String enrollmentBlockList =
                mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist();
        Set<String> blockedEnrollmentsString =
                new HashSet<>(AllowLists.splitAllowList(enrollmentBlockList));
        if (!AllowLists.doesAllowListAllowAll(enrollmentBlockList)
                && !blockedEnrollmentsString.contains(enrollmentId)
                && !json.isNull(SourceHeaderContract.DEBUG_AD_ID)) {
            builder.setDebugAdId(json.optString(SourceHeaderContract.DEBUG_AD_ID));
        }

        Set<String> allowedEnrollmentsString =
                new HashSet<>(
                        AllowLists.splitAllowList(
                                mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()));
        if (allowedEnrollmentsString.contains(enrollmentId)
                && !json.isNull(SourceHeaderContract.DEBUG_JOIN_KEY)) {
            builder.setDebugJoinKey(json.optString(SourceHeaderContract.DEBUG_JOIN_KEY));
        }

        if (asyncRegistration.isWebRequest()
                // Only validate when non-null in request
                && asyncRegistration.getOsDestination() != null
                && !asyncRegistration.getOsDestination().equals(appUri)) {
            LoggerFactory.getMeasurementLogger()
                    .d("Expected destination to match with the supplied one!");
            return false;
        }

        if (appUri != null) {
            builder.setAppDestinations(Collections.singletonList(getBaseUri(appUri)));
        }

        boolean shouldMatchAtLeastOneWebDestination =
                asyncRegistration.isWebRequest() && asyncRegistration.getWebDestination() != null;
        boolean matchedOneWebDestination = false;

        if (!json.isNull(SourceHeaderContract.WEB_DESTINATION)) {
            Set<Uri> destinationSet = new HashSet<>();
            JSONArray jsonDestinations;
            Object obj = json.get(SourceHeaderContract.WEB_DESTINATION);
            if (obj instanceof String) {
                jsonDestinations = new JSONArray();
                jsonDestinations.put(json.getString(SourceHeaderContract.WEB_DESTINATION));
            } else {
                jsonDestinations = json.getJSONArray(SourceHeaderContract.WEB_DESTINATION);
            }
            if (jsonDestinations.length()
                    > mFlags.getMeasurementMaxDistinctWebDestinationsInSourceRegistration()) {
                LoggerFactory.getMeasurementLogger()
                        .d("Source registration exceeded the number of allowed destinations.");
                return false;
            }
            for (int i = 0; i < jsonDestinations.length(); i++) {
                Uri destination = Uri.parse(jsonDestinations.getString(i));
                if (shouldMatchAtLeastOneWebDestination
                        && asyncRegistration.getWebDestination().equals(destination)) {
                    matchedOneWebDestination = true;
                }
                Optional<Uri> topPrivateDomainAndScheme =
                        WebAddresses.topPrivateDomainAndScheme(destination);
                if (topPrivateDomainAndScheme.isEmpty()) {
                    LoggerFactory.getMeasurementLogger()
                            .d(
                                    "Unable to extract top private domain and scheme from web "
                                            + "destination.");
                    return false;
                } else {
                    destinationSet.add(topPrivateDomainAndScheme.get());
                }
            }
            List<Uri> destinationList = new ArrayList<>(destinationSet);
            builder.setWebDestinations(destinationList);
        }

        if (mFlags.getMeasurementEnableCoarseEventReportDestinations()
                && !json.isNull(SourceHeaderContract.COARSE_EVENT_REPORT_DESTINATIONS)) {
            builder.setCoarseEventReportDestinations(
                    json.getBoolean(SourceHeaderContract.COARSE_EVENT_REPORT_DESTINATIONS));
        }

        if (shouldMatchAtLeastOneWebDestination && !matchedOneWebDestination) {
            LoggerFactory.getMeasurementLogger()
                    .d("Expected at least one web_destination to match with the supplied one!");
            return false;
        }

        Source.TriggerDataMatching triggerDataMatching = Source.TriggerDataMatching.MODULUS;

        if (mFlags.getMeasurementEnableTriggerDataMatching()
                && !json.isNull(SourceHeaderContract.TRIGGER_DATA_MATCHING)) {
            // If the token for trigger_data_matching is not in the predefined list, it will throw
            // IllegalArgumentException that will be caught by the overall parser.
            triggerDataMatching =
                    Source.TriggerDataMatching.valueOf(
                            json
                                    .getString(SourceHeaderContract.TRIGGER_DATA_MATCHING)
                                    .toUpperCase(Locale.ENGLISH));
            builder.setTriggerDataMatching(triggerDataMatching);
        }

        JSONObject eventReportWindows = null;
        Integer maxEventLevelReports = null;
        if (mFlags.getMeasurementFlexLiteApiEnabled()
                || mFlags.getMeasurementFlexibleEventReportingApiEnabled()) {
            if (!json.isNull(SourceHeaderContract.MAX_EVENT_LEVEL_REPORTS)) {
                Object maxEventLevelReportsObj = json.get(
                        SourceHeaderContract.MAX_EVENT_LEVEL_REPORTS);
                maxEventLevelReports =
                        json.getInt(SourceHeaderContract.MAX_EVENT_LEVEL_REPORTS);
                if (!FetcherUtil.is64BitInteger(maxEventLevelReportsObj) || maxEventLevelReports < 0
                        || maxEventLevelReports > mFlags.getMeasurementFlexApiMaxEventReports()) {
                    return false;
                }
                builder.setMaxEventLevelReports(maxEventLevelReports);
            }

            if (!json.isNull(SourceHeaderContract.EVENT_REPORT_WINDOWS)) {
                if (!json.isNull(SourceHeaderContract.EVENT_REPORT_WINDOW)) {
                    LoggerFactory.getMeasurementLogger()
                            .d(
                                    "Only one of event_report_window and event_report_windows is"
                                            + " expected");
                    return false;
                }
                Optional<JSONObject> maybeEventReportWindows =
                        getValidEventReportWindows(
                                new JSONObject(
                                        json.getString(SourceHeaderContract.EVENT_REPORT_WINDOWS)),
                                expiry);
                if (!maybeEventReportWindows.isPresent()) {
                    LoggerFactory.getMeasurementLogger()
                            .d("Invalid value for event_report_windows");
                    return false;
                }
                eventReportWindows = maybeEventReportWindows.get();
                builder.setEventReportWindows(eventReportWindows.toString());
            }
        }

        if (mFlags.getMeasurementFlexibleEventReportingApiEnabled()
                && (!json.isNull(SourceHeaderContract.TRIGGER_SPECS)
                        || !json.isNull(SourceHeaderContract.TRIGGER_DATA))) {
            String triggerSpecString;
            if (!json.isNull(SourceHeaderContract.TRIGGER_DATA)) {
                if (!json.isNull(SourceHeaderContract.TRIGGER_SPECS)) {
                    LoggerFactory.getMeasurementLogger().d(
                            "Only one of trigger_data or trigger_specs is expected");
                    return false;
                }
                JSONArray triggerData = json.getJSONArray(SourceHeaderContract.TRIGGER_DATA);
                // Empty top-level trigger data results in an empty trigger specs list.
                if (triggerData.length() == 0) {
                    triggerSpecString = triggerData.toString();
                // Populated top-level trigger data results in one trigger spec object.
                } else {
                    JSONArray triggerSpecsArray = new JSONArray();
                    JSONObject triggerSpec = new JSONObject();
                    triggerSpec.put(SourceHeaderContract.TRIGGER_DATA, triggerData);
                    triggerSpecsArray.put(triggerSpec);
                    triggerSpecString = triggerSpecsArray.toString();
                }
            } else {
                triggerSpecString = json.getString(SourceHeaderContract.TRIGGER_SPECS);
            }

            final int finalMaxEventLevelReports =
                    Source.getOrDefaultMaxEventLevelReports(
                            asyncRegistration.getSourceType(),
                            maxEventLevelReports,
                            mFlags);

            Optional<TriggerSpec[]> maybeTriggerSpecArray =
                    getValidTriggerSpecs(
                            triggerSpecString,
                            eventReportWindows,
                            effectiveExpiry,
                            asyncRegistration.getSourceType(),
                            finalMaxEventLevelReports,
                            triggerDataMatching);

            if (!maybeTriggerSpecArray.isPresent()) {
                LoggerFactory.getMeasurementLogger().d("Invalid Trigger Spec format");
                return false;
            }

            builder.setTriggerSpecs(
                    new TriggerSpecs(
                            maybeTriggerSpecArray.get(),
                            finalMaxEventLevelReports,
                            null));
        }

        if (mFlags.getMeasurementEnableSharedSourceDebugKey()
                && !json.isNull(SourceHeaderContract.SHARED_DEBUG_KEY)) {
            try {
                builder.setSharedDebugKey(
                        new UnsignedLong(json.getString(SourceHeaderContract.SHARED_DEBUG_KEY)));
            } catch (NumberFormatException e) {
                LoggerFactory.getMeasurementLogger()
                        .e(e, "parseCommonSourceParams: parsing shared debug key failed");
            }
        }
        return true;
    }

    private Optional<TriggerSpec[]> getValidTriggerSpecs(
            String triggerSpecString,
            JSONObject eventReportWindows,
            long expiry,
            Source.SourceType sourceType,
            int maxEventLevelReports,
            Source.TriggerDataMatching triggerDataMatching) {
        List<Pair<Long, Long>> parsedEventReportWindows =
                Source.getOrDefaultEventReportWindowsForFlex(
                        eventReportWindows, sourceType, TimeUnit.SECONDS.toMillis(expiry), mFlags);
        long defaultStart = parsedEventReportWindows.get(0).first;
        List<Long> defaultEnds =
                parsedEventReportWindows.stream().map((x) -> x.second).collect(Collectors.toList());
        try {
            JSONArray triggerSpecArray = new JSONArray(triggerSpecString);
            TriggerSpec[] validTriggerSpecs = new TriggerSpec[triggerSpecArray.length()];
            Set<UnsignedLong> triggerDataSet = new HashSet<>();
            for (int i = 0; i < triggerSpecArray.length(); i++) {
                Optional<TriggerSpec> maybeTriggerSpec = getValidTriggerSpec(
                        triggerSpecArray.getJSONObject(i),
                        expiry,
                        defaultStart,
                        defaultEnds,
                        triggerDataSet,
                        maxEventLevelReports);
                if (!maybeTriggerSpec.isPresent()) {
                    return Optional.empty();
                }
                validTriggerSpecs[i] = maybeTriggerSpec.get();
            }
            // Check cardinality of trigger_data across the whole trigger spec array
            if (triggerDataSet.size() > mFlags.getMeasurementFlexApiMaxTriggerDataCardinality()) {
                return Optional.empty();
            }
            if (mFlags.getMeasurementEnableTriggerDataMatching()
                    && triggerDataMatching == Source.TriggerDataMatching.MODULUS
                    && !isContiguousStartingAtZero(triggerDataSet)) {
                return Optional.empty();
            }
            return Optional.of(validTriggerSpecs);
        } catch (JSONException | IllegalArgumentException ex) {
            LoggerFactory.getMeasurementLogger().d(ex, "Trigger Spec parsing failed");
            return Optional.empty();
        }
    }

    private Optional<TriggerSpec> getValidTriggerSpec(
            JSONObject triggerSpecJson,
            long expiry,
            long defaultStart,
            List<Long> defaultEnds,
            Set<UnsignedLong> triggerDataSet,
            int maxEventLevelReports) throws JSONException {
        Optional<JSONArray> maybeTriggerDataListJson = extractLongJsonArray(
                triggerSpecJson, TriggerSpecs.FlexEventReportJsonKeys.TRIGGER_DATA);
        if (maybeTriggerDataListJson.isEmpty()) {
            return Optional.empty();
        }
        List<UnsignedLong> triggerDataList =
                TriggerSpec.getTriggerDataArrayFromJSON(maybeTriggerDataListJson.get());
        if (triggerDataList.isEmpty()
                || triggerDataList.size()
                        > mFlags.getMeasurementFlexApiMaxTriggerDataCardinality()) {
            return Optional.empty();
        }
        // Check exclusivity of trigger_data across the whole trigger spec array, and validate
        // trigger data magnitude.
        for (UnsignedLong triggerData : triggerDataList) {
            if (!triggerDataSet.add(triggerData)
                    || triggerData.compareTo(TriggerSpecs.MAX_TRIGGER_DATA_VALUE) > 0) {
                return Optional.empty();
            }
        }

        if (!triggerSpecJson.isNull(TriggerSpecs.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS)) {
            Optional<JSONObject> maybeEventReportWindows =
                    getValidEventReportWindows(
                            triggerSpecJson.getJSONObject(
                                    TriggerSpecs.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS),
                            expiry);
            if (!maybeEventReportWindows.isPresent()) {
                return Optional.empty();
            }
        }

        TriggerSpec.SummaryOperatorType summaryWindowOperator =
                TriggerSpec.SummaryOperatorType.COUNT;
        if (!triggerSpecJson.isNull(TriggerSpecs.FlexEventReportJsonKeys.SUMMARY_WINDOW_OPERATOR)) {
            // If a summary window operator is not in the predefined list, it will throw
            // IllegalArgumentException that will be caught by the overall parser.
            summaryWindowOperator =
                    TriggerSpec.SummaryOperatorType.valueOf(
                            triggerSpecJson
                                    .getString(
                                            TriggerSpecs.FlexEventReportJsonKeys
                                                    .SUMMARY_WINDOW_OPERATOR)
                                    .toUpperCase(Locale.ENGLISH));
        }
        List<Long> summaryBuckets = null;
        if (!triggerSpecJson.isNull(TriggerSpecs.FlexEventReportJsonKeys.SUMMARY_BUCKETS)) {
            Optional<JSONArray> maybeSummaryBucketsJson = extractLongJsonArray(
                    triggerSpecJson, TriggerSpecs.FlexEventReportJsonKeys.SUMMARY_BUCKETS);

            if (maybeSummaryBucketsJson.isEmpty()) {
                return Optional.empty();
            }

            summaryBuckets = TriggerSpec.getLongListFromJSON(maybeSummaryBucketsJson.get());

            if (summaryBuckets.isEmpty() || summaryBuckets.size() > maxEventLevelReports
                    || !TriggerSpec.isStrictIncreasing(summaryBuckets)) {
                return Optional.empty();
            }

            for (Long bucket : summaryBuckets) {
                if (bucket < 0L || bucket > TriggerSpecs.MAX_BUCKET_THRESHOLD) {
                    return Optional.empty();
                }
            }
        }

        return Optional.of(
              new TriggerSpec.Builder(
                      triggerSpecJson,
                      defaultStart,
                      defaultEnds,
                      maxEventLevelReports).build());
    }

    private Optional<JSONObject> getValidEventReportWindows(JSONObject jsonReportWindows,
            long expiry) throws JSONException {
        // Start time in seconds
        long startTime = 0;
        if (!jsonReportWindows.isNull(TriggerSpecs.FlexEventReportJsonKeys.START_TIME)) {
            if (!FetcherUtil.is64BitInteger(jsonReportWindows.get(
                    TriggerSpecs.FlexEventReportJsonKeys.START_TIME))) {
                return Optional.empty();
            }
            // We continue to use startTime in seconds for validation but convert it to milliseconds
            // for the return JSONObject.
            startTime =
                    jsonReportWindows.getLong(TriggerSpecs.FlexEventReportJsonKeys.START_TIME);
            jsonReportWindows.put(TriggerSpecs.FlexEventReportJsonKeys.START_TIME,
                    TimeUnit.SECONDS.toMillis(startTime));
        }
        if (startTime < 0 || startTime > expiry) {
            return Optional.empty();
        }

        Optional<JSONArray> maybeWindowEndsJson = extractLongJsonArray(
                jsonReportWindows, TriggerSpecs.FlexEventReportJsonKeys.END_TIMES);

        if (maybeWindowEndsJson.isEmpty()) {
            return Optional.empty();
        }

        List<Long> windowEnds = TriggerSpec.getLongListFromJSON(maybeWindowEndsJson.get());

        int windowEndsSize = windowEnds.size();
        if (windowEnds.isEmpty()
                || windowEndsSize > mFlags.getMeasurementFlexApiMaxEventReportWindows()) {
            return Optional.empty();
        }

        // Clamp last window end to expiry and min event report window.
        Long lastWindowsEnd = windowEnds.get(windowEndsSize - 1);
        if (lastWindowsEnd < 0) {
            return Optional.empty();
        }
        windowEnds.set(windowEndsSize - 1, extractValidNumberInRange(
                lastWindowsEnd,
                mFlags.getMeasurementMinimumEventReportWindowInSeconds(),
                expiry));

        if (windowEndsSize > 1) {
            // Clamp first window end to min event report window
            Long firstWindowsEnd = windowEnds.get(0);
            if (firstWindowsEnd < 0) {
                return Optional.empty();
            }
            windowEnds.set(0, Math.max(
                    firstWindowsEnd,
                    mFlags.getMeasurementMinimumEventReportWindowInSeconds()));
        }

        if (startTime >= windowEnds.get(0) || !TriggerSpec.isStrictIncreasing(windowEnds)) {
            return Optional.empty();
        }

        jsonReportWindows.put(
                TriggerSpecs.FlexEventReportJsonKeys.END_TIMES,
                // Convert end times to milliseconds for internal implementation.
                new JSONArray(windowEnds.stream().map((x) ->
                        TimeUnit.SECONDS.toMillis(x)).collect(Collectors.toList())));

        return Optional.of(jsonReportWindows);
    }

    /** Parse a {@code Source}, given response headers, adding the {@code Source} to a given list */
    @VisibleForTesting
    public Optional<Source> parseSource(
            AsyncRegistration asyncRegistration,
            String enrollmentId,
            Map<String, List<String>> headers,
            AsyncFetchStatus asyncFetchStatus) {
        boolean arDebugPermission = asyncRegistration.getDebugKeyAllowed();
        LoggerFactory.getMeasurementLogger()
                .d("Source ArDebug permission enabled %b", arDebugPermission);
        Source.Builder builder = new Source.Builder();
        builder.setRegistrationId(asyncRegistration.getRegistrationId());
        builder.setPublisher(getBaseUri(asyncRegistration.getTopOrigin()));
        builder.setEnrollmentId(enrollmentId);
        builder.setRegistrant(asyncRegistration.getRegistrant());
        builder.setSourceType(asyncRegistration.getSourceType());
        builder.setAttributionMode(Source.AttributionMode.TRUTHFULLY);
        builder.setEventTime(asyncRegistration.getRequestTime());
        builder.setAdIdPermission(asyncRegistration.hasAdIdPermission());
        builder.setArDebugPermission(arDebugPermission);
        builder.setPublisherType(
                asyncRegistration.isWebRequest() ? EventSurfaceType.WEB : EventSurfaceType.APP);
        Optional<Uri> registrationUriOrigin =
                WebAddresses.originAndScheme(asyncRegistration.getRegistrationUri());
        if (!registrationUriOrigin.isPresent()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AsyncSourceFetcher: "
                                    + "Invalid or empty registration uri - "
                                    + asyncRegistration.getRegistrationUri());
            return Optional.empty();
        }
        builder.setRegistrationOrigin(registrationUriOrigin.get());

        builder.setPlatformAdId(asyncRegistration.getPlatformAdId());

        List<String> field =
                headers.get(SourceHeaderContract.HEADER_ATTRIBUTION_REPORTING_REGISTER_SOURCE);
        if (field == null || field.size() != 1) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AsyncSourceFetcher: "
                                    + "Invalid Attribution-Reporting-Register-Source header.");
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.HEADER_ERROR);
            return Optional.empty();
        }
        try {
            JSONObject json = new JSONObject(field.get(0));
            boolean isValid =
                    parseCommonSourceParams(json, asyncRegistration, builder, enrollmentId);
            if (!isValid) {
                asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                return Optional.empty();
            }
            if (!json.isNull(SourceHeaderContract.AGGREGATION_KEYS)) {
                if (!areValidAggregationKeys(
                        json.getJSONObject(SourceHeaderContract.AGGREGATION_KEYS))) {
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }
                builder.setAggregateSource(json.getString(SourceHeaderContract.AGGREGATION_KEYS));
            }
            if (mFlags.getMeasurementEnableXNA()
                    && !json.isNull(SourceHeaderContract.SHARED_AGGREGATION_KEYS)) {
                // Parsed as JSONArray for validation
                JSONArray sharedAggregationKeys =
                        json.getJSONArray(SourceHeaderContract.SHARED_AGGREGATION_KEYS);
                builder.setSharedAggregationKeys(sharedAggregationKeys.toString());
            }
            if (mFlags.getMeasurementEnableSharedFilterDataKeysXNA()
                    && !json.isNull(SourceHeaderContract.SHARED_FILTER_DATA_KEYS)) {
                // Parsed as JSONArray for validation
                JSONArray sharedFilterDataKeys =
                        json.getJSONArray(SourceHeaderContract.SHARED_FILTER_DATA_KEYS);
                builder.setSharedFilterDataKeys(sharedFilterDataKeys.toString());
            }
            if (mFlags.getMeasurementEnablePreinstallCheck()
                    && !json.isNull(SourceHeaderContract.DROP_SOURCE_IF_INSTALLED)) {
                builder.setDropSourceIfInstalled(
                        json.getBoolean(SourceHeaderContract.DROP_SOURCE_IF_INSTALLED));
            }
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.SUCCESS);
            Source source = builder.build();
            // Build privacy parameters, catching an arithmetic exception in case an inordinate
            // number of report states is presented.
            source.hasValidInformationGain(mFlags);
            return Optional.of(source);
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger().d(e, "AsyncSourceFetcher: invalid JSON");
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.PARSING_ERROR);
            return Optional.empty();
        } catch (IllegalArgumentException | ArithmeticException e) {
            LoggerFactory.getMeasurementLogger().d(e, "AsyncSourceFetcher: IllegalArgumentException"
                    + " or ArithmeticException");
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
            return Optional.empty();
        }
    }

    /** Provided a testing hook. */
    @NonNull
    @VisibleForTesting
    public URLConnection openUrl(@NonNull URL url) throws IOException {
        return mNetworkConnection.setup(url);
    }

    /**
     * Fetch a source type registration.
     *
     * @param asyncRegistration a {@link AsyncRegistration}, a request the record.
     * @param asyncFetchStatus a {@link AsyncFetchStatus}, stores Ad Tech server status.
     */
    public Optional<Source> fetchSource(
            AsyncRegistration asyncRegistration,
            AsyncFetchStatus asyncFetchStatus,
            AsyncRedirects asyncRedirects) {
        HttpURLConnection urlConnection = null;
        Map<String, List<String>> headers;
        if (!asyncRegistration.getRegistrationUri().getScheme().equalsIgnoreCase("https")) {
            LoggerFactory.getMeasurementLogger().d("Invalid scheme for registrationUri.");
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.INVALID_URL);
            return Optional.empty();
        }
        // TODO(b/276825561): Fix code duplication between fetchSource & fetchTrigger request flow
        try {
            urlConnection =
                    (HttpURLConnection)
                            openUrl(new URL(asyncRegistration.getRegistrationUri().toString()));
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty(
                    SourceRequestContract.SOURCE_INFO,
                    asyncRegistration.getSourceType().getValue());
            urlConnection.setInstanceFollowRedirects(false);
            String body = asyncRegistration.getPostBody();
            if (mFlags.getFledgeMeasurementReportAndRegisterEventApiEnabled() && body != null) {
                urlConnection.setRequestProperty("Content-Type", "text/plain");
                urlConnection.setDoOutput(true);
                OutputStream os = urlConnection.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                osw.write(body);
                osw.flush();
                osw.close();
            }

            headers = urlConnection.getHeaderFields();
            asyncFetchStatus.setResponseSize(FetcherUtil.calculateHeadersCharactersLength(headers));
            int responseCode = urlConnection.getResponseCode();
            LoggerFactory.getMeasurementLogger().d("Response code = " + responseCode);
            if (!FetcherUtil.isRedirect(responseCode) && !FetcherUtil.isSuccess(responseCode)) {
                asyncFetchStatus.setResponseStatus(
                        AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                return Optional.empty();
            }
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
        } catch (MalformedURLException e) {
            LoggerFactory.getMeasurementLogger().d(e, "Malformed registration target URL");
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.INVALID_URL);
            return Optional.empty();
        } catch (IOException e) {
            LoggerFactory.getMeasurementLogger().e(e, "Failed to get registration response");
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
            return Optional.empty();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        asyncRedirects.configure(headers, mFlags, asyncRegistration);

        if (!isSourceHeaderPresent(headers)) {
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.HEADER_MISSING);
            asyncFetchStatus.setRedirectOnlyStatus(true);
            return Optional.empty();
        }

        Optional<String> enrollmentId =
                mFlags.isDisableMeasurementEnrollmentCheck()
                        ? WebAddresses.topPrivateDomainAndScheme(
                                        asyncRegistration.getRegistrationUri())
                                .map(Uri::toString)
                        : Enrollment.getValidEnrollmentId(
                                asyncRegistration.getRegistrationUri(),
                                asyncRegistration.getRegistrant().getAuthority(),
                                mEnrollmentDao,
                                mContext,
                                mFlags);
        if (enrollmentId.isEmpty()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "fetchSource: Valid enrollment id not found. Registration URI: %s",
                            asyncRegistration.getRegistrationUri());
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.INVALID_ENROLLMENT);
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return Optional.empty();
        }

        Optional<Source> parsedSource =
                parseSource(asyncRegistration, enrollmentId.get(), headers, asyncFetchStatus);
        return parsedSource;
    }

    private boolean isSourceHeaderPresent(Map<String, List<String>> headers) {
        return headers.containsKey(
                SourceHeaderContract.HEADER_ATTRIBUTION_REPORTING_REGISTER_SOURCE);
    }

    private boolean areValidAggregationKeys(JSONObject aggregationKeys) {
        if (aggregationKeys.length()
                > mFlags.getMeasurementMaxAggregateKeysPerSourceRegistration()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Aggregation-keys have more entries than permitted. %s",
                            aggregationKeys.length());
            return false;
        }
        for (String id : aggregationKeys.keySet()) {
            if (!FetcherUtil.isValidAggregateKeyId(id)) {
                LoggerFactory.getMeasurementLogger()
                        .d("SourceFetcher: aggregation key ID is invalid. %s", id);
                return false;
            }
            String keyPiece = aggregationKeys.optString(id);
            if (!FetcherUtil.isValidAggregateKeyPiece(keyPiece, mFlags)) {
                LoggerFactory.getMeasurementLogger()
                        .d("SourceFetcher: aggregation key-piece is invalid. %s", keyPiece);
                return false;
            }
        }
        return true;
    }

    private static boolean isContiguousStartingAtZero(Set<UnsignedLong> unsignedLongs) {
        UnsignedLong upperBound = new UnsignedLong(((long) unsignedLongs.size()) - 1L);
        for (UnsignedLong unsignedLong : unsignedLongs) {
            if (unsignedLong.compareTo(upperBound) > 0) {
                return false;
            }
        }
        return true;
    }

    private static Optional<JSONArray> extractLongJsonArray(JSONObject json, String key)
            throws JSONException {
        JSONArray jsonArray = json.getJSONArray(key);
        for (int i = 0; i < jsonArray.length(); i++) {
            if (!FetcherUtil.is64BitInteger(jsonArray.get(i))) {
                return Optional.empty();
            }
        }
        return Optional.of(jsonArray);
    }

    private static long roundSecondsToWholeDays(long seconds) {
        long remainder = seconds % ONE_DAY_IN_SECONDS;
        // Return value should be at least one whole day.
        boolean roundUp = (remainder >= ONE_DAY_IN_SECONDS / 2L) || (seconds == remainder);
        return seconds - remainder + (roundUp ? ONE_DAY_IN_SECONDS : 0);
    }

    private interface SourceHeaderContract {
        String HEADER_ATTRIBUTION_REPORTING_REGISTER_SOURCE =
                "Attribution-Reporting-Register-Source";
        String SOURCE_EVENT_ID = "source_event_id";
        String DEBUG_KEY = "debug_key";
        String DESTINATION = "destination";
        String EXPIRY = "expiry";
        String EVENT_REPORT_WINDOW = "event_report_window";
        String AGGREGATABLE_REPORT_WINDOW = "aggregatable_report_window";
        String PRIORITY = "priority";
        String INSTALL_ATTRIBUTION_WINDOW_KEY = "install_attribution_window";
        String POST_INSTALL_EXCLUSIVITY_WINDOW_KEY = "post_install_exclusivity_window";
        String FILTER_DATA = "filter_data";
        String WEB_DESTINATION = "web_destination";
        String AGGREGATION_KEYS = "aggregation_keys";
        String SHARED_AGGREGATION_KEYS = "shared_aggregation_keys";
        String DEBUG_REPORTING = "debug_reporting";
        String DEBUG_JOIN_KEY = "debug_join_key";
        String DEBUG_AD_ID = "debug_ad_id";
        String COARSE_EVENT_REPORT_DESTINATIONS = "coarse_event_report_destinations";
        String TRIGGER_SPECS = "trigger_specs";
        String MAX_EVENT_LEVEL_REPORTS = "max_event_level_reports";
        String EVENT_REPORT_WINDOWS = "event_report_windows";
        String SHARED_DEBUG_KEY = "shared_debug_key";
        String SHARED_FILTER_DATA_KEYS = "shared_filter_data_keys";
        String DROP_SOURCE_IF_INSTALLED = "drop_source_if_installed";
        String TRIGGER_DATA_MATCHING = "trigger_data_matching";
        String TRIGGER_DATA = "trigger_data";
    }

    private interface SourceRequestContract {
        String SOURCE_INFO = "Attribution-Reporting-Source-Info";
    }
}
