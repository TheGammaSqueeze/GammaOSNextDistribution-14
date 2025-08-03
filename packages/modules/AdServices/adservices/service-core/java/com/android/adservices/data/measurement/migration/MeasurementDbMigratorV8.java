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
 * Migrates Measurement DB to version 8. This upgrade adds columns for debug join keys to source and
 * trigger tables.
 */
public class MeasurementDbMigratorV8 extends AbstractMeasurementDbMigrator {
    private static final String[] ALTER_STATEMENTS = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.DEBUG_JOIN_KEY),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.DEBUG_JOIN_KEY)
    };

    public MeasurementDbMigratorV8() {
        super(8);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        for (String sql : ALTER_STATEMENTS) {
            db.execSQL(sql);
        }
    }
}
