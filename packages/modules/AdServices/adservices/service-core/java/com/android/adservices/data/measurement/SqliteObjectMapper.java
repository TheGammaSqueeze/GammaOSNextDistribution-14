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

package com.android.adservices.data.measurement;

import static java.util.function.Predicate.not;

import android.database.Cursor;
import android.net.Uri;


import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.registration.AsyncRedirect;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.reporting.DebugReport;
import com.android.adservices.service.measurement.util.UnsignedLong;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Helper class for SQLite operations. */
public class SqliteObjectMapper {
    /**
     * Create {@link EventReport} object from SQLite datastore.
     */
    static EventReport constructEventReportFromCursor(Cursor cursor) {
        EventReport.Builder builder = new EventReport.Builder();
        setTextColumn(cursor, MeasurementTables.EventReportContract.ID,
                builder::setId);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID,
                builder::setSourceEventId);
        setLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_PRIORITY,
                builder::setTriggerPriority);
        setIntColumn(cursor, MeasurementTables.EventReportContract.STATUS,
                builder::setStatus);
        setIntColumn(
                cursor,
                MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS,
                builder::setDebugReportStatus);
        setUnsignedLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_DATA,
                builder::setTriggerData);
        setUnsignedLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY,
                builder::setTriggerDedupKey);
        setTextColumn(
                cursor,
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                (destinations) ->
                        builder.setAttributionDestinations(destinationsStringToList(destinations)));
        setTextColumn(cursor, MeasurementTables.EventReportContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setLongColumn(cursor, MeasurementTables.EventReportContract.REPORT_TIME,
                builder::setReportTime);
        setLongColumn(cursor, MeasurementTables.EventReportContract.TRIGGER_TIME,
                builder::setTriggerTime);
        setTextColumn(cursor, MeasurementTables.EventReportContract.SOURCE_TYPE,
                (enumValue) -> builder.setSourceType(Source.SourceType.valueOf(enumValue)));
        setDoubleColumn(cursor, MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE,
                builder::setRandomizedTriggerRate);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.EventReportContract.SOURCE_DEBUG_KEY,
                builder::setSourceDebugKey);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEY,
                builder::setTriggerDebugKey);
        setTextColumn(
                cursor,
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEYS,
                (concatArray) ->
                        builder.setTriggerDebugKeys(unsignedLongsStringToList(concatArray)));
        setTextColumn(
                cursor, MeasurementTables.EventReportContract.SOURCE_ID, builder::setSourceId);
        setTextColumn(
                cursor, MeasurementTables.EventReportContract.TRIGGER_ID, builder::setTriggerId);
        setTextColumn(
                cursor,
                MeasurementTables.EventReportContract.REGISTRATION_ORIGIN,
                registration_origin ->
                        builder.setRegistrationOrigin(Uri.parse(registration_origin)));
        setTextColumn(
                cursor,
                MeasurementTables.EventReportContract.TRIGGER_SUMMARY_BUCKET,
                builder::setTriggerSummaryBucket);
        return builder.build();
    }

    /** Create {@link Source} object from SQLite datastore. */
    static Source constructSourceFromCursor(Cursor cursor) {
        Source.Builder builder = new Source.Builder();
        setTextColumn(cursor, MeasurementTables.SourceContract.ID,
                builder::setId);
        setUnsignedLongColumn(cursor, MeasurementTables.SourceContract.EVENT_ID,
                builder::setEventId);
        setLongColumn(cursor, MeasurementTables.SourceContract.PRIORITY,
                builder::setPriority);
        setTextColumn(cursor, MeasurementTables.SourceContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setUriColumn(cursor, MeasurementTables.SourceContract.PUBLISHER,
                builder::setPublisher);
        setIntColumn(cursor, MeasurementTables.SourceContract.PUBLISHER_TYPE,
                builder::setPublisherType);
        setTextColumn(cursor, MeasurementTables.SourceContract.SOURCE_TYPE,
                (enumValue) -> builder.setSourceType(Source.SourceType.valueOf(enumValue)));
        setLongColumn(cursor, MeasurementTables.SourceContract.EXPIRY_TIME,
                builder::setExpiryTime);
        setLongColumn(cursor, MeasurementTables.SourceContract.EVENT_REPORT_WINDOW,
                builder::setEventReportWindow);
        setLongColumn(cursor, MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW,
                builder::setAggregatableReportWindow);
        setLongColumn(cursor, MeasurementTables.SourceContract.EVENT_TIME,
                builder::setEventTime);
        setTextColumn(
                cursor,
                MeasurementTables.SourceContract.EVENT_REPORT_DEDUP_KEYS,
                (concatArray) ->
                        builder.setEventReportDedupKeys(unsignedLongsStringToList(concatArray)));
        setTextColumn(
                cursor,
                MeasurementTables.SourceContract.AGGREGATE_REPORT_DEDUP_KEYS,
                (concatArray) ->
                        builder.setAggregateReportDedupKeys(
                                unsignedLongsStringToList(concatArray)));
        setIntColumn(cursor, MeasurementTables.SourceContract.STATUS,
                builder::setStatus);
        setUriColumn(cursor, MeasurementTables.SourceContract.REGISTRANT,
                builder::setRegistrant);
        setIntColumn(cursor, MeasurementTables.SourceContract.ATTRIBUTION_MODE,
                builder::setAttributionMode);
        setLongColumn(cursor, MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW,
                builder::setInstallAttributionWindow);
        setLongColumn(cursor, MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW,
                builder::setInstallCooldownWindow);
        setBooleanColumn(cursor, MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED,
                builder::setInstallAttributed);
        setTextColumn(cursor, MeasurementTables.SourceContract.FILTER_DATA,
                builder::setFilterDataString);
        setTextColumn(cursor, MeasurementTables.SourceContract.AGGREGATE_SOURCE,
                builder::setAggregateSource);
        setIntColumn(cursor, MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS,
                builder::setAggregateContributions);
        setUnsignedLongColumn(cursor, MeasurementTables.SourceContract.DEBUG_KEY,
                builder::setDebugKey);
        setBooleanColumn(
                cursor,
                MeasurementTables.SourceContract.DEBUG_REPORTING,
                builder::setIsDebugReporting);
        setBooleanColumn(
                cursor,
                MeasurementTables.SourceContract.AD_ID_PERMISSION,
                builder::setAdIdPermission);
        setBooleanColumn(
                cursor,
                MeasurementTables.SourceContract.AR_DEBUG_PERMISSION,
                builder::setArDebugPermission);
        setTextColumn(
                cursor,
                MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS,
                builder::setSharedAggregationKeys);
        setTextColumn(
                cursor,
                MeasurementTables.SourceContract.REGISTRATION_ID,
                builder::setRegistrationId);
        setTextColumn(
                cursor, MeasurementTables.SourceContract.DEBUG_JOIN_KEY, builder::setDebugJoinKey);
        setLongColumn(
                cursor, MeasurementTables.SourceContract.INSTALL_TIME, builder::setInstallTime);
        setTextColumn(
                cursor, MeasurementTables.SourceContract.PLATFORM_AD_ID, builder::setPlatformAdId);
        setTextColumn(cursor, MeasurementTables.SourceContract.DEBUG_AD_ID, builder::setDebugAdId);
        setUriColumn(
                cursor,
                MeasurementTables.SourceContract.REGISTRATION_ORIGIN,
                builder::setRegistrationOrigin);
        setBooleanColumn(
                cursor,
                MeasurementTables.SourceContract.COARSE_EVENT_REPORT_DESTINATIONS,
                builder::setCoarseEventReportDestinations);
        setTextColumn(
                cursor,
                MeasurementTables.SourceContract.TRIGGER_SPECS,
                builder::setTriggerSpecsString);
        setIntColumn(
                cursor,
                MeasurementTables.SourceContract.MAX_EVENT_LEVEL_REPORTS,
                builder::setMaxEventLevelReports);
        setTextColumn(
                cursor,
                MeasurementTables.SourceContract.EVENT_ATTRIBUTION_STATUS,
                builder::setEventAttributionStatus);
        setTextColumn(
                cursor,
                MeasurementTables.SourceContract.PRIVACY_PARAMETERS,
                builder::setPrivacyParameters);
        setTextColumn(
                cursor,
                MeasurementTables.SourceContract.EVENT_REPORT_WINDOWS,
                builder::setEventReportWindows);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.SourceContract.SHARED_DEBUG_KEY,
                builder::setSharedDebugKey);
        setTextColumn(
                cursor,
                MeasurementTables.SourceContract.SHARED_FILTER_DATA_KEYS,
                builder::setSharedFilterDataKeys);
        setTextColumn(cursor, MeasurementTables.SourceContract.TRIGGER_DATA_MATCHING,
                (enumValue) -> builder.setTriggerDataMatching(
                        Source.TriggerDataMatching.valueOf(enumValue)));
        return builder.build();
    }

    /** Create {@link Trigger} object from SQLite datastore. */
    public static Trigger constructTriggerFromCursor(Cursor cursor) {
        Trigger.Builder builder = new Trigger.Builder();
        setTextColumn(cursor, MeasurementTables.TriggerContract.ID,
                builder::setId);
        setTextColumn(
                cursor,
                MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                builder::setEventTriggers);
        setUriColumn(cursor, MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                builder::setAttributionDestination);
        setIntColumn(cursor, MeasurementTables.TriggerContract.DESTINATION_TYPE,
                builder::setDestinationType);
        setTextColumn(cursor, MeasurementTables.TriggerContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setIntColumn(cursor, MeasurementTables.TriggerContract.STATUS,
                builder::setStatus);
        setLongColumn(cursor, MeasurementTables.TriggerContract.TRIGGER_TIME,
                builder::setTriggerTime);
        setUriColumn(cursor, MeasurementTables.TriggerContract.REGISTRANT,
                builder::setRegistrant);
        setTextColumn(cursor, MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                builder::setAggregateTriggerData);
        setTextColumn(cursor, MeasurementTables.TriggerContract.AGGREGATE_VALUES,
                builder::setAggregateValues);
        setTextColumn(
                cursor,
                MeasurementTables.TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS,
                builder::setAggregateDeduplicationKeys);
        setTextColumn(cursor, MeasurementTables.TriggerContract.FILTERS, builder::setFilters);
        setTextColumn(cursor, MeasurementTables.TriggerContract.NOT_FILTERS,
                builder::setNotFilters);
        setUnsignedLongColumn(cursor, MeasurementTables.TriggerContract.DEBUG_KEY,
                builder::setDebugKey);
        setBooleanColumn(
                cursor,
                MeasurementTables.TriggerContract.DEBUG_REPORTING,
                builder::setIsDebugReporting);
        setBooleanColumn(
                cursor,
                MeasurementTables.TriggerContract.AD_ID_PERMISSION,
                builder::setAdIdPermission);
        setBooleanColumn(
                cursor,
                MeasurementTables.TriggerContract.AR_DEBUG_PERMISSION,
                builder::setArDebugPermission);
        setTextColumn(
                cursor,
                MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG,
                builder::setAttributionConfig);
        setTextColumn(
                cursor,
                MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING,
                builder::setAdtechBitMapping);
        setTextColumn(
                cursor, MeasurementTables.TriggerContract.DEBUG_JOIN_KEY, builder::setDebugJoinKey);
        setTextColumn(
                cursor, MeasurementTables.TriggerContract.PLATFORM_AD_ID, builder::setPlatformAdId);
        setTextColumn(cursor, MeasurementTables.TriggerContract.DEBUG_AD_ID, builder::setDebugAdId);
        setTextColumn(
                cursor,
                MeasurementTables.TriggerContract.REGISTRATION_ORIGIN,
                registration_origin ->
                        builder.setRegistrationOrigin(Uri.parse(registration_origin)));
        setUriColumn(
                cursor,
                MeasurementTables.TriggerContract.AGGREGATION_COORDINATOR_ORIGIN,
                builder::setAggregationCoordinatorOrigin);
        setTextColumn(
                cursor,
                MeasurementTables.TriggerContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG,
                (enumValue) ->
                        builder.setAggregatableSourceRegistrationTimeConfig(
                                Trigger.SourceRegistrationTimeConfig.valueOf(enumValue)));
        return builder.build();
    }

    /**
     * Create {@link AggregateReport} object from SQLite datastore.
     */
    static AggregateReport constructAggregateReport(Cursor cursor) {
        AggregateReport.Builder builder = new AggregateReport.Builder();
        setTextColumn(cursor, MeasurementTables.AggregateReport.ID,
                builder::setId);
        setUriColumn(cursor, MeasurementTables.AggregateReport.PUBLISHER,
                builder::setPublisher);
        setUriColumn(cursor, MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION,
                builder::setAttributionDestination);
        setLongColumn(
                cursor,
                MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME,
                builder::setSourceRegistrationTime);
        setLongColumn(cursor, MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME,
                builder::setScheduledReportTime);
        setTextColumn(cursor, MeasurementTables.AggregateReport.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setTextColumn(cursor, MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD,
                builder::setDebugCleartextPayload);
        setIntColumn(cursor, MeasurementTables.AggregateReport.STATUS,
                builder::setStatus);
        setIntColumn(
                cursor,
                MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS,
                builder::setDebugReportStatus);
        setTextColumn(cursor, MeasurementTables.AggregateReport.API_VERSION,
                builder::setApiVersion);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY,
                builder::setSourceDebugKey);
        setUnsignedLongColumn(
                cursor,
                MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY,
                builder::setTriggerDebugKey);
        setTextColumn(cursor, MeasurementTables.AggregateReport.SOURCE_ID, builder::setSourceId);
        setTextColumn(cursor, MeasurementTables.AggregateReport.TRIGGER_ID, builder::setTriggerId);
        setUnsignedLongColumn(
                cursor, MeasurementTables.AggregateReport.DEDUP_KEY, builder::setDedupKey);
        setTextColumn(
                cursor,
                MeasurementTables.AggregateReport.REGISTRATION_ORIGIN,
                registration_origin ->
                        builder.setRegistrationOrigin(Uri.parse(registration_origin)));
        setUriColumn(
                cursor,
                MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN,
                builder::setAggregationCoordinatorOrigin);
        setBooleanColumn(
                cursor, MeasurementTables.AggregateReport.IS_FAKE_REPORT, builder::setIsFakeReport);
        return builder.build();
    }

    /**
     * Create {@link AggregateEncryptionKey} object from SQLite datastore.
     */
    static AggregateEncryptionKey constructAggregateEncryptionKeyFromCursor(Cursor cursor) {
        AggregateEncryptionKey.Builder builder = new AggregateEncryptionKey.Builder();
        setTextColumn(cursor, MeasurementTables.AggregateEncryptionKey.ID,
                builder::setId);
        setTextColumn(cursor, MeasurementTables.AggregateEncryptionKey.KEY_ID,
                builder::setKeyId);
        setTextColumn(cursor, MeasurementTables.AggregateEncryptionKey.PUBLIC_KEY,
                builder::setPublicKey);
        setLongColumn(cursor, MeasurementTables.AggregateEncryptionKey.EXPIRY,
                builder::setExpiry);
        setUriColumn(
                cursor,
                MeasurementTables.AggregateEncryptionKey.AGGREGATION_COORDINATOR_ORIGIN,
                builder::setAggregationCoordinatorOrigin);
        return builder.build();
    }

    /** Create {@link DebugReport} object from SQLite datastore. */
    static DebugReport constructDebugReportFromCursor(Cursor cursor) {
        DebugReport.Builder builder = new DebugReport.Builder();
        setTextColumn(cursor, MeasurementTables.DebugReportContract.ID, builder::setId);
        setTextColumn(cursor, MeasurementTables.DebugReportContract.TYPE, builder::setType);
        setTextColumn(cursor, MeasurementTables.DebugReportContract.BODY, builder::setBody);
        setTextColumn(
                cursor,
                MeasurementTables.DebugReportContract.ENROLLMENT_ID,
                builder::setEnrollmentId);
        setUriColumn(
                cursor,
                MeasurementTables.DebugReportContract.REGISTRATION_ORIGIN,
                builder::setRegistrationOrigin);
        setTextColumn(
                cursor,
                MeasurementTables.DebugReportContract.REFERENCE_ID,
                builder::setReferenceId);
        setLongColumn(
                cursor,
                MeasurementTables.DebugReportContract.INSERTION_TIME,
                builder::setInsertionTime);
        setUriColumn(
                cursor, MeasurementTables.DebugReportContract.REGISTRANT, builder::setRegistrant);
        return builder.build();
    }

    /** Create {@link AsyncRegistration} object from SQLite datastore. */
    public static AsyncRegistration constructAsyncRegistration(Cursor cursor) {
        AsyncRegistration.Builder builder = new AsyncRegistration.Builder();
        setTextColumn(cursor, MeasurementTables.AsyncRegistrationContract.ID, builder::setId);
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION,
                builder::setWebDestination);
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.OS_DESTINATION,
                builder::setOsDestination);
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI,
                builder::setRegistrationUri);
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION,
                builder::setVerifiedDestination);
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN,
                builder::setTopOrigin);
        setIntColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE,
                (enumValue) ->
                        builder.setSourceType(
                                enumValue == null ? null : Source.SourceType.values()[enumValue]));
        setUriColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                builder::setRegistrant);
        setLongColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REQUEST_TIME,
                builder::setRequestTime);
        setLongColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.RETRY_COUNT,
                builder::setRetryCount);
        setIntColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.TYPE,
                (enumValue) ->
                        builder.setType(
                                enumValue == null
                                        ? null
                                        : AsyncRegistration.RegistrationType.values()[enumValue]));
        setBooleanColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED,
                builder::setDebugKeyAllowed);
        setBooleanColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.AD_ID_PERMISSION,
                builder::setAdIdPermission);
        setTextColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID,
                builder::setRegistrationId);
        setTextColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.PLATFORM_AD_ID,
                builder::setPlatformAdId);
        setTextColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REQUEST_POST_BODY,
                builder::setPostBody);
        setTextColumn(
                cursor,
                MeasurementTables.AsyncRegistrationContract.REDIRECT_BEHAVIOR,
                (enumValue) ->
                        builder.setRedirectBehavior(
                                enumValue == null
                                        ? null
                                        : AsyncRedirect.RedirectBehavior.valueOf(enumValue)));
        return builder.build();
    }

    static List<Uri> destinationsStringToList(String destinations) {
        return Arrays.stream(destinations.split(" "))
                .map(String::trim)
                .filter(not(String::isEmpty))
                .map(destination -> Uri.parse(destination))
                .collect(Collectors.toList());
    }

    private static <BuilderType> void setUriColumn(Cursor cursor, String column, Function<Uri,
            BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getString, (x) -> setter.apply(Uri.parse(x)));
    }

    private static <BuilderType> void setIntColumn(
            Cursor cursor, String column, Function<Integer, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getInt, setter);
    }

    private static <BuilderType> void setDoubleColumn(Cursor cursor, String column,
            Function<Double, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getDouble, setter);
    }

    private static <BuilderType> void setLongColumn(Cursor cursor, String column,
                                                    Function<Long, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getLong, setter);
    }

    private static <BuilderType> void setUnsignedLongColumn(Cursor cursor, String column,
                                                    Function<UnsignedLong, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getLong, signedLong ->
                setter.apply(new UnsignedLong(signedLong)));
    }

    private static <BuilderType> void setTextColumn(Cursor cursor, String column,
                                                    Function<String, BuilderType> setter) {
        setColumnValue(cursor, column, cursor::getString, setter);
    }

    private static <BuilderType> void setBooleanColumn(Cursor cursor, String column,
            Function<Boolean, BuilderType> setter) {
        setIntColumn(cursor, column, (x) -> setter.apply(x == 1));
    }

    private static <BuilderType, DataType> void setColumnValue(
            Cursor cursor, String column, Function<Integer, DataType> getColVal,
            Function<DataType, BuilderType> setter) {
        int index = cursor.getColumnIndex(column);
        if (index > -1 && !cursor.isNull(index)) {
            setter.apply(getColVal.apply(index));
        }
    }

    private static List<UnsignedLong> unsignedLongsStringToList(String concatArray) {
        return Arrays.stream(concatArray.split(","))
                .map(String::trim)
                .filter(not(String::isEmpty))
                // TODO (b/295059367): Negative numbers handling to be reverted
                .map(parseCleanUnsignedLong())
                .collect(Collectors.toList());
    }

    private static Function<String, UnsignedLong> parseCleanUnsignedLong() {
        return string -> {
            if (string.startsWith("-")) {
                // It's in the long range
                return new UnsignedLong(Long.parseLong(string));
            }
            return new UnsignedLong(string);
        };
    }
}
