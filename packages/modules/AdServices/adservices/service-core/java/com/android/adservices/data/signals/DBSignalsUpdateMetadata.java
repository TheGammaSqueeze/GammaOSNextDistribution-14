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

/** Represents an entry for signals update metadata. */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(tableName = DBSignalsUpdateMetadata.TABLE_NAME, inheritSuperIndices = true)
public abstract class DBSignalsUpdateMetadata {

    public static final String TABLE_NAME = "signals_update_metadata";

    /** The ad-tech buyer */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "buyer")
    @PrimaryKey
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /** The last time update happened to a buyer's signals */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "last_signals_updated_time")
    public abstract Instant getLastSignalsUpdatedTime();

    /**
     * @return an instance of {@link DBSignalsUpdateMetadata}
     */
    public static DBSignalsUpdateMetadata create(
            @NonNull AdTechIdentifier buyer, @NonNull Instant lastSignalsUpdatedTime) {
        return builder().setBuyer(buyer).setLastSignalsUpdatedTime(lastSignalsUpdatedTime).build();
    }

    /**
     * @return a builder for creating a {@link DBSignalsUpdateMetadata}
     */
    public static DBSignalsUpdateMetadata.Builder builder() {
        return new AutoValue_DBSignalsUpdateMetadata.Builder();
    }

    /** Provides a builder to create an instance of {@link DBSignalsUpdateMetadata} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** For more details see {@link #getBuyer()} */
        public abstract DBSignalsUpdateMetadata.Builder setBuyer(@NonNull AdTechIdentifier value);

        /** For more details see {@link #getLastSignalsUpdatedTime()} ()} */
        public abstract DBSignalsUpdateMetadata.Builder setLastSignalsUpdatedTime(
                @NonNull Instant value);

        /**
         * @return an instance of {@link DBSignalsUpdateMetadata}
         */
        public abstract DBSignalsUpdateMetadata build();
    }
}
