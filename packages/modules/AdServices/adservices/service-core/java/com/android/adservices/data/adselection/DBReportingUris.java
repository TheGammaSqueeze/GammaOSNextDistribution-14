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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

import java.util.Objects;

/**
 * Table to look up reporting URIs.
 *
 * @deprecated Columns moved to DBAuctionServerAdSelection table
 */
@Deprecated(since = "Columns moved to DBAuctionServerAdSelection table")
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "reporting_uris",
        indices = {@Index(value = {"ad_selection_id"})},
        primaryKeys = {"ad_selection_id"})
@TypeConverters({FledgeRoomConverters.class})
public abstract class DBReportingUris {
    /** The id associated with this auction. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "ad_selection_id")
    public abstract long getAdSelectionId();

    /** The reporting uri associated with seller. */
    @AutoValue.CopyAnnotations
    @NonNull
    @ColumnInfo(name = "seller_reporting_uri")
    public abstract Uri getSellerReportingUri();

    /** The reporting uri associated with auction winner/buyer. */
    @AutoValue.CopyAnnotations
    @NonNull
    @ColumnInfo(name = "buyer_reporting_uri")
    public abstract Uri getBuyerReportingUri();

    /** Returns an AutoValue builder for a {@link DBReportingUris} entity. */
    @NonNull
    public static DBReportingUris.Builder builder() {
        return new AutoValue_DBReportingUris.Builder();
    }

    /**
     * Creates a {@link DBReportingUris} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBReportingUris create(
            long adSelectionId, @NonNull Uri sellerReportingUri, @NonNull Uri buyerReportingUri) {
        Objects.requireNonNull(sellerReportingUri);
        Objects.requireNonNull(buyerReportingUri);

        return builder()
                .setAdSelectionId(adSelectionId)
                .setSellerReportingUri(sellerReportingUri)
                .setBuyerReportingUri(buyerReportingUri)
                .build();
    }

    /** Builder class for a {@link DBReportingUris}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets ad selection id. */
        public abstract DBReportingUris.Builder setAdSelectionId(long adSelectionId);

        /** Sets seller reporting uri. */
        public abstract DBReportingUris.Builder setSellerReportingUri(
                @NonNull Uri sellerReportingUri);

        /** Sets buyer reporting uri. */
        public abstract DBReportingUris.Builder setBuyerReportingUri(
                @NonNull Uri buyerReportingUri);

        /** Builds the {@link DBReportingUris}. */
        public abstract DBReportingUris build();
    }
}
