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

import android.annotation.NonNull;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;

/** Migrates Measurement DB from user version 1 to 2. */
public class MeasurementDbMigratorV2 extends AbstractMeasurementDbMigrator {

    private static final String[] ALTER_STATEMENTS_VER_2 = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.SOURCE_DEBUG_KEY),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.EventReportContract.TABLE,
                MeasurementTables.EventReportContract.TRIGGER_DEBUG_KEY),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.SOURCE_DEBUG_KEY),
        String.format(
                "ALTER TABLE %1$s ADD %2$s INTEGER",
                MeasurementTables.AggregateReport.TABLE,
                MeasurementTables.AggregateReport.TRIGGER_DEBUG_KEY)
    };

    public MeasurementDbMigratorV2() {
        super(2);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        for (String sql : ALTER_STATEMENTS_VER_2) {
            db.execSQL(sql);
        }
    }
}
