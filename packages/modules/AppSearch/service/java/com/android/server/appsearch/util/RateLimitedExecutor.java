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

package com.android.server.appsearch.util;

import android.annotation.NonNull;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.appsearch.AppSearchRateLimitConfig;
import com.android.server.appsearch.external.localstorage.stats.CallStats;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RateLimitedExecutor implements ExecutorService {
    private static final String TAG = "AppSearchRateLimitExec";
    private final ExecutorService mExecutor;

    /**
     * Lock needed for operations in this class.
     */
    private final Object mLock = new Object();

    /**
     * A map of packageName -> {@link TaskCostInfo} of package task count and cost currently on
     * task queue.
     */
    @GuardedBy("mLock")
    private final ArrayMap<String, TaskCostInfo> mPerPackageTaskCostsLocked = new ArrayMap<>();

    /**
     * The {@link AppSearchRateLimitConfig} for the executor
     */
    @GuardedBy("mLock")
    private AppSearchRateLimitConfig mRateLimitConfigLocked;

    /**
     * Keeps track of the task queue size.
     */
    @GuardedBy("mLock")
    private int mTaskQueueSizeLocked;

    /**
     * Sum of costs of all tasks currently on the executor queue.
     */
    @GuardedBy("mLock")
    private int mTaskQueueTotalCostLocked;

    public RateLimitedExecutor(@NonNull ExecutorService executor,
            @NonNull AppSearchRateLimitConfig rateLimitConfig) {
        mExecutor = Objects.requireNonNull(executor);
        mRateLimitConfigLocked = Objects.requireNonNull(rateLimitConfig);
        mTaskQueueSizeLocked = 0;
        mTaskQueueTotalCostLocked = 0;
    }

    /**
     * Returns true and executes the runnable if it can be accepted by the rate-limited executor.
     * Otherwise returns false.
     *
     * @param lambda        The lambda to execute on the rate-limited executor.
     * @param packageName   Package making this lambda call.
     * @param apiType       Api type of this lambda call.
     */
    public boolean execute(@NonNull Runnable lambda, @NonNull String packageName,
            @CallStats.CallType int apiType) {
        Objects.requireNonNull(lambda);
        Objects.requireNonNull(packageName);
        if (!addTaskToQueue(packageName, apiType)) {
            return false;
        }
        mExecutor.execute(() -> {
            try {
                lambda.run();
            } finally {
                removeTaskFromQueue(packageName, apiType);
            }
        });
        return true;
    }
    @NonNull
    public ExecutorService getExecutor() {
        return mExecutor;
    }

    @VisibleForTesting
    public int getTaskQueueSize() {
        synchronized (mLock) {
            return mTaskQueueSizeLocked;
        }
    }


    @VisibleForTesting
    @NonNull
    public ArrayMap<String, TaskCostInfo> getPerPackageTaskCosts() {
        synchronized (mLock) {
            return new ArrayMap<>(mPerPackageTaskCostsLocked);
        }
    }

    /**
     * Sets the rate limit config for this rate limited executor.
     */
    public void setRateLimitConfig(@NonNull AppSearchRateLimitConfig rateLimitConfigLocked) {
        synchronized (mLock) {
            mRateLimitConfigLocked = Objects.requireNonNull(rateLimitConfigLocked);
        }
    }

    /**
     * Returns true and adds a task to the executor queue by incrementing the count for the task's
     * package and api type, if allowed. Otherwise returns false.
     */
    @VisibleForTesting
    public boolean addTaskToQueue(@NonNull String packageName, @CallStats.CallType int apiType) {
        synchronized (mLock) {
            Objects.requireNonNull(packageName);
            TaskCostInfo packageTaskCostInfo = mPerPackageTaskCostsLocked.get(packageName);
            int totalPackageApiCost =
                    packageTaskCostInfo == null ? 0 : packageTaskCostInfo.mTotalTaskCost;
            int apiCost = mRateLimitConfigLocked.getApiCost(apiType);
            if (totalPackageApiCost + apiCost
                    > mRateLimitConfigLocked.getTaskQueuePerPackageCapacity() ||
                    mTaskQueueTotalCostLocked + apiCost
                            > mRateLimitConfigLocked.getTaskQueueTotalCapacity()) {
                return false;
            } else {
                ++mTaskQueueSizeLocked;
                mTaskQueueTotalCostLocked += apiCost;
                addPackageTaskInfoLocked(packageName, apiCost);
                return true;
            }
        }
    }

    /**
     * Removes a task from the executor queue by decrementing the count for the task's package
     * and api type.
     */
    @VisibleForTesting
    public void removeTaskFromQueue(@NonNull String packageName, @CallStats.CallType int apiType) {
        synchronized (mLock) {
            Objects.requireNonNull(packageName);
            if (!mPerPackageTaskCostsLocked.containsKey(packageName)) {
                Log.e(TAG,
                        "There are no tasks to remove from the queue for package: " + packageName);
                return;
            }
            int apiCost = mRateLimitConfigLocked.getApiCost(apiType);
            --mTaskQueueSizeLocked;
            mTaskQueueTotalCostLocked -= apiCost;
            removePackageTaskInfoLocked(packageName, apiCost);
        }
    }

    @GuardedBy("mLock")
    private void addPackageTaskInfoLocked(@NonNull String packageName, int apiCost) {
        TaskCostInfo packageTaskCostInfo = mPerPackageTaskCostsLocked.get(packageName);
        if (packageTaskCostInfo == null) {
            packageTaskCostInfo = new TaskCostInfo(0, 0);
            mPerPackageTaskCostsLocked.put(packageName, packageTaskCostInfo);
        }
        ++packageTaskCostInfo.mTaskCount;
        packageTaskCostInfo.mTotalTaskCost += apiCost;
    }

    @GuardedBy("mLock")
    private void removePackageTaskInfoLocked(@NonNull String packageName, int apiCost) {
        TaskCostInfo packageTaskCostInfo = mPerPackageTaskCostsLocked.get(packageName);
        if (packageTaskCostInfo == null) {
            Log.e(TAG, "There are no tasks to remove from the queue for package: " + packageName);
            return;
        }
        --packageTaskCostInfo.mTaskCount;
        packageTaskCostInfo.mTotalTaskCost -= apiCost;
        if (packageTaskCostInfo.mTaskCount <= 0 || packageTaskCostInfo.mTotalTaskCost <= 0) {
            mPerPackageTaskCostsLocked.remove(packageName);
        }
    }

    @Override
    public void execute(@NonNull Runnable lambda) {
        mExecutor.execute(lambda);
    }

    @Override
    public void shutdown() {
        mExecutor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return mExecutor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return mExecutor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return mExecutor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return mExecutor.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return mExecutor.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return mExecutor.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return mExecutor.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return mExecutor.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit unit) throws InterruptedException {
        return mExecutor.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return mExecutor.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return mExecutor.invokeAny(tasks, timeout, unit);
    }

    /**
     * Class containing the integer pair of task count and total task costs.
     */
    public static final class TaskCostInfo {
        public int mTaskCount;
        public int mTotalTaskCost;

        TaskCostInfo(int taskCount, int totalTaskCost) {
            mTaskCount = taskCount;
            mTotalTaskCost = totalTaskCost;
        }
    }
}
