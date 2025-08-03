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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;

import com.google.common.util.concurrent.FluentFuture;

import java.util.List;

/**
 * Interface that generates Scores for Ads that have been through auction
 */
public interface AdsScoreGenerator {

    /**
     * @param adBiddingOutcomes results from running bidding
     * @param adSelectionConfig data provided by seller for running ad Selection
     * @return a Future of {link @AdScoringOutcome}
     * @throws AdServicesException in case of scoring failure
     */
    FluentFuture<List<AdScoringOutcome>> runAdScoring(
            @NonNull List<AdBiddingOutcome> adBiddingOutcomes,
            @NonNull AdSelectionConfig adSelectionConfig) throws AdServicesException;
}
