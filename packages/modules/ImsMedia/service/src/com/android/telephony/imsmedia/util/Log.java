/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.telephony.imsmedia.util;

import android.annotation.IntDef;
import android.os.Build;
import android.support.annotation.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class provides the logging interface and utility methods
 * that will be helpful when adding the log statement.
 */
public final class Log {
    public static final String TAG = "Log";
    public static final String HIDDEN = "****";
    public static final String EMPTY = "(empty)";
    public static final String NULL = "(null)";
    /**
     * Logging options.
     */

    /**
     * Priority Level constant for Logging all level logs; use Log.v.
     */
    public static final int LOG_LEVEL_ALL = 0;

    /**
     * Priority Level constant for Logging Verbose logs; use Log.v.
     */
    public static final int LOG_LEVEL_VERBOSE = 1;

    /**
     * Priority Level constant for Logging debug logs; use Log.d.
     */
    public static final int LOG_LEVEL_DEBUG = 2;

    /**
     * Priority Level constant for Logging Info logs; use Log.i.
     */
    public static final int LOG_LEVEL_INFO = 3;

    /**
     * Priority Level constant for Logging Warning logs; use Log.w.
     */
    public static final int LOG_LEVEL_WARN = 4;

    /**
     * Priority Level constant for Logging Error logs; use Log.e.
     */
    public static final int LOG_LEVEL_ERROR = 5;

    /** @hide */
    @IntDef(
        value = {
            LOG_LEVEL_ALL,
            LOG_LEVEL_VERBOSE,
            LOG_LEVEL_DEBUG,
            LOG_LEVEL_INFO,
            LOG_LEVEL_WARN,
            LOG_LEVEL_ERROR
        })
    @Retention(RetentionPolicy.SOURCE)
    public @interface LogLevel {}

    // all levels(V / D / I / W / E)
    public static final int DEFAULT_LOG_LEVEL = LOG_LEVEL_ALL;

    private static int sDebug = -1;
    private static int sLogLevel = LOG_LEVEL_ALL;

    static {
        if (Build.IS_DEBUGGABLE) {
            sDebug = 1;
            init(DEFAULT_LOG_LEVEL);
            Log.d(TAG, "All logs are enabled");
        } else {
            init(LOG_LEVEL_DEBUG);
            Log.d(TAG, "Verbose logs are disabled");
        }
    }

    /**
     * Initializes the logging configuration.
     *
     * @param logLevel A logging priority level.
     */
    public static void init(@LogLevel int logLevel) {
        setLogLevel(logLevel);
    }

    /**
     * Returns whether the debug mode is enabled or not.
     */
    public static boolean isDebuggable() {
        if (sDebug == 1) {
            return true;
        } else if (sDebug == -1) {
            return isLoggable(android.util.Log.DEBUG);
        }

        return false;
    }

    /**
     * Checks whether android logging is enabled for the given log level.
     *
     * @param level log level
     */
    public static boolean isLoggable(int level) {
        return android.util.Log.isLoggable(TAG, level);
    }

    /** Sets the debug mode from the specified flag. */
    @VisibleForTesting
    static void setDebuggable(boolean enableDebug) {
        sDebug = enableDebug ? 1 : 0;
    }

    /** Returns the log Level. */
    @VisibleForTesting
    static int getLogLevel() {
        return sLogLevel;
    }

    /** Sets the log Level. */
    public static void setLogLevel(@LogLevel int logLevel) {
        sLogLevel = logLevel;
    }

    /** Checks whether ImsMedia logging is enabled or not for given level. */
    @VisibleForTesting
    static boolean isLogEnabled(@LogLevel int level) {
        if (sLogLevel == LOG_LEVEL_ALL) {
            // All logs are available as default.
            return true;
        }
        return sLogLevel <= level;
    }

    /**
     * Prints the verbose level log.
     */
    public static void v(String tag, String msg) {
        if (isLogEnabled(LOG_LEVEL_VERBOSE)) {
            android.util.Log.v(tag, msg);
        }
    }

    /**
     * Prints the debug level log.
     */
    public static void d(String tag, String msg) {
        if (isLogEnabled(LOG_LEVEL_DEBUG)) {
            android.util.Log.d(tag, msg);
        }
    }

    /**
     * Prints the debug level log when the debug mode is enabled.
     */
    public static void dc(String tag, String msg) {
        if (isDebuggable()) {
            d(tag, msg);
        }
    }

    /**
     * Prints the information level log.
     */
    public static void i(String tag, String msg) {
        if (isLogEnabled(LOG_LEVEL_INFO)) {
            android.util.Log.i(tag, msg);
        }
    }

    /**
     * Prints the warning level log.
     */
    public static void w(String tag, String msg) {
        if (isLogEnabled(LOG_LEVEL_WARN)) {
            android.util.Log.w(tag, msg);
        }
    }

    /**
     * Prints the error level log.
     */
    public static void e(String tag, String msg) {
        if (isLogEnabled(LOG_LEVEL_ERROR)) {
            android.util.Log.e(tag, msg);
        }
    }

    /**
     * Prints the error level log.
     */
    public static void e(String tag, String msg, Throwable t) {
        if (isLogEnabled(LOG_LEVEL_ERROR)) {
            android.util.Log.e(tag, msg, t);
        }
    }

    /**
     * Hides personal identifiable information logging string if debug mode is disabled.
     *
     * @param msg logging string
     * @return hidden string if debug mode is disabled. Original string otherwise.
     */
    public static String hidePii(String msg) {
        if (msg == null) {
            return NULL;
        } else if (msg.isEmpty()) {
            return EMPTY;
        } else if (isDebuggable()) {
            return msg;
        }

        return HIDDEN;
    }

}
