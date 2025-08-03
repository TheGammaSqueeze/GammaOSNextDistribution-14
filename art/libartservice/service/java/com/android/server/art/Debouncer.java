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

package com.android.server.art;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A class that executes commands with a minimum interval.
 *
 * @hide
 */
public class Debouncer {
    @NonNull private Supplier<ScheduledExecutorService> mScheduledExecutorFactory;
    private final long mIntervalMs;
    @GuardedBy("this") @Nullable private ScheduledFuture<?> mCurrentTask = null;
    @GuardedBy("this") @Nullable private ScheduledExecutorService mExecutor = null;

    public Debouncer(
            long intervalMs, @NonNull Supplier<ScheduledExecutorService> scheduledExecutorFactory) {
        mScheduledExecutorFactory = scheduledExecutorFactory;
        mIntervalMs = intervalMs;
    }

    private void runTask(@NonNull Runnable command, @NonNull ScheduledExecutorService executor) {
        synchronized (this) {
            // In rare cases, at this point, another task may have been scheduled on the same
            // executor, and `mExecutor` will be null or a new executor when that task is run, but
            // that's okay. Either that task won't pass the check below or it will be cancelled.
            // For simplicity, every task only shuts down its own executor.
            // We only need to guarantee the following:
            // - No new task is scheduled on an executor after the executor is shut down.
            // - Every executor is eventually shut down.
            if (mExecutor == executor) {
                mExecutor.shutdown();
                mExecutor = null;
            }
        }
        command.run();
    }

    /**
     * Runs the given command after the interval has passed. If another command comes in during
     * this interval, the previous one will never run.
     */
    synchronized public void maybeRunAsync(@NonNull Runnable command) {
        if (mCurrentTask != null) {
            mCurrentTask.cancel(false /* mayInterruptIfRunning */);
        }
        if (mExecutor == null) {
            mExecutor = mScheduledExecutorFactory.get();
        }
        ScheduledExecutorService executor = mExecutor;
        mCurrentTask = mExecutor.schedule(
                () -> runTask(command, executor), mIntervalMs, TimeUnit.MILLISECONDS);
    }
}
