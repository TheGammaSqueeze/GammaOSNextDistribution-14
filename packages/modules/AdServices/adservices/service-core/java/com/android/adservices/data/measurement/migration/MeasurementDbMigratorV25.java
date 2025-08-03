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
import com.android.adservices.data.measurement.MeasurementTables;

import java.util.HashMap;
import java.util.Map;

/**
 * Migrates Measurement DB to version 25 by performing following steps - 1) Add registration_origin
 * column to msmt_attribution table 2) Insert values for registration_origin by copying
 * registration_origin from msmt_source table as registration_origin in attribution table by
 * matching sourceId
 */
public class MeasurementDbMigratorV25 extends AbstractMeasurementDbMigrator {

    public MeasurementDbMigratorV25() {
        super(25);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        addRegistrationColumn(db);
        Map<String, Uri> sourceIdToReportingOrigin = getSourceIdToReportingOrigin(db);
        insertRegistrationOriginOrDelete(db, sourceIdToReportingOrigin);
    }

    private void addRegistrationColumn(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.REGISTRATION_ORIGIN);
    }

    private Map<String, Uri> getSourceIdToReportingOrigin(@NonNull SQLiteDatabase db) {
        Map<String, Uri> sourceIdToRegistrationUrl = new HashMap<>();
        try (Cursor cursor =
                db.query(
                        /*table=*/ MeasurementTables.SourceContract.TABLE,
                        /*columns=*/ new String[] {
                            MeasurementTables.SourceContract.ID,
                            MeasurementTables.SourceContract.REGISTRATION_ORIGIN
                        },
                        /*selection*/ MeasurementTables.SourceContract.REGISTRATION_ORIGIN
                                + " IS NOT NULL",
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                LoggerFactory.getMeasurementLogger()
                        .d("Failed to find any sources with non-empty registration urls");
                return sourceIdToRegistrationUrl;
            }

            while (cursor.moveToNext()) {
                String sourceId =
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.SourceContract.ID));
                String registrationOrigin =
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.SourceContract.REGISTRATION_ORIGIN));
                sourceIdToRegistrationUrl.put(sourceId, Uri.parse(registrationOrigin));
            }
            return sourceIdToRegistrationUrl;
        }
    }

    /**
     * The method inserts registration_origin to attribution table. registration_origin is copied
     * from sourceIdToReportingUrl map by matching sourceId. If reportingUrl does not exist for a
     * given record's sourceId this method will delete the record.
     */
    private void insertRegistrationOriginOrDelete(
            @NonNull SQLiteDatabase db, Map<String, Uri> sourceIdToReportingUrl) {
        try (Cursor cursor =
                db.query(
                        /*table=*/ MeasurementTables.AttributionContract.TABLE,
                        /*columns=*/ new String[] {
                            MeasurementTables.AttributionContract.ID,
                            MeasurementTables.AttributionContract.SOURCE_ID
                        },
                        /*selection*/ null,
                        /*selectionArgs=*/ null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {

            while (cursor.moveToNext()) {
                String id =
                        cursor.getString(
                                cursor.getColumnIndex(MeasurementTables.AttributionContract.ID));
                String sourceId =
                        cursor.getString(
                                cursor.getColumnIndex(
                                        MeasurementTables.AttributionContract.SOURCE_ID));
                Uri reportingUri = sourceIdToReportingUrl.get(sourceId);
                if (reportingUri == null) {
                    // source id not found in source table. delete the record from attribution
                    // table
                    LoggerFactory.getMeasurementLogger()
                            .d("Reporting origin not found for source id - " + sourceId);
                    deleteRecordFromAttributionTable(db, id);

                } else {
                    ContentValues values = new ContentValues();
                    // use reporting origin from source table as registration_origin
                    values.put(
                            MeasurementTables.AttributionContract.REGISTRATION_ORIGIN,
                            reportingUri.toString());
                    long rows =
                            db.update(
                                    MeasurementTables.AttributionContract.TABLE,
                                    values,
                                    MeasurementTables.AttributionContract.ID + " = ? ",
                                    new String[] {id});
                    if (rows != 1) {
                        LoggerFactory.getMeasurementLogger()
                                .d(
                                        "Failed to insert registration_origin for id "
                                                + id
                                                + " in table "
                                                + MeasurementTables.AttributionContract.TABLE);
                        deleteRecordFromAttributionTable(db, id);
                    }
                }
            }
        }
    }

    private void deleteRecordFromAttributionTable(SQLiteDatabase db, String recordId) {
        LoggerFactory.getMeasurementLogger()
                .d(
                        "Deleting record with id - "
                                + recordId
                                + " from table - "
                                + MeasurementTables.AttributionContract.TABLE);
        db.delete(
                MeasurementTables.AttributionContract.TABLE,
                MeasurementTables.AttributionContract.ID + " = ? ",
                new String[] {recordId});
    }
}
