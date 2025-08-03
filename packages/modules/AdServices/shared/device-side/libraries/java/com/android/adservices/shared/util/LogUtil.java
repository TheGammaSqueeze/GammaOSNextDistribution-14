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

package com.android.adservices.shared.util;

import android.util.Log;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Locale;

/**
 * Utility class for logging to logcat with the "spe" tag.
 *
 * @deprecated TODO(b/280460130): need to define a proper logging policy.
 */
@Deprecated
public final class LogUtil {
    private static final String TAG = "adservices-shared";

    public static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /** Log the message as VERBOSE. Return The number of bytes written. */
    public static int v(String msg) {
        if (VERBOSE) {
            return Log.v(TAG, msg);
        }
        return 0;
    }

    /** Log the message as VERBOSE. Return The number of bytes written. */
    @FormatMethod
    public static int v(@FormatString String format, Object... params) {
        if (VERBOSE) {
            String msg = format(format, params);
            return Log.v(TAG, msg);
        }
        return 0;
    }

    /** Log the message as DEBUG. Return The number of bytes written. */
    public static int d(String msg) {
        if (DEBUG) {
            return Log.d(TAG, msg);
        }
        return 0;
    }

    /** Log the message as DEBUG. Return The number of bytes written. */
    @FormatMethod
    public static int d(@FormatString String format, Object... params) {
        if (DEBUG) {
            String msg = format(format, params);
            return Log.d(TAG, msg);
        }
        return 0;
    }

    /** Log the message as DEBUG. Return The number of bytes written. */
    @FormatMethod
    public static int d(Throwable tr, @FormatString String format, Object... params) {
        if (DEBUG) {
            String msg = format(format, params);
            return Log.d(TAG, msg, tr);
        }
        return 0;
    }

    /** Log the message as INFO. Return The number of bytes written. */
    public static int i(String msg) {
        return Log.i(TAG, msg);
    }

    /** Log the message as INFO. Return The number of bytes written */
    @FormatMethod
    public static int i(@FormatString String format, Object... params) {
        String msg = format(format, params);
        return Log.i(TAG, msg);
    }

    /** Log the message as ERROR. Return The number of bytes written */
    public static int w(String msg) {
        return Log.w(TAG, msg);
    }

    /** Log the message as WARNING. Return The number of bytes written */
    @FormatMethod
    public static int w(@FormatString String format, Object... params) {
        String msg = format(format, params);
        return Log.w(TAG, msg);
    }

    /** Log the message as ERROR. Return The number of bytes written */
    public static int e(String msg) {
        return Log.e(TAG, msg);
    }

    /** Log the message as ERROR. Return The number of bytes written */
    @FormatMethod
    public static int e(@FormatString String format, Object... params) {
        String msg = format(format, params);
        return Log.e(TAG, msg);
    }

    /** Log the message as ERROR. Return The number of bytes written */
    public static int e(Throwable tr, String msg) {
        return Log.e(TAG, msg, tr);
    }

    /** Log the message as ERROR. Return The number of bytes written */
    @FormatMethod
    public static int e(Throwable tr, @FormatString String format, Object... params) {
        return e(tr, format(format, params));
    }

    /** Log the message as WARNING. Return The number of bytes written */
    @FormatMethod
    public static int w(Throwable tr, @FormatString String format, Object... params) {
        String msg = format(format, params);
        return Log.w(TAG, msg, tr);
    }

    private static String format(String format, Object... args) {
        return String.format(Locale.US, format, args);
    }
}
