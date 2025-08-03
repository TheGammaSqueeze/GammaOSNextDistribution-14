/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV11;
import com.android.adservices.data.measurement.migration.MeasurementTablesDeprecated;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Container class for Measurement PPAPI table definitions and constants.
 */
public final class MeasurementTables {
    public static final String MSMT_TABLE_PREFIX = "msmt_";
    public static final String INDEX_PREFIX = "idx_";

    /**
     * Array of all Measurement related tables. The AdTechUrls table is not included in the
     * Measurement tables because it will be used for a more general purpose.
     */
    // TODO(b/237306788): Move AdTechUrls tables to common tables and add method to delete common
    //  tables.
    public static final String[] ALL_MSMT_TABLES = {
        MeasurementTables.SourceContract.TABLE,
        MeasurementTables.SourceDestination.TABLE,
        MeasurementTables.TriggerContract.TABLE,
        MeasurementTables.EventReportContract.TABLE,
        MeasurementTables.AggregateReport.TABLE,
        MeasurementTables.AggregateEncryptionKey.TABLE,
        MeasurementTables.AttributionContract.TABLE,
        MeasurementTables.AsyncRegistrationContract.TABLE,
        MeasurementTables.DebugReportContract.TABLE,
        MeasurementTables.XnaIgnoredSourcesContract.TABLE,
        KeyValueDataContract.TABLE,
    };

    public static final String[] V6_TABLES = {
        // Source & Trigger table should always be at the top to avoid foreign key constraint
        // failures in other tables.
        MeasurementTables.SourceContract.TABLE,
        MeasurementTables.TriggerContract.TABLE,
        MeasurementTables.EventReportContract.TABLE,
        MeasurementTables.AggregateReport.TABLE,
        MeasurementTables.AggregateEncryptionKey.TABLE,
        MeasurementTables.AttributionContract.TABLE,
        MeasurementTables.AsyncRegistrationContract.TABLE,
        MeasurementTables.DebugReportContract.TABLE,
        MeasurementTables.XnaIgnoredSourcesContract.TABLE
    };

    public static final String[] V1_TABLES = {
        SourceContract.TABLE,
        TriggerContract.TABLE,
        EventReportContract.TABLE,
        AggregateReport.TABLE,
        AggregateEncryptionKey.TABLE,
        AttributionContract.TABLE,
        AsyncRegistrationContract.TABLE,
    };

    /** Contract for asynchronous Registration. */
    public interface AsyncRegistrationContract {
        String TABLE = MSMT_TABLE_PREFIX + "async_registration_contract";
        String ID = "_id";
        String REGISTRATION_URI = "registration_uri";
        String TOP_ORIGIN = "top_origin";
        String SOURCE_TYPE = "source_type";
        String REGISTRANT = "registrant";
        String REQUEST_TIME = "request_time";
        String RETRY_COUNT = "retry_count";
        String TYPE = "type";
        String WEB_DESTINATION = "web_destination";
        String OS_DESTINATION = "os_destination";
        String VERIFIED_DESTINATION = "verified_destination";
        String DEBUG_KEY_ALLOWED = "debug_key_allowed";
        String AD_ID_PERMISSION = "ad_id_permission";
        String REGISTRATION_ID = "registration_id";
        String PLATFORM_AD_ID = "platform_ad_id";
        String REQUEST_POST_BODY = "request_post_body";
        String REDIRECT_BEHAVIOR = "redirect_behavior";
    }

    /** Contract for Source. */
    public interface SourceContract {
        String TABLE = MSMT_TABLE_PREFIX + "source";
        String ID = "_id";
        String EVENT_ID = "event_id";
        String PUBLISHER = "publisher";
        String PUBLISHER_TYPE = "publisher_type";
        String EVENT_REPORT_DEDUP_KEYS = "event_report_dedup_keys";
        String AGGREGATE_REPORT_DEDUP_KEYS = "aggregate_report_dedup_keys";
        String EVENT_TIME = "event_time";
        String EXPIRY_TIME = "expiry_time";
        String EVENT_REPORT_WINDOW = "event_report_window";
        String AGGREGATABLE_REPORT_WINDOW = "aggregatable_report_window";
        String PRIORITY = "priority";
        String STATUS = "status";
        String SOURCE_TYPE = "source_type";
        String ENROLLMENT_ID = "enrollment_id";
        String REGISTRANT = "registrant";
        String ATTRIBUTION_MODE = "attribution_mode";
        String INSTALL_ATTRIBUTION_WINDOW = "install_attribution_window";
        String INSTALL_COOLDOWN_WINDOW = "install_cooldown_window";
        String IS_INSTALL_ATTRIBUTED = "is_install_attributed";
        String FILTER_DATA = "filter_data";
        String SHARED_FILTER_DATA_KEYS = "shared_filter_data_keys";
        String AGGREGATE_SOURCE = "aggregate_source";
        String AGGREGATE_CONTRIBUTIONS = "aggregate_contributions";
        String DEBUG_KEY = "debug_key";
        String DEBUG_REPORTING = "debug_reporting";
        String AD_ID_PERMISSION = "ad_id_permission";
        String AR_DEBUG_PERMISSION = "ar_debug_permission";
        String REGISTRATION_ID = "registration_id";
        String SHARED_AGGREGATION_KEYS = "shared_aggregation_keys";
        String INSTALL_TIME = "install_time";
        String DEBUG_JOIN_KEY = "debug_join_key";
        String TRIGGER_SPECS = "trigger_specs";
        String PLATFORM_AD_ID = "platform_ad_id";
        String DEBUG_AD_ID = "debug_ad_id";
        String REGISTRATION_ORIGIN = "registration_origin";
        String COARSE_EVENT_REPORT_DESTINATIONS = "coarse_event_report_destinations";
        String EVENT_ATTRIBUTION_STATUS = "event_attribution_status";
        String PRIVACY_PARAMETERS = "privacy_parameters";
        String EVENT_REPORT_WINDOWS = "event_report_windows";
        String MAX_EVENT_LEVEL_REPORTS = "max_event_level_reports";
        String SHARED_DEBUG_KEY = "shared_debug_key";
        String TRIGGER_DATA_MATCHING = "trigger_data_matching";
    }

    /** Contract for sub-table for destinations in Source. */
    public interface SourceDestination {
        String TABLE = MSMT_TABLE_PREFIX + "source_destination";
        String SOURCE_ID = "source_id";
        String DESTINATION_TYPE = "destination_type";
        String DESTINATION = "destination";
    }

    /** Contract for Trigger. */
    public interface TriggerContract {
        String TABLE = MSMT_TABLE_PREFIX + "trigger";
        String ID = "_id";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String DESTINATION_TYPE = "destination_type";
        String TRIGGER_TIME = "trigger_time";
        String STATUS = "status";
        String REGISTRANT = "registrant";
        String ENROLLMENT_ID = "enrollment_id";
        String EVENT_TRIGGERS = "event_triggers";
        String AGGREGATE_TRIGGER_DATA = "aggregate_trigger_data";
        String AGGREGATE_VALUES = "aggregate_values";
        String AGGREGATABLE_DEDUPLICATION_KEYS = "aggregatable_deduplication_keys";
        String FILTERS = "filters";
        String NOT_FILTERS = "not_filters";
        String DEBUG_KEY = "debug_key";
        String DEBUG_REPORTING = "debug_reporting";
        String AD_ID_PERMISSION = "ad_id_permission";
        String AR_DEBUG_PERMISSION = "ar_debug_permission";
        String ATTRIBUTION_CONFIG = "attribution_config";
        String X_NETWORK_KEY_MAPPING = "x_network_key_mapping";
        String DEBUG_JOIN_KEY = "debug_join_key";
        String PLATFORM_AD_ID = "platform_ad_id";
        String DEBUG_AD_ID = "debug_ad_id";
        String REGISTRATION_ORIGIN = "registration_origin";
        String AGGREGATION_COORDINATOR_ORIGIN = "aggregation_coordinator_origin";
        String AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG =
                "aggregatable_source_registration_time_config";
    }

    /** Contract for EventReport. */
    public interface EventReportContract {
        String TABLE = MSMT_TABLE_PREFIX + "event_report";
        String ID = "_id";
        String SOURCE_EVENT_ID = "source_event_id";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String REPORT_TIME = "report_time";
        String TRIGGER_DATA = "trigger_data";
        String TRIGGER_PRIORITY = "trigger_priority";
        String TRIGGER_DEDUP_KEY = "trigger_dedup_key";
        String TRIGGER_TIME = "trigger_time";
        String STATUS = "status";
        String DEBUG_REPORT_STATUS = "debug_report_status";
        String SOURCE_TYPE = "source_type";
        String ENROLLMENT_ID = "enrollment_id";
        String RANDOMIZED_TRIGGER_RATE = "randomized_trigger_rate";
        String SOURCE_DEBUG_KEY = "source_debug_key";
        String TRIGGER_DEBUG_KEY = "trigger_debug_key";
        String TRIGGER_DEBUG_KEYS = "trigger_debug_keys";
        String SOURCE_ID = "source_id";
        String TRIGGER_ID = "trigger_id";
        String REGISTRATION_ORIGIN = "registration_origin";
        String TRIGGER_SUMMARY_BUCKET = "trigger_summary_bucket";
    }

    /** Contract for Attribution rate limit. */
    public interface AttributionContract {
        String TABLE = MSMT_TABLE_PREFIX + "attribution";
        String ID = "_id";
        String SOURCE_SITE = "source_site";
        String SOURCE_ORIGIN = "source_origin";
        String DESTINATION_SITE = "attribution_destination_site";
        String DESTINATION_ORIGIN = "destination_origin";
        // TODO: b/276638412 rename to source time
        String TRIGGER_TIME = "trigger_time";
        String REGISTRANT = "registrant";
        String ENROLLMENT_ID = "enrollment_id";
        String SOURCE_ID = "source_id";
        String TRIGGER_ID = "trigger_id";
        String REGISTRATION_ORIGIN = "registration_origin";
        String SCOPE = "scope";
    }

    /** Contract for Unencrypted aggregate payload. */
    public interface AggregateReport {
        String TABLE = MSMT_TABLE_PREFIX + "aggregate_report";
        String ID = "_id";
        String PUBLISHER = "publisher";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String SOURCE_REGISTRATION_TIME = "source_registration_time";
        String SCHEDULED_REPORT_TIME = "scheduled_report_time";
        String ENROLLMENT_ID = "enrollment_id";
        String DEBUG_CLEARTEXT_PAYLOAD = "debug_cleartext_payload";
        String STATUS = "status";
        String DEBUG_REPORT_STATUS = "debug_report_status";
        String API_VERSION = "api_version";
        String SOURCE_DEBUG_KEY = "source_debug_key";
        String TRIGGER_DEBUG_KEY = "trigger_debug_key";
        String SOURCE_ID = "source_id";
        String TRIGGER_ID = "trigger_id";
        String DEDUP_KEY = "dedup_key";
        String REGISTRATION_ORIGIN = "registration_origin";
        String AGGREGATION_COORDINATOR_ORIGIN = "aggregation_coordinator_origin";
        String IS_FAKE_REPORT = "is_fake_report";
    }

    /** Contract for aggregate encryption key. */
    public interface AggregateEncryptionKey {
        String TABLE = MSMT_TABLE_PREFIX + "aggregate_encryption_key";
        String ID = "_id";
        String KEY_ID = "key_id";
        String PUBLIC_KEY = "public_key";
        String EXPIRY = "expiry";
        String AGGREGATION_COORDINATOR_ORIGIN = "aggregation_coordinator_origin";
    }

    /** Contract for debug reports. */
    public interface DebugReportContract {
        String TABLE = MSMT_TABLE_PREFIX + "debug_report";
        String ID = "_id";
        String TYPE = "type";
        String BODY = "body";
        String ENROLLMENT_ID = "enrollment_id";
        String REGISTRATION_ORIGIN = "registration_origin";
        String REFERENCE_ID = "reference_id";
        String INSERTION_TIME = "insertion_time";
        String REGISTRANT = "registrant";
    }

    /** Contract for xna ignored sources. */
    public interface XnaIgnoredSourcesContract {
        String TABLE = MSMT_TABLE_PREFIX + "xna_ignored_sources";
        String SOURCE_ID = "source_id";
        String ENROLLMENT_ID = "enrollment_id";
    }

    /** Contract for key-value store */
    public interface KeyValueDataContract {
        String TABLE = MSMT_TABLE_PREFIX + "key_value_data";
        String DATA_TYPE = "data_type";
        String KEY = "_key"; // Avoid collision with SQLite keyword 'key'
        String VALUE = "value";
    }

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_V6 =
            "CREATE TABLE "
                    + AsyncRegistrationContract.TABLE
                    + " ("
                    + AsyncRegistrationContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTablesDeprecated.AsyncRegistration.ENROLLMENT_ID
                    + " TEXT, "
                    + AsyncRegistrationContract.REGISTRATION_URI
                    + " TEXT, "
                    + AsyncRegistrationContract.WEB_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.OS_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.VERIFIED_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.TOP_ORIGIN
                    + " TEXT, "
                    + MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_TYPE
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_COUNT
                    + " INTEGER, "
                    + AsyncRegistrationContract.SOURCE_TYPE
                    + " INTEGER, "
                    + AsyncRegistrationContract.REGISTRANT
                    + " TEXT, "
                    + AsyncRegistrationContract.REQUEST_TIME
                    + " INTEGER, "
                    + AsyncRegistrationContract.RETRY_COUNT
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.AsyncRegistration.LAST_PROCESSING_TIME
                    + " INTEGER, "
                    + AsyncRegistrationContract.TYPE
                    + " INTEGER, "
                    + AsyncRegistrationContract.DEBUG_KEY_ALLOWED
                    + " INTEGER, "
                    + AsyncRegistrationContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + AsyncRegistrationContract.REGISTRATION_ID
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_LATEST =
            "CREATE TABLE "
                    + AsyncRegistrationContract.TABLE
                    + " ("
                    + AsyncRegistrationContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AsyncRegistrationContract.REGISTRATION_URI
                    + " TEXT, "
                    + AsyncRegistrationContract.WEB_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.OS_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.VERIFIED_DESTINATION
                    + " TEXT, "
                    + AsyncRegistrationContract.TOP_ORIGIN
                    + " TEXT, "
                    + AsyncRegistrationContract.SOURCE_TYPE
                    + " INTEGER, "
                    + AsyncRegistrationContract.REGISTRANT
                    + " TEXT, "
                    + AsyncRegistrationContract.REQUEST_TIME
                    + " INTEGER, "
                    + AsyncRegistrationContract.RETRY_COUNT
                    + " INTEGER, "
                    + AsyncRegistrationContract.TYPE
                    + " INTEGER, "
                    + AsyncRegistrationContract.DEBUG_KEY_ALLOWED
                    + " INTEGER, "
                    + AsyncRegistrationContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + AsyncRegistrationContract.REGISTRATION_ID
                    + " TEXT NOT NULL,"
                    + AsyncRegistrationContract.PLATFORM_AD_ID
                    + " TEXT, "
                    + AsyncRegistrationContract.REQUEST_POST_BODY
                    + " TEXT, "
                    + AsyncRegistrationContract.REDIRECT_BEHAVIOR
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V6 =
            "CREATE TABLE "
                    + SourceContract.TABLE
                    + " ("
                    + SourceContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + SourceContract.EVENT_ID
                    + " INTEGER, "
                    + SourceContract.PUBLISHER
                    + " TEXT, "
                    + SourceContract.PUBLISHER_TYPE
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.SourceContract.APP_DESTINATION
                    + " TEXT, "
                    + SourceContract.ENROLLMENT_ID
                    + " TEXT, "
                    + SourceContract.EVENT_TIME
                    + " INTEGER, "
                    + SourceContract.EXPIRY_TIME
                    + " INTEGER, "
                    + SourceContract.EVENT_REPORT_WINDOW
                    + " INTEGER, "
                    + SourceContract.AGGREGATABLE_REPORT_WINDOW
                    + " INTEGER, "
                    + SourceContract.PRIORITY
                    + " INTEGER, "
                    + SourceContract.STATUS
                    + " INTEGER, "
                    + SourceContract.EVENT_REPORT_DEDUP_KEYS
                    + " TEXT, "
                    + SourceContract.AGGREGATE_REPORT_DEDUP_KEYS
                    + " TEXT, "
                    + SourceContract.SOURCE_TYPE
                    + " TEXT, "
                    + SourceContract.REGISTRANT
                    + " TEXT, "
                    + SourceContract.ATTRIBUTION_MODE
                    + " INTEGER, "
                    + SourceContract.INSTALL_ATTRIBUTION_WINDOW
                    + " INTEGER, "
                    + SourceContract.INSTALL_COOLDOWN_WINDOW
                    + " INTEGER, "
                    + SourceContract.IS_INSTALL_ATTRIBUTED
                    + " INTEGER, "
                    + SourceContract.FILTER_DATA
                    + " TEXT, "
                    + SourceContract.AGGREGATE_SOURCE
                    + " TEXT, "
                    + SourceContract.AGGREGATE_CONTRIBUTIONS
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.SourceContract.WEB_DESTINATION
                    + " TEXT, "
                    + SourceContract.DEBUG_KEY
                    + " INTEGER , "
                    + SourceContract.DEBUG_REPORTING
                    + " INTEGER, "
                    + SourceContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + SourceContract.AR_DEBUG_PERMISSION
                    + " INTEGER, "
                    + SourceContract.REGISTRATION_ID
                    + " TEXT, "
                    + SourceContract.SHARED_AGGREGATION_KEYS
                    + " TEXT, "
                    + SourceContract.INSTALL_TIME
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_LATEST =
            "CREATE TABLE "
                    + SourceContract.TABLE
                    + " ("
                    + SourceContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + SourceContract.EVENT_ID
                    + " INTEGER, "
                    + SourceContract.PUBLISHER
                    + " TEXT, "
                    + SourceContract.PUBLISHER_TYPE
                    + " INTEGER, "
                    + SourceContract.ENROLLMENT_ID
                    + " TEXT, "
                    + SourceContract.EVENT_TIME
                    + " INTEGER, "
                    + SourceContract.EXPIRY_TIME
                    + " INTEGER, "
                    + SourceContract.EVENT_REPORT_WINDOW
                    + " INTEGER, "
                    + SourceContract.AGGREGATABLE_REPORT_WINDOW
                    + " INTEGER, "
                    + SourceContract.PRIORITY
                    + " INTEGER, "
                    + SourceContract.STATUS
                    + " INTEGER, "
                    + SourceContract.EVENT_REPORT_DEDUP_KEYS
                    + " TEXT, "
                    + SourceContract.AGGREGATE_REPORT_DEDUP_KEYS
                    + " TEXT, "
                    + SourceContract.SOURCE_TYPE
                    + " TEXT, "
                    + SourceContract.REGISTRANT
                    + " TEXT, "
                    + SourceContract.ATTRIBUTION_MODE
                    + " INTEGER, "
                    + SourceContract.INSTALL_ATTRIBUTION_WINDOW
                    + " INTEGER, "
                    + SourceContract.INSTALL_COOLDOWN_WINDOW
                    + " INTEGER, "
                    + SourceContract.IS_INSTALL_ATTRIBUTED
                    + " INTEGER, "
                    + SourceContract.FILTER_DATA
                    + " TEXT, "
                    + SourceContract.AGGREGATE_SOURCE
                    + " TEXT, "
                    + SourceContract.AGGREGATE_CONTRIBUTIONS
                    + " INTEGER, "
                    + SourceContract.DEBUG_KEY
                    + " INTEGER , "
                    + SourceContract.DEBUG_REPORTING
                    + " INTEGER, "
                    + SourceContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + SourceContract.AR_DEBUG_PERMISSION
                    + " INTEGER, "
                    + SourceContract.REGISTRATION_ID
                    + " TEXT, "
                    + SourceContract.SHARED_AGGREGATION_KEYS
                    + " TEXT, "
                    + SourceContract.INSTALL_TIME
                    + " INTEGER, "
                    + SourceContract.DEBUG_JOIN_KEY
                    + " TEXT, "
                    + SourceContract.TRIGGER_SPECS
                    + " TEXT, "
                    + SourceContract.MAX_EVENT_LEVEL_REPORTS
                    + " INTEGER, "
                    + SourceContract.PLATFORM_AD_ID
                    + " TEXT, "
                    + SourceContract.DEBUG_AD_ID
                    + " TEXT, "
                    + SourceContract.REGISTRATION_ORIGIN
                    + " TEXT, "
                    + SourceContract.COARSE_EVENT_REPORT_DESTINATIONS
                    + " INTEGER, "
                    + SourceContract.EVENT_ATTRIBUTION_STATUS
                    + " TEXT, "
                    + SourceContract.PRIVACY_PARAMETERS
                    + " TEXT, "
                    + SourceContract.EVENT_REPORT_WINDOWS
                    + " TEXT, "
                    + SourceContract.SHARED_DEBUG_KEY
                    + " INTEGER, "
                    + SourceContract.SHARED_FILTER_DATA_KEYS
                    + " TEXT, "
                    + SourceContract.TRIGGER_DATA_MATCHING
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_DESTINATION_LATEST =
            "CREATE TABLE "
                    + SourceDestination.TABLE
                    + " ("
                    + SourceDestination.SOURCE_ID
                    + " TEXT, "
                    + SourceDestination.DESTINATION_TYPE
                    + " INTEGER, "
                    + SourceDestination.DESTINATION
                    + " TEXT, "
                    + "FOREIGN KEY ("
                    + SourceDestination.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER_V6 =
            "CREATE TABLE "
                    + TriggerContract.TABLE
                    + " ("
                    + TriggerContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + TriggerContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + TriggerContract.DESTINATION_TYPE
                    + " INTEGER, "
                    + TriggerContract.ENROLLMENT_ID
                    + " TEXT, "
                    + TriggerContract.TRIGGER_TIME
                    + " INTEGER, "
                    + TriggerContract.EVENT_TRIGGERS
                    + " TEXT, "
                    + TriggerContract.STATUS
                    + " INTEGER, "
                    + TriggerContract.REGISTRANT
                    + " TEXT, "
                    + TriggerContract.AGGREGATE_TRIGGER_DATA
                    + " TEXT, "
                    + TriggerContract.AGGREGATE_VALUES
                    + " TEXT, "
                    + TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS
                    + " TEXT, "
                    + TriggerContract.FILTERS
                    + " TEXT, "
                    + TriggerContract.NOT_FILTERS
                    + " TEXT, "
                    + TriggerContract.DEBUG_KEY
                    + " INTEGER, "
                    + TriggerContract.DEBUG_REPORTING
                    + " INTEGER, "
                    + TriggerContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + TriggerContract.AR_DEBUG_PERMISSION
                    + " INTEGER, "
                    + TriggerContract.ATTRIBUTION_CONFIG
                    + " TEXT, "
                    + TriggerContract.X_NETWORK_KEY_MAPPING
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER_LATEST =
            "CREATE TABLE "
                    + TriggerContract.TABLE
                    + " ("
                    + TriggerContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + TriggerContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + TriggerContract.DESTINATION_TYPE
                    + " INTEGER, "
                    + TriggerContract.ENROLLMENT_ID
                    + " TEXT, "
                    + TriggerContract.TRIGGER_TIME
                    + " INTEGER, "
                    + TriggerContract.EVENT_TRIGGERS
                    + " TEXT, "
                    + TriggerContract.STATUS
                    + " INTEGER, "
                    + TriggerContract.REGISTRANT
                    + " TEXT, "
                    + TriggerContract.AGGREGATE_TRIGGER_DATA
                    + " TEXT, "
                    + TriggerContract.AGGREGATE_VALUES
                    + " TEXT, "
                    + TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS
                    + " TEXT, "
                    + TriggerContract.FILTERS
                    + " TEXT, "
                    + TriggerContract.NOT_FILTERS
                    + " TEXT, "
                    + TriggerContract.DEBUG_KEY
                    + " INTEGER, "
                    + TriggerContract.DEBUG_REPORTING
                    + " INTEGER, "
                    + TriggerContract.AD_ID_PERMISSION
                    + " INTEGER, "
                    + TriggerContract.AR_DEBUG_PERMISSION
                    + " INTEGER, "
                    + TriggerContract.ATTRIBUTION_CONFIG
                    + " TEXT, "
                    + TriggerContract.X_NETWORK_KEY_MAPPING
                    + " TEXT, "
                    + TriggerContract.DEBUG_JOIN_KEY
                    + " TEXT, "
                    + TriggerContract.PLATFORM_AD_ID
                    + " TEXT, "
                    + TriggerContract.DEBUG_AD_ID
                    + " TEXT, "
                    + TriggerContract.REGISTRATION_ORIGIN
                    + " TEXT, "
                    + TriggerContract.AGGREGATION_COORDINATOR_ORIGIN
                    + " TEXT, "
                    + TriggerContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG
                    + " TEXT "
                    + ")";

    // Only used in V3
    public static final String CREATE_TABLE_EVENT_REPORT_V3 =
            "CREATE TABLE "
                    + EventReportContract.TABLE
                    + " ("
                    + EventReportContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EventReportContract.SOURCE_EVENT_ID
                    + " INTEGER, "
                    + EventReportContract.ENROLLMENT_ID
                    + " TEXT, "
                    + EventReportContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + EventReportContract.REPORT_TIME
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_DATA
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_PRIORITY
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_DEDUP_KEY
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_TIME
                    + " INTEGER, "
                    + EventReportContract.STATUS
                    + " INTEGER, "
                    + EventReportContract.DEBUG_REPORT_STATUS
                    + " INTEGER, "
                    + EventReportContract.SOURCE_TYPE
                    + " TEXT, "
                    + EventReportContract.RANDOMIZED_TRIGGER_RATE
                    + " DOUBLE, "
                    + EventReportContract.SOURCE_DEBUG_KEY
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_DEBUG_KEY
                    + " INTEGER, "
                    + EventReportContract.SOURCE_ID
                    + " TEXT, "
                    + EventReportContract.TRIGGER_ID
                    + " TEXT, "
                    + "FOREIGN KEY ("
                    + EventReportContract.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE, "
                    + "FOREIGN KEY ("
                    + EventReportContract.TRIGGER_ID
                    + ") REFERENCES "
                    + TriggerContract.TABLE
                    + "("
                    + TriggerContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    public static final String CREATE_TABLE_EVENT_REPORT_LATEST =
            "CREATE TABLE "
                    + EventReportContract.TABLE
                    + " ("
                    + EventReportContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + EventReportContract.SOURCE_EVENT_ID
                    + " INTEGER, "
                    + EventReportContract.ENROLLMENT_ID
                    + " TEXT, "
                    + EventReportContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + EventReportContract.REPORT_TIME
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_DATA
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_PRIORITY
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_DEDUP_KEY
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_TIME
                    + " INTEGER, "
                    + EventReportContract.STATUS
                    + " INTEGER, "
                    + EventReportContract.DEBUG_REPORT_STATUS
                    + " INTEGER, "
                    + EventReportContract.SOURCE_TYPE
                    + " TEXT, "
                    + EventReportContract.RANDOMIZED_TRIGGER_RATE
                    + " DOUBLE, "
                    + EventReportContract.SOURCE_DEBUG_KEY
                    + " INTEGER, "
                    + EventReportContract.TRIGGER_DEBUG_KEY
                    + " INTEGER, "
                    + EventReportContract.SOURCE_ID
                    + " TEXT, "
                    + EventReportContract.TRIGGER_ID
                    + " TEXT, "
                    + EventReportContract.REGISTRATION_ORIGIN
                    + " TEXT, "
                    + EventReportContract.TRIGGER_SUMMARY_BUCKET
                    + " TEXT, "
                    + EventReportContract.TRIGGER_DEBUG_KEYS
                    + " TEXT, "
                    + "FOREIGN KEY ("
                    + EventReportContract.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE, "
                    + "FOREIGN KEY ("
                    + EventReportContract.TRIGGER_ID
                    + ") REFERENCES "
                    + TriggerContract.TABLE
                    + "("
                    + TriggerContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    public static final String CREATE_TABLE_ATTRIBUTION_V6 =
            "CREATE TABLE "
                    + AttributionContract.TABLE
                    + " ("
                    + AttributionContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AttributionContract.SOURCE_SITE
                    + " TEXT, "
                    + AttributionContract.SOURCE_ORIGIN
                    + " TEXT, "
                    + AttributionContract.DESTINATION_SITE
                    + " TEXT, "
                    + AttributionContract.DESTINATION_ORIGIN
                    + " TEXT, "
                    + AttributionContract.ENROLLMENT_ID
                    + " TEXT, "
                    + AttributionContract.TRIGGER_TIME
                    + " INTEGER, "
                    + AttributionContract.REGISTRANT
                    + " TEXT, "
                    + AttributionContract.SOURCE_ID
                    + " TEXT, "
                    + AttributionContract.TRIGGER_ID
                    + " TEXT, "
                    + "FOREIGN KEY ("
                    + AttributionContract.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE, "
                    + "FOREIGN KEY ("
                    + AttributionContract.TRIGGER_ID
                    + ") REFERENCES "
                    + TriggerContract.TABLE
                    + "("
                    + TriggerContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    public static final String CREATE_TABLE_ATTRIBUTION_LATEST =
            "CREATE TABLE "
                    + AttributionContract.TABLE
                    + " ("
                    + AttributionContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AttributionContract.SOURCE_SITE
                    + " TEXT, "
                    + AttributionContract.SOURCE_ORIGIN
                    + " TEXT, "
                    + AttributionContract.DESTINATION_SITE
                    + " TEXT, "
                    + AttributionContract.DESTINATION_ORIGIN
                    + " TEXT, "
                    + AttributionContract.ENROLLMENT_ID
                    + " TEXT, "
                    + AttributionContract.TRIGGER_TIME
                    + " INTEGER, "
                    + AttributionContract.REGISTRANT
                    + " TEXT, "
                    + AttributionContract.SOURCE_ID
                    + " TEXT, "
                    + AttributionContract.TRIGGER_ID
                    + " TEXT, "
                    + AttributionContract.REGISTRATION_ORIGIN
                    + " TEXT, "
                    + AttributionContract.SCOPE
                    + " INTEGER, "
                    + "FOREIGN KEY ("
                    + AttributionContract.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE, "
                    + "FOREIGN KEY ("
                    + AttributionContract.TRIGGER_ID
                    + ") REFERENCES "
                    + TriggerContract.TABLE
                    + "("
                    + TriggerContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    public static final String CREATE_TABLE_AGGREGATE_REPORT_V6 =
            "CREATE TABLE "
                    + AggregateReport.TABLE
                    + " ("
                    + AggregateReport.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AggregateReport.PUBLISHER
                    + " TEXT, "
                    + AggregateReport.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + AggregateReport.SOURCE_REGISTRATION_TIME
                    + " INTEGER, "
                    + AggregateReport.SCHEDULED_REPORT_TIME
                    + " INTEGER, "
                    + AggregateReport.ENROLLMENT_ID
                    + " TEXT, "
                    + AggregateReport.DEBUG_CLEARTEXT_PAYLOAD
                    + " TEXT, "
                    + AggregateReport.STATUS
                    + " INTEGER, "
                    + AggregateReport.DEBUG_REPORT_STATUS
                    + " INTEGER, "
                    + AggregateReport.API_VERSION
                    + " TEXT, "
                    + AggregateReport.SOURCE_DEBUG_KEY
                    + " INTEGER, "
                    + AggregateReport.TRIGGER_DEBUG_KEY
                    + " INTEGER, "
                    + AggregateReport.SOURCE_ID
                    + " TEXT, "
                    + AggregateReport.TRIGGER_ID
                    + " TEXT, "
                    + "FOREIGN KEY ("
                    + AggregateReport.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE "
                    + "FOREIGN KEY ("
                    + AggregateReport.TRIGGER_ID
                    + ") REFERENCES "
                    + TriggerContract.TABLE
                    + "("
                    + TriggerContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    public static final String CREATE_TABLE_AGGREGATE_REPORT_LATEST =
            "CREATE TABLE "
                    + AggregateReport.TABLE
                    + " ("
                    + AggregateReport.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AggregateReport.PUBLISHER
                    + " TEXT, "
                    + AggregateReport.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + AggregateReport.SOURCE_REGISTRATION_TIME
                    + " INTEGER, "
                    + AggregateReport.SCHEDULED_REPORT_TIME
                    + " INTEGER, "
                    + AggregateReport.ENROLLMENT_ID
                    + " TEXT, "
                    + AggregateReport.DEBUG_CLEARTEXT_PAYLOAD
                    + " TEXT, "
                    + AggregateReport.STATUS
                    + " INTEGER, "
                    + AggregateReport.DEBUG_REPORT_STATUS
                    + " INTEGER, "
                    + AggregateReport.API_VERSION
                    + " TEXT, "
                    + AggregateReport.SOURCE_DEBUG_KEY
                    + " INTEGER, "
                    + AggregateReport.TRIGGER_DEBUG_KEY
                    + " INTEGER, "
                    + AggregateReport.SOURCE_ID
                    + " TEXT, "
                    + AggregateReport.TRIGGER_ID
                    + " TEXT, "
                    + AggregateReport.DEDUP_KEY
                    + " INTEGER, "
                    + AggregateReport.REGISTRATION_ORIGIN
                    + " TEXT, "
                    + AggregateReport.AGGREGATION_COORDINATOR_ORIGIN
                    + " TEXT, "
                    + AggregateReport.IS_FAKE_REPORT
                    + " INTEGER, "
                    + "FOREIGN KEY ("
                    + AggregateReport.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE "
                    + "FOREIGN KEY ("
                    + AggregateReport.TRIGGER_ID
                    + ") REFERENCES "
                    + TriggerContract.TABLE
                    + "("
                    + TriggerContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    public static final String CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V6 =
            "CREATE TABLE "
                    + AggregateEncryptionKey.TABLE
                    + " ("
                    + AggregateEncryptionKey.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AggregateEncryptionKey.KEY_ID
                    + " TEXT, "
                    + AggregateEncryptionKey.PUBLIC_KEY
                    + " TEXT, "
                    + AggregateEncryptionKey.EXPIRY
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_LATEST =
            "CREATE TABLE "
                    + AggregateEncryptionKey.TABLE
                    + " ("
                    + AggregateEncryptionKey.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + AggregateEncryptionKey.KEY_ID
                    + " TEXT, "
                    + AggregateEncryptionKey.PUBLIC_KEY
                    + " TEXT, "
                    + AggregateEncryptionKey.EXPIRY
                    + " INTEGER, "
                    + AggregateEncryptionKey.AGGREGATION_COORDINATOR_ORIGIN
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_DEBUG_REPORT_V3 =
            "CREATE TABLE IF NOT EXISTS "
                    + DebugReportContract.TABLE
                    + " ("
                    + DebugReportContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + DebugReportContract.TYPE
                    + " TEXT, "
                    + DebugReportContract.BODY
                    + " TEXT, "
                    + DebugReportContract.ENROLLMENT_ID
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_DEBUG_REPORT_LATEST =
            "CREATE TABLE IF NOT EXISTS "
                    + DebugReportContract.TABLE
                    + " ("
                    + DebugReportContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + DebugReportContract.TYPE
                    + " TEXT, "
                    + DebugReportContract.BODY
                    + " TEXT, "
                    + DebugReportContract.ENROLLMENT_ID
                    + " TEXT, "
                    + DebugReportContract.REGISTRATION_ORIGIN
                    + " TEXT, "
                    + DebugReportContract.REFERENCE_ID
                    + " TEXT, "
                    + DebugReportContract.INSERTION_TIME
                    + " INTEGER, "
                    + DebugReportContract.REGISTRANT
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_XNA_IGNORED_SOURCES_V6 =
            "CREATE TABLE "
                    + XnaIgnoredSourcesContract.TABLE
                    + " ("
                    + XnaIgnoredSourcesContract.SOURCE_ID
                    + " TEXT NOT NULL, "
                    + XnaIgnoredSourcesContract.ENROLLMENT_ID
                    + " TEXT NOT NULL, "
                    + "FOREIGN KEY ("
                    + XnaIgnoredSourcesContract.SOURCE_ID
                    + ") REFERENCES "
                    + SourceContract.TABLE
                    + "("
                    + SourceContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    public static final String CREATE_TABLE_XNA_IGNORED_SOURCES_LATEST =
            CREATE_TABLE_XNA_IGNORED_SOURCES_V6;

    public static final String CREATE_TABLE_KEY_VALUE_STORE_LATEST =
            MeasurementDbMigratorV11.CREATE_TABLE_KEY_VALUE_DATA_V11;

    public static final String[] CREATE_INDEXES = {
        "CREATE INDEX "
                + INDEX_PREFIX
                + SourceContract.TABLE
                + "_ei_et "
                + "ON "
                + SourceContract.TABLE
                + "( "
                + SourceContract.ENROLLMENT_ID
                + ", "
                + SourceContract.EXPIRY_TIME
                + " DESC "
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + SourceContract.TABLE
                + "_et "
                + "ON "
                + SourceContract.TABLE
                + "("
                + SourceContract.EXPIRY_TIME
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + SourceContract.TABLE
                + "_p_s_et "
                + "ON "
                + SourceContract.TABLE
                + "("
                + SourceContract.PUBLISHER
                + ", "
                + SourceContract.STATUS
                + ", "
                + SourceContract.EVENT_TIME
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + SourceContract.TABLE
                + "_ei "
                + "ON "
                + SourceContract.TABLE
                + "("
                + SourceContract.ENROLLMENT_ID
                + ")",
        "CREATE INDEX "
                + MeasurementTables.INDEX_PREFIX
                + MeasurementTables.SourceDestination.TABLE
                + "_d"
                + " ON "
                + MeasurementTables.SourceDestination.TABLE
                + "("
                + MeasurementTables.SourceDestination.DESTINATION
                + ")",
        "CREATE INDEX "
                + MeasurementTables.INDEX_PREFIX
                + MeasurementTables.SourceDestination.TABLE
                + "_s"
                + " ON "
                + MeasurementTables.SourceDestination.TABLE
                + "("
                + MeasurementTables.SourceDestination.SOURCE_ID
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + TriggerContract.TABLE
                + "_ad_ei_tt "
                + "ON "
                + TriggerContract.TABLE
                + "( "
                + TriggerContract.ATTRIBUTION_DESTINATION
                + ", "
                + TriggerContract.ENROLLMENT_ID
                + ", "
                + TriggerContract.TRIGGER_TIME
                + " ASC)",
        "CREATE INDEX "
                + INDEX_PREFIX
                + TriggerContract.TABLE
                + "_tt "
                + "ON "
                + TriggerContract.TABLE
                + "("
                + TriggerContract.TRIGGER_TIME
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + AttributionContract.TABLE
                + "_s_ss_ds_ei_tt"
                + " ON "
                + AttributionContract.TABLE
                + "("
                + AttributionContract.SCOPE
                + ", "
                + AttributionContract.SOURCE_SITE
                + ", "
                + AttributionContract.DESTINATION_SITE
                + ", "
                + AttributionContract.ENROLLMENT_ID
                + ", "
                + AttributionContract.TRIGGER_TIME
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + XnaIgnoredSourcesContract.TABLE
                + "_ei "
                + "ON "
                + XnaIgnoredSourcesContract.TABLE
                + "("
                + XnaIgnoredSourcesContract.ENROLLMENT_ID
                + ")",
    };

    public static final String[] CREATE_INDEXES_V6 = {
        "CREATE INDEX "
                + INDEX_PREFIX
                + SourceContract.TABLE
                + "_ad_ei_et "
                + "ON "
                + SourceContract.TABLE
                + "( "
                + MeasurementTablesDeprecated.SourceContract.APP_DESTINATION
                + ", "
                + SourceContract.ENROLLMENT_ID
                + ", "
                + SourceContract.EXPIRY_TIME
                + " DESC "
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + SourceContract.TABLE
                + "_et "
                + "ON "
                + SourceContract.TABLE
                + "("
                + SourceContract.EXPIRY_TIME
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + SourceContract.TABLE
                + "_p_ad_wd_s_et "
                + "ON "
                + SourceContract.TABLE
                + "("
                + SourceContract.PUBLISHER
                + ", "
                + MeasurementTablesDeprecated.SourceContract.APP_DESTINATION
                + ", "
                + MeasurementTablesDeprecated.SourceContract.WEB_DESTINATION
                + ", "
                + SourceContract.STATUS
                + ", "
                + SourceContract.EVENT_TIME
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + TriggerContract.TABLE
                + "_ad_ei_tt "
                + "ON "
                + TriggerContract.TABLE
                + "( "
                + TriggerContract.ATTRIBUTION_DESTINATION
                + ", "
                + TriggerContract.ENROLLMENT_ID
                + ", "
                + TriggerContract.TRIGGER_TIME
                + " ASC)",
        "CREATE INDEX "
                + INDEX_PREFIX
                + TriggerContract.TABLE
                + "_tt "
                + "ON "
                + TriggerContract.TABLE
                + "("
                + TriggerContract.TRIGGER_TIME
                + ")",
        "CREATE INDEX "
                + INDEX_PREFIX
                + AttributionContract.TABLE
                + "_ss_so_ds_do_ei_tt"
                + " ON "
                + AttributionContract.TABLE
                + "("
                + AttributionContract.SOURCE_SITE
                + ", "
                + AttributionContract.SOURCE_ORIGIN
                + ", "
                + AttributionContract.DESTINATION_SITE
                + ", "
                + AttributionContract.DESTINATION_ORIGIN
                + ", "
                + AttributionContract.ENROLLMENT_ID
                + ", "
                + AttributionContract.TRIGGER_TIME
                + ")"
    };

    // Consolidated list of create statements for all tables.
    public static final List<String> CREATE_STATEMENTS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            CREATE_TABLE_SOURCE_LATEST,
                            CREATE_TABLE_SOURCE_DESTINATION_LATEST,
                            CREATE_TABLE_TRIGGER_LATEST,
                            CREATE_TABLE_EVENT_REPORT_LATEST,
                            CREATE_TABLE_ATTRIBUTION_LATEST,
                            CREATE_TABLE_AGGREGATE_REPORT_LATEST,
                            CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_LATEST,
                            CREATE_TABLE_ASYNC_REGISTRATION_LATEST,
                            CREATE_TABLE_DEBUG_REPORT_LATEST,
                            CREATE_TABLE_XNA_IGNORED_SOURCES_LATEST,
                            CREATE_TABLE_KEY_VALUE_STORE_LATEST));

    // Consolidated list of create statements for all tables at version 6.
    public static final List<String> CREATE_STATEMENTS_V6 =
            Collections.unmodifiableList(
                    Arrays.asList(
                            CREATE_TABLE_SOURCE_V6,
                            CREATE_TABLE_TRIGGER_V6,
                            CREATE_TABLE_EVENT_REPORT_V3,
                            CREATE_TABLE_ATTRIBUTION_V6,
                            CREATE_TABLE_AGGREGATE_REPORT_V6,
                            CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V6,
                            CREATE_TABLE_ASYNC_REGISTRATION_V6,
                            CREATE_TABLE_DEBUG_REPORT_V3,
                            CREATE_TABLE_XNA_IGNORED_SOURCES_V6));

    // Private constructor to prevent instantiation.
    private MeasurementTables() {
    }
}
