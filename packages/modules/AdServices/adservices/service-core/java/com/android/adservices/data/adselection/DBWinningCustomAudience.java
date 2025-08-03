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

import android.annotation.NonNull;
import android.annotation.Nullable;

import androidx.room.ColumnInfo;

import com.google.auto.value.AutoValue;

import java.util.Set;

/**
 * This class represents the data related to a custom audience which has won an ad selection run.
 */
@AutoValue
public abstract class DBWinningCustomAudience {

    /** Name of the winning custom audience. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "name")
    @Nullable
    public abstract String getName();

    /** Owner of the winning custom audience, i.e. the caller package name */
    @AutoValue.CopyAnnotations
    @Nullable
    @ColumnInfo(name = "owner")
    public abstract String getOwner();

    /** Ad counter keys for the winning ad in the winning custom audience. */
    @AutoValue.CopyAnnotations
    @Nullable
    @ColumnInfo(name = "ad_counter_int_keys")
    public abstract Set<Integer> getAdCounterIntKeys();

    /** Builder for {@link DBWinningCustomAudience} */
    @NonNull
    public static Builder builder() {
        return new AutoValue_DBWinningCustomAudience.Builder();
    }

    /**
     * Creates a {@link DBWinningCustomAudience} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBWinningCustomAudience create(
            @Nullable String name,
            @Nullable String owner,
            @Nullable Set<Integer> adCounterIntKeys) {
        return builder()
                .setName(name)
                .setOwner(owner)
                .setAdCounterIntKeys(adCounterIntKeys)
                .build();
    }

    /** Builder class for a {@link DBWinningCustomAudience}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the name of the winning custom audience. */
        public abstract Builder setName(@Nullable String name);

        /** Sets the owner of the winning custom audience, i.e. the caller package name */
        public abstract Builder setOwner(@Nullable String owner);

        /** Sets the ad counter keys for the winning ad in the winning custom audience. */
        public abstract Builder setAdCounterIntKeys(@Nullable Set<Integer> adCounterIntKeys);

        /** Builds a {@link DBWinningCustomAudience} object. */
        public abstract DBWinningCustomAudience build();
    }
}
