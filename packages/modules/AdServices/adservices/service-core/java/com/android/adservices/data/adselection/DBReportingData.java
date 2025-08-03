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

import android.annotation.Nullable;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;

import com.google.auto.value.AutoValue;

import java.util.Objects;

/** Table for records related to auction result reporting of an ad selection run. */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = "reporting_data",
        indices = {@Index(value = {"ad_selection_id"})},
        foreignKeys =
                @ForeignKey(
                        entity = DBAdSelectionInitialization.class,
                        parentColumns = "ad_selection_id",
                        childColumns = "ad_selection_id",
                        onDelete = CASCADE),
        primaryKeys = {"ad_selection_id"})
@TypeConverters({FledgeRoomConverters.class})
public abstract class DBReportingData {
    /** The id associated with this auction. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "ad_selection_id")
    public abstract long getAdSelectionId();

    /** The reporting uri associated with seller. */
    @AutoValue.CopyAnnotations
    @Nullable
    @ColumnInfo(name = "seller_reporting_uri")
    public abstract Uri getSellerReportingUri();

    /** The reporting uri associated with auction winner/buyer. */
    @AutoValue.CopyAnnotations
    @Nullable
    @ColumnInfo(name = "buyer_reporting_uri")
    public abstract Uri getBuyerReportingUri();

    /** Returns an AutoValue builder for a {@link DBReportingData} entity. */
    @NonNull
    public static DBReportingData.Builder builder() {
        return new AutoValue_DBReportingData.Builder();
    }

    /**
     * Creates a {@link DBReportingData} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBReportingData create(
            long adSelectionId, @Nullable Uri sellerReportingUri, @Nullable Uri buyerReportingUri) {
        Objects.requireNonNull(sellerReportingUri);
        Objects.requireNonNull(buyerReportingUri);

        return builder()
                .setAdSelectionId(adSelectionId)
                .setSellerReportingUri(sellerReportingUri)
                .setBuyerReportingUri(buyerReportingUri)
                .build();
    }

    /** Builder class for a {@link DBReportingData}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets ad selection id. */
        public abstract DBReportingData.Builder setAdSelectionId(long adSelectionId);

        /** Sets seller reporting uri. */
        public abstract DBReportingData.Builder setSellerReportingUri(
                @Nullable Uri sellerReportingUri);

        /** Sets buyer reporting uri. */
        public abstract DBReportingData.Builder setBuyerReportingUri(
                @Nullable Uri buyerReportingUri);

        /** Builds the {@link DBReportingData}. */
        public abstract DBReportingData build();
    }
}
