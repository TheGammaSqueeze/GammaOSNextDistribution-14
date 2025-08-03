/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.builtin.util;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Trace;
import android.util.Log;
import android.util.Slog;
import android.util.TimingsTraceLog;

import com.android.internal.annotations.GuardedBy;

import java.util.Formatter;
import java.util.Locale;

/**
 * Wrapper class for {@code com.android.server.utils.Slogf}. Check the class for API documentation.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class Slogf {
    // Entire class copied from {@code com.android.server.utils.Slogf}
    @GuardedBy("sMessageBuilder")
    private static final StringBuilder sMessageBuilder;

    @GuardedBy("sMessageBuilder")
    private static final Formatter sFormatter;

    static {
        TimingsTraceLog t = new TimingsTraceLog("SLog", Trace.TRACE_TAG_SYSTEM_SERVER);
        t.traceBegin("static_init");
        sMessageBuilder = new StringBuilder();
        sFormatter = new Formatter(sMessageBuilder, Locale.ENGLISH);
        t.traceEnd();
    }

    // Internal log tag only for isLoggable(), the tag will be set to VERBOSE during the car tests.
    private static final String CAR_TEST_TAG = "CAR.TEST";

    private Slogf() {
        throw new UnsupportedOperationException("provides only static methods");
    }

    /** Same as {@link Log#isLoggable(String, int)}, but also checks for {@code CAR_TEST_TAG}. */
    public static boolean isLoggable(@NonNull String tag, int level) {
        return Log.isLoggable(tag, level) || Log.isLoggable(CAR_TEST_TAG, Log.VERBOSE);
    }

    /** Same as {@link Slog#v(String, String)}. */
    public static int v(@NonNull String tag, @NonNull String msg) {
        return Slog.v(tag, msg);
    }

    /** Same as {@link Slog#v(String, String, Throwable)}. */
    public static int v(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return Slog.v(tag, msg, tr);
    }

    /** Same as {@link Slog#d(String, String)}. */
    public static int d(@NonNull String tag, @NonNull String msg) {
        return Slog.d(tag, msg);
    }

    /** Same as {@link Slog#d(String, String, Throwable)}. */
    public static int d(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return Slog.d(tag, msg, tr);
    }

    /** Same as {@link Slog#i(String, String)}. */
    public static int i(@NonNull String tag, @NonNull String msg) {
        return Slog.i(tag, msg);
    }

    /** Same as {@link Slog#i(String, String, Throwable)}. */
    public static int i(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return Slog.i(tag, msg, tr);
    }

    /** Same as {@link Slog#w(String, String)}. */
    public static int w(@NonNull String tag, @NonNull String msg) {
        return Slog.w(tag, msg);
    }

    /** Same as {@link Slog#w(String, String, Throwable)}. */
    public static int w(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return Slog.w(tag, msg, tr);
    }

    /** Same as {@link Slog#w(String, String)}. */
    public static int w(@NonNull String tag, @NonNull Throwable tr) {
        return Slog.w(tag, tr);
    }

    /** Same as {@link Slog#e(String, String)}. */
    public static int e(@NonNull String tag, @NonNull String msg) {
        return Slog.e(tag, msg);
    }

    /** Same as {@link Slog#e(String, String, Throwable)}. */
    public static int e(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return Slog.e(tag, msg, tr);
    }

    /** Same as {@link Slog#wtf(String, String)}. */
    public static int wtf(@NonNull String tag, @NonNull String msg) {
        return Slog.wtf(tag, msg);
    }

    /** Same as {@link Slog#wtf(String, Throwable). */
    public static int wtf(@NonNull String tag, @NonNull Throwable tr) {
        return Slog.wtf(tag, tr);
    }

    /** Same as {@link Slog#wtf(String, String, Throwable)}. */
    public static int wtf(@NonNull String tag, @NonNull String msg, @NonNull Throwable tr) {
        return Slog.wtf(tag, msg, tr);
    }

    /**
     * Logs a {@link Log.VERBOSE} message.
     * <p>
     * <strong>Note: </strong>the message will only be formatted if {@link Log#VERBOSE} logging is
     * enabled for the given {@code tag}, but the compiler will still create an intermediate array
     * of the objects for the {@code vargars}, which could affect garbage collection. So, if you're
     * calling this method in a critical path, make sure to explicitly do the check before calling
     * it.
     */
    public static void v(@NonNull String tag, @NonNull String format, @Nullable Object... args) {
        if (!isLoggable(tag, Log.VERBOSE)) return;

        v(tag, getMessage(format, args));
    }

    /**
     * Logs a {@link Log.DEBUG} message.
     * <p>
     * <strong>Note: </strong>the message will only be formatted if {@link Log#DEBUG} logging is
     * enabled for the given {@code tag}, but the compiler will still create an intermediate array
     * of the objects for the {@code vargars}, which could affect garbage collection. So, if you're
     * calling this method in a critical path, make sure to explicitly do the check before calling
     * it.
     */
    public static void d(@NonNull String tag, @NonNull String format, @Nullable Object... args) {
        if (!isLoggable(tag, Log.DEBUG)) return;

        d(tag, getMessage(format, args));
    }

    /**
     * Logs a {@link Log.INFO} message.
     * <p>
     * <strong>Note: </strong>the message will only be formatted if {@link Log#INFO} logging is
     * enabled for the given {@code tag}, but the compiler will still create an intermediate array
     * of the objects for the {@code vargars}, which could affect garbage collection. So, if you're
     * calling this method in a critical path, make sure to explicitly do the check before calling
     * it.
     */
    public static void i(@NonNull String tag, @NonNull String format, @Nullable Object... args) {
        if (!isLoggable(tag, Log.INFO)) return;

        i(tag, getMessage(format, args));
    }

    /**
     * Logs a {@link Log.WARN} message.
     * <p>
     * <strong>Note: </strong>the message will only be formatted if {@link Log#WARN} logging is
     * enabled for the given {@code tag}, but the compiler will still create an intermediate array
     * of the objects for the {@code vargars}, which could affect garbage collection. So, if you're
     * calling this method in a critical path, make sure to explicitly do the check before calling
     * it.
     */
    public static void w(@NonNull String tag, @NonNull String format, @Nullable Object... args) {
        if (!isLoggable(tag, Log.WARN)) return;

        w(tag, getMessage(format, args));
    }

    /**
     * Logs a {@link Log.WARN} message with an exception
     * <p>
     * <strong>Note: </strong>the message will only be formatted if {@link Log#WARN} logging is
     * enabled for the given {@code tag}, but the compiler will still create an intermediate array
     * of the objects for the {@code vargars}, which could affect garbage collection. So, if you're
     * calling this method in a critical path, make sure to explicitly do the check before calling
     * it.
     */
    public static void w(@NonNull String tag, @NonNull Exception exception, @NonNull String format,
            @Nullable Object... args) {
        if (!isLoggable(tag, Log.WARN)) return;

        w(tag, getMessage(format, args), exception);
    }

    /**
     * Logs a {@link Log.ERROR} message.
     * <p>
     * <strong>Note: </strong>the message will only be formatted if {@link Log#ERROR} logging is
     * enabled for the given {@code tag}, but the compiler will still create an intermediate array
     * of the objects for the {@code vargars}, which could affect garbage collection. So, if you're
     * calling this method in a critical path, make sure to explicitly do the check before calling
     * it.
     */
    public static void e(@NonNull String tag, @NonNull String format, @Nullable Object... args) {
        if (!isLoggable(tag, Log.ERROR)) return;

        e(tag, getMessage(format, args));
    }

    /**
     * Logs a {@link Log.ERROR} message with an exception
     * <p>
     * <strong>Note: </strong>the message will only be formatted if {@link Log#ERROR} logging is
     * enabled for the given {@code tag}, but the compiler will still create an intermediate array
     * of the objects for the {@code vargars}, which could affect garbage collection. So, if you're
     * calling this method in a critical path, make sure to explicitly do the check before calling
     * it.
     */
    public static void e(@NonNull String tag, @NonNull Exception exception, @NonNull String format,
            @Nullable Object... args) {
        if (!isLoggable(tag, Log.ERROR)) return;

        e(tag, getMessage(format, args), exception);
    }

    /**
     * Logs a {@code wtf} message.
     */
    public static void wtf(@NonNull String tag, @NonNull String format, @Nullable Object... args) {
        wtf(tag, getMessage(format, args));
    }

    /**
     * Logs a {@code wtf} message with an exception.
     */
    public static void wtf(@NonNull String tag, @NonNull Exception exception,
            @NonNull String format, @Nullable Object... args) {
        wtf(tag, getMessage(format, args), exception);
    }

    private static String getMessage(@NonNull String format, @Nullable Object... args) {
        synchronized (sMessageBuilder) {
            sFormatter.format(format, args);
            String message = sMessageBuilder.toString();
            sMessageBuilder.setLength(0);
            return message;
        }
    }
}
