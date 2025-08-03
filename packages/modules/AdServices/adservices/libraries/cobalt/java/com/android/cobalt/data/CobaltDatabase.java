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

package com.android.cobalt.data;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.android.internal.annotations.VisibleForTesting;

/** Room-based database for storing global data, aggregated values, and unsent observations. */
@Database(
        entities = {
            AggregateStoreEntity.class,
            GlobalValueEntity.class,
            ObservationStoreEntity.class,
            ReportEntity.class,
            SystemProfileEntity.class,
            StringHashEntity.class
        },
        version = CobaltDatabase.VERSION,
        autoMigrations = {@AutoMigration(from = 1, to = 2)})
@TypeConverters({Converters.class})
public abstract class CobaltDatabase extends RoomDatabase {
    static final int VERSION = 2;

    /** Get the DAO building blocks. */
    abstract DaoBuildingBlocks daoBuildingBlocks();

    /** Get the DAO for test-only operations. */
    @VisibleForTesting
    public abstract TestOnlyDao testOnlyDao();
}
