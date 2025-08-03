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

import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;

import java.time.Instant;

/**
 * Interface for updating ad counter histograms either during ad selection or after the service is
 * called.
 */
public interface AdCounterHistogramUpdater {
    /**
     * Updates the ad counter histogram for the buyer and custom audience with a win event.
     *
     * <p>Works with on-device ad selection DB objects that are in process of being replaced in
     * go/rb-rm-unified-adselection-dao-design
     */
    void updateWinHistogram(@NonNull DBAdSelection dbAdSelection);

    /**
     * Updates the ad counter histogram for the buyer and custom audience with a win event.
     *
     * <p>Works with the new storage design in go/rb-rm-unified-adselection-dao-design
     */
    void updateWinHistogram(
            @NonNull AdTechIdentifier buyer,
            @NonNull AdSelectionInitialization adSelectionInitialization,
            @NonNull WinningCustomAudience winningCustomAudience);

    /**
     * Updates the ad counter histogram for the ad associated with the given ad selection ID with a
     * non-win event.
     */
    void updateNonWinHistogram(
            long adSelectionId,
            @NonNull String callerPackageName,
            @FrequencyCapFilters.AdEventType int adEventType,
            @NonNull Instant eventTimestamp);
}
