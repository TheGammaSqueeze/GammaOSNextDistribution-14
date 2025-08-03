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

package com.android.rkpdapp.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.android.rkpdapp.ThreadPool;

/**
 * Stores the remotely provisioned keys.
 */
@Database(entities = {ProvisionedKey.class}, exportSchema = false, version = 1)
@TypeConverters({InstantConverter.class})
public abstract class RkpdDatabase extends RoomDatabase {
    public static final String DB_NAME = "rkpd_database";
    /**
     * Provides the DAO object for easy queries.
     */
    public abstract ProvisionedKeyDao provisionedKeyDao();

    private static volatile RkpdDatabase sInstance;

    /**
     * Gets the singleton instance for database.
     */
    public static RkpdDatabase getDatabase(final Context context) {
        RkpdDatabase result = sInstance;
        if (result != null) {
            return result;
        }
        synchronized (RkpdDatabase.class) {
            if (sInstance == null) {
                sInstance = Room.databaseBuilder(context.getApplicationContext(),
                                RkpdDatabase.class, DB_NAME)
                        .setQueryExecutor(ThreadPool.EXECUTOR)
                        .build();
            }
            return sInstance;
        }
    }
}
