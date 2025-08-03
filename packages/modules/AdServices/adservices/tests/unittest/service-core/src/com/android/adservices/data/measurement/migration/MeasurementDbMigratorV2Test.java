/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.data.DbTestUtil.getTableColumns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV2Test extends MeasurementDbMigratorTestBaseDeprecated {

    @Test
    public void performMigration_success() {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.EventReportContract.TABLE, 14));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.AggregateReport.TABLE, 11));
    }

    @Test
    public void performMigration_success_checkAllFields_V1toV2() {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Insert ContentValues with all fields for all tables in V1

        // Async Registration
        db.insert(
                MeasurementTables.AsyncRegistrationContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAsyncRegistrationContentValuesV1());
        assertEquals(
                getTableColumns(db, MeasurementTables.AsyncRegistrationContract.TABLE).size(),
                ContentValueFixtures.generateAsyncRegistrationContentValuesV1().size());

        // Source
        db.insert(
                MeasurementTables.SourceContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateSourceContentValuesV1());
        assertEquals(
                getTableColumns(db, MeasurementTables.SourceContract.TABLE).size(),
                ContentValueFixtures.generateSourceContentValuesV1().size());

        // Trigger
        db.insert(
                MeasurementTables.TriggerContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateTriggerContentValuesV1());
        assertEquals(
                getTableColumns(db, MeasurementTables.TriggerContract.TABLE).size(),
                ContentValueFixtures.generateTriggerContentValuesV1().size());

        // Attribution
        assertEquals(
                getTableColumns(db, MeasurementTables.AttributionContract.TABLE).size(),
                ContentValueFixtures.generateAttributionContentValuesV1().size());
        db.insert(
                MeasurementTables.AttributionContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAttributionContentValuesV1());

        // Event Report
        db.insert(
                MeasurementTables.EventReportContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateEventReportContentValuesV1());
        assertEquals(
                getTableColumns(db, MeasurementTables.EventReportContract.TABLE).size(),
                ContentValueFixtures.generateEventReportContentValuesV1().size());

        // Aggregate Report
        db.insert(
                MeasurementTables.AggregateReport.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAggregateReportContentValuesV1());
        assertEquals(
                getTableColumns(db, MeasurementTables.AggregateReport.TABLE).size(),
                ContentValueFixtures.generateAggregateReportContentValuesV1().size());

        // Aggregate Encryption Key
        db.insert(
                MeasurementTables.AggregateEncryptionKey.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV1());
        assertEquals(
                getTableColumns(db, MeasurementTables.AggregateEncryptionKey.TABLE).size(),
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV1().size());

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();

        verifyAsyncRegistrationAllFieldsV1(db);
        verifySourceAllFieldsV1(db);
        verifyTriggerAllFieldsV1(db);
        verifyAttributionAllFieldsV1(db);
        verifyEventReportAllFieldsV1(db);
        verifyAggregateReportAllFieldsV1(db);
        verifyAggregateEncryptionKeyAllFieldsV1(db);
    }

    private void verifyAsyncRegistrationAllFieldsV1(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.AsyncRegistrationContract.ID,
                        null);

        assertEquals(1, cursor.getCount());
        while (cursor.moveToNext()) {
            ContentValues asyncRegistrationV1 =
                    ContentValueFixtures.generateAsyncRegistrationContentValuesV1();
            ContentValues asyncRegistrationV2 = cursorRowToContentValues(cursor);

            for (String column : asyncRegistrationV1.keySet()) {
                assertEquals(asyncRegistrationV1.get(column), asyncRegistrationV2.get(column));
            }

            // No new columns were added.
            assertEquals(asyncRegistrationV1.size(), asyncRegistrationV2.size());
        }
    }

    private void verifySourceAllFieldsV1(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.SourceContract.ID,
                        null);

        assertEquals(1, cursor.getCount());
        while (cursor.moveToNext()) {
            ContentValues sourceV1 = ContentValueFixtures.generateSourceContentValuesV1();
            ContentValues sourceV2 = cursorRowToContentValues(cursor);

            for (String column : sourceV1.keySet()) {
                assertEquals(sourceV1.get(column), sourceV2.get(column));
            }

            // No new columns were added.
            assertEquals(sourceV1.size(), sourceV2.size());
        }
    }

    private void verifyTriggerAllFieldsV1(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.TriggerContract.ID,
                        null);

        assertEquals(1, cursor.getCount());
        while (cursor.moveToNext()) {
            ContentValues triggerV1 = ContentValueFixtures.generateTriggerContentValuesV1();
            ContentValues triggerV2 = cursorRowToContentValues(cursor);

            for (String column : triggerV1.keySet()) {
                assertEquals(triggerV1.get(column), triggerV2.get(column));
            }

            // No new columns were added.
            assertEquals(triggerV1.size(), triggerV2.size());
        }
    }

    private void verifyAttributionAllFieldsV1(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.AttributionContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.AttributionContract.ID,
                        null);

        assertEquals(1, cursor.getCount());
        while (cursor.moveToNext()) {
            ContentValues attributionV1 = ContentValueFixtures.generateAttributionContentValuesV1();
            ContentValues attributionV2 = cursorRowToContentValues(cursor);

            for (String column : attributionV1.keySet()) {
                assertEquals(attributionV1.get(column), attributionV2.get(column));
            }

            // No new columns were added.
            assertEquals(attributionV1.size(), attributionV2.size());
        }
    }

    private void verifyEventReportAllFieldsV1(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.EventReportContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.EventReportContract.ID,
                        null);

        assertEquals(1, cursor.getCount());
        while (cursor.moveToNext()) {
            ContentValues eventReportV1 = ContentValueFixtures.generateEventReportContentValuesV1();
            ContentValues eventReportV2 = cursorRowToContentValues(cursor);

            for (String column : eventReportV1.keySet()) {
                assertEquals(eventReportV1.get(column), eventReportV2.get(column));
            }

            // The migration added 2 new columns ("source_debug_key" and "trigger_debug_key") to the
            // EventReport table.  Assert those columns are not populated.
            assertEquals(eventReportV1.size() + 2, eventReportV2.size());

            assertNotEquals(
                    -1,
                    cursor.getColumnIndex(MeasurementTables.EventReportContract.SOURCE_DEBUG_KEY));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.EventReportContract.SOURCE_DEBUG_KEY)));

            assertNotEquals(
                    -1,
                    cursor.getColumnIndex(MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEY));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEY)));
        }
    }

    private void verifyAggregateReportAllFieldsV1(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.AggregateReport.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.AggregateReport.ID,
                        null);

        assertEquals(1, cursor.getCount());
        while (cursor.moveToNext()) {
            ContentValues aggregateReportV1 =
                    ContentValueFixtures.generateAggregateReportContentValuesV1();
            ContentValues aggregateReportV2 = cursorRowToContentValues(cursor);

            for (String column : aggregateReportV1.keySet()) {
                assertEquals(aggregateReportV1.get(column), aggregateReportV2.get(column));
            }

            // The migration added 2 new columns ("source_debug_key" and "trigger_debug_key") to the
            // AggregateReport table.  Assert those columns are not populated.
            assertEquals(aggregateReportV1.size() + 2, aggregateReportV2.size());

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY)));

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY)));
        }
    }

    private void verifyAggregateEncryptionKeyAllFieldsV1(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.AggregateEncryptionKey.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.AggregateEncryptionKey.ID,
                        null);

        assertEquals(1, cursor.getCount());
        while (cursor.moveToNext()) {
            ContentValues aggregateEncryptionKeyV1 =
                    ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV1();
            ContentValues aggregateEncryptionKeyV2 = cursorRowToContentValues(cursor);

            for (String column : aggregateEncryptionKeyV1.keySet()) {
                assertEquals(
                        aggregateEncryptionKeyV1.get(column), aggregateEncryptionKeyV2.get(column));
            }

            // No new columns were added.
            assertEquals(aggregateEncryptionKeyV1.size(), aggregateEncryptionKeyV2.size());
        }
    }

    @Override
    int getTargetVersion() {
        return 2;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV2();
    }
}
