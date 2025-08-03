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

package com.android.ondevicepersonalization.services.util;

import android.adservices.ondevicepersonalization.CalleeMetadata;
import android.adservices.ondevicepersonalization.Constants;
import android.os.Bundle;

import com.android.ondevicepersonalization.services.statsd.ApiCallStats;
import com.android.ondevicepersonalization.services.statsd.OdpStatsdLogger;

/** Utilities for stats logging */
public class StatsUtils {
    /** Subtracts callee reported latency from caller reported latency. */
    public static long getOverheadLatencyMillis(long callerLatencyMillis, Bundle result) {
        long calleeLatencyMillis = callerLatencyMillis;
        if (result != null) {
            CalleeMetadata metadata =
                    result.getParcelable(Constants.EXTRA_CALLEE_METADATA, CalleeMetadata.class);
            if (metadata != null) {
                if (metadata.getElapsedTimeMillis() > 0
                        && metadata.getElapsedTimeMillis() < callerLatencyMillis) {
                    calleeLatencyMillis = metadata.getElapsedTimeMillis();
                }
            }
        }
        return callerLatencyMillis - calleeLatencyMillis;
    }

    /** Writes app request usage to statsd. */
    public static void writeAppRequestMetrics(Clock clock, int responseCode, long startTimeMillis) {
        int latencyMillis = (int) (clock.elapsedRealtime() - startTimeMillis);
        ApiCallStats callStats = new ApiCallStats.Builder(ApiCallStats.API_EXECUTE)
                .setLatencyMillis(latencyMillis)
                .setResponseCode(responseCode)
                .build();
        OdpStatsdLogger.getInstance().logApiCallStats(callStats);
    }

    /** Writes service request usage to statsd. */
    public static void writeServiceRequestMetrics(
            int apiName, Bundle result, Clock clock, int responseCode, long startTimeMillis) {
        int latencyMillis = (int) (clock.elapsedRealtime() - startTimeMillis);
        int overheadLatencyMillis =
                (int) StatsUtils.getOverheadLatencyMillis(latencyMillis, result);
        ApiCallStats callStats = new ApiCallStats.Builder(apiName)
                .setLatencyMillis(latencyMillis)
                .setOverheadLatencyMillis(overheadLatencyMillis)
                .setResponseCode(responseCode)
                .build();
        OdpStatsdLogger.getInstance().logApiCallStats(callStats);
    }
    private StatsUtils() {}
}
