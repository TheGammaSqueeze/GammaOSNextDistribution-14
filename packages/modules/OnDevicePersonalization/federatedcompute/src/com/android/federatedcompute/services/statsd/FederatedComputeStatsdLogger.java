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

package com.android.federatedcompute.services.statsd;

import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_API_CALLED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED;

import com.android.federatedcompute.services.stats.FederatedComputeStatsLog;

/** Log API stats and client error stats to StatsD. */
public class FederatedComputeStatsdLogger {
    private static volatile FederatedComputeStatsdLogger sFCStatsdLogger = null;

    /** Returns an instance of {@link FederatedComputeStatsdLogger}. */
    public static FederatedComputeStatsdLogger getInstance() {
        if (sFCStatsdLogger == null) {
            synchronized (FederatedComputeStatsdLogger.class) {
                if (sFCStatsdLogger == null) {
                    sFCStatsdLogger = new FederatedComputeStatsdLogger();
                }
            }
        }
        return sFCStatsdLogger;
    }

    /** Log API call stats e.g. response code, API name etc. */
    public void logApiCallStats(ApiCallStats apiCallStats) {
        FederatedComputeStatsLog.write(
                FEDERATED_COMPUTE_API_CALLED,
                apiCallStats.getApiClass(),
                apiCallStats.getApiName(),
                apiCallStats.getLatencyMillis(),
                apiCallStats.getResponseCode());
    }

    /**
     * Log FederatedComputeTrainingEventReported to track each stage of federated computation job
     * execution.
     */
    public void logTrainingEventReported(TrainingEventReported trainingEvent) {
        FederatedComputeStatsLog.write(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED,
                trainingEvent.getClientVersion(),
                trainingEvent.getEventKind(),
                trainingEvent.getTaskId(),
                trainingEvent.getDurationInMillis(),
                trainingEvent.getExampleSize(),
                trainingEvent.getDataTransferDurationMillis(),
                trainingEvent.getBytesUploaded(),
                trainingEvent.getBytesDownloaded(),
                trainingEvent.getKeyAttestationLatencyMillis());
    }
}
