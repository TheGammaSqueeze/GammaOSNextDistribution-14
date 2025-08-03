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

package com.android.ondevicepersonalization.services.statsd;

import static com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog.ONDEVICEPERSONALIZATION_API_CALLED;

import com.android.ondevicepersonalization.OnDevicePersonalizationStatsLog;

/** Log API stats and client error stats to StatsD. */
public class OdpStatsdLogger {
    private static volatile OdpStatsdLogger sStatsdLogger = null;

    /** Returns an instance of {@link OdpStatsdLogger}. */
    public static OdpStatsdLogger getInstance() {
        if (sStatsdLogger == null) {
            synchronized (OdpStatsdLogger.class) {
                if (sStatsdLogger == null) {
                    sStatsdLogger = new OdpStatsdLogger();
                }
            }
        }
        return sStatsdLogger;
    }

    /** Log API call stats e.g. response code, API name etc. */
    public void logApiCallStats(ApiCallStats apiCallStats) {
        OnDevicePersonalizationStatsLog.write(
                ONDEVICEPERSONALIZATION_API_CALLED,
                apiCallStats.getApiClass(),
                apiCallStats.getApiName(),
                apiCallStats.getLatencyMillis(),
                apiCallStats.getResponseCode(),
                apiCallStats.getOverheadLatencyMillis());
    }
}
