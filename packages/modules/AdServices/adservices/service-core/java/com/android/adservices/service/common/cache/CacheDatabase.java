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

package com.android.adservices.service.common.cache;

import static com.android.adservices.service.common.cache.CacheDatabase.DATABASE_VERSION;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.internal.annotations.GuardedBy;

import java.util.Objects;

/** A class that represents the database for caching web requests related to Fledge */
@Database(
        entities = {DBCacheEntry.class},
        autoMigrations = {
            @AutoMigration(from = 1, to = 2),
        },
        version = DATABASE_VERSION)
@TypeConverters({FledgeRoomConverters.class})
public abstract class CacheDatabase extends RoomDatabase {
    private static final Object SINGLETON_LOCK = new Object();

    // TODO(b/270615351): Create migration rollback test for version bump
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME =
            FileCompatUtils.getAdservicesFilename("fledgehttpcache.db");

    @GuardedBy("SINGLETON_LOCK")
    private static CacheDatabase sSingleton = null;

    /** Returns an instance of the CacheDatabase given a context. */
    public static CacheDatabase getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);
        synchronized (SINGLETON_LOCK) {
            if (Objects.isNull(sSingleton)) {
                sSingleton =
                        FileCompatUtils.roomDatabaseBuilderHelper(
                                        context, CacheDatabase.class, DATABASE_NAME)
                                .fallbackToDestructiveMigration()
                                .build();
            }
            return sSingleton;
        }
    }

    /** @return a Dao to access cached entries */
    public abstract CacheEntryDao getCacheEntryDao();
}
