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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV6Test extends MeasurementDbMigratorTestBaseDeprecated {

    private static final String[][] INSERTED_ASYNC_REGISTRATION = {
        // id, enrollment_id
        {"1", "enrollment-id-1"}
    };

    private static final String[][] MIGRATED_ASYNC_REGISTRATION = {
        // id, enrollment_id
        {"1", "enrollment-id-1"}
    };

    private static final String[][] INSERTED_SOURCE = {
        // id, expiry
        {"1", "1673464509232"}
    };

    private static final String[][] MIGRATED_SOURCE = {
        /* id, expiry, event_report_window, aggregatable_report_window, shared_aggregation_keys,
        install_time */
        {"1", "1673464509232", "1673464509232", "1673464509232", null, null}
    };

    private static final String[][] INSERTED_TRIGGER = {
        // id, attribution_destination, event_triggers
        {"1", "android-app://com.android.app", null},
        {"2", "android-app://com.android.app2", "[{\"trigger_data\":0}]"}
    };

    private static final String[][] MIGRATED_TRIGGER = {
        // id, attribution_destination, event_triggers, attribution_config, x_network_key_mapping
        {"1", "android-app://com.android.app", "[]", null, null},
        {"2", "android-app://com.android.app2", "[{\"trigger_data\":0}]", null, null}
    };

    @Test
    public void performMigration_success_v3ToV6() throws JSONException {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);

        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 17));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 25));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 16));

        insertAsyncRegistrations(db);
        insertSources(db);
        insertTriggers(db);
        new MeasurementDbMigratorV6().performMigration(db, 3, 6);
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 18));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 31));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 19));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.XnaIgnoredSourcesContract.TABLE, 2));
        assertAsyncRegistrationMigration(db);
        assertSourceMigration(db);
        assertTriggerMigration(db);

        db.close();
    }

    @Test
    public void performMigration_twiceToSameVersion() throws JSONException {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);

        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 17));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 25));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 16));

        insertAsyncRegistrations(db);
        insertSources(db);
        insertTriggers(db);
        new MeasurementDbMigratorV6().performMigration(db, 3, 6);
        // Perform migration again.
        new MeasurementDbMigratorV6().performMigration(db, 6, 6);

        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 18));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 31));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 19));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.XnaIgnoredSourcesContract.TABLE, 2));
        assertAsyncRegistrationMigration(db);
        assertSourceMigration(db);
        assertTriggerMigration(db);

        db.close();
    }

    @Test
    public void performMigration_success_checkAllFields_V3toV6() {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);

        // Insert ContentValues with all fields for all tables in V3
        // Async Registration
        db.insert(
                MeasurementTables.AsyncRegistrationContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAsyncRegistrationContentValuesV3());
        assertEquals(
                getTableColumns(db, MeasurementTables.AsyncRegistrationContract.TABLE).size(),
                ContentValueFixtures.generateAsyncRegistrationContentValuesV3().size());

        // Source
        db.insert(
                MeasurementTables.SourceContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateSourceContentValuesV3());
        assertEquals(
                getTableColumns(db, MeasurementTables.SourceContract.TABLE).size(),
                ContentValueFixtures.generateSourceContentValuesV3().size());

        // Trigger
        db.insert(
                MeasurementTables.TriggerContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateTriggerContentValuesV3());
        assertEquals(
                getTableColumns(db, MeasurementTables.TriggerContract.TABLE).size(),
                ContentValueFixtures.generateTriggerContentValuesV3().size());

        // Attribution
        assertEquals(
                getTableColumns(db, MeasurementTables.AttributionContract.TABLE).size(),
                ContentValueFixtures.generateAttributionContentValuesV3().size());
        db.insert(
                MeasurementTables.AttributionContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAttributionContentValuesV3());

        // Event Report
        db.insert(
                MeasurementTables.EventReportContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateEventReportContentValuesV3());
        assertEquals(
                getTableColumns(db, MeasurementTables.EventReportContract.TABLE).size(),
                ContentValueFixtures.generateEventReportContentValuesV3().size());

        // Aggregate Report
        db.insert(
                MeasurementTables.AggregateReport.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAggregateReportContentValuesV3());
        assertEquals(
                getTableColumns(db, MeasurementTables.AggregateReport.TABLE).size(),
                ContentValueFixtures.generateAggregateReportContentValuesV3().size());

        // Aggregate Encryption Key
        db.insert(
                MeasurementTables.AggregateEncryptionKey.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV3());
        assertEquals(
                getTableColumns(db, MeasurementTables.AggregateEncryptionKey.TABLE).size(),
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV3().size());

        // Debug Report
        db.insert(
                MeasurementTables.DebugReportContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateDebugReportContentValuesV3());
        assertEquals(
                getTableColumns(db, MeasurementTables.DebugReportContract.TABLE).size(),
                ContentValueFixtures.generateDebugReportContentValuesV3().size());

        // Execution
        new MeasurementDbMigratorV6().performMigration(db, 3, 6);

        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();

        verifyAsyncRegistrationAllFieldsV3(db);
        verifySourceAllFieldsV3(db);
        verifyTriggerAllFieldsV3(db);
        verifyAttributionAllFieldsV3(db);
        verifyEventReportAllFieldsV3(db);
        verifyAggregateReportAllFieldsV3(db);
        verifyAggregateEncryptionKeyAllFieldsV3(db);
        verifyDebugReportAllFieldsV3(db);
    }

    @Override
    int getTargetVersion() {
        return 6;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV6();
    }

    private static void insertAsyncRegistrations(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_ASYNC_REGISTRATION.length; i++) {
            insertAsyncRegistration(
                    db, INSERTED_ASYNC_REGISTRATION[i][0], INSERTED_ASYNC_REGISTRATION[i][1]);
        }
    }

    private static void insertSources(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_SOURCE.length; i++) {
            insertSource(db, INSERTED_SOURCE[i][0], INSERTED_SOURCE[i][1]);
        }
    }

    private static void insertTriggers(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_TRIGGER.length; i++) {
            insertTrigger(
                    db, INSERTED_TRIGGER[i][0], INSERTED_TRIGGER[i][1], INSERTED_TRIGGER[i][2]);
        }
    }

    private static void insertAsyncRegistration(SQLiteDatabase db, String id, String enrollmentId) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AsyncRegistrationContract.ID, id);
        values.put(MeasurementTablesDeprecated.AsyncRegistration.ENROLLMENT_ID, enrollmentId);
        db.insert(MeasurementTables.AsyncRegistrationContract.TABLE, null, values);
    }

    private static void insertSource(SQLiteDatabase db, String id, String expiry) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, id);
        values.put(MeasurementTables.SourceContract.EXPIRY_TIME, Long.valueOf(expiry));
        db.insert(MeasurementTables.SourceContract.TABLE, null, values);
    }

    private static void insertTrigger(SQLiteDatabase db, String id, String attributionDestination,
            String eventTriggers) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, id);
        values.put(
                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION, attributionDestination);
        values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS, eventTriggers);
        db.insert(MeasurementTables.TriggerContract.TABLE, null, values);
    }

    private static void assertAsyncRegistrationMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        new String[] {
                            MeasurementTables.AsyncRegistrationContract.ID,
                            MeasurementTablesDeprecated.AsyncRegistration.ENROLLMENT_ID,
                            MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID
                        },
                        null,
                        null,
                        null,
                        null,
                        /* orderBy */ MeasurementTables.AsyncRegistrationContract.ID,
                        null);
        int count = 0;
        while (cursor.moveToNext()) {
            assertAsyncRegistrationMigrated(cursor);
            count += 1;
        }
        assertEquals(INSERTED_ASYNC_REGISTRATION.length, count);
    }

    private static void assertSourceMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {
                            MeasurementTables.SourceContract.ID,
                            MeasurementTables.SourceContract.EXPIRY_TIME,
                            MeasurementTables.SourceContract.EVENT_REPORT_WINDOW,
                            MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW,
                            MeasurementTables.SourceContract.REGISTRATION_ID,
                            MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS,
                            MeasurementTables.SourceContract.INSTALL_TIME
                        },
                        null,
                        null,
                        null,
                        null,
                        /* orderBy */ MeasurementTables.SourceContract.ID,
                        null);
        int count = 0;
        while (cursor.moveToNext()) {
            assertSourceMigrated(cursor);
            count += 1;
        }
        assertEquals(INSERTED_SOURCE.length, count);
    }

    private static void assertTriggerMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        new String[] {
                            MeasurementTables.TriggerContract.ID,
                            MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION,
                            MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                            MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG,
                            MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING
                        },
                        null,
                        null,
                        null,
                        null,
                        /* orderBy */ MeasurementTables.TriggerContract.ID,
                        null);
        int count = 0;
        while (cursor.moveToNext()) {
            assertTriggerMigrated(cursor);
            count += 1;
        }
        assertEquals(INSERTED_TRIGGER.length, count);
    }

    private static void assertAsyncRegistrationMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_ASYNC_REGISTRATION[i][0],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.AsyncRegistrationContract.ID)));
        assertEquals(
                MIGRATED_ASYNC_REGISTRATION[i][1],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTablesDeprecated.AsyncRegistration.ENROLLMENT_ID)));
        assertNotNull(
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID)));
    }

    private static void assertSourceMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_SOURCE[i][0],
                cursor.getString(cursor.getColumnIndex(MeasurementTables.SourceContract.ID)));
        assertEquals(
                MIGRATED_SOURCE[i][1],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.SourceContract.EXPIRY_TIME)));
        assertEquals(
                MIGRATED_SOURCE[i][2],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.SourceContract.EVENT_REPORT_WINDOW)));
        assertEquals(
                MIGRATED_SOURCE[i][3],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW)));
        assertEquals(
                MIGRATED_SOURCE[i][4],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS)));
        assertEquals(
                MIGRATED_SOURCE[i][5],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.SourceContract.INSTALL_TIME)));
        assertNotNull(
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.SourceContract.REGISTRATION_ID)));
    }

    private static void assertTriggerMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_TRIGGER[i][0],
                cursor.getString(cursor.getColumnIndex(MeasurementTables.TriggerContract.ID)));
        assertEquals(
                MIGRATED_TRIGGER[i][1],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.ATTRIBUTION_DESTINATION)));
        assertEquals(
                MIGRATED_TRIGGER[i][2],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.EVENT_TRIGGERS)));
        assertEquals(
                MIGRATED_TRIGGER[i][3],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG)));
        assertEquals(
                MIGRATED_TRIGGER[i][4],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING)));
    }

    private void verifyAsyncRegistrationAllFieldsV3(SQLiteDatabase db) {
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
            ContentValues asyncRegistrationV3 =
                    ContentValueFixtures.generateAsyncRegistrationContentValuesV3();
            ContentValues asyncRegistrationV6 = cursorRowToContentValues(cursor);

            for (String column : asyncRegistrationV3.keySet()) {
                assertEquals(asyncRegistrationV3.get(column), asyncRegistrationV6.get(column));
            }

            // The migration added 1 new column ("registration_id") to the AsyncRegistration table.
            // Assert that column was updated with a random UUID.
            assertEquals(asyncRegistrationV3.size() + 1, asyncRegistrationV6.size());

            assertTrue(
                    asyncRegistrationV6.containsKey(
                            MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID));
            assertNotNull(
                    asyncRegistrationV6.get(
                            MeasurementTables.AsyncRegistrationContract.REGISTRATION_ID));
        }
    }

    private void verifySourceAllFieldsV3(SQLiteDatabase db) {
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
            ContentValues sourceV3 = ContentValueFixtures.generateSourceContentValuesV3();
            ContentValues sourceV6 = cursorRowToContentValues(cursor);

            for (String column : sourceV3.keySet()) {
                if (column.equals(MeasurementTablesDeprecated.SourceContract.DEDUP_KEYS)) {
                    // The migration renamed the column from "dedup_keys" to
                    // "event_report_dedup_keys"
                    assertEquals(
                            sourceV3.get(MeasurementTablesDeprecated.SourceContract.DEDUP_KEYS),
                            sourceV6.get(MeasurementTables.SourceContract.EVENT_REPORT_DEDUP_KEYS));
                } else {
                    assertEquals(sourceV3.get(column), sourceV6.get(column));
                }
            }

            // The migration added 6 new columns ("aggregate_report_dedup_keys",
            // "event_report_window", "aggregate_report_window", "registration_id",
            // "shared_aggregation_keys", and "install_time") to the Source table.
            assertEquals(sourceV3.size() + 6, sourceV6.size());

            // The "event_report_window" and "aggregate_report_window" are set with the value from
            // the "expiry time" column.
            assertTrue(sourceV6.containsKey(MeasurementTables.SourceContract.EVENT_REPORT_WINDOW));
            assertEquals(
                    sourceV3.get(MeasurementTables.SourceContract.EXPIRY_TIME),
                    sourceV6.get(MeasurementTables.SourceContract.EVENT_REPORT_WINDOW));

            assertTrue(
                    sourceV6.containsKey(
                            MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW));
            assertEquals(
                    sourceV3.get(MeasurementTables.SourceContract.EXPIRY_TIME),
                    sourceV6.get(MeasurementTables.SourceContract.AGGREGATABLE_REPORT_WINDOW));

            // The "registration_id" column was updated with a random UUID.
            assertTrue(sourceV6.containsKey(MeasurementTables.SourceContract.REGISTRATION_ID));
            assertNotNull(sourceV6.get(MeasurementTables.SourceContract.REGISTRATION_ID));

            // The "aggregate_report_dedup_keys", "shared_aggregation_keys", and "install_time"
            // columns are not populated.
            assertTrue(
                    sourceV6.containsKey(
                            MeasurementTables.SourceContract.AGGREGATE_REPORT_DEDUP_KEYS));
            assertNull(sourceV6.get(MeasurementTables.SourceContract.AGGREGATE_REPORT_DEDUP_KEYS));

            assertTrue(
                    sourceV6.containsKey(MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS));
            assertNull(sourceV6.get(MeasurementTables.SourceContract.SHARED_AGGREGATION_KEYS));

            assertTrue(sourceV6.containsKey(MeasurementTables.SourceContract.INSTALL_TIME));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(MeasurementTables.SourceContract.INSTALL_TIME)));
        }
    }

    private void verifyTriggerAllFieldsV3(SQLiteDatabase db) {
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
            ContentValues triggerV3 = ContentValueFixtures.generateTriggerContentValuesV3();
            ContentValues triggerV6 = cursorRowToContentValues(cursor);

            for (String column : triggerV3.keySet()) {
                assertEquals(triggerV3.get(column), triggerV6.get(column));
            }

            // The migration added 3 new columns ("attribution_config", "x_network_key_mapping", and
            // "aggregatable_deduplication_keys") to the Trigger table.  Assert the columns are not
            // populated.
            assertEquals(triggerV3.size() + 3, triggerV6.size());

            assertTrue(triggerV6.containsKey(MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG));
            assertNull(triggerV6.get(MeasurementTables.TriggerContract.ATTRIBUTION_CONFIG));

            assertTrue(
                    triggerV6.containsKey(MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING));
            assertNull(triggerV6.get(MeasurementTables.TriggerContract.X_NETWORK_KEY_MAPPING));

            assertTrue(
                    triggerV6.containsKey(
                            MeasurementTables.TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS));
            assertNull(
                    triggerV6.get(
                            MeasurementTables.TriggerContract.AGGREGATABLE_DEDUPLICATION_KEYS));
        }
    }

    private void verifyAttributionAllFieldsV3(SQLiteDatabase db) {
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
            ContentValues attributionV3 = ContentValueFixtures.generateAttributionContentValuesV3();
            ContentValues attributionV6 = cursorRowToContentValues(cursor);

            for (String column : attributionV3.keySet()) {
                assertEquals(attributionV3.get(column), attributionV6.get(column));
            }

            // No new columns were added.
            assertEquals(attributionV3.size(), attributionV6.size());
        }
    }

    private void verifyEventReportAllFieldsV3(SQLiteDatabase db) {
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
            ContentValues eventReportV3 = ContentValueFixtures.generateEventReportContentValuesV3();
            ContentValues eventReportV6 = cursorRowToContentValues(cursor);

            for (String column : eventReportV3.keySet()) {
                assertEquals(eventReportV3.get(column), eventReportV6.get(column));
            }

            // No new columns were added.
            assertEquals(eventReportV3.size(), eventReportV3.size());
        }
    }

    private void verifyAggregateReportAllFieldsV3(SQLiteDatabase db) {
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
            ContentValues aggregateReportV3 =
                    ContentValueFixtures.generateAggregateReportContentValuesV3();
            ContentValues aggregateReportV6 = cursorRowToContentValues(cursor);

            for (String column : aggregateReportV3.keySet()) {
                assertEquals(aggregateReportV3.get(column), aggregateReportV6.get(column));
            }

            // No new columns were added.
            assertEquals(aggregateReportV3.size(), aggregateReportV6.size());
        }
    }

    private void verifyAggregateEncryptionKeyAllFieldsV3(SQLiteDatabase db) {
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
            ContentValues aggregateEncryptionKeyV3 =
                    ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV2();
            ContentValues aggregateEncryptionKeyV6 = cursorRowToContentValues(cursor);

            for (String column : aggregateEncryptionKeyV3.keySet()) {
                assertEquals(
                        aggregateEncryptionKeyV3.get(column), aggregateEncryptionKeyV6.get(column));
            }

            // No new columns were added.
            assertEquals(aggregateEncryptionKeyV3.size(), aggregateEncryptionKeyV6.size());
        }
    }

    private void verifyDebugReportAllFieldsV3(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.DebugReportContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        MeasurementTables.DebugReportContract.ID,
                        null);

        assertEquals(1, cursor.getCount());
        while (cursor.moveToNext()) {
            ContentValues debugReportV3 = ContentValueFixtures.generateDebugReportContentValuesV3();
            ContentValues debugReportV6 = cursorRowToContentValues(cursor);

            for (String column : debugReportV3.keySet()) {
                assertEquals(debugReportV3.get(column), debugReportV6.get(column));
            }

            // No new columns were added.
            assertEquals(debugReportV3.size(), debugReportV6.size());
        }
    }
}
