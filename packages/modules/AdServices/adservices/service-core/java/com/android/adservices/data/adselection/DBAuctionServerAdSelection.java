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
import android.annotation.Nullable;
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
import java.util.Set;

/** Table to look up auction server ad selection data. */
@AutoValue
@CopyAnnotations
@Entity(
        tableName = "auction_server_ad_selection",
        indices = {@Index(value = {"ad_selection_id"})},
        primaryKeys = {"ad_selection_id"})
@TypeConverters({FledgeRoomConverters.class})
public abstract class DBAuctionServerAdSelection {
    /** The id associated with this auction. */
    @CopyAnnotations
    @ColumnInfo(name = "ad_selection_id")
    public abstract long getAdSelectionId();

    /** The seller ad tech who requested this auction. */
    @CopyAnnotations
    @NonNull
    @ColumnInfo(name = "seller")
    public abstract AdTechIdentifier getSeller();

    /** The buyer ad tech who won this auction. */
    @CopyAnnotations
    @Nullable
    @ColumnInfo(name = "winner_buyer")
    public abstract AdTechIdentifier getWinnerBuyer();

    /**
     * The winner ad render uri for this auction.
     *
     * <p>Sets {@link Uri#EMPTY} if no remarketing winner
     */
    @CopyAnnotations
    @Nullable
    @ColumnInfo(name = "winner_ad_render_uri")
    public abstract Uri getWinnerAdRenderUri();

    /** Ad counter keys for histogram reporting */
    @CopyAnnotations
    @Nullable
    @ColumnInfo(name = "ad_counter_int_keys")
    public abstract Set<Integer> getAdCounterIntKeys();

    // TODO(b/287157063): Add beacon for interaction reporting

    /** The reporting uri associated with seller. */
    @CopyAnnotations
    @Nullable
    @ColumnInfo(name = "seller_win_reporting_uri")
    public abstract Uri getSellerReportingUri();

    /** The reporting uri associated with auction winner/buyer. */
    @CopyAnnotations
    @Nullable
    @ColumnInfo(name = "buyer_win_reporting_uri")
    public abstract Uri getBuyerReportingUri();

    /** Returns an AutoValue builder for a {@link DBAuctionServerAdSelection} entity. */
    @NonNull
    public static DBAuctionServerAdSelection.Builder builder() {
        return new AutoValue_DBAuctionServerAdSelection.Builder();
    }

    /**
     * Creates a {@link DBAuctionServerAdSelection} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBAuctionServerAdSelection create(
            long adSelectionId,
            @NonNull AdTechIdentifier seller,
            @Nullable AdTechIdentifier winnerBuyer,
            @Nullable Uri winnerAdRenderUri,
            @Nullable Set<Integer> adCounterIntKeys,
            @Nullable Uri sellerReportingUri,
            @Nullable Uri buyerReportingUri) {
        Objects.requireNonNull(seller);

        return builder()
                .setAdSelectionId(adSelectionId)
                .setSeller(seller)
                .setWinnerBuyer(winnerBuyer)
                .setWinnerAdRenderUri(winnerAdRenderUri)
                .setAdCounterIntKeys(adCounterIntKeys)
                .setSellerReportingUri(sellerReportingUri)
                .setBuyerReportingUri(buyerReportingUri)
                .build();
    }

    /** Builder class for a {@link DBAuctionServerAdSelection}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets ad selection id. */
        public abstract DBAuctionServerAdSelection.Builder setAdSelectionId(long adSelectionId);

        /** Sets seller ad tech. */
        public abstract DBAuctionServerAdSelection.Builder setSeller(
                @NonNull AdTechIdentifier seller);

        /** Sets seller ad tech. */
        public abstract DBAuctionServerAdSelection.Builder setWinnerBuyer(
                @Nullable AdTechIdentifier winningBuyer);

        /** Sets ad render uri. */
        public abstract DBAuctionServerAdSelection.Builder setWinnerAdRenderUri(
                @Nullable Uri winnerAdRenderUri);

        /** Sets ad counter keys */
        public abstract DBAuctionServerAdSelection.Builder setAdCounterIntKeys(
                @Nullable Set<Integer> adCounterIntKeys);

        /** Sets seller reporting uri. */
        public abstract DBAuctionServerAdSelection.Builder setSellerReportingUri(
                @Nullable Uri sellerReportingUri);

        /** Sets buyer reporting uri. */
        public abstract DBAuctionServerAdSelection.Builder setBuyerReportingUri(
                @Nullable Uri buyerReportingUri);

        /** Builds the {@link DBAuctionServerAdSelection}. */
        public abstract DBAuctionServerAdSelection build();
    }
}
