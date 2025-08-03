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

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;

import java.time.Instant;

/** POJO representing a Protected Signal. */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(tableName = DBProtectedSignal.TABLE_NAME, inheritSuperIndices = true)
public abstract class DBProtectedSignal {
    public static final String TABLE_NAME = "protected_signals";

    /** The id of the signal. Should be left null to be auto-populated by Room. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    @Nullable
    public abstract Long getId();

    /** The adtech buyer who created/will use the signal. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "buyer", index = true)
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /** The bytes of the signal's key. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "key")
    @NonNull
    public abstract byte[] getKey();

    /** The bytes of the signal's value. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "value")
    @NonNull
    public abstract byte[] getValue();

    /** The time the signal was created (truncated to milliseconds). */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "creationTime")
    @NonNull
    public abstract Instant getCreationTime();

    /** The package that created the signal. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "packageName")
    @NonNull
    public abstract String getPackageName();

    /**
     * @return The builder for this object.
     */
    @NonNull
    public static DBProtectedSignal.Builder builder() {
        return new AutoValue_DBProtectedSignal.Builder().setId(null);
    }

    /** Creates a DBProtectedSignal. Required by Room for AutoValue classes. */
    @NonNull
    public static DBProtectedSignal create(
            @Nullable Long id,
            @NonNull AdTechIdentifier buyer,
            @NonNull byte[] key,
            @NonNull byte[] value,
            @NonNull Instant creationTime,
            @NonNull String packageName) {
        return builder()
                .setId(id)
                .setBuyer(buyer)
                .setKey(key)
                .setValue(value)
                .setCreationTime(creationTime)
                .setPackageName(packageName)
                .build();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** For more details see {@link #getId()} */
        public abstract Builder setId(Long id);

        /** For more details see {@link #getBuyer()} */
        @NonNull
        public abstract Builder setBuyer(@NonNull AdTechIdentifier buyer);

        /** For more details see {@link #getKey()} */
        @NonNull
        public abstract Builder setKey(@NonNull byte[] key);

        /** For more details see {@link #getValue()} */
        @NonNull
        public abstract Builder setValue(@NonNull byte[] value);

        /** For more details see {@link #getCreationTime()} */
        public abstract Builder setCreationTime(@NonNull Instant creationTime);

        /** For more details see {@link #getPackageName()} */
        @NonNull
        public abstract Builder setPackageName(@NonNull String packageName);

        /**
         * @return an instance of {@link DBProtectedSignal}
         */
        @NonNull
        public abstract DBProtectedSignal build();
    }
}
