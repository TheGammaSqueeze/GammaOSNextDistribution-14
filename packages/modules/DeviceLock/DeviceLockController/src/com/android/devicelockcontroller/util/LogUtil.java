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

package com.android.devicelockcontroller.util;

import android.os.Build;
import android.util.Log;
import android.util.Slog;

/**
 * Utility class for logging. Only messages that are loggable for the given tag/log level
 * are effectively logged.
 */
public final class LogUtil {
    @SuppressWarnings("IsLoggableTagLength") // only an issue for android <= 7.0 (error prone).
    private static boolean isLoggable(String tag, int level) {
        return Build.isDebuggable() || Log.isLoggable(tag, level);
    }

    private LogUtil() {}

    /** Log the message as DEBUG. */
    public static int d(String tag, String msg, Throwable tr) {
        if (isLoggable(tag, Log.DEBUG)) {
            return Slog.d(tag, msg, tr);
        }

        return 0;
    }

    /** Log the message as DEBUG. */
    public static int d(String tag, String msg) {
        if (isLoggable(tag, Log.DEBUG)) {
            return Slog.d(tag, msg);
        }

        return 0;
    }

    /** Log the message as ERROR. */
    public static int e(String tag, String msg, Throwable tr) {
        if (isLoggable(tag, Log.ERROR)) {
            return Slog.e(tag, msg, tr);
        }

        return 0;
    }

    /** Log the message as ERROR. */
    public static int e(String tag, String msg) {
        if (isLoggable(tag, Log.ERROR)) {
            return Slog.e(tag, msg);
        }

        return 0;
    }

    /** Log the message as INFO. */
    public static int i(String tag, String msg, Throwable tr) {
        if (isLoggable(tag, Log.INFO)) {
            return Slog.i(tag, msg, tr);
        }

        return 0;
    }

    /** Log the message as INFO. */
    public static int i(String tag, String msg) {
        if (isLoggable(tag, Log.INFO)) {
            return Slog.i(tag, msg);
        }

        return 0;
    }

    /** Log the message as VERBOSE. */
    public static int v(String tag, String msg, Throwable tr) {
        if (isLoggable(tag, Log.VERBOSE)) {
            return Slog.v(tag, msg, tr);
        }

        return 0;
    }

    /** Log the message as VERBOSE. */
    public static int v(String tag, String msg) {
        if (isLoggable(tag, Log.VERBOSE)) {
            return Slog.v(tag, msg);
        }

        return 0;
    }

    /** Log the message as WARN. */
    public static int w(String tag, String msg, Throwable tr) {
        if (isLoggable(tag, Log.WARN)) {
            return Slog.w(tag, msg, tr);
        }

        return 0;
    }

    /** Log the message as WARN. */
    public static int w(String tag, Throwable tr) {
        if (isLoggable(tag, Log.WARN)) {
            return Slog.w(tag, tr);
        }

        return 0;
    }

    /** Log the message as WARN. */
    public static int w(String tag, String msg) {
        if (isLoggable(tag, Log.WARN)) {
            return Slog.w(tag, msg);
        }

        return 0;
    }
}
