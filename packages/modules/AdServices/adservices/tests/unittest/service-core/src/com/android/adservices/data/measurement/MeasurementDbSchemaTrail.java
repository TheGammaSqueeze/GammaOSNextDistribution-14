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

package com.android.adservices.data.measurement;

import static com.android.adservices.data.measurement.MeasurementTables.AggregateEncryptionKey;
import static com.android.adservices.data.measurement.MeasurementTables.AggregateReport;
import static com.android.adservices.data.measurement.MeasurementTables.AsyncRegistrationContract;
import static com.android.adservices.data.measurement.MeasurementTables.AttributionContract;
import static com.android.adservices.data.measurement.MeasurementTables.DebugReportContract;
import static com.android.adservices.data.measurement.MeasurementTables.EventReportContract;
import static com.android.adservices.data.measurement.MeasurementTables.INDEX_PREFIX;
import static com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import static com.android.adservices.data.measurement.MeasurementTables.SourceDestination;
import static com.android.adservices.data.measurement.MeasurementTables.TriggerContract;
import static com.android.adservices.data.measurement.MeasurementTables.XnaIgnoredSourcesContract;

import com.android.adservices.data.measurement.MeasurementTables.KeyValueDataContract;
import com.android.adservices.data.measurement.migration.MeasurementTablesDeprecated;

import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Has scripts to create database at any version. To introduce migration to a new version x, this
 * class should have one entry each to {@link #CREATE_TABLES_STATEMENTS_BY_VERSION} and {@link
 * #CREATE_INDEXES_STATEMENTS_BY_VERSION} for version x. These entries will cause creation of 2 new
 * methods {@code getCreateStatementByTableVx} and {@code getCreateIndexesVx}, where the previous
 * version's (x-1) scripts will be revised to create scripts for version x.
 */
public class MeasurementDbSchemaTrail {
    private static final String CREATE_TABLE_SOURCE_V6 =
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

    public static final String CREATE_TABLE_SOURCE_V8 =
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
                    + " INTEGER, "
                    + SourceContract.DEBUG_JOIN_KEY
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V9 =
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
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V12 =
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
                    + MeasurementTablesDeprecated.SourceContract.MAX_BUCKET_INCREMENTS
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V13 =
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
                    + MeasurementTablesDeprecated.SourceContract.MAX_BUCKET_INCREMENTS
                    + " INTEGER, "
                    + SourceContract.PLATFORM_AD_ID
                    + " TEXT, "
                    + SourceContract.DEBUG_AD_ID
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V14 =
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
                    + MeasurementTablesDeprecated.SourceContract.MAX_BUCKET_INCREMENTS
                    + " INTEGER, "
                    + SourceContract.PLATFORM_AD_ID
                    + " TEXT, "
                    + SourceContract.DEBUG_AD_ID
                    + " TEXT, "
                    + SourceContract.REGISTRATION_ORIGIN
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V16 =
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
                    + MeasurementTablesDeprecated.SourceContract.MAX_BUCKET_INCREMENTS
                    + " INTEGER, "
                    + SourceContract.PLATFORM_AD_ID
                    + " TEXT, "
                    + SourceContract.DEBUG_AD_ID
                    + " TEXT, "
                    + SourceContract.REGISTRATION_ORIGIN
                    + " TEXT, "
                    + SourceContract.COARSE_EVENT_REPORT_DESTINATIONS
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V18 =
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
                    + MeasurementTablesDeprecated.SourceContract.MAX_BUCKET_INCREMENTS
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
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V19 =
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
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V21 =
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
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V22 =
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
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V30 =
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

    public static final String CREATE_TABLE_SOURCE_DESTINATION_V9 =
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

    private static final String CREATE_TABLE_TRIGGER_V6 =
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

    public static final String CREATE_TABLE_TRIGGER_V8 =
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
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER_V13 =
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
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER_V14 =
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
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER_V20 =
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
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER_V32 =
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

    private static final String CREATE_TABLE_EVENT_REPORT_V6 =
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

    private static final String CREATE_TABLE_EVENT_REPORT_V14 =
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

    private static final String CREATE_TABLE_EVENT_REPORT_V23 =
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

    private static final String CREATE_TABLE_EVENT_REPORT_V28 =
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

    private static final String CREATE_TABLE_ATTRIBUTION_V6 =
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

    private static final String CREATE_TABLE_ATTRIBUTION_V25 =
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

    private static final String CREATE_TABLE_ATTRIBUTION_V29 =
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

    private static final String CREATE_TABLE_AGGREGATE_REPORT_V6 =
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

    private static final String CREATE_TABLE_AGGREGATE_REPORT_V10 =
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

    private static final String CREATE_TABLE_AGGREGATE_REPORT_V14 =
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

    private static final String CREATE_TABLE_AGGREGATE_REPORT_V20 =
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

    private static final String CREATE_TABLE_AGGREGATE_REPORT_V27 =
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

    private static final String CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V6 =
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

    private static final String CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V20 =
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

    private static final String CREATE_TABLE_ASYNC_REGISTRATION_V6 =
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

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_V11 =
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
                    + " TEXT NOT NULL"
                    + ")";

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_V13 =
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
                    + MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID
                    + " TEXT NOT NULL,"
                    + MeasurementTables.AsyncRegistrationContract.PLATFORM_AD_ID
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_V24 =
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
                    + MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID
                    + " TEXT NOT NULL,"
                    + MeasurementTables.AsyncRegistrationContract.PLATFORM_AD_ID
                    + " TEXT, "
                    + MeasurementTables.AsyncRegistrationContract.REQUEST_POST_BODY
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_V31 =
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

    private static final String CREATE_TABLE_DEBUG_REPORT_V6 =
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

    private static final String CREATE_TABLE_DEBUG_REPORT_V15 =
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
                    + " TEXT "
                    + ")";
    private static final String CREATE_TABLE_DEBUG_REPORT_V17 =
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
                    + " TEXT "
                    + ")";

    private static final String CREATE_TABLE_DEBUG_REPORT_V26 =
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

    private static final String CREATE_TABLE_XNA_IGNORED_SOURCES_V6 =
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

    public static final String CREATE_TABLE_KEY_VALUE_DATA_V11 =
            "CREATE TABLE "
                    + KeyValueDataContract.TABLE
                    + " ("
                    + KeyValueDataContract.DATA_TYPE
                    + " TEXT NOT NULL, "
                    + KeyValueDataContract.KEY
                    + " TEXT NOT NULL, "
                    + KeyValueDataContract.VALUE
                    + " TEXT, "
                    + " CONSTRAINT type_key_primary_con PRIMARY KEY ( "
                    + KeyValueDataContract.DATA_TYPE
                    + ", "
                    + KeyValueDataContract.KEY
                    + " )"
                    + " )";

    private static final Map<String, String> CREATE_STATEMENT_BY_TABLE_V6 =
            ImmutableMap.of(
                    SourceContract.TABLE, CREATE_TABLE_SOURCE_V6,
                    TriggerContract.TABLE, CREATE_TABLE_TRIGGER_V6,
                    EventReportContract.TABLE, CREATE_TABLE_EVENT_REPORT_V6,
                    AggregateReport.TABLE, CREATE_TABLE_AGGREGATE_REPORT_V6,
                    AttributionContract.TABLE, CREATE_TABLE_ATTRIBUTION_V6,
                    AggregateEncryptionKey.TABLE, CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V6,
                    AsyncRegistrationContract.TABLE, CREATE_TABLE_ASYNC_REGISTRATION_V6,
                    DebugReportContract.TABLE, CREATE_TABLE_DEBUG_REPORT_V6,
                    XnaIgnoredSourcesContract.TABLE, CREATE_TABLE_XNA_IGNORED_SOURCES_V6);

    private static final Map<String, String> CREATE_INDEXES_V6 =
            ImmutableMap.of(
                    INDEX_PREFIX + SourceContract.TABLE + "_ad_ei_et",
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
                    INDEX_PREFIX + SourceContract.TABLE + "_et",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + SourceContract.TABLE
                            + "_et "
                            + "ON "
                            + SourceContract.TABLE
                            + "("
                            + SourceContract.EXPIRY_TIME
                            + ")",
                    INDEX_PREFIX + SourceContract.TABLE + "_p_ad_wd_s_et",
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
                    INDEX_PREFIX + TriggerContract.TABLE + "_ad_ei_tt",
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
                    INDEX_PREFIX + TriggerContract.TABLE + "_tt",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + TriggerContract.TABLE
                            + "_tt "
                            + "ON "
                            + TriggerContract.TABLE
                            + "("
                            + TriggerContract.TRIGGER_TIME
                            + ")",
                    INDEX_PREFIX + AttributionContract.TABLE + "_ss_so_ds_do_ei_tt",
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
                            + ")");

    private static final Map<String, String> CREATE_INDEXES_V6_V7 =
            ImmutableMap.of(
                    INDEX_PREFIX + MeasurementTables.SourceContract.TABLE + "_ei",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + MeasurementTables.SourceContract.TABLE
                            + "_ei "
                            + "ON "
                            + MeasurementTables.SourceContract.TABLE
                            + "("
                            + MeasurementTables.SourceContract.ENROLLMENT_ID
                            + ")",
                    INDEX_PREFIX + MeasurementTables.XnaIgnoredSourcesContract.TABLE + "_ei",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + MeasurementTables.XnaIgnoredSourcesContract.TABLE
                            + "_ei "
                            + "ON "
                            + MeasurementTables.XnaIgnoredSourcesContract.TABLE
                            + "("
                            + MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID
                            + ")");

    private static final Map<String, String> CREATE_INDEXES_V8_V9 =
            ImmutableMap.of(
                    INDEX_PREFIX + MeasurementTables.SourceContract.TABLE + "_ei_et",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + MeasurementTables.SourceContract.TABLE
                            + "_ei_et "
                            + "ON "
                            + MeasurementTables.SourceContract.TABLE
                            + "( "
                            + MeasurementTables.SourceContract.ENROLLMENT_ID
                            + ", "
                            + MeasurementTables.SourceContract.EXPIRY_TIME
                            + " DESC "
                            + ")",
                    INDEX_PREFIX + MeasurementTables.SourceContract.TABLE + "_p_s_et",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + MeasurementTables.SourceContract.TABLE
                            + "_p_s_et "
                            + "ON "
                            + MeasurementTables.SourceContract.TABLE
                            + "("
                            + MeasurementTables.SourceContract.PUBLISHER
                            + ", "
                            + MeasurementTables.SourceContract.STATUS
                            + ", "
                            + MeasurementTables.SourceContract.EVENT_TIME
                            + ")",
                    INDEX_PREFIX + MeasurementTables.SourceDestination.TABLE + "_d",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + MeasurementTables.SourceDestination.TABLE
                            + "_d"
                            + " ON "
                            + MeasurementTables.SourceDestination.TABLE
                            + "("
                            + MeasurementTables.SourceDestination.DESTINATION
                            + ")",
                    INDEX_PREFIX + MeasurementTables.SourceDestination.TABLE + "_s",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + MeasurementTables.SourceDestination.TABLE
                            + "_s"
                            + " ON "
                            + MeasurementTables.SourceDestination.TABLE
                            + "("
                            + MeasurementTables.SourceDestination.SOURCE_ID
                            + ")");

    private static final Map<String, String> CREATE_INDEXES_V28_V29 =
            ImmutableMap.of(
                    INDEX_PREFIX + MeasurementTables.AttributionContract.TABLE
                            + "_s_ss_so_ds_do_ei_tt",
                    "CREATE INDEX "
                            + INDEX_PREFIX
                            + MeasurementTables.AttributionContract.TABLE
                            + "_s_ss_ds_ei_tt"
                            + " ON "
                            + MeasurementTables.AttributionContract.TABLE
                            + "("
                            + MeasurementTables.AttributionContract.SCOPE
                            + ", "
                            + MeasurementTables.AttributionContract.SOURCE_SITE
                            + ", "
                            + MeasurementTables.AttributionContract.DESTINATION_SITE
                            + ", "
                            + MeasurementTables.AttributionContract.ENROLLMENT_ID
                            + ", "
                            + MeasurementTables.AttributionContract.TRIGGER_TIME
                            + ")");

    private static Map<String, String> getCreateStatementByTableV7() {
        return CREATE_STATEMENT_BY_TABLE_V6;
    }

    private static Map<String, String> getCreateStatementByTableV8() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV7());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V8);
        createStatements.put(TriggerContract.TABLE, CREATE_TABLE_TRIGGER_V8);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV9() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV8());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V9);
        createStatements.put(SourceDestination.TABLE, CREATE_TABLE_SOURCE_DESTINATION_V9);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV10() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV9());
        createStatements.put(AggregateReport.TABLE, CREATE_TABLE_AGGREGATE_REPORT_V10);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV11() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV10());
        createStatements.put(AsyncRegistrationContract.TABLE, CREATE_TABLE_ASYNC_REGISTRATION_V11);
        createStatements.put(KeyValueDataContract.TABLE, CREATE_TABLE_KEY_VALUE_DATA_V11);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV12() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV11());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V12);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV13() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV12());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V13);
        createStatements.put(TriggerContract.TABLE, CREATE_TABLE_TRIGGER_V13);
        createStatements.put(AsyncRegistrationContract.TABLE, CREATE_TABLE_ASYNC_REGISTRATION_V13);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV14() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV13());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V14);
        createStatements.put(TriggerContract.TABLE, CREATE_TABLE_TRIGGER_V14);
        createStatements.put(EventReportContract.TABLE, CREATE_TABLE_EVENT_REPORT_V14);
        createStatements.put(AggregateReport.TABLE, CREATE_TABLE_AGGREGATE_REPORT_V14);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV15() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV14());
        createStatements.put(DebugReportContract.TABLE, CREATE_TABLE_DEBUG_REPORT_V15);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV16() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV15());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V16);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV17() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV16());
        createStatements.put(DebugReportContract.TABLE, CREATE_TABLE_DEBUG_REPORT_V17);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV18() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV17());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V18);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV19() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV18());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V19);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV20() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV19());
        createStatements.put(TriggerContract.TABLE, CREATE_TABLE_TRIGGER_V20);
        createStatements.put(AggregateReport.TABLE, CREATE_TABLE_AGGREGATE_REPORT_V20);
        createStatements.put(
                AggregateEncryptionKey.TABLE, CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V20);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV21() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV20());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V21);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV22() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV21());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V22);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV23() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV22());
        createStatements.put(EventReportContract.TABLE, CREATE_TABLE_EVENT_REPORT_V23);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV24() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV23());
        createStatements.put(AsyncRegistrationContract.TABLE, CREATE_TABLE_ASYNC_REGISTRATION_V24);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV25() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV24());
        createStatements.put(AttributionContract.TABLE, CREATE_TABLE_ATTRIBUTION_V25);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV26() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV25());
        createStatements.put(DebugReportContract.TABLE, CREATE_TABLE_DEBUG_REPORT_V26);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV27() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV26());
        createStatements.put(AggregateReport.TABLE, CREATE_TABLE_AGGREGATE_REPORT_V27);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV28() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV27());
        createStatements.put(EventReportContract.TABLE, CREATE_TABLE_EVENT_REPORT_V28);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV29() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV28());
        createStatements.put(AttributionContract.TABLE, CREATE_TABLE_ATTRIBUTION_V29);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV30() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV29());
        createStatements.put(SourceContract.TABLE, CREATE_TABLE_SOURCE_V30);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV31() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV30());
        createStatements.put(AsyncRegistrationContract.TABLE, CREATE_TABLE_ASYNC_REGISTRATION_V31);
        return createStatements;
    }

    private static Map<String, String> getCreateStatementByTableV32() {
        Map<String, String> createStatements = new HashMap<>(getCreateStatementByTableV31());
        createStatements.put(TriggerContract.TABLE, CREATE_TABLE_TRIGGER_V32);
        return createStatements;
    }

    private static Map<String, String> getCreateIndexesV7() {
        Map<String, String> createIndexes = new HashMap<>();
        createIndexes.putAll(CREATE_INDEXES_V6);
        createIndexes.putAll(CREATE_INDEXES_V6_V7);
        return createIndexes;
    }

    private static Map<String, String> getCreateIndexesV8() {
        return getCreateIndexesV7();
    }

    private static Map<String, String> getCreateIndexesV9() {
        Map<String, String> createIndexes = getCreateIndexesV8();
        createIndexes.remove(INDEX_PREFIX + SourceContract.TABLE + "_ad_ei_et");
        createIndexes.remove(INDEX_PREFIX + SourceContract.TABLE + "_p_ad_wd_s_et");
        createIndexes.putAll(CREATE_INDEXES_V8_V9);
        return createIndexes;
    }

    private static Map<String, String> getCreateIndexesV10() {
        return getCreateIndexesV9();
    }

    private static Map<String, String> getCreateIndexesV11() {
        return getCreateIndexesV10();
    }

    private static Map<String, String> getCreateIndexesV12() {
        return getCreateIndexesV11();
    }

    private static Map<String, String> getCreateIndexesV13() {
        return getCreateIndexesV12();
    }

    private static Map<String, String> getCreateIndexesV14() {
        return getCreateIndexesV13();
    }

    private static Map<String, String> getCreateIndexesV15() {
        return getCreateIndexesV14();
    }

    private static Map<String, String> getCreateIndexesV16() {
        return getCreateIndexesV15();
    }

    private static Map<String, String> getCreateIndexesV17() {
        return getCreateIndexesV16();
    }

    private static Map<String, String> getCreateIndexesV18() {
        return getCreateIndexesV17();
    }

    private static Map<String, String> getCreateIndexesV19() {
        return getCreateIndexesV18();
    }

    private static Map<String, String> getCreateIndexesV20() {
        return getCreateIndexesV19();
    }

    private static Map<String, String> getCreateIndexesV21() {
        return getCreateIndexesV20();
    }

    private static Map<String, String> getCreateIndexesV22() {
        return getCreateIndexesV21();
    }

    private static Map<String, String> getCreateIndexesV23() {
        return getCreateIndexesV22();
    }

    private static Map<String, String> getCreateIndexesV24() {
        return getCreateIndexesV23();
    }

    private static Map<String, String> getCreateIndexesV25() {
        return getCreateIndexesV24();
    }

    private static Map<String, String> getCreateIndexesV26() {
        return getCreateIndexesV25();
    }

    private static Map<String, String> getCreateIndexesV27() {
        return getCreateIndexesV26();
    }

    private static Map<String, String> getCreateIndexesV28() {
        return getCreateIndexesV27();
    }

    private static Map<String, String> getCreateIndexesV29() {
        Map<String, String> createIndexes = getCreateIndexesV28();
        createIndexes.remove(INDEX_PREFIX + AttributionContract.TABLE + "_ss_so_ds_do_ei_tt");
        createIndexes.putAll(CREATE_INDEXES_V28_V29);
        return createIndexes;
    }

    private static Map<String, String> getCreateIndexesV30() {
        return getCreateIndexesV29();
    }

    private static Map<String, String> getCreateIndexesV31() {
        return getCreateIndexesV30();
    }

    private static Map<String, String> getCreateIndexesV32() {
        return getCreateIndexesV31();
    }

    private static final Map<Integer, Collection<String>> CREATE_TABLES_STATEMENTS_BY_VERSION =
            new ImmutableMap.Builder<Integer, Collection<String>>()
                    .put(6, CREATE_STATEMENT_BY_TABLE_V6.values())
                    .put(7, getCreateStatementByTableV7().values())
                    .put(8, getCreateStatementByTableV8().values())
                    .put(9, getCreateStatementByTableV9().values())
                    .put(10, getCreateStatementByTableV10().values())
                    .put(11, getCreateStatementByTableV11().values())
                    .put(12, getCreateStatementByTableV12().values())
                    .put(13, getCreateStatementByTableV13().values())
                    .put(14, getCreateStatementByTableV14().values())
                    .put(15, getCreateStatementByTableV15().values())
                    .put(16, getCreateStatementByTableV16().values())
                    .put(17, getCreateStatementByTableV17().values())
                    .put(18, getCreateStatementByTableV18().values())
                    .put(19, getCreateStatementByTableV19().values())
                    .put(20, getCreateStatementByTableV20().values())
                    .put(21, getCreateStatementByTableV21().values())
                    .put(22, getCreateStatementByTableV22().values())
                    .put(23, getCreateStatementByTableV23().values())
                    .put(24, getCreateStatementByTableV24().values())
                    .put(25, getCreateStatementByTableV25().values())
                    .put(26, getCreateStatementByTableV26().values())
                    .put(27, getCreateStatementByTableV27().values())
                    .put(28, getCreateStatementByTableV28().values())
                    .put(29, getCreateStatementByTableV29().values())
                    .put(30, getCreateStatementByTableV30().values())
                    .put(31, getCreateStatementByTableV31().values())
                    .put(32, getCreateStatementByTableV32().values())
                    .build();

    private static final Map<Integer, Collection<String>> CREATE_INDEXES_STATEMENTS_BY_VERSION =
            new ImmutableMap.Builder<Integer, Collection<String>>()
                    .put(6, CREATE_INDEXES_V6.values())
                    .put(7, getCreateIndexesV7().values())
                    .put(8, getCreateIndexesV8().values())
                    .put(9, getCreateIndexesV9().values())
                    .put(10, getCreateIndexesV10().values())
                    .put(11, getCreateIndexesV11().values())
                    .put(12, getCreateIndexesV12().values())
                    .put(13, getCreateIndexesV13().values())
                    .put(14, getCreateIndexesV14().values())
                    .put(15, getCreateIndexesV15().values())
                    .put(16, getCreateIndexesV16().values())
                    .put(17, getCreateIndexesV17().values())
                    .put(18, getCreateIndexesV18().values())
                    .put(19, getCreateIndexesV19().values())
                    .put(20, getCreateIndexesV20().values())
                    .put(21, getCreateIndexesV21().values())
                    .put(22, getCreateIndexesV22().values())
                    .put(23, getCreateIndexesV23().values())
                    .put(24, getCreateIndexesV24().values())
                    .put(25, getCreateIndexesV25().values())
                    .put(26, getCreateIndexesV26().values())
                    .put(27, getCreateIndexesV27().values())
                    .put(28, getCreateIndexesV28().values())
                    .put(29, getCreateIndexesV29().values())
                    .put(30, getCreateIndexesV30().values())
                    .put(31, getCreateIndexesV31().values())
                    .put(32, getCreateIndexesV32().values())
                    .build();

    /**
     * Returns a map of table to the respective create statement at the provided version. Supports
     * only 6+ versions.
     *
     * @param version version for which create statements are requested
     * @return map of table to their create statement
     */
    public static Collection<String> getCreateTableStatementsByVersion(int version) {
        if (version < 6) {
            throw new IllegalArgumentException("Unsupported version " + version);
        }

        return CREATE_TABLES_STATEMENTS_BY_VERSION.get(version);
    }

    /**
     * Returns a list create index statement at the provided version. Supports only 6+ versions.
     *
     * @param version version for which create index statements are requested
     * @return list of create index statements
     */
    public static Collection<String> getCreateIndexStatementsByVersion(int version) {
        if (version < 6) {
            throw new IllegalArgumentException("Unsupported version " + version);
        }

        return CREATE_INDEXES_STATEMENTS_BY_VERSION.get(version);
    }
}
