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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.migration.IMeasurementDbMigrator;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV10;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV11;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV12;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV13;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV14;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV15;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV16;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV17;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV18;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV19;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV20;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV21;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV22;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV23;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV24;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV25;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV26;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV27;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV28;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV29;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV30;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV31;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV32;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV7;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV8;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV9;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Database Helper for Measurement database. */
public class MeasurementDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME =
            FileCompatUtils.getAdservicesFilename("adservices_msmt.db");
    public static final int CURRENT_DATABASE_VERSION = 32;
    public static final int OLD_DATABASE_FINAL_VERSION = 6;

    private static MeasurementDbHelper sSingleton = null;
    private final File mDbFile;
    private final int mDbVersion;
    private final DbHelper mDbHelper;

    @VisibleForTesting
    public MeasurementDbHelper(
            @NonNull Context context, @NonNull String dbName, int dbVersion, DbHelper dbHelper) {
        super(context, dbName, null, dbVersion);
        mDbFile = FileCompatUtils.getDatabasePathHelper(context, dbName);
        this.mDbVersion = dbVersion;
        this.mDbHelper = dbHelper;
    }

    /** Returns an instance of the DbHelper given a context. */
    @NonNull
    public static MeasurementDbHelper getInstance(@NonNull Context ctx) {
        synchronized (MeasurementDbHelper.class) {
            if (sSingleton == null) {
                sSingleton =
                        new MeasurementDbHelper(
                                ctx,
                                DATABASE_NAME,
                                CURRENT_DATABASE_VERSION,
                                DbHelper.getInstance(ctx));
            }
            return sSingleton;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        LoggerFactory.getMeasurementLogger()
                .d(
                        "MeasurementDbHelper.onCreate with version %d. Name: %s",
                        mDbVersion, mDbFile.getName());
        SQLiteDatabase oldDb = mDbHelper.safeGetWritableDatabase();
        // Only migrate data if all V6 tables are present, else skip the migration because
        // database is in an unexpected state.
        if (hasAllV6MeasurementTables(oldDb)) {
            LoggerFactory.getMeasurementLogger()
                    .d("MeasurementDbHelper.onCreate copying data from old db");
            // Migrate Data:
            // 1. Create V6 (old DbHelper's last database version) version of tables
            // 2. Copy data from old database
            // 3. Delete tables from old database
            // 4. Upgrade schema to the latest version
            createV6Schema(db);
            migrateOldDataToNewDatabase(oldDb, db);
            deleteV6TablesFromDatabase(oldDb);
            upgradeSchema(db, OLD_DATABASE_FINAL_VERSION, mDbVersion);
        } else {
            LoggerFactory.getMeasurementLogger()
                    .d("MeasurementDbHelper.onCreate creating empty database");
            createSchema(db);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LoggerFactory.getMeasurementLogger()
                .d(
                        "MeasurementDbHelper.onUpgrade. Attempting to upgrade version from %d to"
                                + " %d.",
                        oldVersion, newVersion);
        upgradeSchema(db, oldVersion, newVersion);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    private List<IMeasurementDbMigrator> getOrderedDbMigrators() {
        return ImmutableList.of(
                new MeasurementDbMigratorV7(),
                new MeasurementDbMigratorV8(),
                new MeasurementDbMigratorV9(),
                new MeasurementDbMigratorV10(),
                new MeasurementDbMigratorV11(),
                new MeasurementDbMigratorV12(),
                new MeasurementDbMigratorV13(),
                new MeasurementDbMigratorV14(mDbHelper),
                new MeasurementDbMigratorV15(mDbHelper),
                new MeasurementDbMigratorV16(),
                new MeasurementDbMigratorV17(),
                new MeasurementDbMigratorV18(),
                new MeasurementDbMigratorV19(),
                new MeasurementDbMigratorV20(),
                new MeasurementDbMigratorV21(),
                new MeasurementDbMigratorV22(),
                new MeasurementDbMigratorV23(),
                new MeasurementDbMigratorV24(),
                new MeasurementDbMigratorV25(),
                new MeasurementDbMigratorV26(),
                new MeasurementDbMigratorV27(),
                new MeasurementDbMigratorV28(),
                new MeasurementDbMigratorV29(),
                new MeasurementDbMigratorV30(),
                new MeasurementDbMigratorV31(),
                new MeasurementDbMigratorV32());
    }

    private boolean hasAllV6MeasurementTables(SQLiteDatabase db) {
        List<String> selectionArgList = new ArrayList<>(Arrays.asList(MeasurementTables.V6_TABLES));
        selectionArgList.add("table"); // Schema type to match
        String[] selectionArgs = new String[selectionArgList.size()];
        selectionArgList.toArray(selectionArgs);
        return DatabaseUtils.queryNumEntries(
                        db,
                        "sqlite_master",
                        "name IN ("
                                + Stream.generate(() -> "?")
                                        .limit(MeasurementTables.V6_TABLES.length)
                                        .collect(Collectors.joining(","))
                                + ")"
                                + " AND type = ?",
                        selectionArgs)
                == MeasurementTables.V6_TABLES.length;
    }

    /** Wraps getWritableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException e) {
            LoggerFactory.getMeasurementLogger().e(e, "Failed to get a writeable database");
            return null;
        }
    }

    public long getDbFileSize() {
        return mDbFile != null && mDbFile.exists() ? mDbFile.length() : -1;
    }

    private void createV6Schema(SQLiteDatabase db) {
        MeasurementTables.CREATE_STATEMENTS_V6.forEach(db::execSQL);
        Arrays.stream(MeasurementTables.CREATE_INDEXES_V6).forEach(db::execSQL);
    }

    private void createSchema(SQLiteDatabase db) {
        if (mDbVersion == CURRENT_DATABASE_VERSION) {
            MeasurementTables.CREATE_STATEMENTS.forEach(db::execSQL);
            Arrays.stream(MeasurementTables.CREATE_INDEXES).forEach(db::execSQL);
        } else {
            // If the provided DB version is not the latest, create the starting schema and upgrade
            // to that. This branch is primarily for testing purpose.
            createV6Schema(db);
            upgradeSchema(db, OLD_DATABASE_FINAL_VERSION, mDbVersion);
        }
    }

    private void migrateOldDataToNewDatabase(SQLiteDatabase oldDb, SQLiteDatabase db) {
        // Ordered iteration to populate Source & Trigger tables before other tables to avoid
        // foreign key constraint failures.
        Arrays.stream(MeasurementTables.V6_TABLES)
                .forEachOrdered((table) -> copyTable(oldDb, db, table));
    }

    private void copyTable(SQLiteDatabase oldDb, SQLiteDatabase newDb, String table) {
        try (Cursor cursor = oldDb.query(table, null, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                ContentValues contentValues = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, contentValues);
                newDb.insert(table, null, contentValues);
            }
        }
    }

    private void deleteV6TablesFromDatabase(SQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys=OFF");
        Arrays.stream(MeasurementTables.V6_TABLES)
                .forEach(
                        (table) ->
                                db.execSQL(
                                        "DROP TABLE IF EXISTS "
                                                + DatabaseUtils.sqlEscapeString(table)));
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    private void upgradeSchema(SQLiteDatabase db, int oldVersion, int newVersion) {
        LoggerFactory.getMeasurementLogger()
                .d(
                        "MeasurementDbHelper.upgradeToLatestSchema. "
                                + "Attempting to upgrade version from %d to %d.",
                        oldVersion, newVersion);
        getOrderedDbMigrators()
                .forEach(dbMigrator -> dbMigrator.performMigration(db, oldVersion, newVersion));
    }
}
