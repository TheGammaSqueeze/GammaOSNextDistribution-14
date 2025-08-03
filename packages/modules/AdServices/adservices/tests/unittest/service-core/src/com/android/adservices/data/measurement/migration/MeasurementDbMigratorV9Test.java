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

import static com.android.adservices.data.DbTestUtil.doesIndexExist;
import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.data.DbTestUtil.getDbHelperForTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV9Test extends MeasurementDbMigratorTestBase {
    private static final String[][] INSERTED_SOURCE = {
        // id, app_destination, web_destination
        {"1", "android-app://com.example", null},
        {"2", null, "https://web.example.test"},
        {"3", "android-app://com.example1", "https://web.example1.test"}
    };

    private static final String[][] MIGRATED_SOURCE_DESTINATION = {
        // source_id, destination_type, destination
        {"1", "0", "android-app://com.example"},
        {"2", "1", "https://web.example.test"},
        {"3", "0", "android-app://com.example1"},
        {"3", "1", "https://web.example1.test"}
    };

    @Override
    int getTargetVersion() {
        return 9;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV9();
    }

    @Test
    public void performMigration_v8ToV9WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext, MEASUREMENT_DATABASE_NAME_FOR_MIGRATION, 8, getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Map<String, List<ContentValues>> fakeData = createFakeDataV8();
        MigrationTestHelper.populateDb(db, fakeData);

        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 32));
        assertFalse(
                doesTableExistAndColumnCountMatch(db,
                      MeasurementTables.SourceDestination.TABLE, 3));

        // Execution
        getTestSubject().performMigration(db, 8, 9);

        // Assertion
        MigrationTestHelper.verifyDataInDb(
                db,
                fakeData,
                ImmutableMap.of(
                        MeasurementTables.SourceContract.TABLE,
                        Set.of(
                                MeasurementTablesDeprecated.SourceContract.APP_DESTINATION,
                                MeasurementTablesDeprecated.SourceContract.WEB_DESTINATION)),
                Map.of());

        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 30));
        assertTrue(doesIndexExist(db, "idx_msmt_source_ei_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_p_s_et"));
        assertTrue(
                doesTableExistAndColumnCountMatch(db,
                      MeasurementTables.SourceDestination.TABLE, 3));
        assertSourceDestinationMigration(db);
        // Assert foreign key relation is intact
        db.delete(MeasurementTables.SourceContract.TABLE, null, null);
        assertEmptySourceDestinationTable(db);
    }

    private Map<String, List<ContentValues>> createFakeDataV8() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        ContentValues source1 = ContentValueFixtures.generateSourceContentValuesV8();
        String source1Id = UUID.randomUUID().toString();
        source1.put(MeasurementTables.SourceContract.ID, source1Id);
        sourceRows.add(source1);
        sourceRows.add(ContentValueFixtures.generateSourceContentValuesV8());
        addSources(sourceRows);
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);

        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        ContentValues trigger1 = ContentValueFixtures.generateTriggerContentValuesV8();
        trigger1.put(MeasurementTables.TriggerContract.ID, UUID.randomUUID().toString());
        triggerRows.add(trigger1);
        triggerRows.add(ContentValueFixtures.generateTriggerContentValuesV8());
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);

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

    private static void addSources(List<ContentValues> sourceRows) {
        for (int i = 0; i < INSERTED_SOURCE.length; i++) {
            addSource(sourceRows, INSERTED_SOURCE[i][0], INSERTED_SOURCE[i][1],
                    INSERTED_SOURCE[i][2]);
        }
    }

    private static void addSource(List<ContentValues> sourceRows, String id,
            String appDestination, String webDestination) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, id);
        values.put(MeasurementTablesDeprecated.SourceContract.APP_DESTINATION, appDestination);
        values.put(MeasurementTablesDeprecated.SourceContract.WEB_DESTINATION, webDestination);
        sourceRows.add(values);
    }

    private static void assertSourceDestinationMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.SourceDestination.TABLE,
                        new String[] {
                            MeasurementTables.SourceDestination.SOURCE_ID,
                            MeasurementTables.SourceDestination.DESTINATION_TYPE,
                            MeasurementTables.SourceDestination.DESTINATION
                        },
                        MeasurementTables.SourceDestination.SOURCE_ID + " IN ('1', '2', '3')",
                        null,
                        null,
                        null,
                        /* orderBy */ String.format("%s ASC, %s ASC",
                                MeasurementTables.SourceDestination.SOURCE_ID,
                                MeasurementTables.SourceDestination.DESTINATION),
                        null);
        while (cursor.moveToNext()) {
            assertSourceDestinationMigrated(cursor);
        }
    }

    private static void assertSourceDestinationMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_SOURCE_DESTINATION[i][0],
                cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.SourceDestination.SOURCE_ID)));
        assertEquals(
                MIGRATED_SOURCE_DESTINATION[i][1],
                cursor.getString(cursor.getColumnIndex(
                        MeasurementTables.SourceDestination.DESTINATION_TYPE)));
        assertEquals(
                MIGRATED_SOURCE_DESTINATION[i][2],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.SourceDestination.DESTINATION)));
    }

    private void assertEmptySourceDestinationTable(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.SourceDestination.TABLE,
                        null, null, null, null, null, null);
        assertEquals(0, cursor.getCount());
    }
}
