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
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.Attribution;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV29Test extends MeasurementDbMigratorTestBase {
    private static final String ID_ONE = "0001";
    private static final String ID_TWO = "0002";

    @Test
    public void performMigration_v28ToV29WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        28,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String source1Id = UUID.randomUUID().toString();
        // Source and Trigger objects must be inserted first to satisfy foreign key dependencies
        populateDb(db, createFakeDataSourceAndTriggerV28(source1Id));
        Pair<Map<String, List<ContentValues>>, List<ContentValues>> fakeDataPair =
                createFakeDataV28(source1Id);
        Map<String, List<ContentValues>> fakeData = fakeDataPair.first;
        List<ContentValues> expectedNewRows = fakeDataPair.second;
        populateDb(db, fakeData);
        // Execution
        getTestSubject().performMigration(db, 28, 29);
        // Assertion
        // We expect actual data to include two new rows.
        fakeData.computeIfPresent(MeasurementTables.AttributionContract.TABLE,
                (k, v) -> {
                        v.addAll(expectedNewRows);
                        return v;
                });
        Map<String, Set<String>> columnsToBeSkipped = Map.of(
                MeasurementTables.AttributionContract.TABLE,
                Set.of(MeasurementTables.AttributionContract.ID));
        MigrationTestHelper.verifyDataInDb(db, fakeData, new HashMap<>(), columnsToBeSkipped);
        try (Cursor cursor =
                db.query(
                        MeasurementTables.AttributionContract.TABLE,
                        new String[] {
                                MeasurementTables.AttributionContract.ID,
                                MeasurementTables.AttributionContract.SCOPE
                        },
                        null,
                        null,
                        null,
                        null,
                        /* orderBy */ MeasurementTables.AttributionContract.ID)) {
            assertEquals(4, cursor.getCount());
            // We expect the migration to create two records of scope event and two of scope
            // aggregate. Confirm record IDs for the first two records since they are known.
            assertTrue(cursor.moveToPosition(0));
            String id1 = cursor.getString(
                    cursor.getColumnIndex(MeasurementTables.AttributionContract.ID));
            assertEquals(ID_ONE, id1);
            @Attribution.Scope int scope1 = cursor.getInt(
                    cursor.getColumnIndex(MeasurementTables.AttributionContract.SCOPE));
            assertEquals(Attribution.Scope.EVENT, scope1);

            assertTrue(cursor.moveToPosition(1));
            String id2 = cursor.getString(
                    cursor.getColumnIndex(MeasurementTables.AttributionContract.ID));
            assertEquals(ID_TWO, id2);
            @Attribution.Scope int scope2 = cursor.getInt(
                    cursor.getColumnIndex(MeasurementTables.AttributionContract.SCOPE));
            assertEquals(Attribution.Scope.EVENT, scope2);

            assertTrue(cursor.moveToPosition(2));
            @Attribution.Scope int scope3 = cursor.getInt(
                    cursor.getColumnIndex(MeasurementTables.AttributionContract.SCOPE));
            assertEquals(Attribution.Scope.AGGREGATE, scope3);

            assertTrue(cursor.moveToPosition(3));
            @Attribution.Scope int scope4 = cursor.getInt(
                    cursor.getColumnIndex(MeasurementTables.AttributionContract.SCOPE));
            assertEquals(Attribution.Scope.AGGREGATE, scope4);
        }
    }

    private Map<String, List<ContentValues>> createFakeDataSourceAndTriggerV28(String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV22();
        source1.put(MeasurementTables.SourceContract.ID, source1Id);
        sourceRows.add(source1);
        sourceRows.add(ContentValueFixtures.generateSourceContentValuesV22());
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

    private Pair<Map<String, List<ContentValues>>, List<ContentValues>> createFakeDataV28(
            String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Destination Table
        List<ContentValues> sourceDestinationRows = new ArrayList<>();
        ContentValues sourceDestination1 =
                ContentValueFixtures.generateSourceDestinationContentValuesV22();
        sourceDestination1.put(MeasurementTables.SourceDestination.SOURCE_ID, source1Id);
        sourceDestinationRows.add(sourceDestination1);
        sourceDestinationRows.add(ContentValueFixtures.generateSourceDestinationContentValuesV22());
        tableRowsMap.put(MeasurementTables.SourceDestination.TABLE, sourceDestinationRows);
        // Attribution Table
        List<ContentValues> attributionRows = new ArrayList<>();
        ContentValues attribution1 = ContentValueFixtures.generateAttributionContentValuesV28();
        ContentValues attribution2 = ContentValueFixtures.generateAttributionContentValuesV28();
        attribution1.put(MeasurementTables.AttributionContract.ID, ID_ONE);
        attribution2.put(MeasurementTables.AttributionContract.ID, ID_TWO);
        attributionRows.add(attribution1);
        attributionRows.add(attribution2);
        tableRowsMap.put(MeasurementTables.AttributionContract.TABLE, attributionRows);
        return Pair.create(
                tableRowsMap,
                List.of(
                        attribution1,
                        ContentValueFixtures.generateAttributionContentValuesV28()));
    }

    @Override
    int getTargetVersion() {
        return 29;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV29();
    }
}
