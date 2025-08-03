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

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import com.google.cobalt.SystemProfile;
import com.google.common.hash.Hashing;

/**
 * Stores the mapping between system profile hashes and the protocol buffers they were generated
 * from.
 */
@AutoValue
@CopyAnnotations
@Entity(tableName = "SystemProfiles")
abstract class SystemProfileEntity {
    /** The system profile hash. */
    @CopyAnnotations
    @ColumnInfo(name = "system_profile_hash")
    @PrimaryKey
    @NonNull
    abstract long systemProfileHash();

    /** The system profile value. */
    @CopyAnnotations
    @ColumnInfo(name = "system_profile")
    @NonNull
    abstract SystemProfile systemProfile();

    /**
     * Creates a {@link SystemProfileEntity}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    static SystemProfileEntity create(long systemProfileHash, SystemProfile systemProfile) {
        return new AutoValue_SystemProfileEntity(systemProfileHash, systemProfile);
    }

    static long getSystemProfileHash(SystemProfile systemProfile) {
        return Hashing.farmHashFingerprint64().hashBytes(systemProfile.toByteArray()).asLong();
    }
}
