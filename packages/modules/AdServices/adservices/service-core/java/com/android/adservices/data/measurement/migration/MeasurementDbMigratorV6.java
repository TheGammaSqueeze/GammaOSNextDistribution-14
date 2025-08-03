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

package com.android.adservices.data.measurement.migration;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.MeasurementTables;

import org.json.JSONArray;

import java.util.UUID;

/** Migrates Measurement DB from user version 3 to 6. */
public class MeasurementDbMigratorV6 extends AbstractMeasurementDbMigrator {
    public static final String CREATE_TABLE_XNA_IGNORED_SOURCES_V6 =
            "CREATE TABLE "
                    + MeasurementTables.XnaIgnoredSourcesContract.TABLE
                    + " ("
                    + MeasurementTables.XnaIgnoredSourcesContract.SOURCE_ID
                    + " TEXT NOT NULL, "
                    + MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID
                    + " TEXT NOT NULL, "
                    + "FOREIGN KEY ("
                    + MeasurementTables.XnaIgnoredSourcesContract.SOURCE_ID
                    + ") REFERENCES "
                    + MeasurementTables.SourceContract.TABLE
                    + "("
                    + MeasurementTables.SourceContract.ID
                    + ") ON DELETE CASCADE"
                    + ")";

    private static final String[] ALTER_STATEMENTS_SOURCE_REPORT = {
        String.format(
                "ALTER TABLE %1$s RENAME COLUMN %2$s TO %3$s",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTablesDeprecated.SourceContract.DEDUP_KEYS,
                MeasurementTables.SourceContract.EVENT_REPORT_DEDUP_KEYS),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.AGGREGATE_REPORT_DEDUP_KEYS),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.EVENT_REPORT_WINDOW),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW)
    };

    private static final String[] ALTER_STATEMENTS_XNA_ASYNC_REGISTRATION = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID),
    };

    private static final String[] ALTER_STATEMENTS_XNA_SOURCE = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTRATION_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.INSTALL_TIME)
    };

    private static final String[] ALTER_STATEMENTS_XNA_TRIGGER = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING)
    };

    private static final String[] UPDATE_XNA_IGNORED_SOURCES_TABLE_STATEMENT = {
        String.format(
                "DROP TABLE IF EXISTS %1$s", MeasurementTables.XnaIgnoredSourcesContract.TABLE),
        CREATE_TABLE_XNA_IGNORED_SOURCES_V6,
    };

    public static final String UPDATE_SOURCE_REPORT_WINDOWS_STATEMENT =
            String.format(
                    "UPDATE %1$s SET %2$s = %3$s, %4$s = %3$s",
                    MeasurementTables.SourceContract.TABLE,
                    MeasurementTables.SourceContract.EVENT_REPORT_WINDOW,
                    MeasurementTables.SourceContract.EXPIRY_TIME,
                    MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW);

    public static final String UPDATE_TRIGGER_STATEMENT = String.format(
            "UPDATE %1$s SET %2$s = '%3$s' WHERE %2$s IS NULL",
            MeasurementTables.TriggerContract.TABLE,
            MeasurementTables.TriggerContract.EVENT_TRIGGERS,
            new JSONArray().toString());

    private static final String[] ALTER_TRIGGER_STATEMENTS_V6 = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS)
    };

    public MeasurementDbMigratorV6() {
        super(6);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        // See if registration_id column is present in the msmt_async_registration table.
        // We use this as a proxy to determine if the db is already at v6.
        if (MigrationHelpers.isColumnPresent(
                db,
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID)) {
            LoggerFactory.getMeasurementLogger().d("Registration id exists. Skipping Migration");
            return;
        }

        // Drop and create a new XnaIgnoredSourcesTable if it exists.
        for (String query : UPDATE_XNA_IGNORED_SOURCES_TABLE_STATEMENT) {
            db.execSQL(query);
        }

        alterAsyncRegistrationTable(db);
        alterSourceTable(db);
        alterTriggerTable(db);

        migrateSourceReportWindows(db);
        migrateAsyncRegistrationRegistrationId(db);
        migrateSourceRegistrationId(db);
        migrateTriggerData(db);
    }

    private static void alterAsyncRegistrationTable(SQLiteDatabase db) {
        if (!MigrationHelpers.isColumnPresent(
                db,
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID)) {
            for (String sql : ALTER_STATEMENTS_XNA_ASYNC_REGISTRATION) {
                db.execSQL(sql);
            }
        }
    }

    private static void alterSourceTable(SQLiteDatabase db) {
        if (!MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.EVENT_REPORT_WINDOW)
                && !MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW)) {
            for (String sql : ALTER_STATEMENTS_SOURCE_REPORT) {
                db.execSQL(sql);
            }
        }
        if (!MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.REGISTRATION_ID)
                && !MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS)
                && !MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.SourceContract.TABLE,
                        MeasurementTables.SourceContract.INSTALL_TIME)) {
            for (String sql : ALTER_STATEMENTS_XNA_SOURCE) {
                db.execSQL(sql);
            }
        }
    }

    private static void alterTriggerTable(SQLiteDatabase db) {
        if (!MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.TriggerContract.TABLE,
                        MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG)
                && !MigrationHelpers.isColumnPresent(
                        db,
                        MeasurementTables.TriggerContract.TABLE,
                        MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING)) {
            for (String sql : ALTER_STATEMENTS_XNA_TRIGGER) {
                db.execSQL(sql);
            }
            for (String sql : ALTER_TRIGGER_STATEMENTS_V6) {
                db.execSQL(sql);
            }
        }
    }

    private static void migrateSourceReportWindows(SQLiteDatabase db) {
        db.execSQL(UPDATE_SOURCE_REPORT_WINDOWS_STATEMENT);
    }

    private static void migrateAsyncRegistrationRegistrationId(SQLiteDatabase db) {
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        new String[] {
                            MeasurementTables.AsyncRegistrationContract.ID,
                            MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID
                        },
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                updateAsyncRegistrationId(db, cursor);
            }
        }
    }

    private static void migrateSourceRegistrationId(SQLiteDatabase db) {
        try (Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {
                            MeasurementTables.SourceContract.ID,
                            MeasurementTables.SourceContract.REGISTRATION_ID
                        },
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                updateSourceRegistrationId(db, cursor);
            }
        }
    }

    private static void updateAsyncRegistrationId(SQLiteDatabase db, Cursor cursor) {
        String id =
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.AsyncRegistrationContract.ID));
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID,
                UUID.randomUUID().toString());
        long rowCount =
                db.update(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        values,
                        MeasurementTables.AsyncRegistrationContract.ID + " = ?",
                        new String[] {id});
        if (rowCount != 1) {
            LoggerFactory.getMeasurementLogger()
                    .d("MeasurementDbMigratorV6: failed to update source record.");
        }
    }

    private static void updateSourceRegistrationId(SQLiteDatabase db, Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndex(MeasurementTables.SourceContract.ID));
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.REGISTRATION_ID, UUID.randomUUID().toString());
        long rowCount =
                db.update(
                        MeasurementTables.SourceContract.TABLE,
                        values,
                        MeasurementTables.SourceContract.ID + " = ?",
                        new String[] {id});
        if (rowCount != 1) {
            LoggerFactory.getMeasurementLogger()
                    .d("MeasurementDbMigratorV6: failed to update source record.");
        }
    }

    private static void migrateTriggerData(SQLiteDatabase db) {
        db.execSQL(UPDATE_TRIGGER_STATEMENT);
    }
}
