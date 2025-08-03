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
import android.os.UserHandle;
import android.util.Slog;

import java.util.Objects;

/**
 * Class to log Health Connect metrics logged every 24hrs.
 *
 * @hide
 */
public class DailyLoggingService {

    private static final String HEALTH_CONNECT_DAILY_LOGGING_SERVICE =
            "HealthConnectDailyLoggingService";

    /** Log daily metrics. */
    public static void logDailyMetrics(@NonNull Context context, @NonNull UserHandle userHandle) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(userHandle);

        logDatabaseStats(context);
        logUsageStats(context, userHandle);
    }

    private static void logDatabaseStats(@NonNull Context context) {
        try {
            DatabaseStatsLogger.log(context);
        } catch (Exception exception) {
            Slog.e(HEALTH_CONNECT_DAILY_LOGGING_SERVICE, "Failed to log database stats", exception);
        }
    }

    private static void logUsageStats(@NonNull Context context, @NonNull UserHandle userHandle) {
        try {
            UsageStatsLogger.log(context, userHandle);
        } catch (Exception exception) {
            Slog.e(HEALTH_CONNECT_DAILY_LOGGING_SERVICE, "Failed to log usage stats", exception);
        }
    }
}
