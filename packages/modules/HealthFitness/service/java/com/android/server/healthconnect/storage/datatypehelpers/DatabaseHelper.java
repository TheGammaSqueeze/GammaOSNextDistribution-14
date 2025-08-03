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

package com.android.server.healthconnect.storage.datatypehelpers;

import android.annotation.NonNull;
import android.util.Pair;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parent class for the database helper classes containing common methods
 *
 * @hide
 */
public abstract class DatabaseHelper {

    private static Set<DatabaseHelper> sDatabaseHelpers = new HashSet<>();

    protected DatabaseHelper() {
        sDatabaseHelpers.add(this);
    }

    /** Deletes all entries from the database for the helper class and clears the cache. */
    public static void clearAllData(@NonNull TransactionManager transactionManager) {
        for (DatabaseHelper databaseHelper : sDatabaseHelpers) {
            databaseHelper.clearData(transactionManager);
        }
        clearAllCache();
    }

    public static void clearAllCache() {
        for (DatabaseHelper databaseHelper : sDatabaseHelpers) {
            databaseHelper.clearCache();
        }
    }

    protected void clearData(@NonNull TransactionManager transactionManager) {
        transactionManager.delete(new DeleteTableRequest(getMainTableName()));
    }

    protected void clearCache() {}

    protected abstract String getMainTableName();

    protected abstract List<Pair<String, String>> getColumnInfo();
}
