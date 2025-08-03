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

import android.annotation.Nullable;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.Instant;
import java.util.List;

/** Dao to manage access to entities in Client parameters table. */
@Dao
public abstract class ClientParametersDao {

    /**
     * Returns an active ClientParameters if it exists with expiry instant more than given
     * timestamp.
     */
    @Nullable
    @Query("SELECT * FROM client_parameters WHERE expiry_instant > :currentTime")
    public abstract List<DBClientParameters> getActiveClientParameters(Instant currentTime);

    /** Inserts the given ClientParameters in table. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertClientParameters(DBClientParameters clientParameters);

    /** Delete all client_parameters older than the given timestamp. */
    @Query("DELETE FROM client_parameters WHERE expiry_instant < :currentTime")
    public abstract void removeExpiredClientParameters(Instant currentTime);

    /** Delete all client parameters from the table. */
    @Query("DELETE FROM client_parameters")
    public abstract int deleteAllClientParameters();
}
