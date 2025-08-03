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

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.KeyValueData;

/**
 * Migrates Measurement DB to version 26. This upgrade adds two columns in the Debug Report table to
 * record Insertion Time and App Registrant and Clears Retry Counts for Event and Aggregate Reports
 */
public class MeasurementDbMigratorV26 extends AbstractMeasurementDbMigrator {

    private void addRegistrantColumn(@androidx.annotation.NonNull SQLiteDatabase db) {
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.DebugReportContract.TABLE,
                MeasurementTables.DebugReportContract.REGISTRANT);
    }

    private void addInsertionTimeColumn(@androidx.annotation.NonNull SQLiteDatabase db) {
        MigrationHelpers.addIntColumnsIfAbsent(
                db,
                MeasurementTables.DebugReportContract.TABLE,
                new String[] {MeasurementTables.DebugReportContract.INSERTION_TIME});
    }

    private void insertInsertionTime(@NonNull SQLiteDatabase db) {
        ContentValues cv = new ContentValues();
        cv.put(MeasurementTables.DebugReportContract.INSERTION_TIME, System.currentTimeMillis());
        db.update(
                MeasurementTables.DebugReportContract.TABLE,
                cv,
                MeasurementTables.DebugReportContract.INSERTION_TIME + " IS NULL",
                null);
    }

    private void clearReportRetryCounts(SQLiteDatabase db) {
        db.delete(
                MeasurementTables.KeyValueDataContract.TABLE,
                MeasurementTables.KeyValueDataContract.DATA_TYPE
                        + " IN (\""
                        + KeyValueData.DataType.AGGREGATE_REPORT_RETRY_COUNT
                        + "\", \""
                        + KeyValueData.DataType.EVENT_REPORT_RETRY_COUNT
                        + "\")",
                new String[] {});
    }

    public MeasurementDbMigratorV26() {
        super(26);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        addInsertionTimeColumn(db);
        addRegistrantColumn(db);
        insertInsertionTime(db);
        clearReportRetryCounts(db);
    }
}
