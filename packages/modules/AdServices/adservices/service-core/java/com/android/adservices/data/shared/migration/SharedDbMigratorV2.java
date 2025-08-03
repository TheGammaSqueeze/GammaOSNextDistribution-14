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
import com.android.adservices.data.shared.SharedDbHelper;

/** Migrates Shared DB from user version 1 to 2, create EncryptionKey table if not have it. */
public class SharedDbMigratorV2 extends AbstractSharedDbMigrator {

    public SharedDbMigratorV2() {
        super(2);
    }

    /**
     * @param db shared db to migrate
     */
    @Override
    protected void performMigration(@NonNull SQLiteDatabase db) {
        if (!SharedDbHelper.hasAllTables(db, EncryptionKeyTables.ENCRYPTION_KEY_TABLES)) {
            EncryptionKeyTables.CREATE_STATEMENTS_V2.forEach(db::execSQL);
        }
    }
}
