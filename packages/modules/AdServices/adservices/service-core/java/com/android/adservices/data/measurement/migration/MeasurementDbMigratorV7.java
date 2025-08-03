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

import com.android.adservices.data.measurement.MeasurementTables;

/** Migrates Measurement DB from user version 6 to 7. */
public class MeasurementDbMigratorV7 extends AbstractMeasurementDbMigrator {
    private static final String[] CREATE_INDEXES = {
        "CREATE INDEX "
                + "idx_"
                + MeasurementTables.SourceContract.TABLE
                + "_ei "
                + "ON "
                + MeasurementTables.SourceContract.TABLE
                + "("
                + MeasurementTables.SourceContract.ENROLLMENT_ID
                + ")",
        "CREATE INDEX "
                + "idx_"
                + MeasurementTables.XnaIgnoredSourcesContract.TABLE
                + "_ei "
                + "ON "
                + MeasurementTables.XnaIgnoredSourcesContract.TABLE
                + "("
                + MeasurementTables.XnaIgnoredSourcesContract.ENROLLMENT_ID
                + ")"
    };

    public MeasurementDbMigratorV7() {
        super(7);
    }

    @Override
    protected void performMigration(SQLiteDatabase db) {
        for (String statement : CREATE_INDEXES) {
            db.execSQL(statement);
        }
    }
}
