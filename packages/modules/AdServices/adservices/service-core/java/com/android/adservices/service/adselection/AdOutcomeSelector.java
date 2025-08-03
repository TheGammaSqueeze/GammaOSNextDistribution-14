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

package com.android.adservices.service.adselection;

import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.annotation.NonNull;

import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;

import com.google.common.util.concurrent.FluentFuture;

import java.util.List;

/**
 * Interface that selects ad outcome from list of ad outcomes that have been winners of {@link
 * android.adservices.adselection.AdSelectionManager#selectAds}
 */
public interface AdOutcomeSelector {
    /**
     * @param adSelectionIdWithBidAndRenderUris list of ad selection id and bid pairs
     * @param adSelectionFromOutcomesConfig {@link AdSelectionFromOutcomesConfig} instance
     * @return a Future of {@code Long} {code @AdSelectionId} of the winner. If no winner then
     *     returns null
     */
    FluentFuture<Long> runAdOutcomeSelector(
            @NonNull List<AdSelectionResultBidAndUri> adSelectionIdWithBidAndRenderUris,
            @NonNull AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig);
}
