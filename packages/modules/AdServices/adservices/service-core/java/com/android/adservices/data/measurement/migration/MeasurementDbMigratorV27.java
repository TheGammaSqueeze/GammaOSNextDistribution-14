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

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.android.adservices.data.measurement.MeasurementTables;

/**
 * Migrates Measurement DB to version 27 by adding the is_fake_report column to the Aggregate Report
 * table.
 */
public class MeasurementDbMigratorV27 extends AbstractMeasurementDbMigrator {

    public MeasurementDbMigratorV27() {
        super(27);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        addIsFakeReportColumn(db);
    }

    private void addIsFakeReportColumn(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addIntColumnsIfAbsent(
                db,
                MeasurementTables.AggregateReport.TABLE,
                new String[] {MeasurementTables.AggregateReport.IS_FAKE_REPORT});
    }
}
