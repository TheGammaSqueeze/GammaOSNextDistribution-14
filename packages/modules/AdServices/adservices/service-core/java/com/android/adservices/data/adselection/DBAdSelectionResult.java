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

import static androidx.room.ForeignKey.CASCADE;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;

import com.google.auto.value.AutoValue;

/** Table for records related to result of an ad selection run. */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = "ad_selection_result",
        indices = {@Index(value = {"ad_selection_id", "winning_buyer", "winning_ad_render_uri"})},
        foreignKeys =
                @ForeignKey(
                        entity = DBAdSelectionInitialization.class,
                        parentColumns = "ad_selection_id",
                        childColumns = "ad_selection_id",
                        onDelete = CASCADE),
        primaryKeys = {"ad_selection_id"})
@TypeConverters({FledgeRoomConverters.class})
public abstract class DBAdSelectionResult {

    /** The id associated with this auction. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "ad_selection_id")
    public abstract long getAdSelectionId();

    /** The buyer whose ad won this ad selection run. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "winning_buyer")
    @Nullable
    public abstract AdTechIdentifier getWinningBuyer();

    /** The winning ad bid for this ad selection run. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "winning_ad_bid")
    public abstract double getWinningAdBid();

    /** The winning ad render uri for this ad selection run. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "winning_ad_render_uri")
    @Nullable
    public abstract Uri getWinningAdRenderUri();

    /** The winning custom audience associated with this ad selection run. */
    @AutoValue.CopyAnnotations
    @Nullable
    @Embedded(prefix = "winning_custom_audience_")
    public abstract DBWinningCustomAudience getWinningCustomAudience();

    /** Returns an AutoValue builder for a {@link DBAdSelectionResult} entity. */
    @NonNull
    public static DBAdSelectionResult.Builder builder() {
        return new AutoValue_DBAdSelectionResult.Builder();
    }

    /**
     * Creates a {@link DBAdSelectionResult} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBAdSelectionResult create(
            long adSelectionId,
            @Nullable AdTechIdentifier winningBuyer,
            double winningAdBid,
            @Nullable Uri winningAdRenderUri,
            @Nullable DBWinningCustomAudience winningCustomAudience) {

        return builder()
                .setAdSelectionId(adSelectionId)
                .setWinningBuyer(winningBuyer)
                .setWinningAdBid(winningAdBid)
                .setWinningAdRenderUri(winningAdRenderUri)
                .setWinningCustomAudience(winningCustomAudience)
                .build();
    }

    /** Builder class for a {@link DBAdSelectionResult}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the id associated with this auction. */
        public abstract Builder setAdSelectionId(long adSelectionId);

        /** Sets the buyer whose ad won this ad selection run. */
        public abstract Builder setWinningBuyer(@Nullable AdTechIdentifier winningBuyer);

        /** Sets the winning ad bid for this ad selection run. */
        public abstract Builder setWinningAdBid(double winningAdBid);

        /** Sets the winning ad render uri for this ad selection run. */
        public abstract Builder setWinningAdRenderUri(@Nullable Uri winningAdRenderUri);

        /** Sets the winning custom audience associated with this ad selection run */
        public abstract Builder setWinningCustomAudience(
                @Nullable DBWinningCustomAudience winningCustomAudience);

        /** Builds a {@link DBAdSelectionResult} object. */
        public abstract DBAdSelectionResult build();
    }
}
