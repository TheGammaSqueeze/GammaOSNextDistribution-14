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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.DataVersionFetcher.getBuyerDataVersion;

import android.adservices.common.AdSelectionSignals;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import java.util.Map;
import java.util.Objects;

/** Contains implementation of extracting data version header into buyer contextual signals. */
public class BuyerContextualSignalsDataVersionImpl
        implements BuyerContextualSignalsDataVersionFetcher {

    /**
     * Tries to fetch the data version from trusted bidding data headers. If it exists, returns
     * {@link AdSelectionSignals} with data version set. Otherwise, returns {@link
     * AdSelectionSignals#EMPTY}
     */
    @Override
    @NonNull
    public AdSelectionSignals getContextualSignalsForGenerateBid(
            @NonNull DBTrustedBiddingData trustedBiddingData,
            @NonNull Map<Uri, TrustedBiddingResponse> trustedBiddingDataByBaseUri) {
        Objects.requireNonNull(trustedBiddingData);
        Objects.requireNonNull(trustedBiddingDataByBaseUri);
        try {
            int dataVersion = getBuyerDataVersion(trustedBiddingData, trustedBiddingDataByBaseUri);
            return BuyerContextualSignals.builder()
                    .setDataVersion(dataVersion)
                    .build()
                    .toAdSelectionSignals();
        } catch (IllegalStateException e) {
            return AdSelectionSignals.EMPTY;
        }
    }

    /**
     * Tries to fetch the data version from trusted bidding data headers. If it exists, returns
     * {@link BuyerContextualSignals} with data version set and {@link AdCost} set if it exists. If
     * both do not exist, returns null/
     */
    @Override
    @Nullable
    public BuyerContextualSignals getContextualSignalsForReportWin(
            @NonNull DBTrustedBiddingData trustedBiddingData,
            @NonNull Map<Uri, TrustedBiddingResponse> trustedBiddingDataByBaseUri,
            @Nullable AdCost adCost) {
        Objects.requireNonNull(trustedBiddingData);
        Objects.requireNonNull(trustedBiddingDataByBaseUri);

        BuyerContextualSignals.Builder builder = BuyerContextualSignals.builder().setAdCost(adCost);

        try {
            builder.setDataVersion(
                    getBuyerDataVersion(trustedBiddingData, trustedBiddingDataByBaseUri));
        } catch (IllegalStateException e) {
            LogUtil.v("Data version Header does not exist!");
        }

        BuyerContextualSignals result = builder.build();

        // Just return a null object if both fields are null
        if (Objects.isNull(result.getAdCost()) && Objects.isNull(result.getDataVersion())) {
            return null;
        }
        return result;
    }
}
