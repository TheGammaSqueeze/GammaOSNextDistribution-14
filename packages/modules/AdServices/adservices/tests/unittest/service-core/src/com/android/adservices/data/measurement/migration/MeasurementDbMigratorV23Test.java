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
import static org.junit.Assert.assertNull;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV23Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v22ToV23WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        22,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String source1Id = UUID.randomUUID().toString();
        // Source and Trigger objects must be inserted first to satisfy foreign key dependencies
        populateDb(db, createFakeDataSourceAndTriggerV22(source1Id));
        Map<String, List<ContentValues>> fakeData = createFakeDataV22(source1Id);
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 22, 23);
        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);
        // Check that new columns are initialized with null value
        List<Pair<String, String>> tableAndNewColumnPairs = new ArrayList<>();
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.EventReportContract.TABLE,
                        MeasurementTables.EventReportContract.TRIGGER_SUMMARY_BUCKET));
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
                            assertNull(cursor.getString(cursor.getColumnIndex(pair.second)));
                        }
                    }
                });
    }

    private Map<String, List<ContentValues>> createFakeDataSourceAndTriggerV22(String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV21();
        source1.put(MeasurementTables.SourceContract.ID, source1Id);
        sourceRows.add(source1);
        sourceRows.add(ContentValueFixtures.generateSourceContentValuesV21());
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);
        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger1 = ContentValueFixtures.generateTriggerContentValuesV21();
        trigger1.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        triggerRows.add(trigger1);
        triggerRows.add(ContentValueFixtures.generateTriggerContentValuesV21());
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);
        return tableRowsMap;
    }

    private Map<String, List<ContentValues>> createFakeDataV22(String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Destination Table
        List<ContentValues> sourceDestinationRows = new ArrayList<>();
        ContentValues sourceDestination1 =
                ContentValueFixtures.generateSourceDestinationContentValuesV21();
        sourceDestination1.put(MeasurementTables.SourceDestination.SOURCE_ID, source1Id);
        sourceDestinationRows.add(sourceDestination1);
        sourceDestinationRows.add(ContentValueFixtures.generateSourceDestinationContentValuesV21());
        tableRowsMap.put(MeasurementTables.SourceDestination.TABLE, sourceDestinationRows);
        // Event Report Table
        List<ContentValues> eventReportRows = new ArrayList<>();
        ContentValues eventReport1 = ContentValueFixtures.generateEventReportContentValuesV21();
        eventReport1.put(MeasurementTables.EventReportContract.ID, UUID.randomUUID().toString());
        eventReportRows.add(eventReport1);
        eventReportRows.add(ContentValueFixtures.generateEventReportContentValuesV21());
        tableRowsMap.put(MeasurementTables.EventReportContract.TABLE, eventReportRows);
        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 23;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV23();
    }
}
