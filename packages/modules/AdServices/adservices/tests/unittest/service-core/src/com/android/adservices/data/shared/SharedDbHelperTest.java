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

import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.verifyDataInDb;
import static com.android.adservices.data.shared.migration.MigrationTestHelper.createReferenceDbAtVersion;

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbHelperTest;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.measurement.DbHelperV1;
import com.android.adservices.data.shared.migration.ContentValueFixtures;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class SharedDbHelperTest {

    private static final String MIGRATION_DB_REFERENCE_NAME =
            "adservices_shared_db_migrate_reference.db";
    private static final String OLD_TEST_DB_NAME = "old_test_db.db";
    private static final String SHARED_DB_NAME = "adservices_shared_db_test.db";
    private static final int ENROLLMENT_OLD_DB_FINAL_VERSION = 7;
    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() {
        Stream.of(MIGRATION_DB_REFERENCE_NAME, OLD_TEST_DB_NAME, SHARED_DB_NAME)
                .map(sContext::getDatabasePath)
                .filter(File::exists)
                .forEach(File::delete);
    }

    @Test
    public void testNewInstall() {
        SharedDbHelper sharedDbHelper =
                new SharedDbHelper(
                        sContext,
                        SHARED_DB_NAME,
                        SharedDbHelper.CURRENT_DATABASE_VERSION,
                        DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = sharedDbHelper.safeGetWritableDatabase();
        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext,
                        MIGRATION_DB_REFERENCE_NAME,
                        SharedDbHelper.CURRENT_DATABASE_VERSION);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, db);
    }

    @Test
    public void testEnrollmentTableMigrationFromOldDatabase() {
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, OLD_TEST_DB_NAME, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper =
                new DbHelper(sContext, OLD_TEST_DB_NAME, ENROLLMENT_OLD_DB_FINAL_VERSION);
        SQLiteDatabase oldDb = dbHelper.safeGetWritableDatabase();

        assertEquals(ENROLLMENT_OLD_DB_FINAL_VERSION, oldDb.getVersion());

        SharedDbHelper sharedDbHelper =
                new SharedDbHelper(
                        sContext,
                        SHARED_DB_NAME,
                        SharedDbHelper.CURRENT_DATABASE_VERSION,
                        dbHelper);
        SQLiteDatabase actualMigratedDb = sharedDbHelper.safeGetWritableDatabase();

        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext,
                        MIGRATION_DB_REFERENCE_NAME,
                        SharedDbHelper.CURRENT_DATABASE_VERSION);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, actualMigratedDb);
        DbHelperTest.assertEnrollmentTableDoesNotExist(oldDb);
    }

    @Test
    public void testMigrationDataIntegrityToV1FromOldDatabase() {
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, OLD_TEST_DB_NAME, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper =
                new DbHelper(sContext, OLD_TEST_DB_NAME, ENROLLMENT_OLD_DB_FINAL_VERSION);
        SQLiteDatabase oldDb = dbHelper.safeGetWritableDatabase();

        assertEquals(ENROLLMENT_OLD_DB_FINAL_VERSION, oldDb.getVersion());
        // Sorted map because in case we need to add in order to avoid FK Constraints
        Map<String, List<ContentValues>> fakeData = createMigrationFakeDataDistinctSites();

        populateDb(oldDb, fakeData);
        SharedDbHelper sharedDbHelper =
                new SharedDbHelper(
                        sContext,
                        SHARED_DB_NAME,
                        SharedDbHelper.CURRENT_DATABASE_VERSION,
                        dbHelper);
        SQLiteDatabase newDb = sharedDbHelper.safeGetWritableDatabase();
        DbHelperTest.assertEnrollmentTableDoesNotExist(oldDb);
        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext,
                        MIGRATION_DB_REFERENCE_NAME,
                        SharedDbHelper.CURRENT_DATABASE_VERSION);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, newDb);
        assertEquals(SharedDbHelper.CURRENT_DATABASE_VERSION, newDb.getVersion());
        verifyDataInDb(newDb, fakeData);
        emptyTables(newDb, EnrollmentTables.ENROLLMENT_TABLES);
        emptyTables(oldDb, EnrollmentTables.ENROLLMENT_TABLES);
    }

    @Test
    public void testMigrationExcludesDuplicatesSites() {
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, OLD_TEST_DB_NAME, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper =
                new DbHelper(sContext, OLD_TEST_DB_NAME, ENROLLMENT_OLD_DB_FINAL_VERSION);
        SQLiteDatabase oldDb = dbHelper.safeGetWritableDatabase();

        assertEquals(ENROLLMENT_OLD_DB_FINAL_VERSION, oldDb.getVersion());
        // Sorted map because in case we need to add in order to avoid FK Constraints
        Map<String, List<ContentValues>> preFakeData = createMigrationFakeDataFull();
        Map<String, List<ContentValues>> postFakeData = createPostMigrationFakeDataFull();
        populateDb(oldDb, preFakeData);
        SharedDbHelper sharedDbHelper =
                new SharedDbHelper(
                        sContext,
                        SHARED_DB_NAME,
                        SharedDbHelper.CURRENT_DATABASE_VERSION,
                        dbHelper);
        SQLiteDatabase newDb = sharedDbHelper.safeGetWritableDatabase();
        DbHelperTest.assertEnrollmentTableDoesNotExist(oldDb);
        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext,
                        MIGRATION_DB_REFERENCE_NAME,
                        SharedDbHelper.CURRENT_DATABASE_VERSION);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, newDb);
        assertEquals(SharedDbHelper.CURRENT_DATABASE_VERSION, newDb.getVersion());
        verifyDataInDb(newDb, postFakeData);
        emptyTables(newDb, EnrollmentTables.ENROLLMENT_TABLES);
        emptyTables(oldDb, EnrollmentTables.ENROLLMENT_TABLES);
    }

    private Map<String, List<ContentValues>> createMigrationFakeDataFull() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        tableRowsMap.put(
                EnrollmentTables.EnrollmentDataContract.TABLE,
                ContentValueFixtures.generateFullSiteEnrollmentListV1());
        return tableRowsMap;
    }

    private Map<String, List<ContentValues>> createMigrationFakeDataDistinctSites() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        tableRowsMap.put(
                EnrollmentTables.EnrollmentDataContract.TABLE,
                ContentValueFixtures.generateDistinctSiteEnrollmentListV1());
        return tableRowsMap;
    }

    private Map<String, List<ContentValues>> createPostMigrationFakeDataFull() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        List<ContentValues> enrollmentRows = new ArrayList<>();
        enrollmentRows.add(ContentValueFixtures.generateEnrollmentUniqueExampleContentValuesV1());
        tableRowsMap.put(EnrollmentTables.EnrollmentDataContract.TABLE, enrollmentRows);
        return tableRowsMap;
    }

    private void emptyTables(SQLiteDatabase db, String[] tables) {
        Arrays.stream(tables).forEach((table) -> db.delete(table, null, null));
    }
}
