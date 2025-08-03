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

import com.android.adservices.LoggerFactory;

/** Handles common functionalities of migrators, e.g. validations. */
public abstract class AbstractMeasurementDbMigrator implements IMeasurementDbMigrator {
    private final int mMigrationTargetVersion;

    public AbstractMeasurementDbMigrator(int migrationTargetVersion) {
        mMigrationTargetVersion = migrationTargetVersion;
    }

    @Override
    public void performMigration(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion >= mMigrationTargetVersion || newVersion < mMigrationTargetVersion) {
            LoggerFactory.getMeasurementLogger()
                    .d("Skipping migration script to db version " + mMigrationTargetVersion);
            return;
        }

        LoggerFactory.getMeasurementLogger()
                .d("Migrating DB to version " + mMigrationTargetVersion);
        performMigration(db);
    }

    /**
     * Takes care of migration the schema and data keeping integrity in check.
     *
     * @param db db to migrate
     */
    protected abstract void performMigration(@NonNull SQLiteDatabase db);
}
