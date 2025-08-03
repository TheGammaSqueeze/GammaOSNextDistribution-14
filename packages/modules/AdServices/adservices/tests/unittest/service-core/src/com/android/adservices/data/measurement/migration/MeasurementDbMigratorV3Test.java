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

import static com.android.adservices.data.DbTestUtil.doesIndexExist;
import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.data.DbTestUtil.getTableColumns;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.common.WebUtil;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.registration.AsyncRegistration;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV3Test extends MeasurementDbMigratorTestBaseDeprecated {
    private static final String FILTERS_V2_1 = "{\"id1\":[\"val11\",\"val12\"]}";
    private static final String FILTERS_V2_2 = "{\"id2\":[\"val21\",\"val22\",\"val23\"]}";
    private static final String FILTERS_V3_1;
    private static final String FILTERS_V3_2;
    private static final String EVENT_TRIGGERS_V2;
    private static final String AGGREGATE_TRIGGER_DATA_V2;
    private static final String EVENT_TRIGGERS_V3;
    private static final String AGGREGATE_TRIGGER_DATA_V3;
    private static final String AD_TECH_DOMAIN = "ad_tech_domain";

    static {
        EVENT_TRIGGERS_V2 =
                "[{\"trigger_data\":1,\"filters\":" + FILTERS_V2_1 + "},"
                + "{\"trigger_data\":2,\"filters\":" + FILTERS_V2_1
                + ",\"not_filters\":" + FILTERS_V2_2 + "},"
                + "{\"priority\":100},{}]";
        AGGREGATE_TRIGGER_DATA_V2 =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"key11\"],"
                + "\"filters\":" + FILTERS_V2_1 + "},"
                + "{\"key_piece\":\"0x800\",\"source_keys\":[\"key21\",\"key22\"],"
                + "\"filters\":" + FILTERS_V2_1 + ",\"not_filters\":" + FILTERS_V2_2 + "}]";
        FILTERS_V3_1 = "[" + FILTERS_V2_1 + "]";
        FILTERS_V3_2 = "[" + FILTERS_V2_2 + "]";
        EVENT_TRIGGERS_V3 =
                "[{\"trigger_data\":1,\"filters\":" + FILTERS_V3_1 + "},"
                + "{\"trigger_data\":2,\"filters\":" + FILTERS_V3_1
                + ",\"not_filters\":" + FILTERS_V3_2 + "},"
                + "{\"priority\":100,\"trigger_data\":\"0\"},{\"trigger_data\":\"0\"}]";
        AGGREGATE_TRIGGER_DATA_V3 =
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"key11\"],"
                + "\"filters\":" + FILTERS_V3_1 + "},"
                + "{\"key_piece\":\"0x800\",\"source_keys\":[\"key21\",\"key22\"],"
                + "\"filters\":" + FILTERS_V3_1 + ",\"not_filters\":" + FILTERS_V3_2 + "}]";
    }

    private static final String[][] INSERTED_EVENT_REPORT_DATA = {
        // id, destination, sourceId
        {"1", "https://example.com", "random one"},
        {"2", "http://will-be-trimmed.example.com", "random two"},
        {"3", "http://example.com/will-be-trimmed", "random three"},
        {"4", "android-app://com.android.app/will-be-trimmed", "random four"},
        {"5", "android-app://com.another.android.app", "random five"},
    };

    private static final String[][] MIGRATED_EVENT_REPORT_DATA = {
        // id, destination, sourceEventId
        {"1", "https://example.com", "random one"},
        {"2", "http://example.com", "random two"},
        {"3", "http://example.com", "random three"},
        {"4", "android-app://com.android.app", "random four"},
        {"5", "android-app://com.another.android.app", "random five"},
    };

    private static final String[][] INSERTED_TRIGGER_DATA = {
        // id, eventTriggers, aggregateTriggerData, filters
        {"1", null, null, "{"}, // Invalid filters JSON
        {"2", "{[]}", null, null},
        {"3", "[}]", null, null},
        {"4", EVENT_TRIGGERS_V2, AGGREGATE_TRIGGER_DATA_V2, FILTERS_V2_1}
    };

    private static final String[][] MIGRATED_TRIGGER_DATA = {
        // id, eventTriggers, aggregateTriggerData, filters
        {"1", "[]", null, null, null},
        {"2", "[]", null, null, null},
        {"3", "[]", null, null, null},
        {"4", EVENT_TRIGGERS_V3, AGGREGATE_TRIGGER_DATA_V3, FILTERS_V3_1}
    };

    @Test
    public void performMigration_success_v2ToV3() throws JSONException {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);

        insertV2Sources(db);
        insertEventReports(db);
        insertTriggers(db);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);

        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 17));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.EventReportContract.TABLE, 17));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.AggregateReport.TABLE, 14));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AttributionContract.TABLE, 10));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 25));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 16));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.DebugReportContract.TABLE, 4));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_ss_so_ds_do_ei_tt"));
        assertSourceMigration(db);
        assertEventReportMigration(db);
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
        insertV2Sources(db);
        insertTriggers(db);
        insertEventReports(db);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);

        // Perform migration again.
        new MeasurementDbMigratorV3().performMigration(db, 3, 3);

        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AsyncRegistrationContract.TABLE, 17));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.EventReportContract.TABLE, 17));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.AggregateReport.TABLE, 14));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AttributionContract.TABLE, 10));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_ss_so_ds_do_ei_tt"));
        assertSourceMigration(db);
        assertTriggerMigration(db);
        assertEventReportMigration(db);
        db.close();
    }

    @Test
    public void insertAndRetrieveAsyncData_afterV3Migration() {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        insertV2Sources(db);
        insertEventReports(db);
        insertTriggers(db);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);
        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Write a new asyncRegistrator row.
        db = dbHelper.getWritableDatabase();
        ContentValues asyncRegistrationRow = getAsyncRegistrationEntry();
        db.insert(MeasurementTables.AsyncRegistrationContract.TABLE, null, asyncRegistrationRow);
        db.close();

        db = dbHelper.getReadableDatabase();
        assertDbContainsAsyncRegistrationValues(db, asyncRegistrationRow);
    }

    @Test
    public void performMigration_insertionCantHappenDueToColumnsMismatch_createsNewTable() {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        // Create an old DB when ad_tech_domain column existed
        String createEventReportWithOldDeprecatedColumn =
                "CREATE TABLE "
                        + MeasurementTables.EventReportContract.TABLE
                        + " ("
                        + MeasurementTables.EventReportContract.ID
                        + " TEXT PRIMARY KEY NOT NULL, "
                        + MeasurementTables.EventReportContract.SOURCE_ID
                        + " INTEGER, "
                        + AD_TECH_DOMAIN // doesn't exist in the current codebase, existed before
                        + " TEXT, "
                        + MeasurementTables.EventReportContract.ENROLLMENT_ID
                        + " TEXT, "
                        + MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION
                        + " TEXT, "
                        + MeasurementTables.EventReportContract.REPORT_TIME
                        + " INTEGER, "
                        + MeasurementTables.EventReportContract.TRIGGER_DATA
                        + " INTEGER, "
                        + MeasurementTables.EventReportContract.TRIGGER_PRIORITY
                        + " INTEGER, "
                        + MeasurementTables.EventReportContract.TRIGGER_DEDUP_KEY
                        + " INTEGER, "
                        + MeasurementTables.EventReportContract.TRIGGER_TIME
                        + " INTEGER, "
                        + MeasurementTables.EventReportContract.STATUS
                        + " INTEGER, "
                        + MeasurementTables.EventReportContract.SOURCE_TYPE
                        + " TEXT, "
                        + MeasurementTables.EventReportContract.RANDOMIZED_TRIGGER_RATE
                        + " DOUBLE "
                        + ")";

        db.execSQL("DROP TABLE " + MeasurementTables.EventReportContract.TABLE);
        db.execSQL(createEventReportWithOldDeprecatedColumn);

        // Execution
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);
        db.close();

        // Assertion
        db = dbHelper.safeGetWritableDatabase();
        assertNotNull(db);
        DbTestUtil.doesTableExistAndColumnCountMatch(
                db, MeasurementTables.EventReportContract.TABLE, 17);
    }

    @Test
    public void performMigration_success_checkAllFields_V2toV3() {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        new MeasurementDbMigratorV2().performMigration(db, 1, 2);

        // Insert ContentValues with all fields for all tables in V2

        // Async Registration
        db.insert(
                MeasurementTables.AsyncRegistrationContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAsyncRegistrationContentValuesV2());
        assertEquals(
                getTableColumns(db, MeasurementTables.AsyncRegistrationContract.TABLE).size(),
                ContentValueFixtures.generateAsyncRegistrationContentValuesV2().size());

        // Source
        db.insert(
                MeasurementTables.SourceContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateSourceContentValuesV2());
        assertEquals(
                getTableColumns(db, MeasurementTables.SourceContract.TABLE).size(),
                ContentValueFixtures.generateSourceContentValuesV2().size());

        // Trigger
        db.insert(
                MeasurementTables.TriggerContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateTriggerContentValuesV2());
        assertEquals(
                getTableColumns(db, MeasurementTables.TriggerContract.TABLE).size(),
                ContentValueFixtures.generateTriggerContentValuesV2().size());

        // Attribution
        assertEquals(
                getTableColumns(db, MeasurementTables.AttributionContract.TABLE).size(),
                ContentValueFixtures.generateAttributionContentValuesV2().size());
        db.insert(
                MeasurementTables.AttributionContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAttributionContentValuesV2());

        // Event Report
        db.insert(
                MeasurementTables.EventReportContract.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateEventReportContentValuesV2());
        assertEquals(
                getTableColumns(db, MeasurementTables.EventReportContract.TABLE).size(),
                ContentValueFixtures.generateEventReportContentValuesV2().size());

        // Aggregate Report
        db.insert(
                MeasurementTables.AggregateReport.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAggregateReportContentValuesV2());
        assertEquals(
                getTableColumns(db, MeasurementTables.AggregateReport.TABLE).size(),
                ContentValueFixtures.generateAggregateReportContentValuesV2().size());

        // Aggregate Encryption Key
        db.insert(
                MeasurementTables.AggregateEncryptionKey.TABLE,
                /* nullColumnHack */ null,
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV2());
        assertEquals(
                getTableColumns(db, MeasurementTables.AggregateEncryptionKey.TABLE).size(),
                ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV2().size());

        // Execution
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);
        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();

        verifyAsyncRegistrationAllFieldsV2(db);
        verifySourceAllFieldsV2(db);
        verifyTriggerAllFieldsV2(db);
        verifyAttributionAllFieldsV2(db);
        verifyEventReportAllFieldsV2(db);
        verifyAggregateReportAllFieldsV2(db);
        verifyAggregateEncryptionKeyAllFieldsV2(db);
    }

    @Override
    int getTargetVersion() {
        return 3;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV3();
    }

    private static void insertEventReports(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_EVENT_REPORT_DATA.length; i++) {
            insertEventReport(
                    db,
                    INSERTED_EVENT_REPORT_DATA[i][0],
                    INSERTED_EVENT_REPORT_DATA[i][1],
                    INSERTED_EVENT_REPORT_DATA[i][2]);
        }
    }

    private static void insertTriggers(SQLiteDatabase db) {
        for (int i = 0; i < INSERTED_TRIGGER_DATA.length; i++) {
            insertTrigger(
                    db,
                    INSERTED_TRIGGER_DATA[i][0],
                    INSERTED_TRIGGER_DATA[i][1],
                    INSERTED_TRIGGER_DATA[i][2],
                    INSERTED_TRIGGER_DATA[i][3]);
        }
    }

    private static void insertV2Sources(SQLiteDatabase db) {
        // insert a source with valid aggregatable source
        String validAggregatableSourceV2 =
                "[\n"
                        + "              {\n"
                        + "                \"id\": \"campaignCounts\",\n"
                        + "                \"key_piece\": \"0x159\"\n"
                        + "              },\n"
                        + "              {\n"
                        + "                \"id\": \"geoValue\",\n"
                        + "                \"key_piece\": \"0x5\"\n"
                        + "              }\n"
                        + "            ]";
        insertSource(db, "1", validAggregatableSourceV2);

        // insert a source with invalid aggregatable source
        String invalidAggregatableSourceV2 =
                "[\n"
                        + "              {\n"
                        + "                \"id\": \"campaignCounts\",\n"
                        + "                \"key_piece\": \"0x159\"\n"
                        + "              ,\n" // missing closing brace making it invalid
                        + "              {\n"
                        + "                \"id\": \"geoValue\",\n"
                        + "                \"key_piece\": \"0x5\"\n"
                        + "              }\n"
                        + "            ]";
        insertSource(db, "2", invalidAggregatableSourceV2);
    }

    private static void insertSource(
            SQLiteDatabase db, String id, String invalidAggregatableSourceV2) {
        ContentValues invalidValues = new ContentValues();
        invalidValues.put(MeasurementTables.SourceContract.ID, id);
        invalidValues.put(
                MeasurementTables.SourceContract.AGGREGATE_SOURCE, invalidAggregatableSourceV2);

        db.insert(MeasurementTables.SourceContract.TABLE, null, invalidValues);
    }

    private static ContentValues getAsyncRegistrationEntry() {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.AsyncRegistrationContract.ID, "id");
        values.put(MeasurementTablesDeprecated.AsyncRegistration.ENROLLMENT_ID, "enrollment_id");
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI,
                WebUtil.validUri("https://foo.test/bar?ad=134").toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION,
                WebUtil.validUri("https://web-destination.test").toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION,
                Uri.parse("android-app://com.ver-app_destination").toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.OS_DESTINATION,
                Uri.parse("android-app://com.app_destination").toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.REGISTRANT,
                Uri.parse("android-app://com.registrant").toString());
        values.put(
                MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN,
                WebUtil.validUri("https://example.test").toString());
        values.put(MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_COUNT, 0);
        values.put(MeasurementTables.AsyncRegistrationContract.REQUEST_TIME, 0L);
        values.put(MeasurementTables.AsyncRegistrationContract.RETRY_COUNT, 0L);
        values.put(MeasurementTablesDeprecated.AsyncRegistration.LAST_PROCESSING_TIME, 0L);
        values.put(
                MeasurementTables.AsyncRegistrationContract.TYPE,
                AsyncRegistration.RegistrationType.APP_SOURCE.ordinal());
        values.put(
                MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE,
                AsyncRegistration.RegistrationType.APP_SOURCE.ordinal());
        values.put(MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED, 1);
        return values;
    }

    private static void assertDbContainsAsyncRegistrationValues(
            SQLiteDatabase db, ContentValues values) {
        Cursor cursor =
                db.query(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        /* columns= */ null,
                        /* selection= */ null,
                        /* selectionArgs= */ null,
                        /* groupBy= */ null,
                        /* having= */ null,
                        /* orderBy= */ null,
                        /* limit= */ null);
        assertThat(cursor.getCount()).isEqualTo(1);
        assertThat(cursor.moveToNext()).isTrue();
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTablesDeprecated.AsyncRegistration
                                                .ENROLLMENT_ID)))
                .isEqualTo(values.get(MeasurementTablesDeprecated.AsyncRegistration.ENROLLMENT_ID));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .REGISTRATION_URI)))
                .isEqualTo(
                        values.get(MeasurementTables.AsyncRegistrationContract.REGISTRATION_URI));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .WEB_DESTINATION)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.WEB_DESTINATION));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .VERIFIED_DESTINATION)))
                .isEqualTo(
                        values.get(
                                MeasurementTables.AsyncRegistrationContract.VERIFIED_DESTINATION));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .OS_DESTINATION)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.OS_DESTINATION));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.REGISTRANT)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.REGISTRANT));
        assertThat(
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.TOP_ORIGIN));
        assertThat(
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTablesDeprecated.AsyncRegistration
                                                .REDIRECT_COUNT)))
                .isEqualTo(
                        values.get(MeasurementTablesDeprecated.AsyncRegistration.REDIRECT_COUNT));
        assertThat(
                        cursor.getLong(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.REQUEST_TIME)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.REQUEST_TIME));
        assertThat(
                        cursor.getLong(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.RETRY_COUNT)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.RETRY_COUNT));
        assertThat(
                        cursor.getLong(
                                cursor.getColumnIndex(
                                        MeasurementTablesDeprecated.AsyncRegistration
                                                .LAST_PROCESSING_TIME)))
                .isEqualTo(
                        values.get(
                                MeasurementTablesDeprecated.AsyncRegistration
                                        .LAST_PROCESSING_TIME));
        assertThat(
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.TYPE)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.TYPE));
        assertThat(
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE)))
                .isEqualTo(values.get(MeasurementTables.AsyncRegistrationContract.SOURCE_TYPE));
        assertThat(
                        cursor.getInt(
                                cursor.getColumnIndex(
                                        MeasurementTables.AsyncRegistrationContract
                                                .DEBUG_KEY_ALLOWED)))
                .isEqualTo(
                        values.get(MeasurementTables.AsyncRegistrationContract.DEBUG_KEY_ALLOWED));
    }

    private static void assertEventReportMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.EventReportContract.TABLE,
                        new String[] {
                            MeasurementTables.EventReportContract.ID,
                            MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION,
                            MeasurementTables.EventReportContract.SOURCE_EVENT_ID
                        },
                        null,
                        null,
                        null,
                        null,
                        /* orderBy */ MeasurementTables.EventReportContract.ID,
                        null);
        int count = 0;
        while (cursor.moveToNext()) {
            assertEventReportMigrated(cursor);
            count += 1;
        }
        assertEquals(INSERTED_EVENT_REPORT_DATA.length, count);
    }

    private static void assertTriggerMigration(SQLiteDatabase db) {
        Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        new String[] {
                            MeasurementTables.TriggerContract.ID,
                            MeasurementTables.TriggerContract.EVENT_TRIGGERS,
                            MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA,
                            MeasurementTables.TriggerContract.FILTERS
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
        assertEquals(INSERTED_TRIGGER_DATA.length, count);
    }

    private static void assertSourceMigration(SQLiteDatabase db) throws JSONException {
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

        assertEquals(2, cursor.getCount());

        // valid aggregate source case
        assertTrue(cursor.moveToNext());
        String aggregateSourceString1 =
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.SourceContract.AGGREGATE_SOURCE));
        JSONObject aggregateSourceJsonObject = new JSONObject(aggregateSourceString1);

        assertEquals(2, aggregateSourceJsonObject.length());
        assertEquals("0x159", aggregateSourceJsonObject.getString("campaignCounts"));
        assertEquals("0x5", aggregateSourceJsonObject.getString("geoValue"));

        // invalid aggregate source case
        assertTrue(cursor.moveToNext());
        String aggregateSourceString2 =
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.SourceContract.AGGREGATE_SOURCE));
        assertNull(aggregateSourceString2);
    }

    private static void insertEventReport(
            SQLiteDatabase db, String id, String destination, String sourceId) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.ID, id);
        values.put(MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION, destination);
        values.put(MeasurementTables.EventReportContract.SOURCE_ID, sourceId);
        db.insert(MeasurementTables.EventReportContract.TABLE, null, values);
    }

    private static void insertTrigger(SQLiteDatabase db, String id, String eventTriggers,
            String aggregateTriggerData, String filters) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.TriggerContract.ID, id);
        values.put(MeasurementTables.TriggerContract.EVENT_TRIGGERS, eventTriggers);
        values.put(MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA, aggregateTriggerData);
        values.put(MeasurementTables.TriggerContract.FILTERS, filters);
        db.insert(MeasurementTables.TriggerContract.TABLE, null, values);
    }

    private static void assertEventReportMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_EVENT_REPORT_DATA[i][0],
                cursor.getString(cursor.getColumnIndex(MeasurementTables.EventReportContract.ID)));
        assertEquals(
                MIGRATED_EVENT_REPORT_DATA[i][1],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.EventReportContract.ATTRIBUTION_DESTINATION)));
        assertEquals(
                MIGRATED_EVENT_REPORT_DATA[i][2],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.EventReportContract.SOURCE_EVENT_ID)));
    }

    private static void assertTriggerMigrated(Cursor cursor) {
        int i = cursor.getPosition();
        assertEquals(
                MIGRATED_TRIGGER_DATA[i][0],
                cursor.getString(cursor.getColumnIndex(MeasurementTables.TriggerContract.ID)));
        assertEquals(
                MIGRATED_TRIGGER_DATA[i][1],
                cursor.getString(
                        cursor.getColumnIndex(MeasurementTables.TriggerContract.EVENT_TRIGGERS)));
        assertEquals(
                MIGRATED_TRIGGER_DATA[i][2],
                cursor.getString(
                        cursor.getColumnIndex(
                                MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA)));
        assertEquals(
                MIGRATED_TRIGGER_DATA[i][3],
                cursor.getString(cursor.getColumnIndex(MeasurementTables.TriggerContract.FILTERS)));
    }

    private void verifyAsyncRegistrationAllFieldsV2(SQLiteDatabase db) {
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

        // The migration drops the old table and creates a new one without copying the data.  So
        // there should be 0 entries.
        assertEquals(0, cursor.getCount());
    }

    private void verifySourceAllFieldsV2(SQLiteDatabase db) {
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
            ContentValues sourceV2 = ContentValueFixtures.generateSourceContentValuesV2();
            ContentValues sourceV3 = cursorRowToContentValues(cursor);

            for (String column : sourceV2.keySet()) {
                if (column.equals(MeasurementTables.SourceContract.AGGREGATE_SOURCE)) {
                    // Aggregate source is modified
                    assertEquals(
                            ContentValueFixtures.SourceValues.AGGREGATE_SOURCE_V3,
                            sourceV3.get(column));
                } else {
                    assertEquals(sourceV2.get(column), sourceV3.get(column));
                }
            }

            // The migration added 3 new columns ("debug_reporting", "ad_id_permission", and
            // "ar_debug_permission") to the Source table.  Assert those columns are not populated.
            assertEquals(sourceV2.size() + 3, sourceV3.size());

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.SourceContract.DEBUG_REPORTING));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.SourceContract.DEBUG_REPORTING)));

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.SourceContract.AD_ID_PERMISSION));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.SourceContract.AD_ID_PERMISSION)));

            assertNotEquals(
                    -1,
                    cursor.getColumnIndex(MeasurementTables.SourceContract.AR_DEBUG_PERMISSION));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.SourceContract.AR_DEBUG_PERMISSION)));
        }
    }

    private void verifyTriggerAllFieldsV2(SQLiteDatabase db) {
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
            ContentValues triggerV2 = ContentValueFixtures.generateTriggerContentValuesV2();
            ContentValues triggerV3 = cursorRowToContentValues(cursor);

            for (String column : triggerV2.keySet()) {
                if (column.equals(MeasurementTables.TriggerContract.EVENT_TRIGGERS)) {
                    // Event triggers is modified.
                    assertEquals(
                            ContentValueFixtures.TriggerValues.EVENT_TRIGGERS_V3,
                            triggerV3.get(column));
                } else if (column.equals(
                        MeasurementTables.TriggerContract.AGGREGATE_TRIGGER_DATA)) {
                    // Aggregate trigger data is modified.
                    assertEquals(
                            ContentValueFixtures.TriggerValues.AGGREGATE_TRIGGER_DATA_V3,
                            triggerV3.get(column));
                } else if (column.equals(MeasurementTables.TriggerContract.FILTERS)) {
                    // Filter is modified.
                    assertEquals(
                            ContentValueFixtures.TriggerValues.FILTERS_V3, triggerV3.get(column));
                } else {
                    assertEquals(triggerV2.get(column), triggerV3.get(column));
                }
            }
            // The migration added 4 new columns ("not_filters", "debug_reporting",
            // "ad_id_permission", and "ar_debug_permission") to the Trigger table.  Assert those
            // columns are not populated.
            assertEquals(triggerV2.size() + 4, triggerV3.size());

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.TriggerContract.NOT_FILTERS));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(MeasurementTables.TriggerContract.NOT_FILTERS)));

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.TriggerContract.DEBUG_REPORTING));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.TriggerContract.DEBUG_REPORTING)));

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.TriggerContract.AD_ID_PERMISSION));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.TriggerContract.AD_ID_PERMISSION)));

            assertNotEquals(
                    -1,
                    cursor.getColumnIndex(MeasurementTables.TriggerContract.AR_DEBUG_PERMISSION));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.TriggerContract.AR_DEBUG_PERMISSION)));
        }
    }

    private void verifyAttributionAllFieldsV2(SQLiteDatabase db) {
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
            ContentValues attributionV2 = ContentValueFixtures.generateAttributionContentValuesV2();
            ContentValues attributionV3 = cursorRowToContentValues(cursor);

            for (String column : attributionV2.keySet()) {
                assertEquals(attributionV2.get(column), attributionV3.get(column));
            }

            // The migration added 2 new columns ("source_id" and "trigger_id") to the Attribution
            // table.  Assert those columns are not populated.
            assertEquals(attributionV2.size() + 2, attributionV3.size());

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.AttributionContract.SOURCE_ID));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.AttributionContract.SOURCE_ID)));

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.AttributionContract.TRIGGER_ID));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.AttributionContract.TRIGGER_ID)));
        }
    }

    private void verifyEventReportAllFieldsV2(SQLiteDatabase db) {
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
            ContentValues eventReportV2 = ContentValueFixtures.generateEventReportContentValuesV2();
            ContentValues eventReportV3 = cursorRowToContentValues(cursor);

            for (String column : eventReportV2.keySet()) {
                if (column.equals(MeasurementTables.EventReportContract.SOURCE_ID)) {
                    // The migration renamed the column from "source_id" to "source_event_id"
                    assertEquals(
                            eventReportV2.get(MeasurementTables.EventReportContract.SOURCE_ID),
                            eventReportV3.get(
                                    MeasurementTables.EventReportContract.SOURCE_EVENT_ID));
                } else {
                    assertEquals(eventReportV2.get(column), eventReportV3.get(column));
                }
            }

            // The migration added 3 new columns ("source_id", "trigger_id", and
            // "debug_report_status") to the EventReport table.  Assert those columns are not
            // populated.
            assertEquals(eventReportV2.size() + 3, eventReportV3.size());

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.EventReportContract.SOURCE_ID));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.EventReportContract.SOURCE_ID)));

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.EventReportContract.TRIGGER_ID));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.EventReportContract.TRIGGER_ID)));

            assertNotEquals(
                    -1,
                    cursor.getColumnIndex(
                            MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.EventReportContract.DEBUG_REPORT_STATUS)));
        }
    }

    private void verifyAggregateReportAllFieldsV2(SQLiteDatabase db) {
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
            ContentValues aggregateReportV2 =
                    ContentValueFixtures.generateAggregateReportContentValuesV2();
            ContentValues aggregateReportV3 = cursorRowToContentValues(cursor);

            for (String column : aggregateReportV2.keySet()) {
                assertEquals(aggregateReportV2.get(column), aggregateReportV3.get(column));
            }

            // The migration added 3 new columns ("source_id", "trigger_id", and
            // "debug_report_status") to the Aggregate Report table.  Assert those columns are not
            // populated.
            assertEquals(aggregateReportV2.size() + 3, aggregateReportV3.size());

            assertNotEquals(-1, cursor.getColumnIndex(MeasurementTables.AggregateReport.SOURCE_ID));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(MeasurementTables.AggregateReport.SOURCE_ID)));

            assertNotEquals(
                    -1, cursor.getColumnIndex(MeasurementTables.AggregateReport.TRIGGER_ID));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(MeasurementTables.AggregateReport.TRIGGER_ID)));

            assertNotEquals(
                    -1,
                    cursor.getColumnIndex(MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS));
            assertTrue(
                    cursor.isNull(
                            cursor.getColumnIndex(
                                    MeasurementTables.AggregateReport.DEBUG_REPORT_STATUS)));
        }
    }

    private void verifyAggregateEncryptionKeyAllFieldsV2(SQLiteDatabase db) {
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
            ContentValues aggregateEncryptionKeyV2 =
                    ContentValueFixtures.generateAggregateEncryptionKeyContentValuesV2();
            ContentValues aggregateEncryptionKeyV3 = cursorRowToContentValues(cursor);

            for (String column : aggregateEncryptionKeyV2.keySet()) {
                assertEquals(
                        aggregateEncryptionKeyV2.get(column), aggregateEncryptionKeyV3.get(column));
            }

            // No new columns were added.
            assertEquals(aggregateEncryptionKeyV2.size(), aggregateEncryptionKeyV3.size());
        }
    }
}
