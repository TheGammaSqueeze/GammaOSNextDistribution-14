/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.timezone.location.provider.core;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.timezone.location.common.PiiLoggable;

import java.time.Duration;
import java.time.Instant;

/** Contains helper methods for logging. */
public final class LogUtils {

    public static final String LOG_TAG = "OfflineLTZP";
    private static final boolean DBG = false;

    private LogUtils() {}

    /** Formats the supplied system clock timestamp for debug output. */
    public static String formatUtcTime(long timeMillis) {
        return Instant.ofEpochMilli(timeMillis).toString();
    }

    /** Formats the supplied elapse realtime clock timestamp  for debug output. */
    public static String formatElapsedRealtimeMillis(long elapsedRealtimeMillis) {
        return Duration.ofMillis(elapsedRealtimeMillis).toString();
    }

    /** Logs at debug level when debug logging is enabled via {@link #DBG}. */
    public static void logDebug(@NonNull PiiLoggable piiLoggable) {
        if (DBG) {
            Log.d(LOG_TAG, piiLoggable.toString());
        }
    }

    /** Logs at debug level when debug logging is enabled via {@link #DBG}. */
    public static void logDebug(@NonNull String msg) {
        if (DBG) {
            Log.d(LOG_TAG, msg);
        }
    }

    /** Logs at warn level. */
    public static void logWarn(@NonNull PiiLoggable piiLoggable) {
        Log.w(LOG_TAG, piiLoggable.toString());
    }

    /** Logs at warn level. */
    public static void logWarn(@NonNull String msg) {
        Log.w(LOG_TAG, msg);
    }

    /** Logs at warn level. */
    public static void logWarn(@NonNull PiiLoggable piiLoggable, @Nullable Throwable t) {
        Log.w(LOG_TAG, piiLoggable.toString(), t);
    }

    /** Logs at warn level. */
    public static void logWarn(@NonNull String msg, @Nullable Throwable t) {
        Log.w(LOG_TAG, msg, t);
    }
}
