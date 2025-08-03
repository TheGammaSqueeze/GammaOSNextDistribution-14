/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.stats;

import android.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.shared.util.Clock;

import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Class for the Api Service Latency Calculator. This class uses a clock that its
 * clock#elapsedRealtime() should always be monotonic to track the time points of a process. The
 * {@link ApiServiceLatencyCalculator} constructor will set the {@link
 * ApiServiceLatencyCalculator#mStartElapsedTimestamp}. Calling {@link
 * ApiServiceLatencyCalculator#getApiServiceInternalFinalLatencyInMs()} will stop the time
 * calculator and return the latency for the process by the start and stop elapsed timestamps. Once
 * the calculator is stopped, the {@link ApiServiceLatencyCalculator#mStopElapsedTimestamp} will not
 * be changed. Calling {@link ApiServiceLatencyCalculator#getApiServiceElapsedLatencyInMs()} will
 * not stop the time calculator, only get the time elapsed since the start elapsed timestamp.
 */
@ThreadSafe
public class ApiServiceLatencyCalculator {
    private final long mStartElapsedTimestamp;
    private volatile long mStopElapsedTimestamp;
    private volatile boolean mRunning;
    private final Clock mClock;

    ApiServiceLatencyCalculator(@NonNull Clock clock) {
        Objects.requireNonNull(clock);
        mClock = clock;
        mStartElapsedTimestamp = mClock.elapsedRealtime();
        mRunning = true;
        LogUtil.v("ApiServiceLatencyCalculator has started at %d", mStartElapsedTimestamp);
    }

    /**
     * Stops a {@link ApiServiceLatencyCalculator} instance from time calculation. If an instance is
     * not running, calling this method will do nothing.
     */
    private void stop() {
        if (!mRunning) {
            return;
        }
        synchronized (this) {
            if (!mRunning) {
                return;
            }
            mStopElapsedTimestamp = mClock.elapsedRealtime();
            mRunning = false;
            LogUtil.v("ApiServiceLatencyCalculator stopped.");
        }
    }

    /** @return the calculator's start timestamp since the system boots. */
    long getStartElapsedTimestamp() {
        return mStartElapsedTimestamp;
    }

    /**
     * @return the elapsed timestamp since the system boots if the {@link
     *     ApiServiceLatencyCalculator} instance is still running, otherwise the timestamp when it
     *     was stopped.
     */
    long getServiceElapsedTimestamp() {
        if (mRunning) {
            return mClock.elapsedRealtime();
        }
        LogUtil.v("The ApiServiceLatencyCalculator instance has previously been stopped.");
        return mStopElapsedTimestamp;
    }

    /**
     * @return the api service elapsed time latency since {@link ApiServiceLatencyCalculator} starts
     *     in milliseconds on the service side. This method will not stop the {@link
     *     ApiServiceLatencyCalculator} and should be used for getting intermediate stage latency of
     *     a API process.
     */
    int getApiServiceElapsedLatencyInMs() {
        return (int) (getServiceElapsedTimestamp() - mStartElapsedTimestamp);
    }

    /**
     * @return the api service overall latency since the {@link ApiServiceLatencyCalculator} starts
     *     in milliseconds without binder latency, on the server side. This method will stop the
     *     calculator if still running and the returned latency value will no longer change once the
     *     calculator is stopped. It should be used to get the complete process latency of an API
     *     within the server side.
     */
    int getApiServiceInternalFinalLatencyInMs() {
        stop();
        return getApiServiceElapsedLatencyInMs();
    }
}
