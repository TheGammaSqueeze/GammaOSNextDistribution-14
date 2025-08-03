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

package com.android.adservices.service.common;

import androidx.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;

import com.google.common.util.concurrent.FluentFuture;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Utility class helping to ensure that only one thread is running a given task.
 *
 * @param <T> Type of the result returned by the task.
 */
public class SingletonRunner<T> {
    /**
     * Represents the runner of an interruptable task. The parameter {@code stopFlagChecker} can be
     * invoked to check if a request to stop the task has been received.
     *
     * @param <T> the return type of the task.
     */
    public interface InterruptableTaskRunner<T> {
        /**
         * Run the task.
         *
         * @param stopFlagChecker returns true if the task should be stopped.
         */
        FluentFuture<T> run(Supplier<Boolean> stopFlagChecker);
    }

    // Critical region to guarantee that write changes to mWorkInProgress and
    // mStopWorkRequestPending are logically consistent
    // (i.e. we are checking that there is WIP before setting mStopWorkRequestPending to true)
    private final Object mWorkStatusWriteLock = new Object();
    private final String mTaskDescription;
    private volatile boolean mStopWorkRequestPending;
    private FluentFuture<T> mRunningTaskResult = null;
    InterruptableTaskRunner<T> mTaskRunner;

    public SingletonRunner(
            @NonNull String taskDescription, @NonNull InterruptableTaskRunner<T> taskRunner) {
        Objects.requireNonNull(taskDescription);
        Objects.requireNonNull(taskRunner);

        mTaskDescription = taskDescription;
        mStopWorkRequestPending = false;
        mTaskRunner = taskRunner;
    }

    /**
     * Ensures that there is only one thread running the task and that requests to stop are not
     * interfering with new starting jobs
     */
    public FluentFuture<T> runSingleInstance() {
        synchronized (mWorkStatusWriteLock) {
            if (mRunningTaskResult != null) {
                LogUtil.w("Already running %s, skipping call", mTaskDescription);
            } else {
                mRunningTaskResult =
                        mTaskRunner
                                .run(this::shouldStop)
                                // not using a callback to be sure that the status is reset
                                // when the future is completed.
                                .transform(
                                        result -> {
                                            signalWorkIsComplete();
                                            return result;
                                        },
                                        AdServicesExecutors.getLightWeightExecutor())
                                .catching(
                                        RuntimeException.class,
                                        e -> {
                                            signalWorkIsComplete();
                                            throw e;
                                        },
                                        AdServicesExecutors.getLightWeightExecutor());
            }

            // Returning the value of mRunningTaskResult from inside the critical region
            // to avoid returning null if the runner completes so early that it is actually
            // resetting the mRunningTaskResult in this instance.
            return mRunningTaskResult;
        }
    }

    private void signalWorkIsComplete() {
        synchronized (mWorkStatusWriteLock) {
            mRunningTaskResult = null;
            mStopWorkRequestPending = false;
        }
    }

    private boolean shouldStop() {
        return mStopWorkRequestPending;
    }

    /** Requests that any ongoing work be stopped gracefully and waits for work to be stopped. */
    public void stopWork() {
        LogUtil.d("%s stop work requested", mTaskDescription);

        synchronized (mWorkStatusWriteLock) {
            if (mRunningTaskResult == null) {
                LogUtil.d("%s not running", mTaskDescription);
                return;
            }

            mStopWorkRequestPending = true;
        }

        LogUtil.d("%s configured to shut down", mTaskDescription);
    }
}
