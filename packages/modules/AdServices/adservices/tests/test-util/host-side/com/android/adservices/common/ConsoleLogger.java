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
import com.android.tradefed.log.LogUtil.CLog;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

public final class ConsoleLogger implements RealLogger {

    private static final ConsoleLogger sInstance = new ConsoleLogger();

    public static ConsoleLogger getInstance() {
        return sInstance;
    }

    private ConsoleLogger() {}

    @Override
    @FormatMethod
    public void log(LogLevel level, String tag, @FormatString String msgFmt, Object... msgArgs) {
        String message = "[" + tag + "] " + String.format(msgFmt, msgArgs);
        switch (level) {
            case WTF:
                CLog.wtf(message);
                return;
            case ERROR:
                CLog.e(message);
                return;
            case WARNING:
                CLog.w(message);
                return;
            case INFO:
                CLog.i(message);
                return;
            case DEBUG:
                CLog.d(message);
                return;
            case VERBOSE:
                CLog.v(message);
                return;
            default:
                CLog.wtf("invalid level (" + level + "): " + message);
        }
    }

    @Override
    @FormatMethod
    public void log(
            LogLevel level,
            String tag,
            Throwable t,
            @FormatString String msgFmt,
            Object... msgArgs) {
        String message = "[" + tag + "] " + String.format(msgFmt, msgArgs);
        switch (level) {
            case WTF:
                CLog.wtf(message);
                CLog.wtf(t);
                return;
            case ERROR:
                CLog.e(message);
                CLog.e(t);
                return;
            case WARNING:
                CLog.w(message);
                CLog.w(t);
                return;
            case INFO:
                CLog.i(message);
                CLog.i("Exception: %s", t);
                return;
            case DEBUG:
                CLog.d(message);
                CLog.d("Exception: %s", t);
                return;
            case VERBOSE:
                CLog.v(message);
                CLog.v("Exception: %s", t);
                return;
            default:
                CLog.wtf("invalid level (" + level + "): " + message);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
