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

package com.android.federatedcompute.services.common;

import android.os.SystemClock;

/**
 * The implementation of {@link Clock}. Allows replacement of clock operations for testing. It is
 * monotonic until device reboots.
 */
public class MonotonicClock implements Clock {
    private static final MonotonicClock INSTANCE = new MonotonicClock();

    private final long mStartTimestampMs;

    public static Clock getInstance() {
        return INSTANCE;
    }

    private MonotonicClock() {
        mStartTimestampMs = System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    @Override
    public long currentTimeMillis() {
        return mStartTimestampMs + elapsedRealtime();
    }

    @Override
    public long elapsedRealtime() {
        return SystemClock.elapsedRealtime();
    }
}
