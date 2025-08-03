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

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.Source;

/**
 * Migrates Measurement DB to version 30. This upgrade adds the trigger_data_matching column to the
 * source table, updates all records to trigger_data_matching modulus.
 */
public class MeasurementDbMigratorV30 extends AbstractMeasurementDbMigrator {
    private void addTriggerDataMatchingColumn(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.TRIGGER_DATA_MATCHING);
    }

    private void updateTriggerDataMatchingForAllSourceRecords(@NonNull SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.SourceContract.TRIGGER_DATA_MATCHING,
                Source.TriggerDataMatching.MODULUS.name());
        long rows =
                db.update(
                        MeasurementTables.SourceContract.TABLE,
                        values,
                        null,
                        new String[0]);
        LoggerFactory.getMeasurementLogger()
                .d("Updated trigger_data_matching for " + rows + " source records.");
    }

    public MeasurementDbMigratorV30() {
        super(30);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        addTriggerDataMatchingColumn(db);
        updateTriggerDataMatchingForAllSourceRecords(db);
    }
}
