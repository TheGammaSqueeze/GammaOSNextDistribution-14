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

/**
 * Represents an entry for encoder logic metadata for a buyer, the actual logic will be persisted on
 * the device as flat files. This DB entry is meant for record keeping of file-system storage.
 */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(tableName = DBEncoderLogicMetadata.TABLE_NAME, inheritSuperIndices = true)
public abstract class DBEncoderLogicMetadata {

    // Due to the table name already into the M train.
    // Retain the table name as table delete is not permitted.
    public static final String TABLE_NAME = "encoder_logics"; // NOTYPO

    /** The ad-tech buyer who owns the logic */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "buyer")
    @PrimaryKey
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /** The version provided by ad-tech when this encoding logic was downloaded */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "version")
    public abstract int getVersion();

    /** The time at which this entry for encoding logic was persisted */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "creation_time", index = true)
    @NonNull
    public abstract Instant getCreationTime();

    /** The number of time the JS failed to execute in a row. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "failed_encoding_count", defaultValue = "0")
    public abstract int getFailedEncodingCount();

    /**
     * @return an instance of {@link DBEncoderLogicMetadata}
     */
    public static DBEncoderLogicMetadata create(
            @NonNull AdTechIdentifier buyer,
            int version,
            @NonNull Instant creationTime,
            int failedEncodingCount) {
        return builder()
                .setBuyer(buyer)
                .setVersion(version)
                .setCreationTime(creationTime)
                .setFailedEncodingCount(failedEncodingCount)
                .build();
    }

    /**
     * @return a builder for creating a {@link DBEncoderLogicMetadata}
     */
    public static DBEncoderLogicMetadata.Builder builder() {
        return new AutoValue_DBEncoderLogicMetadata.Builder().setFailedEncodingCount(0);
    }

    /** Provides a builder to create an instance of {@link DBEncoderLogicMetadata} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** For more details see {@link #getBuyer()} */
        public abstract Builder setBuyer(@NonNull AdTechIdentifier value);

        /** For more details see {@link #getVersion()} */
        public abstract Builder setVersion(int value);

        /** For more details see {@link #getCreationTime()} */
        public abstract Builder setCreationTime(@NonNull Instant value);

        /** For more details see {@link #getFailedEncodingCount()} */
        public abstract Builder setFailedEncodingCount(int failedEncodingCount);

        /**
         * @return an instance of {@link DBEncoderLogicMetadata}
         */
        public abstract DBEncoderLogicMetadata build();
    }
}
