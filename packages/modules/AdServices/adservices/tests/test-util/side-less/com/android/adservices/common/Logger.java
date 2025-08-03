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
package com.android.adservices.common;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;

/** Helper class providing convenience methods to log a message. */
public final class Logger {

    private final RealLogger mRealLogger;
    private final String mTag;

    public Logger(RealLogger realLogger, Class<?> clazz) {
        this(realLogger, Objects.requireNonNull(clazz).getSimpleName());
    }

    public Logger(RealLogger realLogger, String tag) {
        mRealLogger = Objects.requireNonNull(realLogger);
        mTag = Objects.requireNonNull(tag);
    }

    public String getTag() {
        return mTag;
    }

    /** Convenience method to log a WTF message. */
    @FormatMethod
    public void wtf(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        log(LogLevel.WTF, msgFmt, msgArgs);
    }

    /** Convenience method to log a WTF message with an exception. */
    @FormatMethod
    public void wtf(Throwable t, @FormatString String msgFmt, @Nullable Object... msgArgs) {
        log(LogLevel.WTF, t, msgFmt, msgArgs);
    }

    /** Convenience method to log an error message. */
    @FormatMethod
    public void e(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        log(LogLevel.ERROR, msgFmt, msgArgs);
    }

    /** Convenience method to log an error message with an exception. */
    @FormatMethod
    public void e(Throwable t, @FormatString String msgFmt, @Nullable Object... msgArgs) {
        log(LogLevel.ERROR, t, msgFmt, msgArgs);
    }

    /** Convenience method to log a warning message. */
    @FormatMethod
    public void w(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        log(LogLevel.WARNING, msgFmt, msgArgs);
    }

    /** Convenience method to log a warning message with an exception. */
    @FormatMethod
    public void w(Throwable t, @FormatString String msgFmt, @Nullable Object... msgArgs) {
        log(LogLevel.WARNING, t, msgFmt, msgArgs);
    }

    /** Convenience method to log a info message. */
    @FormatMethod
    public void i(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        log(LogLevel.INFO, msgFmt, msgArgs);
    }

    /** Convenience method to log a debug message. */
    @FormatMethod
    public void d(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        log(LogLevel.DEBUG, msgFmt, msgArgs);
    }

    /** Convenience method to log a verbose message. */
    @FormatMethod
    public void v(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        log(LogLevel.VERBOSE, msgFmt, msgArgs);
    }

    /** Logs a message in the given level. */
    @FormatMethod
    void log(LogLevel level, @FormatString String msgFmt, @Nullable Object... msgArgs) {
        mRealLogger.log(level, mTag, msgFmt, msgArgs);
    }

    /** Logs a message (and an exception) in the given level. */
    @FormatMethod
    void log(
            LogLevel level, Throwable t, @FormatString String msgFmt, @Nullable Object... msgArgs) {
        mRealLogger.log(level, mTag, t, msgFmt, msgArgs);
    }

    @Override
    public String toString() {
        return "Logger[realLogger=" + mRealLogger + ", tag=" + mTag + "]";
    }

    /** Level of log messages. */
    enum LogLevel {
        WTF,
        ERROR,
        WARNING,
        INFO,
        DEBUG,
        VERBOSE
    }

    /** Low-level implementation of the logger */
    public interface RealLogger {

        /** Logs a message in the given level. */
        @FormatMethod
        void log(
                LogLevel level,
                String tag,
                @FormatString String msgFmt,
                @Nullable Object... msgArgs);

        /** Logs a message (with an exception) in the given level. */
        @FormatMethod
        void log(
                LogLevel level,
                String tag,
                Throwable throwable,
                @FormatString String msgFmt,
                @Nullable Object... msgArgs);
    }
}
