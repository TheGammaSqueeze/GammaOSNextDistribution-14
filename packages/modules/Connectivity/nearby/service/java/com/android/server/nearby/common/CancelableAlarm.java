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

package com.android.server.nearby.common;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.util.Log;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * A cancelable alarm with a name. This is a simple wrapper around the logic for posting a runnable
 * on a scheduled executor service and (possibly) later canceling it.
 */
public class CancelableAlarm {

    private static final String TAG = "NearbyCancelableAlarm";

    private final String mName;
    private final Runnable mRunnable;
    private final long mDelayMillis;
    private final ScheduledExecutorService mExecutor;
    private final boolean mIsRecurring;

    // The future containing the alarm.
    private volatile ScheduledFuture<?> mFuture;

    private CancellationFlag mCancellationFlag;

    private CancelableAlarm(
            String name,
            Runnable runnable,
            long delayMillis,
            ScheduledExecutorService executor,
            boolean isRecurring) {
        this.mName = name;
        this.mRunnable = runnable;
        this.mDelayMillis = delayMillis;
        this.mExecutor = executor;
        this.mIsRecurring = isRecurring;
    }

    /**
     * Creates an alarm.
     *
     * @param name the task name
     * @param runnable command the task to execute
     * @param delayMillis delay the time from now to delay execution
     * @param executor the executor that schedules commands to run
     */
    public static CancelableAlarm createSingleAlarm(
            String name,
            Runnable runnable,
            long delayMillis,
            ScheduledExecutorService executor) {
        CancelableAlarm cancelableAlarm =
                new CancelableAlarm(name, runnable, delayMillis, executor, /* isRecurring= */
                        false);
        cancelableAlarm.scheduleExecutor();
        return cancelableAlarm;
    }

    /**
     * Creates a recurring alarm.
     *
     * @param name the task name
     * @param runnable command the task to execute
     * @param delayMillis delay the time from now to delay execution
     * @param executor the executor that schedules commands to run
     */
    public static CancelableAlarm createRecurringAlarm(
            String name,
            Runnable runnable,
            long delayMillis,
            ScheduledExecutorService executor) {
        CancelableAlarm cancelableAlarm =
                new CancelableAlarm(name, runnable, delayMillis, executor, /* isRecurring= */ true);
        cancelableAlarm.scheduleExecutor();
        return cancelableAlarm;
    }

    // A reference to "this" should generally not be passed to another class within the constructor
    // as it may not have completed being constructed.
    private void scheduleExecutor() {
        this.mFuture = mExecutor.schedule(this::processAlarm, mDelayMillis, MILLISECONDS);
        // For tests to pass (NearbySharingChimeraServiceTest) the Cancellation Flag must come
       // after the
        // executor.  Doing so prevents the test code from running the callback immediately.
        this.mCancellationFlag = new CancellationFlag();
    }

    /**
     * Cancels the pending alarm.
     *
     * @return true if the alarm was canceled, or false if there was a problem canceling the alarm.
     */
    public boolean cancel() {
        mCancellationFlag.cancel();
        try {
            return mFuture.cancel(/* mayInterruptIfRunning= */ true);
        } finally {
            Log.v(TAG, "Canceled " + mName + " alarm");
        }
    }

    private void processAlarm() {
        if (mCancellationFlag.isCancelled()) {
            Log.v(TAG, "Ignoring " + mName + " alarm because it has previously been canceled");
            return;
        }

        Log.v(TAG, "Running " + mName + " alarm");
        mRunnable.run();
        if (mIsRecurring) {
            this.mFuture = mExecutor.schedule(this::processAlarm, mDelayMillis, MILLISECONDS);
        }
    }
}
