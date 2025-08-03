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

package com.android.server.healthconnect.logging;

import android.annotation.NonNull;
import android.content.Context;
import android.health.HealthFitnessStatsLog;
import android.os.UserHandle;

import java.util.Objects;

/**
 * Logs Health Connect usage stats.
 *
 * @hide
 */
final class UsageStatsLogger {

    /** Write Health Connect usage stats to statsd. */
    static void log(@NonNull Context context, @NonNull UserHandle userHandle) {
        Objects.requireNonNull(userHandle);
        Objects.requireNonNull(context);

        UsageStatsCollector usageStatsCollector = new UsageStatsCollector(context, userHandle);
        usageStatsCollector.upsertLastAccessLogTimeStamp();
        int numberOfConnectedApps = usageStatsCollector.getPackagesHoldingHealthPermissions();
        int numberOfAvailableApps =
                usageStatsCollector.getNumberOfAppsCompatibleWithHealthConnect();
        boolean isUserMonthlyActive = usageStatsCollector.isUserMonthlyActive();

        // If this condition is true then the user does not uses HC and we should not collect data.
        // This will reduce the load on logging service otherwise we will get daily data from
        // billions of Android devices.
        if (numberOfConnectedApps == 0 && numberOfAvailableApps == 0 && !isUserMonthlyActive) {
            return;
        }

        HealthFitnessStatsLog.write(
                HealthFitnessStatsLog.HEALTH_CONNECT_USAGE_STATS,
                numberOfConnectedApps,
                numberOfAvailableApps,
                isUserMonthlyActive);
    }
}
