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

import static com.android.adservices.data.measurement.migration.MigrationTestHelper.createReferenceDbAtVersion;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.verifyDataInDb;

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.measurement.migration.ContentValueFixtures;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class MeasurementDbHelperTest {

    private static final String MIGRATION_DB_REFERENCE_NAME =
            "adservices_msmt_db_migrate_reference.db";
    private static final String OLD_TEST_DB_NAME = "old_test_db.db";
    private static final String MEASUREMENT_DB_NAME = "adservices_msmt_db_test.db";
    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() {
        Stream.of(MIGRATION_DB_REFERENCE_NAME, OLD_TEST_DB_NAME, MEASUREMENT_DB_NAME)
                .map(sContext::getDatabasePath)
                .filter(File::exists)
                .forEach(File::delete);
    }

    @Test
    public void testNewInstall() {
        MeasurementDbHelper measurementDbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DB_NAME,
                        MeasurementDbHelper.CURRENT_DATABASE_VERSION,
                        DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = measurementDbHelper.safeGetWritableDatabase();
        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext,
                        MIGRATION_DB_REFERENCE_NAME,
                        MeasurementDbHelper.CURRENT_DATABASE_VERSION);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, db);
    }

    @Test
    public void testMigrationFromOldDatabase() {
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, OLD_TEST_DB_NAME, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper =
                new DbHelper(
                        sContext, OLD_TEST_DB_NAME, MeasurementDbHelper.OLD_DATABASE_FINAL_VERSION);
        SQLiteDatabase oldDb = dbHelper.safeGetWritableDatabase();

        assertEquals(MeasurementDbHelper.OLD_DATABASE_FINAL_VERSION, oldDb.getVersion());

        MeasurementDbHelper measurementDbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DB_NAME,
                        MeasurementDbHelper.CURRENT_DATABASE_VERSION,
                        dbHelper);
        SQLiteDatabase actualMigratedDb = measurementDbHelper.safeGetWritableDatabase();

        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext,
                        MIGRATION_DB_REFERENCE_NAME,
                        MeasurementDbHelper.CURRENT_DATABASE_VERSION);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, actualMigratedDb);
        DbTestUtil.assertMeasurementTablesDoNotExist(oldDb);
    }

    @Test
    public void testMigrationDataIntegrityToV6FromOldDatabase() {
        DbHelperV1 dbHelperV1 = new DbHelperV1(sContext, OLD_TEST_DB_NAME, 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper =
                new DbHelper(
                        sContext, OLD_TEST_DB_NAME, MeasurementDbHelper.OLD_DATABASE_FINAL_VERSION);
        SQLiteDatabase oldDb = dbHelper.safeGetWritableDatabase();

        assertEquals(MeasurementDbHelper.OLD_DATABASE_FINAL_VERSION, oldDb.getVersion());
        // Sorted map because we want source/trigger to be inserted before other tables to
        // respect foreign key constraints
        Map<String, List<ContentValues>> fakeData = createFakeData();

        populateDb(oldDb, fakeData);
        MeasurementDbHelper measurementDbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DB_NAME,
                        MeasurementDbHelper.OLD_DATABASE_FINAL_VERSION,
                        dbHelper);
        SQLiteDatabase newDb = measurementDbHelper.safeGetWritableDatabase();
        DbTestUtil.assertMeasurementTablesDoNotExist(oldDb);
        SQLiteDatabase referenceLatestDb =
                createReferenceDbAtVersion(
                        sContext,
                        MIGRATION_DB_REFERENCE_NAME,
                        MeasurementDbHelper.OLD_DATABASE_FINAL_VERSION);
        DbTestUtil.assertDatabasesEqual(referenceLatestDb, newDb);
        assertEquals(MeasurementDbHelper.OLD_DATABASE_FINAL_VERSION, newDb.getVersion());
        verifyDataInDb(newDb, fakeData);
        emptyTables(newDb, MeasurementTables.V6_TABLES);
    }

    private Map<String, List<ContentValues>> createFakeData() {

        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV6();
        source1.put(MeasurementTables.SourceContract.ID, UUID.randomUUID().toString());
        sourceRows.add(source1);
        sourceRows.add(ContentValueFixtures.generateSourceContentValuesV6());
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);

        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger1 = ContentValueFixtures.generateTriggerContentValuesV6();
        trigger1.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        triggerRows.add(trigger1);
        triggerRows.add(ContentValueFixtures.generateTriggerContentValuesV6());
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);

        // Event Report Table
        List<ContentValues> eventReportRows = new ArrayList<>();
        ContentValues eventReport1 = ContentValueFixtures.generateEventReportContentValuesV6();
        eventReport1.put(MeasurementTables.EventReportContract.ID, UUID.randomUUID().toString());
        eventReportRows.add(eventReport1);
        eventReportRows.add(ContentValueFixtures.generateEventReportContentValuesV6());
        tableRowsMap.put(MeasurementTables.EventReportContract.TABLE, eventReportRows);

        // Aggregate Report Table
        List<ContentValues> aggregateReportRows = new ArrayList<>();
        ContentValues aggregateReport1 =
                ContentValueFixtures.generateAggregateReportContentValuesV6();
        aggregateReport1.put(MeasurementTables.AggregateReport.ID, UUID.randomUUID().toString());
        aggregateReportRows.add(aggregateReport1);
        aggregateReportRows.add(ContentValueFixtures.generateAggregateReportContentValuesV6());
        tableRowsMap.put(MeasurementTables.AggregateReport.TABLE, aggregateReportRows);

        // Attribution table
        List<ContentValues> attributionRows = new ArrayList<>();
        ContentValues attribution1 = ContentValueFixtures.generateAttributionContentValuesV6();
        attribution1.put(MeasurementTables.AttributionContract.ID, UUID.randomUUID().toString());
        attributionRows.add(attribution1);
        attributionRows.add(ContentValueFixtures.generateAttributionContentValuesV6());
        tableRowsMap.put(MeasurementTables.AttributionContract.TABLE, attributionRows);

        // Aggregate Encryption Key table
        List<ContentValues> aggregateEncryptionKeyRows = new ArrayList<>();
        ContentValues aggregateEncryptionKey1 =
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV6();
        aggregateEncryptionKey1.put(
                MeasurementTables.AggregateEncryptionKey.ID, UUID.randomUUID().toString());
        aggregateEncryptionKeyRows.add(aggregateEncryptionKey1);
        aggregateEncryptionKeyRows.add(
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV6());
        tableRowsMap.put(
                MeasurementTables.AggregateEncryptionKey.TABLE, aggregateEncryptionKeyRows);

        // Debug Report Table
        List<ContentValues> debugReportRows = new ArrayList<>();
        ContentValues debugReport1 = ContentValueFixtures.generateDebugReportContentValuesV6();
        debugReport1.put(MeasurementTables.DebugReportContract.ID, UUID.randomUUID().toString());
        debugReportRows.add(debugReport1);
        debugReportRows.add(ContentValueFixtures.generateDebugReportContentValuesV6());
        tableRowsMap.put(MeasurementTables.DebugReportContract.TABLE, debugReportRows);

        // Async Registration Table
        List<ContentValues> asyncRegistrationRows = new ArrayList<>();
        ContentValues asyncRegistration1 =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV6();
        asyncRegistration1.put(
                MeasurementTables.AsyncRegistrationContract.ID, UUID.randomUUID().toString());
        asyncRegistrationRows.add(asyncRegistration1);
        asyncRegistrationRows.add(ContentValueFixtures.generateAsyncRegistrationContentValuesV6());
        tableRowsMap.put(MeasurementTables.AsyncRegistrationContract.TABLE, asyncRegistrationRows);

        // XNA ignored sources Table
        List<ContentValues> xnaIgnoredSources = new ArrayList<>();
        ContentValues xnaIgnoredSources1 =
                ContentValueFixtures.generateXnaIgnoredSourcesContentValuesV6();
        xnaIgnoredSources1.put(
                MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID,
                UUID.randomUUID().toString());
        xnaIgnoredSources.add(xnaIgnoredSources1);
        xnaIgnoredSources.add(ContentValueFixtures.generateXnaIgnoredSourcesContentValuesV6());
        tableRowsMap.put(MeasurementTables.XnaIgnoredSourcesContract.TABLE, xnaIgnoredSources);

        return tableRowsMap;
    }

    private void emptyTables(SQLiteDatabase db, String[] tables) {
        Arrays.stream(tables)
                .forEach(
                        (table) -> {
                            db.delete(table, null, null);
                        });
    }
}
