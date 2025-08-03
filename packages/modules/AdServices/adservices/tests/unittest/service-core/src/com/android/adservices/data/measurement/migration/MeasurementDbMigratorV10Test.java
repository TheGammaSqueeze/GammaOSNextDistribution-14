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

import static org.junit.Assert.assertNotEquals;
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
public class MeasurementDbMigratorV10Test extends MeasurementDbMigratorTestBase {
    @Test
    public void performMigration_v9ToV10WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext, MEASUREMENT_DATABASE_NAME_FOR_MIGRATION, 9, getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String source1Id = UUID.randomUUID().toString();
        // Source and Trigger objects must be inserted first to satisfy foreign key dependencies
        Map<String, List<ContentValues>> fakeData = createFakeDataSourceAndTriggerV9(source1Id);
        populateDb(db, fakeData);
        fakeData = createFakeDataV9(source1Id);
        populateDb(db, fakeData);

        // Execution
        getTestSubject().performMigration(db, 9, 10);

        // Assertion
        MigrationTestHelper.verifyDataInDb(db, fakeData);
        // Check that new columns are initialized with null value

        List<Pair<String, String>> tableAndNewColumnPairs = new ArrayList<>();
        tableAndNewColumnPairs.add(
                new Pair<>(
                        MeasurementTables.AggregateReport.TABLE,
                        MeasurementTables.AggregateReport.DEDUP_KEY));

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
                        assertNotEquals(0, cursor.getCount());
                        while (cursor.moveToNext()) {
                            assertNull(cursor.getString(cursor.getColumnIndex(pair.second)));
                        }
                    }
                });
    }

    private Map<String, List<ContentValues>> createFakeDataSourceAndTriggerV9(String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV9();
        source1.put(MeasurementTables.SourceContract.ID, source1Id);
        sourceRows.add(source1);
        sourceRows.add(ContentValueFixtures.generateSourceContentValuesV9());
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);

        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger1 = ContentValueFixtures.generateTriggerContentValuesV8();
        trigger1.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        triggerRows.add(trigger1);
        triggerRows.add(ContentValueFixtures.generateTriggerContentValuesV8());
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);

        return tableRowsMap;
    }

    private Map<String, List<ContentValues>> createFakeDataV9(String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        // Source Destination Table
        List<ContentValues> sourceDestinationRows = new ArrayList<>();
        ContentValues sourceDestination1 =
                ContentValueFixtures.generateSourceDestinationContentValuesV9();
        sourceDestination1.put(MeasurementTables.SourceDestination.SOURCE_ID, source1Id);
        sourceDestinationRows.add(sourceDestination1);
        sourceDestinationRows.add(ContentValueFixtures.generateSourceDestinationContentValuesV9());
        tableRowsMap.put(MeasurementTables.SourceDestination.TABLE, sourceDestinationRows);

        // Event Report Table
        List<ContentValues> eventReportRows = new ArrayList<>();
        ContentValues eventReport1 = ContentValueFixtures.generateEventReportContentValuesV8();
        eventReport1.put(MeasurementTables.EventReportContract.ID, UUID.randomUUID().toString());
        eventReportRows.add(eventReport1);
        eventReportRows.add(ContentValueFixtures.generateEventReportContentValuesV8());
        tableRowsMap.put(MeasurementTables.EventReportContract.TABLE, eventReportRows);

        // Aggregate Report Table
        List<ContentValues> aggregateReportRows = new ArrayList<>();
        ContentValues aggregateReport1 =
                ContentValueFixtures.generateAggregateReportContentValuesV8();
        aggregateReport1.put(MeasurementTables.AggregateReport.ID, UUID.randomUUID().toString());
        aggregateReportRows.add(aggregateReport1);
        aggregateReportRows.add(ContentValueFixtures.generateAggregateReportContentValuesV8());
        tableRowsMap.put(MeasurementTables.AggregateReport.TABLE, aggregateReportRows);

        // Attribution table
        List<ContentValues> attributionRows = new ArrayList<>();
        ContentValues attribution1 = ContentValueFixtures.generateAttributionContentValuesV8();
        attribution1.put(MeasurementTables.AttributionContract.ID, UUID.randomUUID().toString());
        attributionRows.add(attribution1);
        attributionRows.add(ContentValueFixtures.generateAttributionContentValuesV8());
        tableRowsMap.put(MeasurementTables.AttributionContract.TABLE, attributionRows);

        // Aggregate Encryption Key table
        List<ContentValues> aggregateEncryptionKeyRows = new ArrayList<>();
        ContentValues aggregateEncryptionKey1 =
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV8();
        aggregateEncryptionKey1.put(
                MeasurementTables.AggregateEncryptionKey.ID, UUID.randomUUID().toString());
        aggregateEncryptionKeyRows.add(aggregateEncryptionKey1);
        aggregateEncryptionKeyRows.add(
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV8());
        tableRowsMap.put(
                MeasurementTables.AggregateEncryptionKey.TABLE, aggregateEncryptionKeyRows);

        // Debug Report Table
        List<ContentValues> debugReportRows = new ArrayList<>();
        ContentValues debugReport1 = ContentValueFixtures.generateDebugReportContentValuesV8();
        debugReport1.put(MeasurementTables.DebugReportContract.ID, UUID.randomUUID().toString());
        debugReportRows.add(debugReport1);
        debugReportRows.add(ContentValueFixtures.generateDebugReportContentValuesV8());
        tableRowsMap.put(MeasurementTables.DebugReportContract.TABLE, debugReportRows);

        // Async Registration Table
        List<ContentValues> asyncRegistrationRows = new ArrayList<>();
        ContentValues asyncRegistration1 =
                ContentValueFixtures.generateAsyncRegistrationContentValuesV8();
        asyncRegistration1.put(
                MeasurementTables.AsyncRegistrationContract.ID, UUID.randomUUID().toString());
        asyncRegistrationRows.add(asyncRegistration1);
        asyncRegistrationRows.add(ContentValueFixtures.generateAsyncRegistrationContentValuesV8());
        tableRowsMap.put(MeasurementTables.AsyncRegistrationContract.TABLE, asyncRegistrationRows);

        // XNA ignored sources Table
        List<ContentValues> xnaIgnoredSources = new ArrayList<>();
        ContentValues xnaIgnoredSources1 =
                ContentValueFixtures.generateXnaIgnoredSourcesContentValuesV8();
        xnaIgnoredSources1.put(
                MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID,
                UUID.randomUUID().toString());
        xnaIgnoredSources.add(xnaIgnoredSources1);
        xnaIgnoredSources.add(ContentValueFixtures.generateXnaIgnoredSourcesContentValuesV8());
        tableRowsMap.put(MeasurementTables.XnaIgnoredSourcesContract.TABLE, xnaIgnoredSources);
        return tableRowsMap;
    }

    @Override
    int getTargetVersion() {
        return 10;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV10();
    }
}
