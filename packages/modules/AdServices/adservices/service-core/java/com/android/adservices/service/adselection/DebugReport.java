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

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.data.adselection.CustomAudienceSignals;

import com.google.auto.value.AutoValue;

/**
 * Stores debugging information for buy-side, sell-side of a custom audience's outcome in an ad
 * selection auction.
 */
@AutoValue
abstract class DebugReport {

    @NonNull
    abstract CustomAudienceSignals getCustomAudienceSignals();

    @Nullable
    abstract AdTechIdentifier getSeller();

    @Nullable
    abstract Uri getWinDebugReportUri();

    @Nullable
    abstract Uri getLossDebugReportUri();

    @Nullable
    abstract String getSellerRejectReason();

    static Builder builder() {
        return new AutoValue_DebugReport.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

        abstract Builder setCustomAudienceSignals(CustomAudienceSignals customAudienceSignals);

        abstract Builder setSeller(AdTechIdentifier seller);

        abstract Builder setWinDebugReportUri(Uri winDebugReportUri);

        abstract Builder setLossDebugReportUri(Uri lossDebugReportUri);

        abstract Builder setSellerRejectReason(String sellerRejectReason);

        abstract DebugReport build();
    }
}
