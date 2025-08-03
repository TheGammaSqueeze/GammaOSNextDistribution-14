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

import android.adservices.adselection.AdWithBid;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

@AutoValue
abstract class GenerateBidResult {
    abstract AdWithBid getAdWithBid();

    @Nullable
    abstract Uri getWinDebugReportUri();

    @Nullable
    abstract Uri getLossDebugReportUri();

    @Nullable
    abstract AdCost getAdCost();

    static Builder builder() {
        return new AutoValue_GenerateBidResult.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setAdWithBid(AdWithBid adWithBid);

        abstract Builder setWinDebugReportUri(Uri winDebugReportUri);

        abstract Builder setLossDebugReportUri(Uri lossDebugReportUri);

        abstract Builder setAdCost(AdCost adCost);

        abstract GenerateBidResult build();
    }
}
