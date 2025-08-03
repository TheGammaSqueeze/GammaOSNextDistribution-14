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

package com.android.adservices.data.measurement;

import android.annotation.NonNull;
import android.database.sqlite.SQLiteDatabase;

class SQLTransaction implements ITransaction {

    private final SQLiteDatabase mDb;
    private boolean mRollback;

    SQLTransaction(@NonNull SQLiteDatabase db) {
        mDb = db;
        mRollback = false;
    }

    @Override
    public void begin() {
        if (!mDb.inTransaction()) {
            mDb.beginTransaction();
        }
    }

    @Override
    public void rollback() {
        mRollback = true;
    }

    @Override
    public void end() {
        if (mDb.inTransaction()) {
            if (!mRollback) {
                mDb.setTransactionSuccessful();
            }
            mDb.endTransaction();
        }
    }

    /**
     * Get an instance of {@link SQLiteDatabase} for operations.
     *
     * @return {@link SQLiteDatabase} instance
     * @throws DatastoreException if transaction is not active
     */
    @NonNull
    public SQLiteDatabase getDatabase() throws DatastoreException {
        if (!mDb.inTransaction()) {
            throw new DatastoreException("Database should not be queried without transaction.");
        }
        return mDb;
    }
}
