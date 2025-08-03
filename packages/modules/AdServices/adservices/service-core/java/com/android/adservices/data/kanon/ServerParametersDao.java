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

/** Dao to manage access to entities in Server parameters table. */
@Dao
public abstract class ServerParametersDao {

    /** Returns the active ServerParameters. */
    @Nullable
    @Query("SELECT * FROM server_parameters WHERE server_params_sign_expiry_instant > :currentTime")
    public abstract List<DBServerParameters> getActiveServerParameters(Instant currentTime);

    /** Inserts the given ServerParameters in table. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertServerParameters(DBServerParameters serverParameters);

    /** Remove all the server parameters with expiry instant older than the given timestamp. */
    @Query("DELETE FROM server_parameters WHERE server_params_sign_expiry_instant < :currentTime")
    public abstract void removeExpiredServerParameters(Instant currentTime);

    /** Delete all server parameters from the table. */
    @Query("DELETE FROM server_parameters")
    public abstract int deleteAllServerParameters();
}
