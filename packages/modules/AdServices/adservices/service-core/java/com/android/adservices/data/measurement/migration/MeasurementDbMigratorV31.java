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
import com.android.adservices.service.measurement.registration.AsyncRedirect;


/**
 * Migrates Measurement DB to version 31. This upgrade adds one column in the async registration
 * table to persist the configuration for redirecting Location type redirects to .well-known path.
 */
public class MeasurementDbMigratorV31 extends AbstractMeasurementDbMigrator {
    public MeasurementDbMigratorV31() {
        super(31);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        addRedirectBehaviorColumn(db);
        updateRedirectBehaviorForAllAsyncRegistrations(db);
    }

    private void addRedirectBehaviorColumn(SQLiteDatabase db) {
        MigrationHelpers.addTextColumnIfAbsent(
                db,
                MeasurementTables.AsyncRegistrationContract.TABLE,
                MeasurementTables.AsyncRegistrationContract.REDIRECT_BEHAVIOR);
    }

    private void updateRedirectBehaviorForAllAsyncRegistrations(@NonNull SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.AsyncRegistrationContract.REDIRECT_BEHAVIOR,
                AsyncRedirect.RedirectBehavior.AS_IS.name());
        long rows =
                db.update(
                        MeasurementTables.AsyncRegistrationContract.TABLE,
                        values,
                        null,
                        new String[0]);
        MigrationHelpers.logUpdateQuery(
                MeasurementTables.AsyncRegistrationContract.REDIRECT_BEHAVIOR,
                rows,
                MeasurementTables.AsyncRegistrationContract.TABLE);
    }
}
