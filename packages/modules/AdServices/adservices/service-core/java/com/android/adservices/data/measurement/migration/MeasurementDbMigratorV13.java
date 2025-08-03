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
 * Migrates Measurement DB from version 12 to version 13. This upgrade adds columns for Debug AdID
 * values for source and trigger registrations.
 */
public class MeasurementDbMigratorV13 extends AbstractMeasurementDbMigrator {
    private static final String[] ALTER_STATEMENTS = {
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.PLATFORM_AD_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.PLATFORM_AD_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.DEBUG_AD_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.PLATFORM_AD_ID),
        String.format(
                "ALTER TABLE %1$s ADD %2$s TEXT",
                MeasurementTables.TriggerContract.TABLE,
                MeasurementTables.TriggerContract.DEBUG_AD_ID)
    };

    public MeasurementDbMigratorV13() {
        super(13);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        for (String sql : ALTER_STATEMENTS) {
            db.execSQL(sql);
        }
    }
}
