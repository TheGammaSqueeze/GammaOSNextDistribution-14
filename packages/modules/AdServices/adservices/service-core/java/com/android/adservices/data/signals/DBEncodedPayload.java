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

package com.android.adservices.data.signals;

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;

import java.time.Instant;

/** Represents an entry for encoded payload for a buyer. */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(tableName = DBEncodedPayload.TABLE_NAME, inheritSuperIndices = true)
public abstract class DBEncodedPayload {

    public static final String TABLE_NAME = "encoded_payload";

    /** The ad-tech buyer associated with the signals that would be encoded */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "buyer")
    @PrimaryKey
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /** The version provided by Ad tech corresponding for their encoder */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "version")
    public abstract int getVersion();

    /** The time at which this entry for encoded payload was persisted */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "creation_time", index = true)
    @NonNull
    public abstract Instant getCreationTime();

    /** The encoded payload generated using raw signals and encoder provided by buyer */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "encoded_payload", typeAffinity = ColumnInfo.BLOB)
    @NonNull
    public abstract byte[] getEncodedPayload();

    /**
     * @return an instance of {@link DBEncodedPayload}
     */
    public static DBEncodedPayload create(
            @NonNull AdTechIdentifier buyer,
            int version,
            @NonNull Instant creationTime,
            @NonNull byte[] encodedPayload) {
        return builder()
                .setBuyer(buyer)
                .setVersion(version)
                .setCreationTime(creationTime)
                .setEncodedPayload(encodedPayload)
                .build();
    }

    /**
     * @return a builder for creating a {@link DBEncodedPayload}
     */
    public static DBEncodedPayload.Builder builder() {
        return new AutoValue_DBEncodedPayload.Builder();
    }

    /** Provides a builder to create an instance of {@link DBEncodedPayload} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** For more details see {@link #getBuyer()} */
        public abstract Builder setBuyer(@NonNull AdTechIdentifier value);

        /** For more details see {@link #getVersion()} */
        public abstract Builder setVersion(int value);

        /** For more details see {@link #getCreationTime()} */
        public abstract Builder setCreationTime(@NonNull Instant value);

        /** For more details see {@link #getEncodedPayload()} */
        public abstract Builder setEncodedPayload(byte[] value);

        /**
         * @return an instance of {@link DBEncodedPayload}
         */
        public abstract DBEncodedPayload build();
    }
}
