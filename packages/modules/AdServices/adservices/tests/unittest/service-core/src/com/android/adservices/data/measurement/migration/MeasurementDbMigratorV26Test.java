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

import static com.android.adservices.data.DbTestUtil.getDbHelperForTest;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.KeyValueData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV26Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v25ToV26WithData_ClearsReportRetries() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        25,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> testData = createFakeDataV25();
        MigrationTestHelper.populateDb(db, testData);

        // Execution
        getTestSubject().performMigration(db, 25, 26);
        Map<String, List<ContentValues>> expectedData = createExpectedDataV26();

        // Assertion
        MigrationTestHelper.verifyDataInDb(db, expectedData);
    }

    @Test
    public void performMigration_v25ToV26WithData_AddsAndPopulatesColumns() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        25,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> testData = createFakeDataV25();
        MigrationTestHelper.populateDb(db, testData);

        // Execution
        getTestSubject().performMigration(db, 25, 26);

        // Assertion
        // Check that new columns are initialized.
        try (Cursor cursor =
                db.query(
                        MeasurementTables.DebugReportContract.TABLE,
                        new String[] {
                            MeasurementTables.DebugReportContract.REGISTRANT
                        }, /* selection */
                        null, /* selectionArgs */
                        null, /* groupBy */
                        null, /* having */
                        null, /* orderBy */
                        null)) {
            assertNotEquals(0, cursor.getCount());
            while (cursor.moveToNext()) {
                assertNull(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.DebugReportContract.REGISTRANT)));
            }
        }

        try (Cursor cursor =
                db.query(
                        MeasurementTables.DebugReportContract.TABLE,
                        new String[] {
                            MeasurementTables.DebugReportContract.INSERTION_TIME
                        }, /* selection */
                        null, /* selectionArgs */
                        null, /* groupBy */
                        null, /* having */
                        null, /* orderBy */
                        null)) {
            assertNotEquals(0, cursor.getCount());
            // Insertion time within last 10 seconds.
            while (cursor.moveToNext()) {
                assertNotEquals(
                        0,
                        cursor.getLong(
                                cursor.getColumnIndex(
                                        MeasurementTables.DebugReportContract.INSERTION_TIME)));
                assertTrue(
                        cursor.getLong(
                                        cursor.getColumnIndex(
                                                MeasurementTables.DebugReportContract
                                                        .INSERTION_TIME))
                                > System.currentTimeMillis() - 10000L);
                assertTrue(
                        cursor.getInt(
                                        cursor.getColumnIndex(
                                                MeasurementTables.DebugReportContract
                                                        .INSERTION_TIME))
                                < System.currentTimeMillis());
            }
        }
    }

    private Map<String, List<ContentValues>> createFakeDataV25() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        // KeyValueData table
        List<ContentValues> keyValueTableRows =
                ContentValueFixtures.generateKeyValueDataContentValuesV25();

        // DebugReportTable table
        List<ContentValues> debugReportTableRows = new ArrayList<>();
        debugReportTableRows.add(ContentValueFixtures.generateDebugReportContentValuesV17());

        tableRowsMap.put(MeasurementTables.KeyValueDataContract.TABLE, keyValueTableRows);
        tableRowsMap.put(MeasurementTables.DebugReportContract.TABLE, debugReportTableRows);

        return tableRowsMap;
    }

    private Map<String, List<ContentValues>> createExpectedDataV26() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        // KeyValueData table
        List<ContentValues> keyValueTableRows =
                ContentValueFixtures.generateKeyValueDataContentValuesV25();
        keyValueTableRows.removeIf(
                row ->
                        row.getAsString(MeasurementTables.KeyValueDataContract.DATA_TYPE)
                                .equals(
                                        KeyValueData.DataType.AGGREGATE_REPORT_RETRY_COUNT
                                                .toString()));
        keyValueTableRows.removeIf(
                row ->
                        row.getAsString(MeasurementTables.KeyValueDataContract.DATA_TYPE)
                                .equals(KeyValueData.DataType.EVENT_REPORT_RETRY_COUNT.toString()));

        tableRowsMap.put(MeasurementTables.KeyValueDataContract.TABLE, keyValueTableRows);

        // DebugReportTable table
        List<ContentValues> debugReportTableRows = new ArrayList<>();
        debugReportTableRows.add(ContentValueFixtures.generateDebugReportContentValuesV25());

        tableRowsMap.put(MeasurementTables.KeyValueDataContract.TABLE, keyValueTableRows);
        tableRowsMap.put(MeasurementTables.DebugReportContract.TABLE, debugReportTableRows);

        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 26;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV26();
    }
}
