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
package com.android.tradefed.invoker.tracing;

import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.base.Throwables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

/**
 * An executor service that forwards tasks to an underlying implementation while propagating the
 * tracing context.
 *
 * <p>This enables using tracing facilities such as {@code CloseableTraceScope} in submitted tasks.
 */
public final class TracePropagatingExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    /**
     * Creates an {@link ExecutorService} that delegates to the given delegate executor.
     *
     * <p>Note that the active trace on is that is propagated to tasks is the one active on calls to
     * the executor method. This is done because TF constructs most objects before starting the
     * invocation and attaching the trace.
     */
    public static TracePropagatingExecutorService create(ExecutorService delegate) {
        return new TracePropagatingExecutorService(delegate);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrapTask(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrapTask(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrapTask(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return delegate.invokeAll(wrapTasks(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(wrapTasks(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrapTasks(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrapTasks(tasks), timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrapTask(command));
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    private TracePropagatingExecutorService(ExecutorService delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    private <T> Callable<T> wrapTask(Callable<T> task) {
        // Note that we query the active trace on the thread calling the Executor method to
        // then propagate it to tasks.
        @Nullable ActiveTrace rootTrace = TracingLogger.getActiveTrace();
        return () -> {
            try (TraceScope ignored = makeActive(rootTrace)) {
                return task.call();
            }
        };
    }

    private Runnable wrapTask(Runnable command) {
        Callable<?> wrapped = wrapTask(Executors.callable(command));
        return () -> {
            try {
                wrapped.call();
            } catch (Exception e) {
                Throwables.throwIfUnchecked(e);
                // We should never really get here since we're wrapping a Runnable that never throws
                // a checked exception.
                throw new AssertionError(e);
            }
        };
    }

    protected <T> Collection<? extends Callable<T>> wrapTasks(
            Collection<? extends Callable<T>> tasks) {
        List<Callable<T>> wrapped = new ArrayList<>();
        for (Callable<T> task : tasks) {
            wrapped.add(wrapTask(task));
        }
        return wrapped;
    }

    private static TraceScope makeActive(@Nullable ActiveTrace toAttach) {
        // Save the active trace that is currently active on the task's thread for us to later
        // restore it. This is not necessarily {@code null} since tasks could submit additional
        // tasks using the same executor. The thread could also be reused for other tasks which
        // were submitted with different trace contexts.
        ActiveTrace toRestore = setActiveTraceIfChanged(toAttach);

        return () -> {
            // We always restore since the task may have switched traces while running.
            if (setActiveTraceIfChanged(toRestore) != toAttach) {
                CLog.w("Unexpected active trace, close was not correctly called");
            }
            ;
        };
    }

    private static @Nullable ActiveTrace setActiveTraceIfChanged(@Nullable ActiveTrace toAttach) {
        ActiveTrace toRestore = TracingLogger.getActiveTrace();

        // This is an optimization since there's no point switching if the current and target
        // trace are the same.
        if (toAttach != toRestore) {
            TracingLogger.setActiveTrace(toAttach);
        }

        return toRestore;
    }

    interface TraceScope extends AutoCloseable {
        @Override
        public void close();
    }
}
