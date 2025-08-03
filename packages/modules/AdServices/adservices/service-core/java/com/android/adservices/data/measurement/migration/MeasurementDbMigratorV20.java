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
import com.android.adservices.service.Flags;

/**
 * Migrates Measurement DB to version 20. This upgrade adds column 'aggregation_coordinator_origin'
 * to Trigger, AggregateReport & Aggregate Encryption Key Table.
 */
public class MeasurementDbMigratorV20 extends AbstractMeasurementDbMigrator {

    private static String sDefaultCoordinatorOrigin =
            "https://publickeyservice.aws.privacysandboxservices.com";
    private static final String[] ALTER_STATEMENTS = {
            String.format(
                    "ALTER TABLE %1$s ADD %2$s TEXT",
                    MeasurementTables.AggregateReport.TABLE,
                    MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN),
            String.format(
                    "ALTER TABLE %1$s ADD %2$s TEXT",
                    MeasurementTables.TriggerContract.TABLE,
                    MeasurementTables.TriggerContract.AGGREGATION_COORDINATOR_ORIGIN),
            String.format(
                    "ALTER TABLE %1$s ADD %2$s TEXT",
                    MeasurementTables.AggregateEncryptionKey.TABLE,
                    MeasurementTables.AggregateEncryptionKey.AGGREGATION_COORDINATOR_ORIGIN)
    };

    public MeasurementDbMigratorV20() {
        super(20);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        for (String sql : ALTER_STATEMENTS) {
            db.execSQL(sql);
        }
        setCloudProvidersForAggregateEncryptionKey(db);
        setCloudProvidersForAggregateReport(db);
    }

    private void setCloudProvidersForAggregateEncryptionKey(SQLiteDatabase db) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(
                MeasurementTables.AggregateEncryptionKey.AGGREGATION_COORDINATOR_ORIGIN,
                sDefaultCoordinatorOrigin);
        // Set Cloud Coordinator for all present AggregateEncryptionKeys to AWS equivalent.
        db.update(
                MeasurementTables.AggregateEncryptionKey.TABLE,
                contentValues,
                MeasurementTables.AggregateEncryptionKey.AGGREGATION_COORDINATOR_ORIGIN
                        + " IS NULL ",
                null);
    }

    private void setCloudProvidersForAggregateReport(SQLiteDatabase db) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(
                MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN,
                sDefaultCoordinatorOrigin);
        // Set Cloud Coordinator for all present AggregateReport to AWS equivalent.
        db.update(
                MeasurementTables.AggregateReport.TABLE,
                contentValues,
                MeasurementTables.AggregateReport.AGGREGATION_COORDINATOR_ORIGIN + " IS NULL ",
                null);
    }
}
