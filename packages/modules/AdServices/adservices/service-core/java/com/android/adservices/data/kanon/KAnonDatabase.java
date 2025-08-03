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

package com.android.adservices.data.kanon;

import android.annotation.NonNull;
import android.content.Context;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;
import com.android.adservices.service.common.compat.FileCompatUtils;

import java.util.Objects;

@Database(
        entities = {DBServerParameters.class, DBClientParameters.class, DBKAnonMessage.class},
        version = KAnonDatabase.DATABASE_VERSION)
@TypeConverters({FledgeRoomConverters.class})
public abstract class KAnonDatabase extends RoomDatabase {
    private static final Object SINGLETON_LOCK = new Object();
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = FileCompatUtils.getAdservicesFilename("kanon.db");

    public static volatile KAnonDatabase sSingleton = null;

    /** Returns an instance of the KAnonDatabase given a context. */
    public static KAnonDatabase getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        KAnonDatabase singleReadResult = sSingleton;
        if (singleReadResult != null) {
            return singleReadResult;
        }

        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        FileCompatUtils.roomDatabaseBuilderHelper(
                                        context, KAnonDatabase.class, DATABASE_NAME)
                                .fallbackToDestructiveMigration()
                                .build();
            }
            return sSingleton;
        }
    }

    /**
     * @return a Dao to access entities in client_parameters.
     */
    public abstract ClientParametersDao clientParametersDao();

    /**
     * @return a Dao to access entities in server_parameters.
     */
    public abstract ServerParametersDao serverParametersDao();

    /**
     * @return a Dao to access entities in kanon_message table.
     */
    public abstract KAnonMessageDao kAnonMessageDao();
}
