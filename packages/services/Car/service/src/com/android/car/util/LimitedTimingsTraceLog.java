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

package com.android.car.util;

import android.car.builtin.util.TimingsTraceLog;

/**
 * {@link TimingsTraceLog} with additional minimum duration.
 *
 * <p>Any interval shorter than it will be added to trace but will not have logcat. This can be
 * useful to prevent log spam for short durations.
 */
public final class LimitedTimingsTraceLog extends TimingsTraceLog {

    private final int mMinDurationMs;

    /**
     * Same as {@link TimingsTraceLog} except last argument {@code minDurationMs} which specifies
     * the minimum duration to log the duration.
     */
    public LimitedTimingsTraceLog(String tag, long traceTag, int minDurationMs) {
        super(tag, traceTag);
        mMinDurationMs = minDurationMs;
    }

    @Override
    public void logDuration(String name, long timeMs) {
        if (timeMs >= mMinDurationMs) {
            super.logDuration(name, timeMs);
        }
    }
}
