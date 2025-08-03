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

package com.android.adservices.data.measurement.migration;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.util.BaseUriExtractor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Upgrades Measurement DB from user version 2 to 3. */
public class MeasurementDbMigratorV3 extends AbstractMeasurementDbMigrator {
    private static final String ANDROID_APP_SCHEME = "android-app";
    private static final String FILTERS = "filters";
    private static final String NOT_FILTERS = "not_filters";
    private static final String EVENT_REPORT_CONTRACT_BACKUP =
            MeasurementTables.EventReportContract.TABLE + "_backup";
    private static final String AGGREGATE_REPORT_CONTRACT_BACKUP =
            MeasurementTables.AggregateReport.TABLE + "_backup";
    private static final String ATTRIBUTION_CONTRACT_BACKUP =
            MeasurementTables.AttributionContract.TABLE + "_backup";
    private static final String ATTRIBUTION_CREATE_INDEX_SS_SO_DS_DO_EI_TT =
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
                    + ")";

    public static final String CREATE_TABLE_ASYNC_REGISTRATION_V3 =
            "CREATE TABLE "
                    + MeasurementTables.AsyncRegistrationContract.TABLE
                    + " ("
                    + MeasurementTables.AsyncRegistrationContract.ID
                    + " TEXT PRIMARY KEY NOT NULL, "
                    + MeasurementTablesDeprecated.AsyncRegistration.ENROLLMENT_ID
                    + " TEXT, "
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
                    + MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_TYPE
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_COUNT
                    + " INTEGER, "
                    + MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE
                    + " INTEGER, "
                    + MeasurementTables.AsyncRegistrationContract.REGISTRANT
                    + " TEXT, "
                    + MeasurementTables.AsyncRegistrationContract.REQUEST_TIME
                    + " INTEGER, "
                    + MeasurementTables.AsyncRegistrationContract.RETRY_COUNT
                    + " INTEGER, "
                    + MeasurementTablesDeprecated.AsyncRegistration.LAST_PROCESSING_TIME
                    + " INTEGER, "
                    + MeasurementTables.AsyncRegistrationContract.TYPE
                    + " INTEGER, "
                    + MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED
                    + " INTEGER, "
                    + MeasurementTables.AsyncRegistrationContract.AD_ID_PERMISSION
                    + " INTEGER "
                    + ")";

    private static final String[] UPDATE_ASYNC_REGISTRATION_TABLE_QUERIES = {
        String.format(
                "DROP TABLE IF EXISTS %1$s", MeasurementTables.AsyncRegistrationContract.TABLE),
        CREATE_TABLE_ASYNC_REGISTRATION_V3,
    };
    private static final String[] ADD_EVENT_REPORT_COLUMNS_VER_3 = {
        MeasurementTables.EventReportContract.SOURCE_ID,
        MeasurementTables.EventReportContract.TRIGGER_ID,
        MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS
    };
    private static final String[] ADD_AGGREGATE_REPORT_COLUMNS_VER_3 = {
        MeasurementTables.AggregateReport.SOURCE_ID,
        MeasurementTables.AggregateReport.TRIGGER_ID,
        MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS
    };
    private static final String[] ADD_ATTRIBUTION_COLUMNS_VER_3 = {
        MeasurementTables.AttributionContract.SOURCE_ID,
        MeasurementTables.AttributionContract.TRIGGER_ID
    };

    private static final String TRIGGER_DATA_KEY = "trigger_data";
    private static final String TRIGGER_DATA_DEFAULT_VALUE = "0";

    private static final String[] ALTER_STATEMENTS = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.DEBUG_REPORTING),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.DEBUG_REPORTING),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.AD_ID_PERMISSION),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.AR_DEBUG_PERMISSION),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.AD_ID_PERMISSION),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.AR_DEBUG_PERMISSION),
    };

    public MeasurementDbMigratorV3() {
        super(3);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        // Add a check to see if source_event_id column is present in the msmt_event_report table.
        // We use this as a proxy to determine if the db is already at v3.
        if (MigrationHelpers.isColumnPresent(
                db,
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.SOURCE_EVENT_ID)) {
            LoggerFactory.getMeasurementLogger().d("Source event id exists. Skipping Migration");
            return;
        }
        // Drop and create a new AsyncRegistrationTable if it exists.
        for (String query : UPDATE_ASYNC_REGISTRATION_TABLE_QUERIES) {
            db.execSQL(query);
        }
        db.execSQL(MeasurementTables.CREATE_TABLE_DEBUG_REPORT_V3);

        alterEventReportTable(db);
        alterTriggerTable(db);
        alterAggregateReportTable(db);
        alterAttributionTable(db);

        // Create index.
        db.execSQL(ATTRIBUTION_CREATE_INDEX_SS_SO_DS_DO_EI_TT);

        migrateSourceData(db);
        migrateEventReportData(db);
        migrateTriggers(db);

        for (String sql : ALTER_STATEMENTS) {
            db.execSQL(sql);
        }
    }

    private static void alterEventReportTable(SQLiteDatabase db) {
        // If source_id column is present and source_event_id column is absent, convert source_id to
        // source_event_id.
        if (MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.EventReportContract.SOURCE_ID)
                && !MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.EventReportContract.SOURCE_EVENT_ID)) {
            db.execSQL(
                    String.format(
                            "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                            MeasurementTables.EventReportContract.TABLE,
                            MeasurementTables.EventReportContract.SOURCE_ID,
                            MeasurementTables.EventReportContract.SOURCE_EVENT_ID));
        }

        MigrationHelpers.addIntColumnsIfAbsent(
                db, MeasurementTables.EventReportContract.TABLE, ADD_EVENT_REPORT_COLUMNS_VER_3);
        MigrationHelpers.copyAndUpdateTable(
                db,
                MeasurementTables.EventReportContract.TABLE,
                EVENT_REPORT_CONTRACT_BACKUP,
                MeasurementTables.CREATE_TABLE_EVENT_REPORT_V3);
    }

    private static void alterTriggerTable(SQLiteDatabase db) {
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.NOT_FILTERS);
    }

    private static void alterAggregateReportTable(SQLiteDatabase db) {
        MigrationHelpers.addIntColumnsIfAbsent(
                db, MeasurementTables.AggregateReport.TABLE, ADD_AGGREGATE_REPORT_COLUMNS_VER_3);
        MigrationHelpers.copyAndUpdateTable(
                db,
                MeasurementTables.AggregateReport.TABLE,
                AGGREGATE_REPORT_CONTRACT_BACKUP,
                MeasurementTables.CREATE_TABLE_AGGREGATE_REPORT_V6);
    }

    private static void alterAttributionTable(SQLiteDatabase db) {
        MigrationHelpers.addIntColumnsIfAbsent(
                db, MeasurementTables.AttributionContract.TABLE, ADD_ATTRIBUTION_COLUMNS_VER_3);
        MigrationHelpers.copyAndUpdateTable(
                db,
                MeasurementTables.AttributionContract.TABLE,
                ATTRIBUTION_CONTRACT_BACKUP,
                MeasurementTables.CREATE_TABLE_ATTRIBUTION_V6);
    }

    private static void migrateEventReportData(SQLiteDatabase db) {
        try (Cursor cursor = db.query(
                    MeasurementTables.EventReportContract.TABLE,
                    new String[] {
                        MeasurementTables.EventReportContract.ID,
                        MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION
                    },
                    null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                updateEventReport(db, cursor);
            }
        }
    }

    private static void migrateSourceData(SQLiteDatabase db) {
        try (Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {
                            MeasurementTables.SourceContract.ID,
                            MeasurementTables.SourceContract.AGGREGATE_SOURCE
                        },
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                String id =
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.SourceContract.ID));
                String aggregateSourceV2 =
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.SourceContract.AGGREGATE_SOURCE));
                String aggregateSourceV3 = convertAggregateSource(aggregateSourceV2);
                updateAggregateSource(db, id, aggregateSourceV3);
            }
        }
    }

    private static void migrateTriggers(SQLiteDatabase db) {
        try (Cursor cursor = db.query(
                    MeasurementTables.TriggerContract.TABLE,
                    new String[] {
                        MeasurementTables.TriggerContract.ID,
                        MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                        MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                        MeasurementTables.TriggerContract.FILTERS
                    },
                    null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                updateTrigger(db, cursor);
            }
        }
    }

    private static void updateAggregateSource(
            SQLiteDatabase db, String id, String aggregateSourceV3) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.AGGREGATE_SOURCE, aggregateSourceV3);
        long rowCount =
                db.update(
                        MeasurementTables.SourceContract.TABLE,
                        values,
                        MeasurementTables.SourceContract.ID + " = ?",
                        new String[] {id});
        if (rowCount != 1) {
            LoggerFactory.getMeasurementLogger()
                    .d("MeasurementDbMigratorV3: failed to update aggregate source record.");
        }
    }

    private static void updateEventTrigger(SQLiteDatabase db, String id, String eventTriggers) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS, eventTriggers);
        long rowCount =
                db.update(
                        MeasurementTables.TriggerContract.TABLE,
                        values,
                        MeasurementTables.TriggerContract.ID + " = ?",
                        new String[] {id});
        if (rowCount != 1) {
            LoggerFactory.getMeasurementLogger()
                    .d("MeasurementDbMigratorV3: failed to update event trigger record.");
        }
    }

    private static void updateEventReport(SQLiteDatabase db, Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndex(
                MeasurementTables.EventReportContract.ID));
        String destination = cursor.getString(cursor.getColumnIndex(
                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION));
        Optional<String> baseUri = extractBaseUri(destination);
        if (baseUri.isPresent()) {
            ContentValues values = new ContentValues();
            values.put(MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                    baseUri.get());
            long rowCount = db.update(
                    MeasurementTables.EventReportContract.TABLE,
                    values,
                    MeasurementTables.EventReportContract.ID + " = ?",
                    new String[]{id});
            if (rowCount != 1) {
                LoggerFactory.getMeasurementLogger()
                        .d("MeasurementDbMigratorV3: failed to update event report record.");
            }
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("MeasurementDbMigratorV3: baseUri not present. %s", destination);
        }
    }

    private static void updateTrigger(SQLiteDatabase db, Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndex(MeasurementTables.TriggerContract.ID));
        String eventTriggers = cursor.getString(cursor.getColumnIndex(
                MeasurementTables.TriggerContract.EVENT_TRIGGERS));
        String aggregateTriggerData = cursor.getString(cursor.getColumnIndex(
                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA));
        String filters = cursor.getString(cursor.getColumnIndex(
                MeasurementTables.TriggerContract.FILTERS));
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                updateEventTriggers(eventTriggers));
        values.put(MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                convertAggregateTriggerData(aggregateTriggerData));
        if (filters != null) {
            String convertedFilters = Optional.ofNullable(convertFilters(filters))
                    .map(JSONArray::toString).orElse(null);
            values.put(MeasurementTables.TriggerContract.FILTERS, convertedFilters);
        }
        long rowCount = db.update(
                MeasurementTables.TriggerContract.TABLE,
                values,
                MeasurementTables.TriggerContract.ID + " = ?",
                new String[]{id});
        if (rowCount != 1) {
            LoggerFactory.getMeasurementLogger()
                    .d("MeasurementDbMigratorV3: failed to update trigger record.");
        }
    }

    private static Optional<String> extractBaseUri(String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Uri uri = Uri.parse(destination);
        if (uri.getScheme() == null || !uri.isHierarchical() || !uri.isAbsolute()) {
            return Optional.empty();
        }
        if (uri.getScheme().equals(ANDROID_APP_SCHEME)) {
            return Optional.of(BaseUriExtractor.getBaseUri(uri).toString());
        }
        Optional<Uri> topPrivateDomainAndScheme = WebAddresses.topPrivateDomainAndScheme(uri);
        if (topPrivateDomainAndScheme.isPresent()) {
            return Optional.of(topPrivateDomainAndScheme.get().toString());
        }
        return Optional.empty();
    }

    private static String convertAggregateSource(String aggregateSourceStringV2) {
        if (aggregateSourceStringV2 == null) {
            return null;
        }
        try {
            JSONArray jsonArray = new JSONArray(aggregateSourceStringV2);
            Map<String, String> aggregateSourceMap = new HashMap<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String key = jsonObject.getString("id");
                String value = jsonObject.getString("key_piece");
                aggregateSourceMap.put(key, value);
            }
            return new JSONObject(aggregateSourceMap).toString();
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Aggregate source parsing failed when migrating from V2 to V3.");
            return null;
        }
    }

    private static String updateEventTriggers(String eventTriggers) {
        if (eventTriggers == null) {
            return new JSONArray().toString();
        }

        try {
            return updateEventTriggers(new JSONArray(eventTriggers));
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "MeasurementDbMigratorV3: failed to parse event triggers.");
            return new JSONArray().toString();
        }
    }

    private static String updateEventTriggers(JSONArray eventTriggers) throws JSONException {
        for (int i = 0; i < eventTriggers.length(); i++) {
            JSONObject eventTrigger = eventTriggers.getJSONObject(i);
            if (eventTrigger.isNull(TRIGGER_DATA_KEY)) {
                eventTrigger.put(TRIGGER_DATA_KEY, TRIGGER_DATA_DEFAULT_VALUE);
            }
        }
        return convertFiltersInObjectArray(eventTriggers);
    }

    private static String convertAggregateTriggerData(String aggregateTriggerData) {
        if (aggregateTriggerData == null) {
            return null;
        }

        try {
            return convertFiltersInObjectArray(new JSONArray(aggregateTriggerData));
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "MeasurementDbMigratorV3: failed to parse aggregate trigger data.");
            return null;
        }
    }

    private static String convertFiltersInObjectArray(JSONArray objectArray) throws JSONException {
        for (int i = 0; i < objectArray.length(); i++) {
            JSONObject obj = objectArray.getJSONObject(i);
            if (!obj.isNull(FILTERS)) {
                JSONArray convertedFilters = convertFilters(
                        obj.getJSONObject(FILTERS).toString());
                if (convertedFilters == null) {
                    return null;
                } else {
                    obj.put(FILTERS, convertedFilters);
                }
            }
            if (!obj.isNull(NOT_FILTERS)) {
                JSONArray convertedNotFilters = convertFilters(
                        obj.getJSONObject(NOT_FILTERS).toString());
                if (convertedNotFilters == null) {
                    return null;
                } else {
                    obj.put(NOT_FILTERS, convertedNotFilters);
                }
            }
        }

        return objectArray.toString();
    }

    private static JSONArray convertFilters(@NonNull String filters) {
        try {
            return new JSONArray().put(new JSONObject(filters));
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "MeasurementDbMigratorV3: failed to parse filters.");
            return null;
        }
    }
}
