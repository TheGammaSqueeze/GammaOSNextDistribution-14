/*
 * Copyright (C) 2024 The Android Open Source Project
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

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

import java.time.Instant;

@Entity(tableName = DBServerParameters.TABLE_NAME)
@TypeConverters({FledgeRoomConverters.class})
@AutoValue
@CopyAnnotations
public abstract class DBServerParameters {
    public static final String TABLE_NAME = "server_parameters";

    /** Sever public parameters version. */
    @NonNull
    @ColumnInfo(name = "server_public_parameters", typeAffinity = ColumnInfo.BLOB)
    @CopyAnnotations
    @SuppressWarnings({"AutoValueImmutableFields", "AutoValueMutable"})
    public abstract byte[] getServerPublicParameters();

    /** Sever parameter JOIN expiry, this field is returned in the GetSeverParametersResponse. */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "server_params_join_expiry_instant")
    public abstract Instant getServerParamsJoinExpiryInstant();

    /** Sever parameter SIGN expiry, this field is returned in the GetSeverParametersResponse. */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "server_params_sign_expiry_instant")
    public abstract Instant getServerParamsSignExpiryInstant();

    /** Creation instant. Timestamp when the server parameters where created */
    @NonNull
    @CopyAnnotations
    @PrimaryKey
    @ColumnInfo(name = "creation_instant", defaultValue = "CURRENT_TIMESTAMP")
    public abstract Instant getCreationInstant();

    /** Sever parameter version, this field is returned in the GetSeverParametersResponse. */
    @CopyAnnotations
    @NonNull
    @ColumnInfo(name = "server_params_version")
    public abstract String getServerParamsVersion();

    /** Creates and returns a {@link DBServerParameters} object. */
    public static DBServerParameters create(
            @NonNull String serverParamsVersion,
            @NonNull Instant creationInstant,
            @NonNull Instant serverParamsSignExpiryInstant,
            @NonNull Instant serverParamsJoinExpiryInstant,
            @NonNull byte[] serverPublicParameters) {
        return builder()
                .setServerPublicParameters(serverPublicParameters)
                .setServerParamsVersion(serverParamsVersion)
                .setCreationInstant(creationInstant)
                .setServerParamsSignExpiryInstant(serverParamsSignExpiryInstant)
                .setServerParamsJoinExpiryInstant(serverParamsJoinExpiryInstant)
                .build();
    }

    /** Returns a {@link DBServerParameters.Builder} for {@link DBServerParameters}. */
    public static DBServerParameters.Builder builder() {
        return new AutoValue_DBServerParameters.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the server parmas version. */
        public abstract Builder setServerParamsVersion(String serverParamsVersion);

        /** Sets the creation instant. */
        public abstract Builder setCreationInstant(Instant creationInstant);

        /** Sets the server parmas sign expiry instant. */
        public abstract Builder setServerParamsSignExpiryInstant(
                Instant serverParamsSignExpiryInstant);

        /** Sets the server parmas join expiry instant. */
        public abstract Builder setServerParamsJoinExpiryInstant(
                Instant serverParamsJoinExpiryInstant);

        /** Sets the server public abstract parameters. */
        public abstract Builder setServerPublicParameters(byte[] serverPublicParameters);

        /** Builds and return a {@link DBServerParameters} object */
        public abstract DBServerParameters build();
    }
}
