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

package com.android.server.deviceconfig.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/**
 * @hide
 */
public class DeviceConfigDbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "config_infrastructure.db";

    /**
     * TODO(b/265948914) / to consider:
     *
     * - enforce uniqueness of (namespace, key) pairs
     * - synchronize calls that modify the db (maybe reads too?)
     * - probably use a read/write lock
     * - per-process caching of results so we don't go to the db every time
     * - test the sql commands to make sure they work well (e.g. where clauses are
     * written properly)
     * - check the performance of the sql commands and look for optimizations
     * - write a test for adapter.setProperties that has some but not all
     * preexisting properties
     * - Settings.Config has a concept "makeDefault" which is not implemented here
     * - ensure that any sql exceptions are not thrown to the callers (where methods
     * can return
     * false)
     * - see what happens if a caller starts observing changes before the database
     * is loaded/ready (early in the boot process)
     * - I've seen strict mode alerts about doing I/O in the main thread after a
     * device boots. Maybe we can't avoid it but double check.
     * - finish API implementation in DatabaseDataStore
     */

    interface Contract {
        class DeviceConfigEntry implements BaseColumns {
            public static final String TABLE_NAME = "config";
            public static final String COLUMN_NAME_NAMESPACE = "namespace";
            public static final String COLUMN_NAME_KEY = "config_key";
            public static final String COLUMN_NAME_VALUE = "config_value";
        }
    }

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + Contract.DeviceConfigEntry.TABLE_NAME + " (" +
                    Contract.DeviceConfigEntry._ID + " INTEGER PRIMARY KEY," +
                    Contract.DeviceConfigEntry.COLUMN_NAME_NAMESPACE + " TEXT," +
                    Contract.DeviceConfigEntry.COLUMN_NAME_KEY + " TEXT," +
                    Contract.DeviceConfigEntry.COLUMN_NAME_VALUE + " TEXT)";

    public DeviceConfigDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // no op for now
    }

}
