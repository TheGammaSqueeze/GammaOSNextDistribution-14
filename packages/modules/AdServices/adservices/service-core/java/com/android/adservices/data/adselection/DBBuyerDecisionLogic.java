/*
 * Copyright (C) 2022 The Android Open Source Project
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
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Objects;

/**
 * This POJO represents the BuyerDecisionLogic in the buyer_decision_logic table.
 *
 * @hide
 */
@Entity(tableName = "buyer_decision_logic")
public final class DBBuyerDecisionLogic {
    @ColumnInfo(name = "bidding_logic_uri")
    @PrimaryKey
    @NonNull
    private final Uri mBiddingLogicUri;

    @ColumnInfo(name = "buyer_decision_logic_js")
    @NonNull
    private final String mBuyerDecisionLogicJs;

    /**
     * @param biddingLogicUri An {@link Uri} object defining the URI to fetch the buyer-provided
     *     bidding and reporting javascript.
     * @param buyerDecisionLogicJs A {@link String} object contains both the generateBid() and
     *     reportResult() javascript fetched from the biddingLogicUri.
     */
    public DBBuyerDecisionLogic(
            @NonNull Uri biddingLogicUri, @NonNull String buyerDecisionLogicJs) {
        Objects.requireNonNull(biddingLogicUri);
        Objects.requireNonNull(buyerDecisionLogicJs);

        mBiddingLogicUri = biddingLogicUri;
        mBuyerDecisionLogicJs = buyerDecisionLogicJs;
    }

    /** @return the bidding logic uri. */
    @NonNull
    public Uri getBiddingLogicUri() {
        return mBiddingLogicUri;
    }

    /**
     * @return the string contains the buyer-side provided generateBit() and reportResult()
     *     javascript.
     */
    @NonNull
    public String getBuyerDecisionLogicJs() {
        return mBuyerDecisionLogicJs;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof DBBuyerDecisionLogic)) return false;
        DBBuyerDecisionLogic buyerDecisionLogic = (DBBuyerDecisionLogic) o;
        return mBiddingLogicUri.equals(buyerDecisionLogic.mBiddingLogicUri)
                && mBuyerDecisionLogicJs.equals(buyerDecisionLogic.mBuyerDecisionLogicJs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBiddingLogicUri, mBuyerDecisionLogicJs);
    }

    /** Builder for {@link DBBuyerDecisionLogic} object. */
    public static final class Builder {
        @NonNull private Uri mBiddingLogicUri;

        @NonNull private String mBuyerDecisionLogicJs;

        public Builder() {}

        /** Sets the Bidding Logic Uri. */
        @NonNull
        public DBBuyerDecisionLogic.Builder setBiddingLogicUri(@NonNull Uri biddingLogicUri) {
            Objects.requireNonNull(biddingLogicUri);

            this.mBiddingLogicUri = biddingLogicUri;
            return this;
        }

        /** Sets the Buyer Decision Logic JS. */
        @NonNull
        public DBBuyerDecisionLogic.Builder setBuyerDecisionLogicJs(
                @NonNull String buyerDecisionLogicJs) {
            Objects.requireNonNull(buyerDecisionLogicJs);

            this.mBuyerDecisionLogicJs = buyerDecisionLogicJs;
            return this;
        }

        /**
         * Builds an {@link DBBuyerDecisionLogic} instance.
         *
         * @throws NullPointerException if any non-null params are null.
         */
        @NonNull
        public DBBuyerDecisionLogic build() {
            Objects.requireNonNull(mBiddingLogicUri);
            Objects.requireNonNull(mBuyerDecisionLogicJs);

            return new DBBuyerDecisionLogic(mBiddingLogicUri, mBuyerDecisionLogicJs);
        }
    }
}
