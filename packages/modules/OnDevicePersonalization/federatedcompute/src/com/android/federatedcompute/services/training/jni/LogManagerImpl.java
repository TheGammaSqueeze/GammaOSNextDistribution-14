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

package com.android.federatedcompute.services.training.jni;

import com.android.federatedcompute.internal.util.LogUtil;

import com.google.common.base.Preconditions;
import com.google.intelligence.fcp.client.DebugDiagCode;
import com.google.intelligence.fcp.client.HistogramCounters;
import com.google.intelligence.fcp.client.ProdDiagCode;

import javax.annotation.Nullable;

/**
 * An implementation of the NativeLogManager interface based on {@link LogManager}, used by C++
 * code.
 */
public class LogManagerImpl implements LogManager {
    private static final String TAG = LogManagerImpl.class.getSimpleName();
    private final String mClientPackageName;

    public LogManagerImpl(String clientPackageName) {
        this.mClientPackageName = clientPackageName;
    }

    @Override
    public void logProdDiag(int prodDiagCode) {
        // The diag code comes from a C++ engine which could theoretically supply
        // invalid codes.
        ProdDiagCode diagCode = ProdDiagCode.forNumber(prodDiagCode);
        Preconditions.checkNotNull(diagCode);
        LogUtil.i(TAG, "Send FL diagnosis log %s for package %s", diagCode, mClientPackageName);
    }

    @Override
    public void logDebugDiag(int debugDiagCode) {
        DebugDiagCode diagCode = DebugDiagCode.forNumber(debugDiagCode);
        Preconditions.checkNotNull(diagCode);
        LogUtil.i(TAG, "Send FL diagnosis log %s for package %s", diagCode, mClientPackageName);
    }

    @Override
    public void logToLongHistogram(
            int histogramCounterCode,
            int executionIndex,
            int epochIndex,
            int dataSourceTypeCode,
            long value) {
        logToLongHistogram(
                histogramCounterCode, executionIndex, epochIndex, dataSourceTypeCode, null, value);
    }

    @Override
    public void logToLongHistogram(
            int histogramCounterCode,
            int executionIndex,
            int epochIndex,
            int dataSourceTypeCode,
            @Nullable String modelIdentifier,
            long value) {
        HistogramCounters histogramCounter = HistogramCounters.forNumber(histogramCounterCode);
        Preconditions.checkNotNull(histogramCounter);
        LogUtil.i(
                TAG,
                "Calling logToLongHistogram %d %d %d %d %d",
                histogramCounterCode,
                executionIndex,
                epochIndex,
                dataSourceTypeCode,
                value);
        // TODO: implement histogram counter logic in LogManager.

    }
}
