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
import android.annotation.SystemApi;

/**
 * Wrapper class for {@code android.util.TimingsTraceLog}. Check the class for API documentation.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class TimingsTraceLog {

    private final android.util.TimingsTraceLog mTimingsTraceLog;

    public TimingsTraceLog(@NonNull String tag, long traceTag) {
        mTimingsTraceLog = new android.util.TimingsTraceLog(tag, traceTag);
    }

    /** Check {@code android.util.Slog}. */
    public void traceBegin(@NonNull String name) {
        mTimingsTraceLog.traceBegin(name);
    }

    /** Check {@code android.util.Slog}. */
    public void traceEnd() {
        mTimingsTraceLog.traceEnd();
    }

    /** Check {@code android.util.Slog}. */
    public void logDuration(@NonNull String name, long timeMs) {
        mTimingsTraceLog.logDuration(name, timeMs);
    }
}
