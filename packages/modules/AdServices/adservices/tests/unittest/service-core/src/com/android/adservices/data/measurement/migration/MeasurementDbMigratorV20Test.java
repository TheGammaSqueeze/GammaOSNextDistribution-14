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
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV20Test extends MeasurementDbMigratorTestBase {
    private static String sDefaultCoordinatorOrigin =
            Flags.MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN;

    @Test
    public void performMigration_v19ToV20WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        19,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String source1Id = UUID.randomUUID().toString();
        // Source and Trigger objects must be inserted first to satisfy foreign key dependencies
        Map<String, List<ContentValues>> fakeData = createFakeDataSourceAndTriggerV19(source1Id);
        populateDb(db, fakeData);
        fakeData = createFakeDataV19();
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 19, 20);
        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);
        // Check that new columns are initialized with null value
        List<Pair<String, String>> tableAndNewColumnPairs = new ArrayList<>();
        List<Pair<String, String>> tableAndNewColumnPairs2 = new ArrayList<>();
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.AggregateEncryptionKey.TABLE,
                        MeasurementTables.AggregateEncryptionKey.AGGREGATION_COORDINATOR_ORIGIN));
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.AggregateReport.TABLE,
                        MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN));
        tableAndNewColumnPairs2.add(
                new Pair<>(
                        MeasurementTables.TriggerContract.TABLE,
                        MeasurementTables.TriggerContract.AGGREGATION_COORDINATOR_ORIGIN));
        tableAndNewColumnPairs.forEach(
                pair -> {
                    try (Cursor cursor =
                            db.query(
                                    pair.first,
                                    new String[] {pair.second},
                                    null,
                                    null,
                                    null,
                                    null,
                                    null)) {
                        assertEquals(2, cursor.getCount());
                        while (cursor.moveToNext()) {
                            String cloudProvider =
                                    cursor.getString(cursor.getColumnIndex(pair.second));
                            assertNotNull(cloudProvider);
                            assertTrue(cloudProvider.equals(sDefaultCoordinatorOrigin));
                        }
                    }
                });
        tableAndNewColumnPairs2.forEach(
                pair -> {
                    try (Cursor cursor =
                            db.query(
                                    pair.first,
                                    new String[] {pair.second},
                                    null,
                                    null,
                                    null,
                                    null,
                                    null)) {
                        assertNotEquals(0, cursor.getCount());
                        while (cursor.moveToNext()) {
                            assertNull(cursor.getString(cursor.getColumnIndex(pair.second)));
                        }
                    }
                });
    }

    private Map<String, List<ContentValues>> createFakeDataSourceAndTriggerV19(String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV19();
        source1.put(MeasurementTables.SourceContract.ID, source1Id);
        sourceRows.add(source1);
        sourceRows.add(ContentValueFixtures.generateSourceContentValuesV19());
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);
        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger1 = ContentValueFixtures.generateTriggerContentValuesV19();
        trigger1.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        triggerRows.add(trigger1);
        triggerRows.add(ContentValueFixtures.generateTriggerContentValuesV19());
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);
        return tableRowsMap;
    }

    private Map<String, List<ContentValues>> createFakeDataV19() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Aggregate Report Table
        List<ContentValues> aggregateReportRows = new ArrayList<>();
        ContentValues aggregateReport1 =
                ContentValueFixtures.generateAggregateReportContentValuesV19();
        aggregateReport1.put(MeasurementTables.AggregateReport.ID, UUID.randomUUID().toString());
        aggregateReportRows.add(aggregateReport1);
        aggregateReportRows.add(ContentValueFixtures.generateAggregateReportContentValuesV19());
        tableRowsMap.put(MeasurementTables.AggregateReport.TABLE, aggregateReportRows);
        // Aggregate Encryption Key table
        List<ContentValues> aggregateEncryptionKeyRows = new ArrayList<>();
        ContentValues aggregateEncryptionKey1 =
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV18();
        aggregateEncryptionKey1.put(
                MeasurementTables.AggregateEncryptionKey.ID, UUID.randomUUID().toString());
        aggregateEncryptionKeyRows.add(aggregateEncryptionKey1);
        aggregateEncryptionKeyRows.add(
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV18());
        tableRowsMap.put(
                MeasurementTables.AggregateEncryptionKey.TABLE, aggregateEncryptionKeyRows);
        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 20;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV20();
    }
}
