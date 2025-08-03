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

import android.adservices.common.AdSelectionSignals;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import java.util.Map;
import java.util.Objects;

/** No op implementation of {@link BuyerContextualSignalsDataVersionFetcher} */
public class BuyerContextualSignalsDataVersionFetcherNoOpImpl
        implements BuyerContextualSignalsDataVersionFetcher {

    /** Returns {@link AdSelectionSignals#EMPTY} since data version header is disabled. */
    @Override
    @NonNull
    public AdSelectionSignals getContextualSignalsForGenerateBid(
            @NonNull DBTrustedBiddingData trustedBiddingData,
            @NonNull Map<Uri, TrustedBiddingResponse> trustedBiddingDataByBaseUri) {
        Objects.requireNonNull(trustedBiddingData);
        Objects.requireNonNull(trustedBiddingDataByBaseUri);
        return AdSelectionSignals.EMPTY;
    }

    /**
     * Returns {@link BuyerContextualSignals} only with {@link AdCost} if it exists, otherwise
     * returns null;
     */
    @Override
    @Nullable
    public BuyerContextualSignals getContextualSignalsForReportWin(
            @NonNull DBTrustedBiddingData trustedBiddingData,
            @NonNull Map<Uri, TrustedBiddingResponse> trustedBiddingDataByBaseUri,
            @Nullable AdCost adCost) {
        Objects.requireNonNull(trustedBiddingData);
        Objects.requireNonNull(trustedBiddingDataByBaseUri);

        if (Objects.isNull(adCost)) {
            return null;
        }
        return BuyerContextualSignals.builder().setAdCost(adCost).build();
    }
}
