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

/**
 * Migrates Measurement DB to version 28. This upgrade adds one columns in the Event Report table to
 * record a list of trigger debug keys.
 */
public class MeasurementDbMigratorV28 extends AbstractMeasurementDbMigrator {

    private void addTriggerDebugKeysColumn(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEYS);
    }

    private void updateDefaultTriggerDebugKeys(@NonNull SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEYS, "");
        long rows =
                db.update(
                        MeasurementTables.EventReportContract.TABLE,
                        values,
                        null,
                        new String[0]);
        LoggerFactory.getMeasurementLogger()
                .d("Updated trigger debug keys for " + rows + " event report records.");
    }

    public MeasurementDbMigratorV28() {
        super(28);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        addTriggerDebugKeysColumn(db);
        updateDefaultTriggerDebugKeys(db);
    }
}
