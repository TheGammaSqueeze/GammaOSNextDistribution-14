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

package com.android.adservices.data.signals;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;
import com.android.adservices.service.common.compat.FileCompatUtils;

import java.util.Objects;

/** Room based database for protected signals. */
@Database(
        entities = {
            DBProtectedSignal.class,
            DBEncoderEndpoint.class,
            DBEncoderLogicMetadata.class,
            DBEncodedPayload.class,
            DBSignalsUpdateMetadata.class,
        },
        autoMigrations = {
            @AutoMigration(from = 1, to = 2),
            @AutoMigration(from = 2, to = 3),
            @AutoMigration(from = 3, to = 4)
        },
        version = ProtectedSignalsDatabase.DATABASE_VERSION)
@TypeConverters({FledgeRoomConverters.class})
public abstract class ProtectedSignalsDatabase extends RoomDatabase {
    private static final Object SINGLETON_LOCK = new Object();

    public static final int DATABASE_VERSION = 4;
    public static final String DATABASE_NAME =
            FileCompatUtils.getAdservicesFilename("protectedsignals.db");

    private static volatile ProtectedSignalsDatabase sSingleton;

    /** Returns an instance of the ProtectedSignalsDatabase given a context. */
    public static ProtectedSignalsDatabase getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        /* This initialization pattern tends to outperform more naive approaches since it
         * does not attempt to grab the lock if the DB is already initialized.
         * Ref: "Effective Java" 3rd edition by Joshua Bloch (page 334)
         */
        ProtectedSignalsDatabase singleReadResult = sSingleton;
        if (singleReadResult != null) {
            return singleReadResult;
        }
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        FileCompatUtils.roomDatabaseBuilderHelper(
                                        context, ProtectedSignalsDatabase.class, DATABASE_NAME)
                                .fallbackToDestructiveMigration()
                                .build();
            }
            return sSingleton;
        }
    }

    /**
     * Protected signals Dao.
     *
     * @return Dao to access protected signals storage.
     */
    public abstract ProtectedSignalsDao protectedSignalsDao();

    /**
     * Encoder endpoints Dao
     *
     * @return Dao to access encoder end points
     */
    public abstract EncoderEndpointsDao getEncoderEndpointsDao();

    /**
     * Encoder Logics Dao
     *
     * @return Dao to access persisted encoder logic entries
     */
    public abstract EncoderLogicMetadataDao getEncoderLogicMetadataDao();

    /**
     * Encoded Payloads Dao
     *
     * @return Dao to access persisted encoded signals payloads
     */
    public abstract EncodedPayloadDao getEncodedPayloadDao();
}
