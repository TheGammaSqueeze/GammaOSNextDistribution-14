/**
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

package com.android.rkpdapp.utils;

import android.os.SystemClock;
import android.util.Log;

/**
 * Restartable stopwatch class that can be used to measure multiple start->stop time
 * intervals. All measured time intervals are summed and returned by getElapsedMillis.
 */
public class StopWatch implements AutoCloseable {
    private final String mTag;
    private long mStartTime = 0;
    private long mElapsedTime = 0;

    public StopWatch(String tag) {
        mTag = tag;
    }

    /** Start or resume a timer. */
    public void start() {
        if (isRunning()) {
            Log.w(mTag, "Starting a timer that's already been running for "
                    + getElapsedMillis() + "ms");
        } else {
            mStartTime = SystemClock.elapsedRealtime();
        }
    }

    /** Stop recording time. */
    public void stop() {
        if (!isRunning()) {
            Log.w(mTag, "Attempting to stop a timer that hasn't been started.");
        } else {
            mElapsedTime += SystemClock.elapsedRealtime() - mStartTime;
            mStartTime = 0;
        }
    }

    /** Stops the timer if it's running. */
    @Override
    public void close() {
        if (isRunning()) {
            stop();
        }
    }

    /** Get how long the timer has been recording. */
    public int getElapsedMillis() {
        if (isRunning()) {
            return (int) (mElapsedTime + SystemClock.elapsedRealtime() - mStartTime);
        } else {
            return (int) mElapsedTime;
        }
    }

    /** Is the timer currently recording time? */
    public boolean isRunning() {
        return mStartTime != 0;
    }
}

