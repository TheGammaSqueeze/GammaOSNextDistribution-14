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

import com.android.adservices.common.Logger.LogLevel;
import com.android.adservices.common.Logger.RealLogger;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/**
 * Simple implementation of {@link RealLogger} that logs messages on {@code System.out} and errors
 * on {@code System.err}, so it can be used on "side-less" tests (as device-side and host-side
 * classes would be using {@code AndroidLogger} or {@code ConsoleLogger} respectively).
 */
public class StandardStreamsLogger implements RealLogger {

    private static final StandardStreamsLogger sInstance = new StandardStreamsLogger();

    public static StandardStreamsLogger getInstance() {
        return sInstance;
    }

    private StandardStreamsLogger() {}

    @Override
    @FormatMethod
    public void log(LogLevel level, String tag, @FormatString String msgFmt, Object... msgArgs) {
        String msg = String.format(msgFmt, msgArgs);

        System.out.printf("%s %s: %s\n", tag, level, msg);
    }

    @Override
    @FormatMethod
    public void log(
            LogLevel level,
            String tag,
            Throwable throwable,
            @FormatString String msgFmt,
            Object... msgArgs) {
        String msg = String.format(msgFmt, msgArgs);

        System.err.printf("%s %s: %s\n", tag, level, msg);
        throwable.printStackTrace(System.err);
    }

    @Override
    public String toString() {
        return StandardStreamsLogger.class.getSimpleName();
    }
}
