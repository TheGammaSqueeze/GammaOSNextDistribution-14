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

import com.android.adservices.data.encryptionkey.EncryptionKeyTables;

/**
 * Migrates Shared DB from user version 2 to 3. This upgrade adds 'last_fetch_time' column to the
 * EncryptionKey table.
 */
public class SharedDbMigratorV3 extends AbstractSharedDbMigrator {

    public SharedDbMigratorV3() {
        super(3);
    }

    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        MigrationHelpers.addIntColumnsIfAbsent(
                db,
                EncryptionKeyTables.EncryptionKeyContract.TABLE,
                new String[] {EncryptionKeyTables.EncryptionKeyContract.LAST_FETCH_TIME});
    }
}
