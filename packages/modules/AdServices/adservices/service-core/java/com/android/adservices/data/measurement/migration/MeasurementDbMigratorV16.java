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
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementTables;

/**
 * Migrates Measurement DB to version 16. This upgrade adds an integer column to represent a boolean
 * value, i.e. {@link MeasurementTables.SourceContract#COARSE_EVENT_REPORT_DESTINATIONS}.
 */
public class MeasurementDbMigratorV16 extends AbstractMeasurementDbMigrator {
    public MeasurementDbMigratorV16() {
        super(16);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addIntColumnsIfAbsent(
                db,
                MeasurementTables.SourceContract.TABLE,
                new String[] {MeasurementTables.SourceContract.COARSE_EVENT_REPORT_DESTINATIONS});
    }
}
