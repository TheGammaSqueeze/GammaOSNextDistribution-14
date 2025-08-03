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

@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = "reporting_computation_info",
        primaryKeys = {"ad_selection_id"},
        indices = {@Index(value = {"ad_selection_id"})},
        foreignKeys =
                @ForeignKey(
                        entity = DBAdSelectionInitialization.class,
                        parentColumns = "ad_selection_id",
                        childColumns = "ad_selection_id",
                        onDelete = CASCADE))
@TypeConverters({FledgeRoomConverters.class})
public abstract class DBReportingComputationInfo {
    /** The id associated with this auction. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "ad_selection_id")
    public abstract long getAdSelectionId();

    /** Uri to fetch the bidding logic from */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "bidding_logic_uri")
    @NonNull
    public abstract Uri getBiddingLogicUri();

    /** Buyers javascript, which contains {@code reportWin} */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "buyer_decision_logic_js")
    @NonNull
    public abstract String getBuyerDecisionLogicJs();

    /** Contextual signals used in {@code reportResult} */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "seller_contextual_signals")
    @Nullable
    public abstract String getSellerContextualSignals();

    /** Contextual signals used in {@code reportWin} */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "buyer_contextual_signals")
    @Nullable
    public abstract String getBuyerContextualSignals();

    /** The Custom Audience signals of the ad which won the ad selection run. */
    @AutoValue.CopyAnnotations
    @Embedded(prefix = "custom_audience_signals_")
    @Nullable
    public abstract CustomAudienceSignals getCustomAudienceSignals();

    /** The winning ad bid for this ad selection run. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "winning_ad_bid")
    public abstract double getWinningAdBid();

    /** The winning ad render uri for this ad selection run. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "winning_ad_render_uri")
    @Nullable
    public abstract Uri getWinningAdRenderUri();

    /** Returns an AutoValue builder for a {@link DBReportingComputationInfo} entity. */
    @NonNull
    public static DBReportingComputationInfo.Builder builder() {
        return new AutoValue_DBReportingComputationInfo.Builder();
    }

    /**
     * Creates a {@link DBReportingComputationInfo} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBReportingComputationInfo create(
            long adSelectionId,
            @NonNull Uri biddingLogicUri,
            @NonNull String buyerDecisionLogicJs,
            @Nullable String sellerContextualSignals,
            @Nullable String buyerContextualSignals,
            @Nullable CustomAudienceSignals customAudienceSignals,
            double winningAdBid,
            @NonNull Uri winningAdRenderUri) {

        return builder()
                .setAdSelectionId(adSelectionId)
                .setBiddingLogicUri(biddingLogicUri)
                .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                .setSellerContextualSignals(sellerContextualSignals)
                .setBuyerContextualSignals(buyerContextualSignals)
                .setCustomAudienceSignals(customAudienceSignals)
                .setWinningAdBid(winningAdBid)
                .setWinningAdRenderUri(winningAdRenderUri)
                .build();
    }

    /** Builder class for a {@link DBReportingComputationInfo}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the adSelectionId */
        public abstract Builder setAdSelectionId(long value);

        /** Sets the biddingLogicUri */
        public abstract Builder setBiddingLogicUri(@NonNull Uri biddingLogicUri);

        /** Sets the buyerDecisionLogicJs */
        public abstract Builder setBuyerDecisionLogicJs(@NonNull String buyerDecisionLogicJs);

        /** Sets the sellerContextualSignals */
        public abstract Builder setSellerContextualSignals(
                @Nullable String sellerContextualSignals);

        /** Sets the buyerContextualSignals */
        public abstract Builder setBuyerContextualSignals(@Nullable String buyerContextualSignals);

        /** Sets the custom audience signals */
        public abstract Builder setCustomAudienceSignals(
                @Nullable CustomAudienceSignals customAudienceSignals);

        /** Sets the winning ad's bid */
        public abstract Builder setWinningAdBid(double winningAdBid);

        /** Sets the winning ad's render uri */
        public abstract Builder setWinningAdRenderUri(@NonNull Uri winningAdRenderUri);

        /** Builds a {@link DBAdSelectionResult} object. */
        public abstract DBReportingComputationInfo build();
    }
}
