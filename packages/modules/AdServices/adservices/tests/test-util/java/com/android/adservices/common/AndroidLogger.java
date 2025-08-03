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

import android.util.Log;

import com.android.adservices.common.Logger.LogLevel;
import com.android.adservices.common.Logger.RealLogger;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

public final class AndroidLogger implements RealLogger {

    private static final AndroidLogger sInstance = new AndroidLogger();

    public static AndroidLogger getInstance() {
        return sInstance;
    }

    private AndroidLogger() {}

    @Override
    @FormatMethod
    public void log(LogLevel level, String tag, @FormatString String msgFmt, Object... msgArgs) {
        String message = String.format(msgFmt, msgArgs);
        switch (level) {
            case WTF:
                Log.wtf(tag, message);
                return;
            case ERROR:
                Log.e(tag, message);
                return;
            case WARNING:
                Log.w(tag, message);
                return;
            case INFO:
                Log.i(tag, message);
                return;
            case DEBUG:
                Log.d(tag, message);
                return;
            case VERBOSE:
                Log.v(tag, message);
                return;
            default:
                Log.wtf(tag, "invalid level (" + level + "): " + message);
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
        String message = String.format(msgFmt, msgArgs);
        switch (level) {
            case WTF:
                Log.wtf(tag, message, t);
                return;
            case ERROR:
                Log.e(tag, message, t);
                return;
            case WARNING:
                Log.w(tag, message, t);
                return;
            case INFO:
                Log.i(tag, message, t);
                return;
            case DEBUG:
                Log.d(tag, message, t);
                return;
            case VERBOSE:
                Log.v(tag, message, t);
                return;
            default:
                Log.wtf(tag, "invalid level (" + level + "): " + message);
        }
    }

    @Override
    public String toString() {
        return AndroidLogger.class.getSimpleName();
    }
}
