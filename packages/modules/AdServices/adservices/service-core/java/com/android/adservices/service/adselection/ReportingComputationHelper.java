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

import com.android.adservices.data.adselection.datahandlers.ReportingComputationData;

import java.util.Objects;

interface ReportingComputationHelper {
    boolean doesAdSelectionIdExist(long adSelectionId);

    boolean doesAdSelectionMatchingCallerPackageNameExist(
            long adSelectionId, String callerPackageName);

    ReportingComputationData getReportingComputation(long adSelectionId);

    default AdSelectionSignals parseAdSelectionSignalsOrEmpty(String signals) {
        return Objects.isNull(signals)
                ? AdSelectionSignals.EMPTY
                : AdSelectionSignals.fromString(signals);
    }
}
