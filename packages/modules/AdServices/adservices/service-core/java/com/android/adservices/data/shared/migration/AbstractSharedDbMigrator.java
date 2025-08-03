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

package com.android.adservices.data.shared.migration;

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;

/** Handles common functionalities of migrators, e.g. validations. */
public abstract class AbstractSharedDbMigrator implements ISharedDbMigrator {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private final int mMigrationTargetVersion;

    public AbstractSharedDbMigrator(int migrationTargetVersion) {
        mMigrationTargetVersion = migrationTargetVersion;
    }

    @Override
    public void performMigration(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion >= mMigrationTargetVersion || newVersion < mMigrationTargetVersion) {
            sLogger.d("Skipping migration script to db version " + mMigrationTargetVersion);
            return;
        }

        sLogger.d("Migrating Shared DB to version " + mMigrationTargetVersion);
        performMigration(db);
    }

    /**
     * Takes care of migration the schema and data keeping integrity in check.
     *
     * @param db shared db to migrate
     */
    protected abstract void performMigration(@NonNull SQLiteDatabase db);
}
