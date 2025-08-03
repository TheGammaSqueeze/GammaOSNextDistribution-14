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
public class MeasurementDbMigratorV15Test extends MeasurementDbMigratorTestBase {
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
    public void performMigration_v14ToV15WithData_maintainsDataIntegrity() {
        populateEnrollment(ENROLLMENT_ID_1, REGISTRATION_URL_1);
        populateEnrollment(ENROLLMENT_ID_2, REGISTRATION_URL_2);
        populateEnrollment(ENROLLMENT_ID_3, REGISTRATION_URL_3);

        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        14,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String sourceId = UUID.randomUUID().toString();
        Map<String, List<ContentValues>> fakeSourcesAndTriggers =
                createFakeDataSourceAndTriggerV14(sourceId);
        populateDb(db, fakeSourcesAndTriggers);

        // Debug Report Table
        Map<String, List<ContentValues>> fakeDebugReports = new LinkedHashMap<>();
        List<ContentValues> debugReportRows = new ArrayList<>();
        debugReportRows.add(buildDebugReportV14(UUID.randomUUID().toString(), ENROLLMENT_ID_1));
        debugReportRows.add(buildDebugReportV14(null, ENROLLMENT_ID_2));
        fakeDebugReports.put(MeasurementTables.DebugReportContract.TABLE, debugReportRows);
        populateDb(db, fakeDebugReports);
        getTestSubject().performMigration(db, 14, 15);
        MigrationTestHelper.verifyDataInDb(db, fakeDebugReports);
        verifyRegistrationOrigin(db, Arrays.asList(REGISTRATION_URL_1, REGISTRATION_URL_2));
        deleteEnrollments();
    }

    @Test
    public void performMigration_whenEnrollmentIdNotFound_removesDebugReport() {
        populateEnrollment(ENROLLMENT_ID_1, REGISTRATION_URL_1);
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        14,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        List<ContentValues> reportRows = new ArrayList<>();
        String id1 = UUID.randomUUID().toString();
        reportRows.add(buildDebugReportV14(id1, ENROLLMENT_ID_1));
        reportRows.add(buildDebugReportV14(UUID.randomUUID().toString(), ENROLLMENT_ID_3));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.DebugReportContract.TABLE, reportRows);
        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 14, 15);

        try (Cursor cursor =
                db.query(
                        MeasurementTables.DebugReportContract.TABLE,
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

            // debug report with ENROLLMENT_ID_3 should be removed
            assertEquals(1, cursor.getCount());
            cursor.moveToNext();
            assertEquals(
                    id1,
                    cursor.getString(
                            cursor.getColumnIndex(MeasurementTables.DebugReportContract.ID)));
            assertEquals(
                    ENROLLMENT_ID_1,
                    cursor.getString(
                            cursor.getColumnIndex(
                                    MeasurementTables.DebugReportContract.ENROLLMENT_ID)));
            assertEquals(
                    REGISTRATION_URL_1,
                    cursor.getString(cursor.getColumnIndex(REGISTRATION_ORIGIN_COL)));
        }
        deleteEnrollments();
    }

    @Test
    public void performMigration_whenReportingUrlNotFoundOrEmpty_removesDebugReport() {
        populateEnrollment(ENROLLMENT_ID_1, null);
        populateEnrollment(ENROLLMENT_ID_3, "");
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        14,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        List<ContentValues> reportRows = new ArrayList<>();
        reportRows.add(buildDebugReportV14(UUID.randomUUID().toString(), ENROLLMENT_ID_1));
        reportRows.add(buildDebugReportV14(UUID.randomUUID().toString(), ENROLLMENT_ID_3));
        Map<String, List<ContentValues>> fakeData =
                Map.of(MeasurementTables.DebugReportContract.TABLE, reportRows);
        populateDb(db, fakeData);
        getTestSubject().performMigration(db, 14, 15);

        try (Cursor cursor =
                db.query(
                        MeasurementTables.DebugReportContract.TABLE,
                        new String[] {
                            MeasurementTables.DebugReportContract.ID,
                            MeasurementTables.DebugReportContract.ENROLLMENT_ID,
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

    private Map<String, List<ContentValues>> createFakeDataSourceAndTriggerV14(String source1Id) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();
        // Source Table
        List<ContentValues> sourceRows = new ArrayList<>();
        sourceRows.add(buildSourceV14(source1Id, ENROLLMENT_ID_1));
        sourceRows.add(buildSourceV14(null, ENROLLMENT_ID_2));
        tableRowsMap.put(MeasurementTables.SourceContract.TABLE, sourceRows);

        // Trigger Table
        List<ContentValues> triggerRows = new ArrayList<>();
        triggerRows.add(buildTriggerV14(UUID.randomUUID().toString(), ENROLLMENT_ID_1));
        triggerRows.add(buildTriggerV14(null, ENROLLMENT_ID_3));
        tableRowsMap.put(MeasurementTables.TriggerContract.TABLE, triggerRows);
        return tableRowsMap;
    }

    private ContentValues buildSourceV14(String sourceId, String enrollmentId) {
        ContentValues source = ContentValueFixtures.generateSourceContentValuesV14();
        if (sourceId != null) {
            source.put(MeasurementTables.SourceContract.ID, sourceId);
        }
        if (enrollmentId != null) {
            source.put(MeasurementTables.SourceContract.ENROLLMENT_ID, enrollmentId);
        }
        return source;
    }

    private ContentValues buildTriggerV14(String triggerId, String enrollmentId) {
        ContentValues trigger = ContentValueFixtures.generateTriggerContentValuesV14();
        if (triggerId != null) {
            trigger.put(MeasurementTables.TriggerContract.ID, triggerId);
        }
        if (enrollmentId != null) {
            trigger.put(MeasurementTables.TriggerContract.ENROLLMENT_ID, enrollmentId);
        }
        return trigger;
    }

    private ContentValues buildDebugReportV14(String reportId, String enrollmentId) {
        ContentValues report = ContentValueFixtures.generateDebugReportContentValuesV14();
        if (reportId != null) {
            report.put(MeasurementTables.DebugReportContract.ID, reportId);
        }
        if (enrollmentId != null) {
            report.put(MeasurementTables.DebugReportContract.ENROLLMENT_ID, enrollmentId);
        }
        return report;
    }

    private void verifyRegistrationOrigin(
            SQLiteDatabase db, List<String> expectedRegistrationOrigins) {
        try (Cursor cursor =
                db.query(
                        MeasurementTables.DebugReportContract.TABLE,
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
        return 15;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV15(getDbHelperForTest());
    }
}
