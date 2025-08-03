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

import javax.annotation.Nullable;

/**
 * This class offers logging functionality to c++ code. It will be used by
 * //external/federatedcompute/fcp/client/log_manager.h.
 */
public interface LogManager {

    /**
     * Called to log a {@link com.google.android.libraries.micore.learning.proto.ProdDiagCode}.
     *
     * @param prodDiagCode a serialized ProdDiagCode.
     */
    void logProdDiag(int prodDiagCode);

    /**
     * Called to log a {@link com.google.android.libraries.micore.learning.proto.DebugDiagCode}.
     *
     * @param debugDiagCode a serialized DebugDiagCode.
     */
    void logDebugDiag(int debugDiagCode);

    /**
     * Like {@link #logToLongHistogram(int, int, int, int, String, long)}, but without attaching a
     * model identifier.
     */
    void logToLongHistogram(
            int histogramCounterCode,
            int executionIndex,
            int epochIndex,
            int dataSourceType,
            long value);

    /** Called to log a long value, by adding it to an annotated histogram. */
    void logToLongHistogram(
            int histogramCounterCode,
            int executionIndex,
            int epochIndex,
            int dataSourceType,
            @Nullable String modelIdentifier,
            long value);
}
