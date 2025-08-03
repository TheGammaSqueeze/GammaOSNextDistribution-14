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

/**
 * Its implementations will have logic to migration from a version to next version. The logic would
 * essentially have pre data migration, data transfer and post migration (cleanup) steps. Caller
 * should invoke {@link #performMigration(SQLiteDatabase, int, int)} API to perform the migration.
 */
public interface ISharedDbMigrator {
    /**
     * Migrates from a certain version to the next DB version.
     *
     * @param db db to perform migration on
     * @param oldVersion device version before migration
     * @param newVersion target version post migration
     */
    void performMigration(SQLiteDatabase db, int oldVersion, int newVersion);
}
