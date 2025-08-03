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

package com.android.adservices.data.customaudience;

import android.adservices.common.AdSelectionSignals;
import android.adservices.customaudience.PartialCustomAudience;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import com.google.auto.value.AutoValue;

import java.time.Instant;

@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = DBPartialCustomAudience.TABLE_NAME,
        foreignKeys =
                @ForeignKey(
                        entity = DBScheduledCustomAudienceUpdate.class,
                        parentColumns = {"update_id"},
                        childColumns = {"update_id"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"update_id", "name"},
                    unique = true)
        },
        primaryKeys = {"update_id", "name"})
public abstract class DBPartialCustomAudience {
    public static final String TABLE_NAME = "partial_custom_audience";

    /** Unique id associated with a delayed update */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "update_id")
    @NonNull
    public abstract Long getUpdateId();

    /** Gets Custom Audience name */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "name")
    @NonNull
    public abstract String getName();

    /** Gets Custom Audience activation time */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "activation_time")
    @Nullable
    public abstract Instant getActivationTime();

    /** Gets Custom Audience expiration time */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "expiration_time")
    @Nullable
    public abstract Instant getExpirationTime();

    /** Gets Custom Audience's bidding signals */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "bidding_signals")
    @Nullable
    public abstract AdSelectionSignals getUserBiddingSignals();

    /** Provides a {@link DBCustomAudience.Builder} */
    @NonNull
    public static DBPartialCustomAudience.Builder builder() {
        return new AutoValue_DBPartialCustomAudience.Builder()
                .setActivationTime(null)
                .setExpirationTime(null)
                .setUserBiddingSignals(null);
    }

    /** Utility to convert a {@link DBPartialCustomAudience} into a {@link PartialCustomAudience} */
    @NonNull
    public static PartialCustomAudience getPartialCustomAudience(
            DBPartialCustomAudience dbPartialCustomAudience) {
        return new PartialCustomAudience.Builder(dbPartialCustomAudience.getName())
                .setActivationTime(dbPartialCustomAudience.getActivationTime())
                .setExpirationTime(dbPartialCustomAudience.getExpirationTime())
                .setUserBiddingSignals(dbPartialCustomAudience.getUserBiddingSignals())
                .build();
    }

    /** Creates an instance of {@link DBPartialCustomAudience} */
    @NonNull
    public static DBPartialCustomAudience create(
            @NonNull Long updateId,
            @NonNull String name,
            @Nullable Instant activationTime,
            @Nullable Instant expirationTime,
            @Nullable AdSelectionSignals userBiddingSignals) {
        return builder()
                .setUpdateId(updateId)
                .setName(name)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setUserBiddingSignals(userBiddingSignals)
                .build();
    }

    /** Builder for convenient creation of an object of type {@link DBPartialCustomAudience} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** see {@link #getUpdateId()} */
        @NonNull
        public abstract Builder setUpdateId(@NonNull Long updateId);

        /** see {@link #getName()} */
        @NonNull
        public abstract Builder setName(@NonNull String name);

        /** see {@link #getActivationTime()} */
        @NonNull
        public abstract Builder setActivationTime(@Nullable Instant activationTime);

        /** see {@link #getExpirationTime()} */
        @NonNull
        public abstract Builder setExpirationTime(@Nullable Instant expirationTime);

        /** see {@link #getUserBiddingSignals()} */
        @NonNull
        public abstract Builder setUserBiddingSignals(
                @Nullable AdSelectionSignals userBiddingSignals);

        /** Builds a {@link DBCustomAudience.Builder} */
        @NonNull
        public abstract DBPartialCustomAudience build();
    }
}
