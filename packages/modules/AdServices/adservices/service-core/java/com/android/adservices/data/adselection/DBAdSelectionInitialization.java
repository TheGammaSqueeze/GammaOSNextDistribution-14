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

package com.android.adservices.data.adselection;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;

import com.google.auto.value.AutoValue;

import java.time.Instant;
import java.util.Objects;

/** Table to persist records related to start of an ad selection run. */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = "ad_selection_initialization",
        indices = {@Index(value = {"ad_selection_id", "caller_package_name"})},
        primaryKeys = {"ad_selection_id"})
@TypeConverters({FledgeRoomConverters.class})
public abstract class DBAdSelectionInitialization {

    /** The id associated with this auction. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "ad_selection_id")
    public abstract long getAdSelectionId();

    /** The creation time for this ad selection run. */
    @AutoValue.CopyAnnotations
    @NonNull
    @ColumnInfo(name = "creation_instant")
    public abstract Instant getCreationInstant();

    /** The seller ad tech who requested this auction. */
    @AutoValue.CopyAnnotations
    @Nullable
    @ColumnInfo(name = "seller")
    public abstract AdTechIdentifier getSeller();

    /** The caller package which initiated this ad selection run. */
    @AutoValue.CopyAnnotations
    @Nullable
    @ColumnInfo(name = "caller_package_name")
    public abstract String getCallerPackageName();

    /** Returns an AutoValue builder for a {@link DBAdSelectionInitialization} entity. */
    @NonNull
    public static DBAdSelectionInitialization.Builder builder() {
        return new AutoValue_DBAdSelectionInitialization.Builder();
    }

    /**
     * Creates a {@link DBAdSelectionInitialization} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBAdSelectionInitialization create(
            long adSelectionId,
            @NonNull Instant creationInstant,
            @Nullable AdTechIdentifier seller,
            @Nullable String callerPackageName) {
        Objects.requireNonNull(creationInstant);

        return builder()
                .setAdSelectionId(adSelectionId)
                .setCreationInstant(creationInstant)
                .setSeller(seller)
                .setCallerPackageName(callerPackageName)
                .build();
    }

    /** Builder class for a {@link DBAdSelectionInitialization}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets ad selection id. */
        public abstract Builder setAdSelectionId(long adSelectionId);

        /** Sets the creation instant for this ad selection run initialization. */
        public abstract Builder setCreationInstant(@NonNull Instant creationInstant);

        /** Sets seller ad tech. */
        public abstract Builder setSeller(@Nullable AdTechIdentifier seller);

        /** Sets the caller package name which initiated this ad selection run. */
        public abstract Builder setCallerPackageName(@Nullable String callerPackageName);

        /** Builds a {@link DBAdSelectionInitialization} object. */
        public abstract DBAdSelectionInitialization build();
    }
}
