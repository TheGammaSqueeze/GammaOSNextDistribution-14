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
import android.adservices.common.FrequencyCapFilters;
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;

import java.time.Instant;
import java.util.Objects;

/**
 * No-op implementation for an {@link AdCounterHistogramUpdater} which does nothing when ad
 * filtering is disabled.
 */
public class AdCounterHistogramUpdaterNoOpImpl implements AdCounterHistogramUpdater {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public AdCounterHistogramUpdaterNoOpImpl() {}

    @Override
    public void updateWinHistogram(@NonNull DBAdSelection dbAdSelection) {
        Objects.requireNonNull(dbAdSelection);
        sLogger.v("Ad filtering is disabled; skipping win histogram update");
    }

    @Override
    public void updateWinHistogram(
            @NonNull AdTechIdentifier buyer,
            @NonNull AdSelectionInitialization adSelectionInitialization,
            @NonNull WinningCustomAudience winningCustomAudience) {
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(adSelectionInitialization);
        Objects.requireNonNull(winningCustomAudience);
        sLogger.v("Ad filtering is disabled; skipping win histogram update");
    }

    @Override
    public void updateNonWinHistogram(
            long adSelectionId,
            @NonNull String callerPackageName,
            @FrequencyCapFilters.AdEventType int adEventType,
            @NonNull Instant eventTimestamp) {
        Objects.requireNonNull(callerPackageName);
        Objects.requireNonNull(eventTimestamp);
        sLogger.v("Ad filtering is disabled; skipping non-win histogram update");
    }
}
