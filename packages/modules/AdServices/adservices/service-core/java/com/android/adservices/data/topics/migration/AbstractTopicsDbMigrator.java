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

package com.android.adservices.data.topics.migration;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.DbHelper;

/**
 * The abstract class to handle database migration for Topics API. Any Migrator should extend this
 * class and implement {@code performMigration} method to create/drop/alter tables by execute SQLite
 * queries.
 */
public abstract class AbstractTopicsDbMigrator implements ITopicsDbMigrator {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    private final int mMigrationTargetVersion;

    public AbstractTopicsDbMigrator(int migrationTargetVersion) {
        mMigrationTargetVersion = migrationTargetVersion;
    }

    /**
     * A prerequisite method to check whether to perform the migration.
     *
     * <p>In {@link SQLiteOpenHelper}, {@link SQLiteOpenHelper#onUpgrade(SQLiteDatabase, int, int)}
     * is called if current device db version is older than the version to create db.
     *
     * <p>{@code oldVersion} is always the current database version on device before database calls
     * onCreate(). {@code newVersion} is always the database version defined in {@link DbHelper}.
     * Therefore, the migration should be performed only if {@code mMigrationTargetVersion} is
     * between {@code oldVersion} and {@code newVersion}, including {@code newVersion}.
     *
     * @param db db to perform migration on
     * @param oldVersion device version of database
     * @param newVersion target version to create the database
     * @throws IllegalArgumentException if {@code mMigrationTargetVersion} is not between {@code
     *     oldVersion} and {@code newVersion}, including {@code newVersion}.
     */
    @Override
    public void performMigration(SQLiteDatabase db, int oldVersion, int newVersion)
            throws IllegalArgumentException {
        // Perform Migration only when targetVersion is in the between of oldVersion and newVersion.
        // (including the case targetVersion is equal to newVersion)
        if (oldVersion < mMigrationTargetVersion && mMigrationTargetVersion <= newVersion) {

            sLogger.d("Migrating DB to version %d for Topics API.", mMigrationTargetVersion);
            performMigration(db);
            return;
        }

        // TODO(b/295233784): Improve the test coverage for migrating multiple migrators
        sLogger.e(
                String.format(
                        "Skip migration to db version %d for Topics API. oldVersion=%d, "
                                + " newVersion=%d", mMigrationTargetVersion, oldVersion, newVersion)
        );
    }

    /**
     * Execute SQLite Queries to migrate database between versions
     *
     * @param db db to migrate
     */
    protected abstract void performMigration(SQLiteDatabase db);
}
