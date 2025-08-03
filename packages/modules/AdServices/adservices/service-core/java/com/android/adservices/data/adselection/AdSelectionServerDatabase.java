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

package com.android.adservices.data.adselection;

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

/** Room based database for ad selection on servers. */
@SuppressWarnings("deprecation")
@Database(
        entities = {
            DBReportingUris.class,
            DBEncryptionKey.class,
            DBEncryptionContext.class,
            DBAuctionServerAdSelection.class,
            DBProtectedServersEncryptionConfig.class
        },
        version = AdSelectionServerDatabase.DATABASE_VERSION,
        autoMigrations = {
            @AutoMigration(from = 1, to = 2),
            @AutoMigration(from = 2, to = 3),
            @AutoMigration(from = 3, to = 4),
        })
@TypeConverters({FledgeRoomConverters.class})
public abstract class AdSelectionServerDatabase extends RoomDatabase {
    public static final int DATABASE_VERSION = 4;
    public static final String DATABASE_NAME =
            FileCompatUtils.getAdservicesFilename("adselectionserver.db");

    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static AdSelectionServerDatabase sSingleton = null;

    /** Returns an instance of the AdSelectionEncryptionDatabase given a context. */
    public static AdSelectionServerDatabase getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be present.");
        synchronized (SINGLETON_LOCK) {
            if (Objects.isNull(sSingleton)) {
                sSingleton =
                        FileCompatUtils.roomDatabaseBuilderHelper(
                                        context, AdSelectionServerDatabase.class, DATABASE_NAME)
                                .fallbackToDestructiveMigration()
                                .build();
            }
            return sSingleton;
        }
    }

    /**
     * @return a Dao to access entities in {@link DBEncryptionKey} database.
     */
    public abstract EncryptionKeyDao encryptionKeyDao();

    /**
     * @return a Dao to access entities in {@link DBProtectedServersEncryptionConfig} table.
     */
    public abstract ProtectedServersEncryptionConfigDao protectedServersEncryptionConfigDao();

    /**
     * @return a Dao to access entities in {@link DBEncryptionContext} database.
     */
    public abstract EncryptionContextDao encryptionContextDao();

    /**
     * @return a Dao to access entities in {@link DBAuctionServerAdSelection} database.
     */
    public abstract AuctionServerAdSelectionDao auctionServerAdSelectionDao();
}
