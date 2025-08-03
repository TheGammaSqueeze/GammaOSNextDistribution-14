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
import com.android.adservices.service.measurement.Trigger;

/**
 * Migrates Measurement DB to version 32. This upgrade adds the {@link
 * MeasurementTables.TriggerContract#AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG} column to the
 * {@value MeasurementTables.TriggerContract#TABLE} table, updates all records to {@link
 * Trigger.SourceRegistrationTimeConfig#INCLUDE}
 */
public class MeasurementDbMigratorV32 extends AbstractMeasurementDbMigrator {
    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        addAggregatableSourceRegistrationTimeConfigColumn(db);
        updateAggregatableSourceRegistrationTimeConfigForAllTriggerRecords(db);
    }

    private void addAggregatableSourceRegistrationTimeConfigColumn(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG);
    }

    private void updateAggregatableSourceRegistrationTimeConfigForAllTriggerRecords(
            @NonNull SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.TriggerContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG,
                Trigger.SourceRegistrationTimeConfig.INCLUDE.name());
        long rows = db.update(MeasurementTables.TriggerContract.TABLE, values, null, new String[0]);

        MigrationHelpers.logUpdateQuery(
                MeasurementTables.TriggerContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG,
                rows,
                MeasurementTables.TriggerContract.TABLE);
    }

    public MeasurementDbMigratorV32() {
        super(32);
    }
}
