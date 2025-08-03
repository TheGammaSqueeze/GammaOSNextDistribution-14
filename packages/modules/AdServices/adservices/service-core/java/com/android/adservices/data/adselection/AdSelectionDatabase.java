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

package com.android.adservices.data.adselection;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;
import com.android.adservices.service.common.compat.FileCompatUtils;

import java.util.Objects;

/** Room based database for Ad Selection. */
@Database(
        entities = {
            DBAdSelection.class,
            DBBuyerDecisionLogic.class,
            DBAdSelectionOverride.class,
            DBAdSelectionFromOutcomesOverride.class,
            DBRegisteredAdInteraction.class,
            DBBuyerDecisionOverride.class,
            DBReportingData.class,
            DBAdSelectionInitialization.class,
            DBAdSelectionResult.class,
            DBReportingComputationInfo.class
        },
        version = AdSelectionDatabase.DATABASE_VERSION,
        autoMigrations = {
            @AutoMigration(from = 1, to = 2),
            @AutoMigration(from = 2, to = 3),
            @AutoMigration(from = 3, to = 4),
            @AutoMigration(from = 4, to = 5),
            @AutoMigration(from = 5, to = 6),
            @AutoMigration(from = 6, to = 7),
            @AutoMigration(from = 7, to = 8)
        })
@TypeConverters({FledgeRoomConverters.class})
public abstract class AdSelectionDatabase extends RoomDatabase {
    private static final Object SINGLETON_LOCK = new Object();

    public static final int DATABASE_VERSION = 8;
    // TODO(b/230653780): Should we separate the DB.
    public static final String DATABASE_NAME =
            FileCompatUtils.getAdservicesFilename("adselection.db");

    private static volatile AdSelectionDatabase sSingleton = null;

    /** Returns an instance of the AdSelectionDatabase given a context. */
    public static AdSelectionDatabase getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        // Initialization pattern recommended on page 334 of "Effective Java" 3rd edition
        AdSelectionDatabase singleReadResult = sSingleton;
        if (singleReadResult != null) {
            return singleReadResult;
        }
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        FileCompatUtils.roomDatabaseBuilderHelper(
                                        context, AdSelectionDatabase.class, DATABASE_NAME)
                                .fallbackToDestructiveMigration()
                                .build();
            }
            return sSingleton;
        }
    }

    /**
     * @return a Dao to access entities in AdSelection database.
     */
    public abstract AdSelectionEntryDao adSelectionEntryDao();
}
