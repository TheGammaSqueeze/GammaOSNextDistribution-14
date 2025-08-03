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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.measurement.migration.MeasurementTablesDeprecated;
import com.android.adservices.data.topics.TopicsTables;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Snapshot of DBHelper at Version 1 */
public class DbHelperV1 extends DbHelper {

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_V1 =
            "CREATE TABLE "
                    + MeasurementTables.AsyncRegistrationContract.TABLE
                    + " ("
                    + MeasurementTables.AsyncRegistrationContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI
                    + " TEXT, "
                    + MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION
                    + " TEXT, "
                    + MeasurementTables.AsyncRegistrationContract.OS_DESTINATION
                    + " TEXT, "
                    + MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION
                    + " TEXT, "
                    + MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN
                    + " TEXT, "
                    + MeasurementTablesDeprecated.AsyncRegistration.REDIRECT
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.AsyncRegistration.INPUT_EVENT
                    + " INTEGER, "
                    + MeasurementTables.AsyncRegistrationContract.REGISTRANT
                    + " TEXT, "
                    + MeasurementTablesDeprecated.AsyncRegistration.SCHEDULED_TIME
                    + " INTEGER, "
                    + MeasurementTables.AsyncRegistrationContract.RETRY_COUNT
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.AsyncRegistration.LAST_PROCESSING_TIME
                    + " INTEGER, "
                    + MeasurementTables.AsyncRegistrationContract.TYPE
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_SOURCE_V1 =
            "CREATE TABLE "
                    + MeasurementTables.SourceContract.TABLE
                    + " ("
                    + MeasurementTables.SourceContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTables.SourceContract.EVENT_ID
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.PUBLISHER
                    + " TEXT, "
                    + MeasurementTables.SourceContract.PUBLISHER_TYPE
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.SourceContract.APP_DESTINATION
                    + " TEXT, "
                    + MeasurementTables.SourceContract.ENROLLMENT_ID
                    + " TEXT, "
                    + MeasurementTables.SourceContract.EVENT_TIME
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.EXPIRY_TIME
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.PRIORITY
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.STATUS
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.SourceContract.DEDUP_KEYS
                    + " TEXT, "
                    + MeasurementTables.SourceContract.SOURCE_TYPE
                    + " TEXT, "
                    + MeasurementTables.SourceContract.REGISTRANT
                    + " TEXT, "
                    + MeasurementTables.SourceContract.ATTRIBUTION_MODE
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.INSTALL_ATTRIBUTION_WINDOW
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.INSTALL_COOLDOWN_WINDOW
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED
                    + " INTEGER, "
                    + MeasurementTables.SourceContract.FILTER_DATA
                    + " TEXT, "
                    + MeasurementTables.SourceContract.AGGREGATE_SOURCE
                    + " TEXT, "
                    + MeasurementTables.SourceContract.AGGREGATE_CONTRIBUTIONS
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.SourceContract.WEB_DESTINATION
                    + " TEXT, "
                    + MeasurementTables.SourceContract.DEBUG_KEY
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_TRIGGER_V1 =
            "CREATE TABLE "
                    + MeasurementTables.TriggerContract.TABLE
                    + " ("
                    + MeasurementTables.TriggerContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.DESTINATION_TYPE
                    + " INTEGER, "
                    + MeasurementTables.TriggerContract.ENROLLMENT_ID
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.TRIGGER_TIME
                    + " INTEGER, "
                    + MeasurementTables.TriggerContract.EVENT_TRIGGERS
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.STATUS
                    + " INTEGER, "
                    + MeasurementTables.TriggerContract.REGISTRANT
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.AGGREGATE_VALUES
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.FILTERS
                    + " TEXT, "
                    + MeasurementTables.TriggerContract.DEBUG_KEY
                    + " INTEGER "
                    + ")";

    public static final String CREATE_TABLE_EVENT_REPORT_V1 =
            "CREATE TABLE "
                    + MeasurementTables.EventReportContract.TABLE
                    + " ("
                    + MeasurementTables.EventReportContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTables.EventReportContract.SOURCE_ID
                    + " INTEGER, "
                    + MeasurementTables.EventReportContract.ENROLLMENT_ID
                    + " TEXT, "
                    + MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + MeasurementTables.EventReportContract.REPORT_TIME
                    + " INTEGER, "
                    + MeasurementTables.EventReportContract.TRIGGER_DATA
                    + " INTEGER, "
                    + MeasurementTables.EventReportContract.TRIGGER_PRIORITY
                    + " INTEGER, "
                    + MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY
                    + " INTEGER, "
                    + MeasurementTables.EventReportContract.TRIGGER_TIME
                    + " INTEGER, "
                    + MeasurementTables.EventReportContract.STATUS
                    + " INTEGER, "
                    + MeasurementTables.EventReportContract.SOURCE_TYPE
                    + " TEXT, "
                    + MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE
                    + " DOUBLE "
                    + ")";

    public static final String CREATE_TABLE_ATTRIBUTION_V1 =
            "CREATE TABLE "
                    + MeasurementTables.AttributionContract.TABLE
                    + " ("
                    + MeasurementTables.AttributionContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTables.AttributionContract.SOURCE_SITE
                    + " TEXT, "
                    + MeasurementTables.AttributionContract.SOURCE_ORIGIN
                    + " TEXT, "
                    + MeasurementTables.AttributionContract.DESTINATION_SITE
                    + " TEXT, "
                    + MeasurementTables.AttributionContract.DESTINATION_ORIGIN
                    + " TEXT, "
                    + MeasurementTables.AttributionContract.ENROLLMENT_ID
                    + " TEXT, "
                    + MeasurementTables.AttributionContract.TRIGGER_TIME
                    + " INTEGER, "
                    + MeasurementTables.AttributionContract.REGISTRANT
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_AGGREGATE_REPORT_V1 =
            "CREATE TABLE "
                    + MeasurementTables.AggregateReport.TABLE
                    + " ("
                    + MeasurementTables.AggregateReport.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTables.AggregateReport.PUBLISHER
                    + " TEXT, "
                    + MeasurementTables.AggregateReport.ATTRIBUTION_DESTINATION
                    + " TEXT, "
                    + MeasurementTables.AggregateReport.SOURCE_REGISTRATION_TIME
                    + " INTEGER, "
                    + MeasurementTables.AggregateReport.SCHEDULED_REPORT_TIME
                    + " INTEGER, "
                    + MeasurementTables.AggregateReport.ENROLLMENT_ID
                    + " TEXT, "
                    + MeasurementTables.AggregateReport.DEBUG_CLEARTEXT_PAYLOAD
                    + " TEXT, "
                    + MeasurementTables.AggregateReport.STATUS
                    + " INTEGER, "
                    + MeasurementTables.AggregateReport.API_VERSION
                    + " TEXT "
                    + ")";

    public static final String CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V1 =
            "CREATE TABLE "
                    + MeasurementTables.AggregateEncryptionKey.TABLE
                    + " ("
                    + MeasurementTables.AggregateEncryptionKey.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTables.AggregateEncryptionKey.KEY_ID
                    + " TEXT, "
                    + MeasurementTables.AggregateEncryptionKey.PUBLIC_KEY
                    + " TEXT, "
                    + MeasurementTables.AggregateEncryptionKey.EXPIRY
                    + " INTEGER "
                    + ")";

    /**
     * @param context the context
     * @param dbName Name of database to query
     * @param dbVersion db version
     */
    public DbHelperV1(Context context, String dbName, int dbVersion) {
        super(context, dbName, dbVersion);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String sql : TopicsTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
        for (String sql : MEASUREMENT_CREATE_STATEMENTS_V1) {
            db.execSQL(sql);
        }
        for (String sql : CREATE_INDEXES_V1) {
            db.execSQL(sql);
        }
        for (String sql : EnrollmentTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
    }

    // Snapshot of Measurement Table Create Statements at Version 1
    private static final List<String> MEASUREMENT_CREATE_STATEMENTS_V1 =
            Collections.unmodifiableList(
                    Arrays.asList(
                            CREATE_TABLE_SOURCE_V1,
                            CREATE_TABLE_TRIGGER_V1,
                            CREATE_TABLE_EVENT_REPORT_V1,
                            CREATE_TABLE_ATTRIBUTION_V1,
                            CREATE_TABLE_AGGREGATE_REPORT_V1,
                            CREATE_TABLE_AGGREGATE_ENCRYPTION_KEY_V1,
                            CREATE_TABLE_ASYNC_REGISTRATION_V1));

    public static final String[] CREATE_INDEXES_V1 = {
        "CREATE INDEX "
                + MeasurementTables.INDEX_PREFIX
                + MeasurementTables.SourceContract.TABLE
                + "_ad_ei_et "
                + "ON "
                + MeasurementTables.SourceContract.TABLE
                + "( "
                + MeasurementTablesDeprecated.SourceContract.APP_DESTINATION
                + ", "
                + MeasurementTables.SourceContract.ENROLLMENT_ID
                + ", "
                + MeasurementTables.SourceContract.EXPIRY_TIME
                + " DESC "
                + ")",
        "CREATE INDEX "
                + MeasurementTables.INDEX_PREFIX
                + MeasurementTables.SourceContract.TABLE
                + "_et "
                + "ON "
                + MeasurementTables.SourceContract.TABLE
                + "("
                + MeasurementTables.SourceContract.EXPIRY_TIME
                + ")",
        "CREATE INDEX "
                + MeasurementTables.INDEX_PREFIX
                + MeasurementTables.SourceContract.TABLE
                + "_p_ad_wd_s_et "
                + "ON "
                + MeasurementTables.SourceContract.TABLE
                + "("
                + MeasurementTables.SourceContract.PUBLISHER
                + ", "
                + MeasurementTablesDeprecated.SourceContract.APP_DESTINATION
                + ", "
                + MeasurementTablesDeprecated.SourceContract.WEB_DESTINATION
                + ", "
                + MeasurementTables.SourceContract.STATUS
                + ", "
                + MeasurementTables.SourceContract.EVENT_TIME
                + ")",
        "CREATE INDEX "
                + MeasurementTables.INDEX_PREFIX
                + MeasurementTables.TriggerContract.TABLE
                + "_ad_ei_tt "
                + "ON "
                + MeasurementTables.TriggerContract.TABLE
                + "( "
                + MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION
                + ", "
                + MeasurementTables.TriggerContract.ENROLLMENT_ID
                + ", "
                + MeasurementTables.TriggerContract.TRIGGER_TIME
                + " ASC)",
        "CREATE INDEX "
                + MeasurementTables.INDEX_PREFIX
                + MeasurementTables.TriggerContract.TABLE
                + "_tt "
                + "ON "
                + MeasurementTables.TriggerContract.TABLE
                + "("
                + MeasurementTables.TriggerContract.TRIGGER_TIME
                + ")",
        "CREATE INDEX "
                + MeasurementTables.INDEX_PREFIX
                + MeasurementTables.AttributionContract.TABLE
                + "_ss_so_ds_do_ei_tt"
                + " ON "
                + MeasurementTables.AttributionContract.TABLE
                + "("
                + MeasurementTables.AttributionContract.SOURCE_SITE
                + ", "
                + MeasurementTables.AttributionContract.SOURCE_ORIGIN
                + ", "
                + MeasurementTables.AttributionContract.DESTINATION_SITE
                + ", "
                + MeasurementTables.AttributionContract.DESTINATION_ORIGIN
                + ", "
                + MeasurementTables.AttributionContract.ENROLLMENT_ID
                + ", "
                + MeasurementTables.AttributionContract.TRIGGER_TIME
                + ")"
    };
}
