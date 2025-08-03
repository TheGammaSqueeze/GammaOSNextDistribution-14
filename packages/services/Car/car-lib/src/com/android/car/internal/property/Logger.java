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

package com.android.car.internal.property;

import android.car.builtin.util.Slogf;
import android.util.Log;

/**
 * A logger that would use {@link Slogf} or {@link Log} depending on whether
 * to use system logger.
 *
 * @hide
 */
public final class Logger {

    private final boolean mUseSystemLogger;
    private final boolean mDbg;
    private final String mTag;

    public Logger(boolean useSystemLogger, String tag) {
        mUseSystemLogger = useSystemLogger;
        mTag = tag;
        if (useSystemLogger) {
            mDbg = Slogf.isLoggable(mTag, Log.DEBUG);
        } else {
            mDbg = Log.isLoggable(mTag, Log.DEBUG);
        }
    }

    /**
     * Logs a debug message.
     */
    public void logD(String msg) {
        if (mUseSystemLogger) {
            Slogf.d(mTag, msg);
        } else {
            Log.d(mTag, msg);
        }
    }

    /**
     * Logs a warning message.
     */
    public void logW(String msg) {
        if (mUseSystemLogger) {
            Slogf.w(mTag, msg);
        } else {
            Log.w(mTag, msg);
        }
    }

    /**
     * Whether debug logging should be enabled.
     */
    public boolean dbg() {
        return mDbg;
    }
}
