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

package com.android.adservices.data.shared;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.encryptionkey.EncryptionKeyTables;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.enrollment.SqliteObjectMapper;
import com.android.adservices.data.shared.migration.ISharedDbMigrator;
import com.android.adservices.data.shared.migration.SharedDbMigratorV2;
import com.android.adservices.data.shared.migration.SharedDbMigratorV3;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Database Helper for Shared AdServices database. */
public class SharedDbHelper extends SQLiteOpenHelper {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private static final String DATABASE_NAME =
            FileCompatUtils.getAdservicesFilename("adservices_shared.db");
    public static final int CURRENT_DATABASE_VERSION = 3;
    private static SharedDbHelper sSingleton = null;
    private final File mDbFile;
    private final int mDbVersion;
    private final DbHelper mDbHelper;

    @VisibleForTesting
    public SharedDbHelper(
            @NonNull Context context, @NonNull String dbName, int dbVersion, DbHelper dbHelper) {
        super(context, dbName, null, dbVersion);
        mDbFile = FileCompatUtils.getDatabasePathHelper(context, dbName);
        this.mDbVersion = dbVersion;
        this.mDbHelper = dbHelper;
    }

    /** Returns an instance of the SharedDbHelper given a context. */
    @NonNull
    public static SharedDbHelper getInstance(@NonNull Context ctx) {
        synchronized (SharedDbHelper.class) {
            if (sSingleton == null) {
                sSingleton =
                        new SharedDbHelper(
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
        sLogger.d(
                "SharedDbHelper.onCreate with version %d. Name: %s", mDbVersion, mDbFile.getName());
        SQLiteDatabase oldDb = mDbHelper.safeGetWritableDatabase();
        // Only need to check enrollment table, as that one was the only table living previously in
        // adservices.db
        if (hasAllTables(oldDb, EnrollmentTables.ENROLLMENT_TABLES)) {
            // Create encryption key table V2 schema so migration works as expected
            createEncryptionKeyV2Schema(db);
            // Copy enrollment data from old db to new db.
            migrateEnrollmentTables(db, oldDb);
            // Update new db from version 1 to latest
            upgradeSchema(db, 1, mDbVersion);
        } else {
            // Create current schema for both enrollment and encryption key tables.
            createSchema(db);
        }
    }

    private void migrateEnrollmentTables(SQLiteDatabase db, SQLiteDatabase oldDb) {
        sLogger.d("SharedDbHelper.migrateEnrollmentTables copying Enrollment data from old db");
        // Migrate Data:
        // 1. Create V1 (old DbHelper's last database version) version of tables
        createEnrollmentV1Schema(db);
        // 2. Copy data from old database
        migrateOldDataToNewDatabase(oldDb, db, EnrollmentTables.ENROLLMENT_TABLES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        sLogger.d(
                "SharedDbHelper.onUpgrade. Attempting to upgrade version from %d to %d.",
                oldVersion, newVersion);
        upgradeSchema(db, oldVersion, newVersion);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
    }

    private List<ISharedDbMigrator> getOrderedDbMigrators() {
        return ImmutableList.of(new SharedDbMigratorV2(), new SharedDbMigratorV3());
    }

    /** Check whether db has all tables. */
    public static boolean hasAllTables(SQLiteDatabase db, String[] tableArray) {
        List<String> selectionArgList = new ArrayList<>(Arrays.asList(tableArray));
        selectionArgList.add("table"); // Schema type to match
        String[] selectionArgs = new String[selectionArgList.size()];
        selectionArgList.toArray(selectionArgs);
        return DatabaseUtils.queryNumEntries(
                        db,
                        "sqlite_master",
                        "name IN ("
                                + Stream.generate(() -> "?")
                                        .limit(tableArray.length)
                                        .collect(Collectors.joining(","))
                                + ")"
                                + " AND type = ?",
                        selectionArgs)
                == tableArray.length;
    }

    /** Wraps getWritableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException e) {
            sLogger.e(e, "Failed to get a writeable database");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            return null;
        }
    }

    /** Wraps getReadableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetReadableDatabase() {
        try {
            return super.getReadableDatabase();
        } catch (SQLiteException e) {
            sLogger.e(e, "Failed to get a readable database");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            return null;
        }
    }

    private void createSchema(SQLiteDatabase db) {
        EnrollmentTables.CREATE_STATEMENTS_V1.forEach(db::execSQL);
        EncryptionKeyTables.CREATE_STATEMENTS_V3.forEach(db::execSQL);
    }

    private void createEnrollmentV1Schema(SQLiteDatabase db) {
        EnrollmentTables.CREATE_STATEMENTS_V1.forEach(db::execSQL);
    }

    private void createEncryptionKeyV2Schema(SQLiteDatabase db) {
        EncryptionKeyTables.CREATE_STATEMENTS_V2.forEach(db::execSQL);
    }

    private void migrateOldDataToNewDatabase(
            SQLiteDatabase oldDb, SQLiteDatabase db, String[] tables) {
        // Ordered iteration to populate tables to avoid
        // foreign key constraint failures.
        Arrays.stream(tables).forEachOrdered((table) -> copyOrMigrateTable(oldDb, db, table));
    }

    private void copyOrMigrateTable(SQLiteDatabase oldDb, SQLiteDatabase newDb, String table) {
        // We are moving from Origin-Based Enrollment to Site-Based Enrollment as part of this
        // migration
        //
        switch (table) {
            case EnrollmentTables.EnrollmentDataContract.TABLE:
                migrateEnrollmentTable(oldDb, newDb, table);
                return;
            default:
                copyTable(oldDb, newDb, table);
        }
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

    private void migrateEnrollmentTable(SQLiteDatabase oldDb, SQLiteDatabase newDb, String table) {
        try (Cursor cursor = oldDb.query(table, null, null, null, null, null, null, null)) {
            // Enrollment table is moving to Site based enrollment.
            // We are filtering out records with duplicated sites.
            Set<Uri> duplicateSites = new HashSet<>();
            Map<Uri, ContentValues> siteToEnrollmentMap = new HashMap<>();
            while (cursor.moveToNext()) {
                Optional<Uri> site = tryGetSiteFromEnrollmentRow(cursor);
                ContentValues contentValues = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, contentValues);
                if (site.isEmpty()) {
                    newDb.insert(table, null, contentValues);
                } else if (siteToEnrollmentMap.containsKey(site.get())) {
                    duplicateSites.add(site.get());
                } else {
                    siteToEnrollmentMap.put(site.get(), contentValues);
                }
            }
            siteToEnrollmentMap.forEach(
                    (site, enrollment) -> {
                        if (duplicateSites.contains(site)) {
                            return;
                        }
                        newDb.insert(table, null, enrollment);
                    });
        }
    }

    private Optional<Uri> tryGetSiteFromEnrollmentRow(Cursor cursor) {
        try {
            EnrollmentData enrollmentData =
                    SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
            Uri uri = Uri.parse(enrollmentData.getAttributionReportingUrl().get(0));
            return WebAddresses.topPrivateDomainAndScheme(uri);
        } catch (IndexOutOfBoundsException ex) {
            return Optional.empty();
        }
    }

    private void upgradeSchema(SQLiteDatabase db, int oldVersion, int newVersion) {
        sLogger.d(
                "SharedDbHelper.upgradeToLatestSchema. "
                        + "Attempting to upgrade version from %d to %d.",
                oldVersion, newVersion);
        getOrderedDbMigrators()
                .forEach(dbMigrator -> dbMigrator.performMigration(db, oldVersion, newVersion));
    }
}
