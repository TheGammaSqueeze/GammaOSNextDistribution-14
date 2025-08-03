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

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;

import java.time.Instant;

@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = DBScheduledCustomAudienceUpdate.TABLE_NAME,
        indices = {
            @Index(
                    value = {"owner", "buyer", "update_uri"},
                    unique = true)
        })
public abstract class DBScheduledCustomAudienceUpdate {
    public static final String TABLE_NAME = "scheduled_custom_audience_update";

    /** Unique id associated with each delayed update */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "update_id")
    @PrimaryKey(autoGenerate = true)
    @Nullable
    public abstract Long getUpdateId();

    /** Owner package for scheduling the update */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "owner")
    @NonNull
    public abstract String getOwner();

    /** Buyer associated with the delayed update */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "buyer")
    @NonNull
    public abstract AdTechIdentifier getBuyer();

    /** Endpoint for DSP which provides the update for custom audience */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "update_uri")
    @NonNull
    public abstract Uri getUpdateUri();

    /** Scheduled time for triggering the update */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "scheduled_time")
    @NonNull
    public abstract Instant getScheduledTime();

    /** Time at which update was created */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "creation_time")
    @NonNull
    public abstract Instant getCreationTime();

    /** Tracks if the update was created by a debuggable app or not */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "is_debuggable", defaultValue = "false")
    public abstract boolean getIsDebuggable();

    /** Provides a {@link DBScheduledCustomAudienceUpdate.Builder} */
    @NonNull
    public static DBScheduledCustomAudienceUpdate.Builder builder() {
        return new AutoValue_DBScheduledCustomAudienceUpdate.Builder()
                .setUpdateId(null)
                .setIsDebuggable(false);
    }

    /** Creates an instance of {@link DBScheduledCustomAudienceUpdate} */
    @NonNull
    public static DBScheduledCustomAudienceUpdate create(
            @Nullable Long updateId,
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull Uri updateUri,
            @NonNull Instant scheduledTime,
            @NonNull Instant creationTime,
            boolean isDebuggable) {
        return builder()
                .setUpdateId(updateId)
                .setOwner(owner)
                .setBuyer(buyer)
                .setUpdateUri(updateUri)
                .setScheduledTime(scheduledTime)
                .setCreationTime(creationTime)
                .setIsDebuggable(isDebuggable)
                .build();
    }

    /** Builder for creating an object of type {@link DBScheduledCustomAudienceUpdate} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** see {@link #getUpdateId()} */
        public abstract Builder setUpdateId(Long updateId);

        /** see {@link #getOwner()} */
        @NonNull
        public abstract Builder setOwner(@NonNull String owner);

        /** see {@link #getBuyer()} */
        @NonNull
        public abstract Builder setBuyer(@NonNull AdTechIdentifier buyer);

        /** see {@link #getUpdateUri()} */
        @NonNull
        public abstract Builder setUpdateUri(@NonNull Uri updateUri);

        /** see {@link #getScheduledTime()} */
        @NonNull
        public abstract Builder setScheduledTime(@NonNull Instant scheduledTime);

        /** see {@link #getCreationTime()} */
        @NonNull
        public abstract Builder setCreationTime(@NonNull Instant creationTime);

        /** see {@link #getIsDebuggable()} */
        @NonNull
        public abstract Builder setIsDebuggable(boolean value);

        /** Builds a {@link DBScheduledCustomAudienceUpdate.Builder} */
        @NonNull
        public abstract DBScheduledCustomAudienceUpdate build();
    }
}
