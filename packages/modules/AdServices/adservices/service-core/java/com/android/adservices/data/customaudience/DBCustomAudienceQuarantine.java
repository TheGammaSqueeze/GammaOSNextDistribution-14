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

package com.android.adservices.data.customaudience;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

import com.google.auto.value.AutoValue;

import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * Table containing owner + buyer pairings that are quarantined from calling {@link
 * android.adservices.customaudience.CustomAudienceManager#fetchAndJoinCustomAudience(FetchAndJoinCustomAudienceRequest,
 * Executor, OutcomeReceiver)}
 */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = "custom_audience_quarantine",
        indices = {@Index(value = {"owner", "buyer"})},
        primaryKeys = {"owner", "buyer"})
public abstract class DBCustomAudienceQuarantine {
    /**
     * @return the owner
     */
    @AutoValue.CopyAnnotations
    @NonNull
    @ColumnInfo(name = "owner")
    public abstract String getOwner();

    /**
     * @return the buyer
     */
    @AutoValue.CopyAnnotations
    @NonNull
    @ColumnInfo(name = "buyer")
    public abstract AdTechIdentifier getBuyer();

    /**
     * @return the expiration time of the quarantine
     */
    @AutoValue.CopyAnnotations
    @NonNull
    @ColumnInfo(name = "quarantine_expiration_time")
    public abstract Instant getQuarantineExpirationTime();

    /**
     * Creates a {@link DBCustomAudienceQuarantine} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBCustomAudienceQuarantine create(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull Instant quarantineExpirationTime) {
        return builder()
                .setOwner(owner)
                .setBuyer(buyer)
                .setQuarantineExpirationTime(quarantineExpirationTime)
                .build();
    }

    /** Returns an AutoValue builder for a {@link DBCustomAudienceQuarantine} entity. */
    @NonNull
    public static DBCustomAudienceQuarantine.Builder builder() {
        return new AutoValue_DBCustomAudienceQuarantine.Builder();
    }

    /** Builder class for a {@link DBCustomAudienceQuarantine}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the owner */
        @NonNull
        public abstract Builder setOwner(@NonNull String value);

        /** Sets the buyer */
        @NonNull
        public abstract Builder setBuyer(@NonNull AdTechIdentifier value);

        /**
         * @return Sets the expiration time of the quarantine
         */
        @NonNull
        public abstract Builder setQuarantineExpirationTime(@NonNull Instant value);

        /** Builds the {@link DBCustomAudienceQuarantine}. */
        @NonNull
        public abstract DBCustomAudienceQuarantine build();
    }
}
