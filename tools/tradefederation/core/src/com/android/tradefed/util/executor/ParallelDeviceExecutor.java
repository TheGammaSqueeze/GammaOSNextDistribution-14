/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tradefed.util.executor;

import com.android.tradefed.invoker.tracing.TracePropagatingExecutorService;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Wrapper of {@link ExecutorService} to execute a function in parallel. */
public class ParallelDeviceExecutor<V> {

    private static final AtomicInteger poolNumber = new AtomicInteger(1);

    private final int mPoolSize;
    private List<Throwable> mErrors;

    public ParallelDeviceExecutor(int poolSize) {
        mPoolSize = poolSize;
        mErrors = new ArrayList<>();
    }

    /**
     * Invoke all the {@link Callable} with the timeout limit.
     *
     * @param callableTasks The List of tasks.
     * @param timeout The timeout to apply, or zero for unlimited.
     * @param unit The unit of the timeout.
     * @return The list of results for each callable task.
     */
    public List<V> invokeAll(List<Callable<V>> callableTasks, long timeout, TimeUnit unit) {
        List<V> results = new ArrayList<>();
        if (callableTasks.isEmpty()) {
            return results;
        }
        ThreadGroup currentGroup = Thread.currentThread().getThreadGroup();
        ThreadFactory factory =
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t =
                                new Thread(
                                        currentGroup,
                                        r,
                                        "tf-pool-task-" + poolNumber.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                };
        ExecutorService executor =
                TracePropagatingExecutorService.create(
                        Executors.newFixedThreadPool(mPoolSize, factory));
        try {
            List<Future<V>> futures =
                    timeout == 0L
                            ? executor.invokeAll(callableTasks)
                            : executor.invokeAll(callableTasks, timeout, unit);
            for (Future<V> future : futures) {
                try {
                    results.add(future.get());
                } catch (CancellationException cancellationException) {
                    mErrors.add(cancellationException);
                } catch (ExecutionException execException) {
                    mErrors.add(execException.getCause());
                }
            }
        } catch (InterruptedException e) {
            CLog.e(e);
            mErrors.add(e);
        } finally {
            executor.shutdownNow();
        }
        return results;
    }

    /** Whether or not some errors occurred or not. */
    public boolean hasErrors() {
        return !mErrors.isEmpty();
    }

    /** The list of errors from the execution of all tasks. */
    public List<Throwable> getErrors() {
        return mErrors;
    }
}
