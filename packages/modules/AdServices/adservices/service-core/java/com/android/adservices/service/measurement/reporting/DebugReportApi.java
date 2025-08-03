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

package com.android.adservices.service.measurement.reporting;

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Class used to send debug reports to Ad-Tech {@link DebugReport} */
public class DebugReportApi {

    /** Define different verbose debug report types. */
    public interface Type {
        String SOURCE_DESTINATION_LIMIT = "source-destination-limit";
        String SOURCE_DESTINATION_RATE_LIMIT = "source-destination-rate-limit";
        String SOURCE_NOISED = "source-noised";
        String SOURCE_STORAGE_LIMIT = "source-storage-limit";
        String SOURCE_SUCCESS = "source-success";
        String SOURCE_UNKNOWN_ERROR = "source-unknown-error";
        String SOURCE_FLEXIBLE_EVENT_REPORT_VALUE_ERROR =
                "source-flexible-event-report-value-error";
        String TRIGGER_AGGREGATE_DEDUPLICATED = "trigger-aggregate-deduplicated";
        String TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET = "trigger-aggregate-insufficient-budget";
        String TRIGGER_AGGREGATE_NO_CONTRIBUTIONS = "trigger-aggregate-no-contributions";
        String TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED = "trigger-aggregate-report-window-passed";
        String TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT =
                "trigger-attributions-per-source-destination-limit";
        String TRIGGER_EVENT_DEDUPLICATED = "trigger-event-deduplicated";
        String TRIGGER_EVENT_EXCESSIVE_REPORTS = "trigger-event-excessive-reports";
        String TRIGGER_EVENT_LOW_PRIORITY = "trigger-event-low-priority";
        String TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS =
                "trigger-event-no-matching-configurations";
        String TRIGGER_EVENT_NOISE = "trigger-event-noise";
        String TRIGGER_EVENT_REPORT_WINDOW_PASSED = "trigger-event-report-window-passed";
        String TRIGGER_NO_MATCHING_FILTER_DATA = "trigger-no-matching-filter-data";
        String TRIGGER_NO_MATCHING_SOURCE = "trigger-no-matching-source";
        String TRIGGER_REPORTING_ORIGIN_LIMIT = "trigger-reporting-origin-limit";
        String TRIGGER_EVENT_STORAGE_LIMIT = "trigger-event-storage-limit";
        String TRIGGER_UNKNOWN_ERROR = "trigger-unknown-error";
        String TRIGGER_AGGREGATE_STORAGE_LIMIT = "trigger-aggregate-storage-limit";
        String TRIGGER_AGGREGATE_EXCESSIVE_REPORTS = "trigger-aggregate-excessive-reports";
        String TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED = "trigger-event-report-window-not-started";
        String TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA = "trigger-event-no-matching-trigger-data";
    }

    /** Defines different verbose debug report body parameters. */
    @VisibleForTesting
    public interface Body {
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String LIMIT = "limit";
        String RANDOMIZED_TRIGGER_RATE = "randomized_trigger_rate";
        String SCHEDULED_REPORT_TIME = "scheduled_report_time";
        String SOURCE_DEBUG_KEY = "source_debug_key";
        String SOURCE_EVENT_ID = "source_event_id";
        String SOURCE_SITE = "source_site";
        String SOURCE_TYPE = "source_type";
        String TRIGGER_DATA = "trigger_data";
        String TRIGGER_DEBUG_KEY = "trigger_debug_key";
    }

    private enum PermissionState {
        GRANTED,
        DENIED,
        NONE
    }

    private final Context mContext;
    private final Flags mFlags;
    private final DatastoreManager mDatastoreManager;
    private final EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;
    private final SourceNoiseHandler mSourceNoiseHandler;

    public DebugReportApi(Context context, Flags flags) {
        this(
                context,
                flags,
                new EventReportWindowCalcDelegate(flags),
                new SourceNoiseHandler(flags));
    }

    // TODO(b/292009320): Keep only one constructor in DebugReportApi
    @VisibleForTesting
    DebugReportApi(
            Context context,
            Flags flags,
            EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
            SourceNoiseHandler sourceNoiseHandler) {
        mContext = context;
        mFlags = flags;
        mDatastoreManager = DatastoreManagerFactory.getDatastoreManager(context);
        mEventReportWindowCalcDelegate = eventReportWindowCalcDelegate;
        mSourceNoiseHandler = sourceNoiseHandler;
    }

    @VisibleForTesting
    public DebugReportApi(
            Context context,
            Flags flags,
            EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
            SourceNoiseHandler sourceNoiseHandler,
            DatastoreManager datastoreManager) {
        mContext = context;
        mFlags = flags;
        mEventReportWindowCalcDelegate = eventReportWindowCalcDelegate;
        mSourceNoiseHandler = sourceNoiseHandler;
        mDatastoreManager = datastoreManager;
    }

    /** Schedules the Source Success Debug Report */
    public void scheduleSourceSuccessDebugReport(Source source, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_SUCCESS)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_SUCCESS)) {
            return;
        }
        if (!isSourcePermissionGranted(source)) {
            LoggerFactory.getMeasurementLogger().d("Skipping debug report %s", Type.SOURCE_SUCCESS);
            return;
        }
        scheduleReport(
                Type.SOURCE_SUCCESS,
                generateSourceDebugReportBody(source, null),
                source.getEnrollmentId(),
                source.getRegistrationOrigin(),
                source.getRegistrant(),
                dao);
    }

    /** Schedules the Source Destination limit Debug Report */
    public void scheduleSourceDestinationLimitDebugReport(
            Source source, String limit, IMeasurementDao dao) {
        scheduleSourceDestinationLimitDebugReport(
                source, limit, Type.SOURCE_DESTINATION_LIMIT, dao);
    }

    /** Schedules the Source Destination rate-limit Debug Report */
    public void scheduleSourceDestinationRateLimitDebugReport(
            Source source, String limit, IMeasurementDao dao) {
        scheduleSourceDestinationLimitDebugReport(
                source, limit, Type.SOURCE_DESTINATION_RATE_LIMIT, dao);
    }

    /** Schedules the Source Noised Debug Report */
    public void scheduleSourceNoisedDebugReport(Source source, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_NOISED)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_NOISED)) {
            return;
        }
        if (!isSourcePermissionGranted(source)) {
            LoggerFactory.getMeasurementLogger().d("Skipping debug report %s", Type.SOURCE_NOISED);
            return;
        }
        scheduleReport(
                Type.SOURCE_NOISED,
                generateSourceDebugReportBody(source, null),
                source.getEnrollmentId(),
                source.getRegistrationOrigin(),
                source.getRegistrant(),
                dao);
    }

    /** Schedules Source Storage Limit Debug Report */
    public void scheduleSourceStorageLimitDebugReport(
            Source source, String limit, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_STORAGE_LIMIT)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_STORAGE_LIMIT)) {
            return;
        }
        if (!isSourcePermissionGranted(source)) {
            LoggerFactory.getMeasurementLogger()
                    .d("Skipping debug report %s", Type.SOURCE_STORAGE_LIMIT);
            return;
        }
        scheduleReport(
                Type.SOURCE_STORAGE_LIMIT,
                generateSourceDebugReportBody(source, limit),
                source.getEnrollmentId(),
                source.getRegistrationOrigin(),
                source.getRegistrant(),
                dao);
    }

    /** Schedules Source Flexible Event API Debug Report */
    public void scheduleSourceFlexibleEventReportApiDebugReport(
            Source source, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_FLEXIBLE_EVENT_REPORT_VALUE_ERROR)) {
            return;
        }
        if (isAdTechNotOptIn(
                source.isDebugReporting(), Type.SOURCE_FLEXIBLE_EVENT_REPORT_VALUE_ERROR)) {
            return;
        }
        if (!isSourcePermissionGranted(source)) {
            LoggerFactory.getMeasurementLogger()
                    .d("Skipping debug report %s", Type.SOURCE_FLEXIBLE_EVENT_REPORT_VALUE_ERROR);
            return;
        }
        scheduleReport(
                Type.SOURCE_FLEXIBLE_EVENT_REPORT_VALUE_ERROR,
                generateSourceDebugReportBody(source, null),
                source.getEnrollmentId(),
                source.getRegistrationOrigin(),
                source.getRegistrant(),
                dao);
    }

    /** Schedules the Source Unknown Error Debug Report */
    public void scheduleSourceUnknownErrorDebugReport(Source source, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_UNKNOWN_ERROR)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_UNKNOWN_ERROR)) {
            return;
        }
        if (!isSourcePermissionGranted(source)) {
            LoggerFactory.getMeasurementLogger()
                    .d("Skipping debug report %s", Type.SOURCE_UNKNOWN_ERROR);
            return;
        }
        scheduleReport(
                Type.SOURCE_UNKNOWN_ERROR,
                generateSourceDebugReportBody(source, null),
                source.getEnrollmentId(),
                source.getRegistrationOrigin(),
                source.getRegistrant(),
                dao);
    }

    /**
     * Schedules trigger-no-matching-source and trigger-unknown-error debug reports when trigger
     * doesn't have related source.
     */
    public void scheduleTriggerNoMatchingSourceDebugReport(
            Trigger trigger, IMeasurementDao dao, String type) throws DatastoreException {
        if (isTriggerDebugFlagDisabled(type)) {
            return;
        }
        if (isAdTechNotOptIn(trigger.isDebugReporting(), type)) {
            return;
        }
        if (!isTriggerPermissionGranted(trigger)) {
            LoggerFactory.getMeasurementLogger().d("Skipping trigger debug report %s", type);
            return;
        }
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                new DebugKeyAccessor(dao).getDebugKeysForVerboseTriggerDebugReport(null, trigger);
        scheduleReport(
                type,
                generateTriggerDebugReportBody(null, trigger, null, debugKeyPair, true),
                trigger.getEnrollmentId(),
                trigger.getRegistrationOrigin(),
                trigger.getRegistrant(),
                dao);
    }

    /** Schedules Trigger Debug Reports with/without limit, pass in Type for different types. */
    public void scheduleTriggerDebugReport(
            Source source,
            Trigger trigger,
            @Nullable String limit,
            IMeasurementDao dao,
            String type)
            throws DatastoreException {
        if (isTriggerDebugFlagDisabled(type)) {
            return;
        }
        if (isAdTechNotOptIn(trigger.isDebugReporting(), type)) {
            return;
        }
        if (!isSourceAndTriggerPermissionsGranted(source, trigger)) {
            LoggerFactory.getMeasurementLogger().d("Skipping trigger debug report %s", type);
            return;
        }
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                new DebugKeyAccessor(dao).getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        scheduleReport(
                type,
                generateTriggerDebugReportBody(source, trigger, limit, debugKeyPair, false),
                source.getEnrollmentId(),
                trigger.getRegistrationOrigin(),
                source.getRegistrant(),
                dao);
    }

    /**
     * Schedules Trigger Debug Report with all body fields, Used for trigger-low-priority report and
     * trigger-event-excessive-reports.
     */
    public void scheduleTriggerDebugReportWithAllFields(
            Source source,
            Trigger trigger,
            UnsignedLong triggerData,
            IMeasurementDao dao,
            String type)
            throws DatastoreException {
        if (isTriggerDebugFlagDisabled(type)) {
            return;
        }
        if (isAdTechNotOptIn(trigger.isDebugReporting(), type)) {
            return;
        }
        if (!isSourceAndTriggerPermissionsGranted(source, trigger)) {
            LoggerFactory.getMeasurementLogger().d("Skipping trigger debug report %s", type);
            return;
        }
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                new DebugKeyAccessor(dao).getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        scheduleReport(
                type,
                generateTriggerDebugReportBodyWithAllFields(
                        source, trigger, triggerData, debugKeyPair),
                source.getEnrollmentId(),
                trigger.getRegistrationOrigin(),
                source.getRegistrant(),
                dao);
    }

    /** Schedules the Source Destination limit type Debug Report */
    private void scheduleSourceDestinationLimitDebugReport(
            Source source, String limit, String type, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_DESTINATION_LIMIT)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_DESTINATION_LIMIT)) {
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
            body.put(Body.ATTRIBUTION_DESTINATION, generateSourceDestinations(source));
            body.put(Body.SOURCE_SITE, generateSourceSite(source));
            body.put(Body.LIMIT, limit);
            if (getAdIdPermissionFromSource(source) == PermissionState.GRANTED
                    || getArDebugPermissionFromSource(source) == PermissionState.GRANTED) {
                body.put(Body.SOURCE_DEBUG_KEY, source.getDebugKey());
            }
            scheduleReport(
                    type,
                    body,
                    source.getEnrollmentId(),
                    source.getRegistrationOrigin(),
                    source.getRegistrant(),
                    dao);
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger().e(e, "Json error in debug report %s", type);
        }
    }

    /**
     * Schedules the Debug Report to be sent
     *
     * @param type The type of the debug report
     * @param body The body of the debug report
     * @param enrollmentId Ad Tech enrollment ID
     * @param registrationOrigin Reporting origin of the report
     * @param dao Measurement DAO
     * @param registrant App Registrant
     */
    private void scheduleReport(
            @NonNull String type,
            @NonNull JSONObject body,
            @NonNull String enrollmentId,
            @NonNull Uri registrationOrigin,
            @Nullable Uri registrant,
            @NonNull IMeasurementDao dao) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(body);
        Objects.requireNonNull(enrollmentId);
        Objects.requireNonNull(dao);
        if (type.isEmpty() || body.length() == 0) {
            LoggerFactory.getMeasurementLogger().d("Empty debug report found %s", type);
            return;
        }
        if (enrollmentId.isEmpty()) {
            LoggerFactory.getMeasurementLogger().d("Empty enrollment found %s", type);
            return;
        }

        DebugReport debugReport =
                new DebugReport.Builder()
                        .setId(UUID.randomUUID().toString())
                        .setType(type)
                        .setBody(body)
                        .setEnrollmentId(enrollmentId)
                        .setRegistrationOrigin(registrationOrigin)
                        .setInsertionTime(System.currentTimeMillis())
                        .setRegistrant(registrant)
                        .build();
        try {
            dao.insertDebugReport(debugReport);
        } catch (DatastoreException e) {
            LoggerFactory.getMeasurementLogger().e(e, "Failed to insert debug report %s", type);
        }

        VerboseDebugReportingJobService.scheduleIfNeeded(mContext, /*forceSchedule=*/ false);
    }

    /** Get AdIdPermission State from Source */
    private PermissionState getAdIdPermissionFromSource(Source source) {
        if (source.getPublisherType() == EventSurfaceType.APP) {
            if (source.hasAdIdPermission()) {
                return PermissionState.GRANTED;
            } else {
                LoggerFactory.getMeasurementLogger().d("Source doesn't have AdId permission");
                return PermissionState.DENIED;
            }
        }
        return PermissionState.NONE;
    }

    /** Get ArDebugPermission State from Source */
    private PermissionState getArDebugPermissionFromSource(Source source) {
        if (source.getPublisherType() == EventSurfaceType.WEB) {
            if (source.hasArDebugPermission()) {
                return PermissionState.GRANTED;
            } else {
                LoggerFactory.getMeasurementLogger().d("Source doesn't have ArDebug permission");
                return PermissionState.DENIED;
            }
        }
        return PermissionState.NONE;
    }

    private PermissionState getAdIdPermissionFromTrigger(Trigger trigger) {
        if (trigger.getDestinationType() == EventSurfaceType.APP) {
            if (trigger.hasAdIdPermission()) {
                return PermissionState.GRANTED;
            } else {
                LoggerFactory.getMeasurementLogger().d("Trigger doesn't have AdId permission");
                return PermissionState.DENIED;
            }
        }
        return PermissionState.NONE;
    }

    private PermissionState getArDebugPermissionFromTrigger(Trigger trigger) {
        if (trigger.getDestinationType() == EventSurfaceType.WEB) {
            if (trigger.hasArDebugPermission()) {
                return PermissionState.GRANTED;
            } else {
                LoggerFactory.getMeasurementLogger().d("Trigger doesn't have ArDebug permission");
                return PermissionState.DENIED;
            }
        }
        return PermissionState.NONE;
    }

    /**
     * Check AdId and ArDebug permissions for both source and trigger. Return true if all of them
     * are not in {@link PermissionState#DENIED} state.
     */
    private boolean isSourceAndTriggerPermissionsGranted(Source source, Trigger trigger) {
        return isSourcePermissionGranted(source) && isTriggerPermissionGranted(trigger);
    }

    private boolean isSourcePermissionGranted(Source source) {
        return getAdIdPermissionFromSource(source) != PermissionState.DENIED
                && getArDebugPermissionFromSource(source) != PermissionState.DENIED;
    }

    private boolean isTriggerPermissionGranted(Trigger trigger) {
        return getAdIdPermissionFromTrigger(trigger) != PermissionState.DENIED
                && getArDebugPermissionFromTrigger(trigger) != PermissionState.DENIED;
    }

    /** Get is Ad tech not op-in and log */
    private boolean isAdTechNotOptIn(boolean optIn, String type) {
        if (!optIn) {
            LoggerFactory.getMeasurementLogger()
                    .d("Ad-tech not opt-in. Skipping debug report %s", type);
        }
        return !optIn;
    }

    /** Generates source debug report body */
    private JSONObject generateSourceDebugReportBody(
            @NonNull Source source, @Nullable String limit) {
        JSONObject body = new JSONObject();
        try {
            body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
            body.put(Body.ATTRIBUTION_DESTINATION, generateSourceDestinations(source));
            body.put(Body.SOURCE_SITE, generateSourceSite(source));
            body.put(Body.LIMIT, limit);
            body.put(Body.SOURCE_DEBUG_KEY, source.getDebugKey());
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Json error while generating source debug report body.");
        }
        return body;
    }

    private static Object generateSourceDestinations(Source source) throws JSONException {
        List<Uri> destinations = new ArrayList<>();
        Optional.ofNullable(source.getAppDestinations()).ifPresent(destinations::addAll);
        List<Uri> webDestinations = source.getWebDestinations();
        if (webDestinations != null) {
            for (Uri webDestination : webDestinations) {
                Optional<Uri> webUri = WebAddresses.topPrivateDomainAndScheme(webDestination);
                webUri.ifPresent(destinations::add);
            }
        }
        return ReportUtil.serializeAttributionDestinations(destinations);
    }

    private static Uri generateSourceSite(Source source) {
        if (source.getPublisherType() == EventSurfaceType.APP) {
            return source.getPublisher();
        } else {
            return WebAddresses.topPrivateDomainAndScheme(source.getPublisher()).orElse(null);
        }
    }

    /** Generates trigger debug report body */
    private JSONObject generateTriggerDebugReportBody(
            @Nullable Source source,
            @NonNull Trigger trigger,
            @Nullable String limit,
            @NonNull Pair<UnsignedLong, UnsignedLong> debugKeyPair,
            boolean isTriggerNoMatchingSource) {
        JSONObject body = new JSONObject();
        try {
            body.put(Body.ATTRIBUTION_DESTINATION, trigger.getAttributionDestinationBaseUri());
            body.put(Body.TRIGGER_DEBUG_KEY, debugKeyPair.second);
            if (isTriggerNoMatchingSource) {
                return body;
            }
            body.put(Body.LIMIT, limit);
            body.put(Body.SOURCE_DEBUG_KEY, debugKeyPair.first);
            body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
            body.put(Body.SOURCE_SITE, generateSourceSite(source));
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Json error while generating trigger debug report body.");
        }
        return body;
    }

    /**
     * Generates trigger debug report body with all fields in event-level attribution report. Used
     * for trigger-low-priority, trigger-event-excessive-reports debug reports.
     */
    private JSONObject generateTriggerDebugReportBodyWithAllFields(
            @NonNull Source source,
            @NonNull Trigger trigger,
            @Nullable UnsignedLong triggerData,
            @NonNull Pair<UnsignedLong, UnsignedLong> debugKeyPair) {
        JSONObject body = new JSONObject();
        try {
            body.put(Body.ATTRIBUTION_DESTINATION, trigger.getAttributionDestinationBaseUri());
            body.put(
                    Body.SCHEDULED_REPORT_TIME,
                    String.valueOf(
                            TimeUnit.MILLISECONDS.toSeconds(
                                    mEventReportWindowCalcDelegate.getReportingTime(
                                            source,
                                            trigger.getTriggerTime(),
                                            trigger.getDestinationType()))));
            body.put(Body.SOURCE_EVENT_ID, source.getEventId());
            body.put(Body.SOURCE_TYPE, source.getSourceType().getValue());
            body.put(
                    Body.RANDOMIZED_TRIGGER_RATE,
                    mSourceNoiseHandler.getRandomizedTriggerRate(source));
            if (triggerData != null) {
                body.put(Body.TRIGGER_DATA, triggerData.toString());
            }
            if (debugKeyPair.first != null) {
                body.put(Body.SOURCE_DEBUG_KEY, debugKeyPair.first);
            }
            if (debugKeyPair.second != null) {
                body.put(Body.TRIGGER_DEBUG_KEY, debugKeyPair.second);
            }
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Json error while generating trigger debug report body with all fields.");
        }
        return body;
    }

    /** Checks flags for source debug reports. */
    private boolean isSourceDebugFlagDisabled(String type) {
        if (!mFlags.getMeasurementEnableDebugReport()
                || !mFlags.getMeasurementEnableSourceDebugReport()) {
            LoggerFactory.getMeasurementLogger()
                    .d("Source flag is disabled for %s debug report", type);
            return true;
        }
        return false;
    }

    /** Checks flags for trigger debug reports. */
    private boolean isTriggerDebugFlagDisabled(String type) {
        if (!mFlags.getMeasurementEnableDebugReport()
                || !mFlags.getMeasurementEnableTriggerDebugReport()) {
            LoggerFactory.getMeasurementLogger()
                    .d("Trigger flag is disabled for %s debug report", type);
            return true;
        }
        return false;
    }
}
