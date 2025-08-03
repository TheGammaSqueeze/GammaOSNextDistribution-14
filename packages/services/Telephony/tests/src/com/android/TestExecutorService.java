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

package com.android;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An implementation of ExecutorService that just runs the requested task on the thread that it
 * was called on for testing purposes.
 */
public class TestExecutorService implements ScheduledExecutorService {

    private static class CompletedFuture<T> implements Future<T>, ScheduledFuture<T> {

        private final Callable<T> mTask;
        private final long mDelayMs;
        // Wrap callable in a CompletableFuture to support delays in execution.
        private final CompletableFuture<T> mFuture = new CompletableFuture<>();

        CompletedFuture(Callable<T> task) {
            mTask = task;
            mDelayMs = 0;
        }

        CompletedFuture(Callable<T> task, long delayMs) {
            mTask = task;
            mDelayMs = delayMs;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return mFuture.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return mFuture.isCancelled();
        }

        @Override
        public boolean isDone() {
            return mFuture.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return mFuture.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            // delays not implemented, this should complete via completeTask for better control.
            return mFuture.get(timeout, unit);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            if (unit == TimeUnit.MILLISECONDS) {
                return mDelayMs;
            } else {
                // not implemented
                return 0;
            }
        }

        @Override
        public int compareTo(Delayed o) {
            if (o == null) return 1;
            if (o.getDelay(TimeUnit.MILLISECONDS) > mDelayMs) return -1;
            if (o.getDelay(TimeUnit.MILLISECONDS) < mDelayMs) return 1;
            return 0;
        }

        public void completeTask() {
            try {
                mFuture.complete(mTask.call());
            } catch (Exception e) {
                mFuture.completeExceptionally(e);
            }
        }
    }

    private final ArrayList<Runnable> mPendingRunnables = new ArrayList<>();
    private final boolean mWaitToComplete;
    private boolean mIsShutdown = false;

    public TestExecutorService() {
        mWaitToComplete = false;
    }

    /**
     * Create a test executor service that also allows the constructor to provide a parameter to
     * control when pending Runnables are executed.
     * @param waitToComplete If true, this executor will wait to complete any pending Runnables
     *                       until {@link #executePending()}} is called.
     */
    public TestExecutorService(boolean waitToComplete) {
        mWaitToComplete = waitToComplete;
    }

    @Override
    public void shutdown() {
        mIsShutdown = true;
        for (Runnable r : mPendingRunnables) {
            r.run();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        mIsShutdown = true;
        List<Runnable> runnables = new ArrayList<>(mPendingRunnables);
        mPendingRunnables.clear();
        return runnables;
    }

    @Override
    public boolean isShutdown() {
        return mIsShutdown;
    }

    @Override
    public boolean isTerminated() {
        return mIsShutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        shutdown();
        return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        CompletedFuture<T> f = new CompletedFuture<>(task);
        onExecute(f::completeTask);
        return f;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Future<?> submit(Runnable task) {
        CompletedFuture<Void> f = new CompletedFuture<>(() -> {
            task.run();
            return null;
        });
        onExecute(f::completeTask);
        return f;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit unit) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        long millisDelay = TimeUnit.MILLISECONDS.convert(delay, unit);
        CompletedFuture<Void> f = new CompletedFuture<>(() -> {
            command.run();
            return null;
        }, millisDelay);
        onExecute(f::completeTask);
        return f;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        long millisDelay = TimeUnit.MILLISECONDS.convert(delay, unit);
        CompletedFuture<V> f = new CompletedFuture<>(callable, millisDelay);
        onExecute(f::completeTask);
        return f;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
            TimeUnit unit) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
            long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void execute(Runnable command) {
        onExecute(command);
    }

    private void onExecute(Runnable command) {
        if (mWaitToComplete) {
            mPendingRunnables.add(command);
        } else {
            command.run();
        }
    }

    public void executePending() {
        for (Runnable r : mPendingRunnables) {
            r.run();
        }
        mPendingRunnables.clear();
    }
}
