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
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;

import java.time.Instant;

/** End-point provided by Ad tech to download their encoding logic. */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(tableName = DBEncoderEndpoint.TABLE_NAME, inheritSuperIndices = true)
public abstract class DBEncoderEndpoint {

    public static final String TABLE_NAME = "encoder_endpoints";

    /** The ad-tech buyer associated with the signals that would be encoded */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "buyer")
    @PrimaryKey
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /** uri pointing to the encoding logic provided by the buyer during signals fetch */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "download_uri")
    @NonNull
    public abstract Uri getDownloadUri();

    /**
     * @return a builder for creating a {@link DBEncoderEndpoint}
     */
    @NonNull
    public static DBEncoderEndpoint.Builder builder() {
        return new AutoValue_DBEncoderEndpoint.Builder();
    }

    /** The time at which this entry for encoding end-point was created */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "creation_time", index = true)
    @androidx.annotation.NonNull
    public abstract Instant getCreationTime();

    /**
     * @param buyer see {@link #getBuyer()}
     * @param downloadUri see {@link #getDownloadUri()}
     * @param creationTime {@link #getCreationTime()}
     * @return an instance of {@link DBEncoderEndpoint}
     */
    public static DBEncoderEndpoint create(
            @NonNull AdTechIdentifier buyer,
            @NonNull Uri downloadUri,
            @NonNull Instant creationTime) {

        return builder()
                .setBuyer(buyer)
                .setDownloadUri(downloadUri)
                .setCreationTime(creationTime)
                .build();
    }

    /** Provides a builder to create an instance of {@link DBEncoderEndpoint} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** For more details see {@link #getBuyer()} */
        public abstract Builder setBuyer(@NonNull AdTechIdentifier value);

        /** For more details see {@link #getDownloadUri()} */
        public abstract Builder setDownloadUri(@NonNull Uri value);

        /** For more details see {@link #getCreationTime()} */
        public abstract Builder setCreationTime(@NonNull Instant value);

        /**
         * @return an instance of {@link DBEncoderEndpoint}
         */
        public abstract DBEncoderEndpoint build();
    }
}
