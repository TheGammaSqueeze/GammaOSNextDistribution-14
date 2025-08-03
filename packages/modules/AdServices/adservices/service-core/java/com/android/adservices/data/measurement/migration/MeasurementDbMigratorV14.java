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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.enrollment.EnrollmentData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Migrates Measurement DB to version 14 by performing following steps - 1) Add registration_origin
 * column to msmt_source, msmt_trigger, msmt_event_report and msmt_aggregate_report tables 2) Insert
 * values for registration_origin by copying attribution reporting origin url from enrollment table
 * as registration_origin in destination table by matching enrollment_id
 */
public class MeasurementDbMigratorV14 extends AbstractMeasurementDbMigrator {

    private static final String ID_COLUMN = "_id";
    private static final String ENROLLMENT_ID_COLUMN = "enrollment_id";
    private static final String REGISTRATION_ORIGIN_COLUMN = "registration_origin";
    private final DbHelper mDbHelper;

    public MeasurementDbMigratorV14(DbHelper dbHelper) {
        super(14);
        mDbHelper = dbHelper;
    }

    List<String> mMigrationTables =
            Arrays.asList(
                    MeasurementTables.SourceContract.TABLE,
                    MeasurementTables.TriggerContract.TABLE,
                    MeasurementTables.EventReportContract.TABLE,
                    MeasurementTables.AggregateReport.TABLE);

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        addRegistrationColumn(db);
        Map<String, Uri> enrollmentToReportingOrigin = getEnrollmentToReportingOrigin();

        for (String table : mMigrationTables) {
            insertRegistrationOriginOrDelete(db, table, enrollmentToReportingOrigin);
        }
    }

    private void addRegistrationColumn(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.REGISTRATION_ORIGIN);
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.REGISTRATION_ORIGIN);
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.REGISTRATION_ORIGIN);
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.REGISTRATION_ORIGIN);
    }

    private Map<String, Uri> getEnrollmentToReportingOrigin() {
        Map<String, Uri> enrollmentIdToReportingUrl = new HashMap<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        try (Cursor cursor =
                db.query(
                        /*table=*/ EnrollmentTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ new String[] {
                            EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID,
                            EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL
                        },
                        /*selection*/ EnrollmentTables.EnrollmentDataContract
                                        .ATTRIBUTION_REPORTING_URL
                                + " IS NOT NULL",
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "Failed to find any enrollments with non-empty attribution"
                                        + " reporting url");
                return enrollmentIdToReportingUrl;
            }

            while (cursor.moveToNext()) {
                String enrollmentId =
                        cursor.getString(
                                cursor.getColumnIndex(
                                        EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID));
                String attributionReportingColumnValue =
                        cursor.getString(
                                cursor.getColumnIndex(
                                        EnrollmentTables.EnrollmentDataContract
                                                .ATTRIBUTION_REPORTING_URL));
                List<String> reportingUrls =
                        EnrollmentData.splitEnrollmentInputToList(attributionReportingColumnValue);
                if (reportingUrls.isEmpty()) {
                    continue;
                }
                Optional<Uri> reportingOrigin =
                        WebAddresses.originAndScheme(Uri.parse(reportingUrls.get(0)));
                reportingOrigin.ifPresent(
                        uri -> enrollmentIdToReportingUrl.putIfAbsent(enrollmentId, uri));
            }
            return enrollmentIdToReportingUrl;
        }
    }

    /**
     * The method inserts registration_origin to source, trigger, event report and aggregate report
     * tables registration_origin is copied from enrollmentIdToReportingUrl map by matching
     * enrollmentId. If reportingUrl does not exist for a given record's enrollmentId this method
     * will delete the record.
     */
    private void insertRegistrationOriginOrDelete(
            @NonNull SQLiteDatabase db, String table, Map<String, Uri> enrollmentIdToReportingUrl) {
        try (Cursor cursor =
                db.query(
                        /*table=*/ table,
                        /*columns=*/ new String[] {ID_COLUMN, ENROLLMENT_ID_COLUMN},
                        /*selection*/ null,
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {

            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndex(ID_COLUMN));
                String enrollmentId = cursor.getString(cursor.getColumnIndex(ENROLLMENT_ID_COLUMN));
                Uri reportingUri = enrollmentIdToReportingUrl.get(enrollmentId);
                if (reportingUri == null) {
                    // no reporting origin found. delete the data
                    LoggerFactory.getMeasurementLogger()
                            .d("Reporting origin not found for enrollment id - " + enrollmentId);

                    deleteRecord(db, table, id);

                } else {
                    ContentValues values = new ContentValues();
                    // use reporting origin from enrollment table as registration_origin
                    values.put(REGISTRATION_ORIGIN_COLUMN, reportingUri.toString());
                    long rows = db.update(table, values, ID_COLUMN + " = ? ", new String[] {id});
                    if (rows != 1) {
                        LoggerFactory.getMeasurementLogger()
                                .d(
                                        "Failed to insert registration_origin for id "
                                                + id
                                                + " in table "
                                                + table);
                        deleteRecord(db, table, id);
                    }
                }
            }
        }
    }

    private void deleteRecord(SQLiteDatabase db, String table, String recordId) {
        LoggerFactory.getMeasurementLogger()
                .d("Deleting record with id - " + recordId + " from table - " + table);
        db.delete(table, ID_COLUMN + " = ? ", new String[] {recordId});
    }
}
