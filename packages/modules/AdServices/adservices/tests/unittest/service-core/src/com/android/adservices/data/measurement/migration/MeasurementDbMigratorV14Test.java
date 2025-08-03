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

import com.android.adservices.common.WebUtil;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV14Test extends MeasurementDbMigratorTestBase {

    private static final String ENROLLMENT_ID_1 = "enrollment_id_1";
    private static final String REGISTRATION_URL_1 =
            WebUtil.validUrl("https://subdomain.example1.test");

    private static final String ENROLLMENT_ID_2 = "enrollment_id_12";
    private static final String REGISTRATION_URL_2 =
            WebUtil.validUrl("https://subdomain1.example2.test");

    private static final String ENROLLMENT_ID_3 = "enrollment_id_13";
    private static final String REGISTRATION_URL_3 =
            WebUtil.validUrl("https://subdomain2.example2.test");

    private static final String REGISTRATION_ORIGIN_COL = "registration_origin";

    @Test
    public void performMigration_v13ToV14WithData_maintainsDataIntegrity() {
        populateEnrollment(ENROLLMENT_ID_1, REGISTRATION_URL_1);
        populateEnrollment(ENROLLMENT_ID_2, REGISTRATION_URL_2);
        populateEnrollment(ENROLLMENT_ID_3, REGISTRATION_URL_3);
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        13,
                        getDbHelperForTest());

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String sourceId = UUID.randomUUID().toString();
        Map<String, List<ContentValues>> fakeSourcesAndTriggers =
                createFakeDataSourceAndTriggerV13(sourceId);
        populateDb(db, fakeSourcesAndTriggers);
        Map<String, List<ContentValues>> fakeReports = createFakeDataV13();
        populateDb(db, fakeReports);
        getTestSubject().performMigration(db, 13, 14);
        MigrationTestHelper.verifyDataInDb(db, fakeReports);

        verifyRegistrationOrigin(
                db,
                MeasurementTables.SourceContract.TABLE,
                Arrays.asList(REGISTRATION_URL_1, REGISTRATION_URL_2));
        verifyRegistrationOrigin(
                db,
                MeasurementTables.TriggerContract.TABLE,
                Arrays.asList(REGISTRATION_URL_1, REGISTRATION_URL_3));
        verifyRegistrationOrigin(
                db,
                MeasurementTables.EventReportContract.TABLE,
                Arrays.asList(REGISTRATION_URL_1, REGISTRATION_URL_2));
        verifyRegistrationOrigin(
                db,
                MeasurementTables.AggregateReport.TABLE,
                Arrays.asList(REGISTRATION_URL_1, REGISTRATION_URL_3));
        deleteEnrollments();
    }

    @Test
    public void performMigration_enrollmentIdNotFound_removesSource() {
        populateEnrollment(ENROLLMENT_ID_1, REGISTRATION_URL_1);
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        13,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        List<ContentValues> sourceRows = new ArrayList<>();
        String id1 = UUID.randomUUID().toString();
        sourceRows.add(buildSourceV13(id1, ENROLLMENT_ID_1));
        sourceRows.add(buildSourceV13(UUID.randomUUID().toString(), ENROLLMENT_ID_3));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.SourceContract.TABLE, sourceRows);
        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 13, 14);

        try (Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {
                            MeasurementTables.SourceContract.ID,
                            MeasurementTables.SourceContract.ENROLLMENT_ID,
                            REGISTRATION_ORIGIN_COL
                        },
                        null,
                        null,
                        null,
                        null,
                        null)) {

            // source with ENROLLMENT_ID_3 should be removed
            assertEquals(1, cursor.getCount());
            cursor.moveToNext();
            assertEquals(
                    id1,
                    cursor.getString(cursor.getColumnIndex(MeasurementTables.SourceContract.ID)));
            assertEquals(
                    ENROLLMENT_ID_1,
                    cursor.getString(
                            cursor.getColumnIndex(MeasurementTables.SourceContract.ENROLLMENT_ID)));
            assertEquals(
                    REGISTRATION_URL_1,
                    cursor.getString(cursor.getColumnIndex(REGISTRATION_ORIGIN_COL)));
        }
        deleteEnrollments();
    }

    @Test
    public void performMigration_whenReportingUrlNotFoundOrEmpty_removesSource() {
        populateEnrollment(ENROLLMENT_ID_1, null);
        populateEnrollment(ENROLLMENT_ID_3, "");
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        13,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        List<ContentValues> sourceRows = new ArrayList<>();
        sourceRows.add(buildSourceV13(UUID.randomUUID().toString(), ENROLLMENT_ID_1));
        sourceRows.add(buildSourceV13(UUID.randomUUID().toString(), ENROLLMENT_ID_3));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.SourceContract.TABLE, sourceRows);
        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 13, 14);

        try (Cursor cursor =
                db.query(
                        MeasurementTables.SourceContract.TABLE,
                        new String[] {
                            MeasurementTables.SourceContract.ID,
                            MeasurementTables.SourceContract.ENROLLMENT_ID,
                            REGISTRATION_ORIGIN_COL
                        },
                        null,
                        null,
                        null,
                        null,
                        null)) {

            assertEquals(0, cursor.getCount());
            deleteEnrollments();
        }
    }

    @Test
    public void performMigration_whenEnrollmentIdNotFound_removesTrigger() {
        populateEnrollment(ENROLLMENT_ID_1, REGISTRATION_URL_1);
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        13,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        List<ContentValues> triggerRows = new ArrayList<>();
        String id1 = UUID.randomUUID().toString();
        triggerRows.add(buildTriggerV13(id1, ENROLLMENT_ID_1));
        triggerRows.add(buildTriggerV13(UUID.randomUUID().toString(), ENROLLMENT_ID_3));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.TriggerContract.TABLE, triggerRows);
        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 13, 14);

        try (Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        new String[] {
                            MeasurementTables.TriggerContract.ID,
                            MeasurementTables.TriggerContract.ENROLLMENT_ID,
                            REGISTRATION_ORIGIN_COL
                        },
                        null,
                        null,
                        null,
                        null,
                        null)) {

            // trigger with ENROLLMENT_ID_3 should be removed
            assertEquals(1, cursor.getCount());
            cursor.moveToNext();
            assertEquals(
                    id1,
                    cursor.getString(cursor.getColumnIndex(MeasurementTables.TriggerContract.ID)));
            assertEquals(
                    ENROLLMENT_ID_1,
                    cursor.getString(
                            cursor.getColumnIndex(
                                    MeasurementTables.TriggerContract.ENROLLMENT_ID)));
            assertEquals(
                    REGISTRATION_URL_1,
                    cursor.getString(cursor.getColumnIndex(REGISTRATION_ORIGIN_COL)));
        }
        deleteEnrollments();
    }

    @Test
    public void performMigration_whenReportingUrlNotFoundOrEmpty_removesTrigger() {
        populateEnrollment(ENROLLMENT_ID_1, null);
        populateEnrollment(ENROLLMENT_ID_3, "");
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        13,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        List<ContentValues> triggerRows = new ArrayList<>();
        triggerRows.add(buildTriggerV13(UUID.randomUUID().toString(), ENROLLMENT_ID_1));
        triggerRows.add(buildTriggerV13(UUID.randomUUID().toString(), ENROLLMENT_ID_3));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.TriggerContract.TABLE, triggerRows);
        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 13, 14);

        try (Cursor cursor =
                db.query(
                        MeasurementTables.TriggerContract.TABLE,
                        new String[] {
                            MeasurementTables.TriggerContract.ID,
                            MeasurementTables.TriggerContract.ENROLLMENT_ID,
                            REGISTRATION_ORIGIN_COL
                        },
                        null,
                        null,
                        null,
                        null,
                        null)) {

            assertEquals(0, cursor.getCount());
            deleteEnrollments();
        }
    }

    @Test
    public void performMigration_whenEnrollmentIdNotFound_removesEventReport() {
        populateEnrollment(ENROLLMENT_ID_1, REGISTRATION_URL_1);
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        13,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        sourceRows.add(buildSourceV13(null, ENROLLMENT_ID_1));
        Map<String, List<ContentValues>> fakeSources =
                Map.of(MeasurementTables.SourceContract.TABLE, sourceRows);
        populateDb(db, fakeSources);
        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        triggerRows.add(buildTriggerV13(null, ENROLLMENT_ID_1));
        Map<String, List<ContentValues>> fakeTriggers =
                Map.of(MeasurementTables.TriggerContract.TABLE, triggerRows);
        populateDb(db, fakeTriggers);

        List<ContentValues> reportRows = new ArrayList<>();
        String id1 = UUID.randomUUID().toString();
        reportRows.add(buildEventReportV13(id1, ENROLLMENT_ID_1));
        reportRows.add(buildEventReportV13(UUID.randomUUID().toString(), ENROLLMENT_ID_3));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.EventReportContract.TABLE, reportRows);
        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 13, 14);

        try (Cursor cursor =
                db.query(
                        MeasurementTables.EventReportContract.TABLE,
                        new String[] {
                            MeasurementTables.EventReportContract.ID,
                            MeasurementTables.EventReportContract.ENROLLMENT_ID,
                            REGISTRATION_ORIGIN_COL
                        },
                        null,
                        null,
                        null,
                        null,
                        null)) {

            // event report with ENROLLMENT_ID_3 should be removed
            assertEquals(1, cursor.getCount());
            cursor.moveToNext();
            assertEquals(
                    id1,
                    cursor.getString(
                            cursor.getColumnIndex(MeasurementTables.EventReportContract.ID)));
            assertEquals(
                    ENROLLMENT_ID_1,
                    cursor.getString(
                            cursor.getColumnIndex(
                                    MeasurementTables.EventReportContract.ENROLLMENT_ID)));
            assertEquals(
                    REGISTRATION_URL_1,
                    cursor.getString(cursor.getColumnIndex(REGISTRATION_ORIGIN_COL)));
        }
        deleteEnrollments();
    }

    @Test
    public void performMigration_whenReportingUrlNotFoundOrEmpty_removesEventReport() {
        populateEnrollment(ENROLLMENT_ID_1, null);
        populateEnrollment(ENROLLMENT_ID_3, "");
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        13,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        sourceRows.add(buildSourceV13(null, ENROLLMENT_ID_1));
        Map<String, List<ContentValues>> fakeSources =
                Map.of(MeasurementTables.SourceContract.TABLE, sourceRows);
        populateDb(db, fakeSources);
        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        triggerRows.add(buildTriggerV13(null, ENROLLMENT_ID_1));
        Map<String, List<ContentValues>> fakeTriggers =
                Map.of(MeasurementTables.TriggerContract.TABLE, triggerRows);
        populateDb(db, fakeTriggers);

        List<ContentValues> reportRows = new ArrayList<>();
        reportRows.add(buildEventReportV13(UUID.randomUUID().toString(), ENROLLMENT_ID_1));
        reportRows.add(buildEventReportV13(UUID.randomUUID().toString(), ENROLLMENT_ID_3));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.EventReportContract.TABLE, reportRows);
        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 13, 14);

        try (Cursor cursor =
                db.query(
                        MeasurementTables.EventReportContract.TABLE,
                        new String[] {
                            MeasurementTables.EventReportContract.ID,
                            MeasurementTables.EventReportContract.ENROLLMENT_ID,
                            REGISTRATION_ORIGIN_COL
                        },
                        null,
                        null,
                        null,
                        null,
                        null)) {

            assertEquals(0, cursor.getCount());
            deleteEnrollments();
        }
    }

    @Test
    public void performMigration_whenEnrollmentIdNotFound_removesAggregateReport() {
        populateEnrollment(ENROLLMENT_ID_1, REGISTRATION_URL_1);
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        13,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        sourceRows.add(buildSourceV13(null, ENROLLMENT_ID_1));
        Map<String, List<ContentValues>> fakeSources =
                Map.of(MeasurementTables.SourceContract.TABLE, sourceRows);
        populateDb(db, fakeSources);
        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        triggerRows.add(buildTriggerV13(null, ENROLLMENT_ID_1));
        Map<String, List<ContentValues>> fakeTriggers =
                Map.of(MeasurementTables.TriggerContract.TABLE, triggerRows);
        populateDb(db, fakeTriggers);

        List<ContentValues> reportRows = new ArrayList<>();
        String id1 = UUID.randomUUID().toString();
        reportRows.add(buildAggregateReportV13(id1, ENROLLMENT_ID_1));
        reportRows.add(buildAggregateReportV13(UUID.randomUUID().toString(), ENROLLMENT_ID_3));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.AggregateReport.TABLE, reportRows);
        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 13, 14);

        try (Cursor cursor =
                db.query(
                        MeasurementTables.AggregateReport.TABLE,
                        new String[] {
                            MeasurementTables.AggregateReport.ID,
                            MeasurementTables.AggregateReport.ENROLLMENT_ID,
                            REGISTRATION_ORIGIN_COL
                        },
                        null,
                        null,
                        null,
                        null,
                        null)) {

            // aggregate report with ENROLLMENT_ID_3 should be removed
            assertEquals(1, cursor.getCount());
            cursor.moveToNext();
            assertEquals(
                    id1,
                    cursor.getString(cursor.getColumnIndex(MeasurementTables.AggregateReport.ID)));
            assertEquals(
                    ENROLLMENT_ID_1,
                    cursor.getString(
                            cursor.getColumnIndex(
                                    MeasurementTables.AggregateReport.ENROLLMENT_ID)));
            assertEquals(
                    REGISTRATION_URL_1,
                    cursor.getString(cursor.getColumnIndex(REGISTRATION_ORIGIN_COL)));
        }
        deleteEnrollments();
    }

    @Test
    public void performMigration_whenReportingUrlNotFoundOrEmpty_removesAggregateReport() {
        populateEnrollment(ENROLLMENT_ID_1, null);
        populateEnrollment(ENROLLMENT_ID_3, "");
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        13,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        sourceRows.add(buildSourceV13(null, ENROLLMENT_ID_1));
        Map<String, List<ContentValues>> fakeSources =
                Map.of(MeasurementTables.SourceContract.TABLE, sourceRows);
        populateDb(db, fakeSources);
        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        triggerRows.add(buildTriggerV13(null, ENROLLMENT_ID_1));
        Map<String, List<ContentValues>> fakeTriggers =
                Map.of(MeasurementTables.TriggerContract.TABLE, triggerRows);
        populateDb(db, fakeTriggers);

        List<ContentValues> reportRows = new ArrayList<>();
        reportRows.add(buildAggregateReportV13(UUID.randomUUID().toString(), ENROLLMENT_ID_1));
        reportRows.add(buildAggregateReportV13(UUID.randomUUID().toString(), ENROLLMENT_ID_3));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.AggregateReport.TABLE, reportRows);
        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 13, 14);

        try (Cursor cursor =
                db.query(
                        MeasurementTables.AggregateReport.TABLE,
                        new String[] {
                            MeasurementTables.AggregateReport.ID,
                            MeasurementTables.AggregateReport.ENROLLMENT_ID,
                            REGISTRATION_ORIGIN_COL
                        },
                        null,
                        null,
                        null,
                        null,
                        null)) {

            assertEquals(0, cursor.getCount());
            deleteEnrollments();
        }
    }

    private void populateEnrollment(String enrollmentId, String attributionUrl) {
        DbHelper testHelper = getDbHelperForTest();
        SQLiteDatabase db = testHelper.safeGetWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID, enrollmentId);
        values.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL, attributionUrl);
        db.insertWithOnConflict(
                EnrollmentTables.EnrollmentDataContract.TABLE,
                /*nullColumnHack=*/ null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void deleteEnrollments() {
        DbHelper testHelper = getDbHelperForTest();
        SQLiteDatabase db = testHelper.safeGetWritableDatabase();
        db.delete(EnrollmentTables.EnrollmentDataContract.TABLE, null, null);
    }

    private Map<String, List<ContentValues>> createFakeDataSourceAndTriggerV13(String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        sourceRows.add(buildSourceV13(source1Id, ENROLLMENT_ID_1));
        sourceRows.add(buildSourceV13(null, ENROLLMENT_ID_2));
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);

        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        triggerRows.add(buildTriggerV13(UUID.randomUUID().toString(), ENROLLMENT_ID_1));
        triggerRows.add(buildTriggerV13(null, ENROLLMENT_ID_3));
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);
        return tableRowsMap;
    }

    private Map<String, List<ContentValues>> createFakeDataV13() {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        // Event Report Table
        List<ContentValues> eventReportRows = new ArrayList<>();
        eventReportRows.add(buildEventReportV13(UUID.randomUUID().toString(), ENROLLMENT_ID_1));
        eventReportRows.add(buildEventReportV13(null, ENROLLMENT_ID_2));
        tableRowsMap.put(MeasurementTables.EventReportContract.TABLE, eventReportRows);

        // Aggregate Report Table
        List<ContentValues> aggregateReportRows = new ArrayList<>();
        aggregateReportRows.add(
                buildAggregateReportV13(UUID.randomUUID().toString(), ENROLLMENT_ID_1));
        aggregateReportRows.add(buildAggregateReportV13(null, ENROLLMENT_ID_3));
        tableRowsMap.put(MeasurementTables.AggregateReport.TABLE, aggregateReportRows);

        return tableRowsMap;
    }

    private ContentValues buildSourceV13(String sourceId, String enrollmentId) {
        ContentValues source = ContentValueFixtures.generateSourceContentValuesV13();
        if (sourceId != null) {
            source.put(MeasurementTables.SourceContract.ID, sourceId);
        }
        if (enrollmentId != null) {
            source.put(MeasurementTables.SourceContract.ENROLLMENT_ID, enrollmentId);
        }
        return source;
    }

    private ContentValues buildTriggerV13(String triggerId, String enrollmentId) {
        ContentValues trigger = ContentValueFixtures.generateTriggerContentValuesV13();
        if (triggerId != null) {
            trigger.put(MeasurementTables.TriggerContract.ID, triggerId);
        }
        if (enrollmentId != null) {
            trigger.put(MeasurementTables.TriggerContract.ENROLLMENT_ID, enrollmentId);
        }
        return trigger;
    }

    private ContentValues buildEventReportV13(String reportId, String enrollmentId) {
        ContentValues report = ContentValueFixtures.generateEventReportContentValuesV13();
        if (reportId != null) {
            report.put(MeasurementTables.EventReportContract.ID, reportId);
        }
        if (enrollmentId != null) {
            report.put(MeasurementTables.EventReportContract.ENROLLMENT_ID, enrollmentId);
        }
        return report;
    }

    private ContentValues buildAggregateReportV13(String reportId, String enrollmentId) {
        ContentValues report = ContentValueFixtures.generateAggregateReportContentValuesV13();
        if (reportId != null) {
            report.put(MeasurementTables.AggregateReport.ID, reportId);
        }
        if (enrollmentId != null) {
            report.put(MeasurementTables.AggregateReport.ENROLLMENT_ID, enrollmentId);
        }
        return report;
    }

    private void verifyRegistrationOrigin(
            SQLiteDatabase db, String table, List<String> expectedRegistrationOrigins) {
        try (Cursor cursor =
                db.query(
                        table,
                        new String[] {REGISTRATION_ORIGIN_COL},
                        null,
                        null,
                        null,
                        null,
                        null)) {
            assertEquals(2, cursor.getCount());
            while (cursor.moveToNext()) {
                String registrationOrigin =
                        cursor.getString(cursor.getColumnIndex(REGISTRATION_ORIGIN_COL));
                assertTrue(expectedRegistrationOrigins.contains(registrationOrigin));
            }
        }
    }

    @Override
    int getTargetVersion() {
        return 14;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV14(getDbHelperForTest());
    }
}
