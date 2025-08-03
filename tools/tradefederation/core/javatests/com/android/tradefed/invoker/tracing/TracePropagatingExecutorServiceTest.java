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
package com.android.tradefed.invoker.tracing;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.util.concurrent.MoreExecutors;

import com.google.common.truth.Subject;
import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.Nullable;

@RunWith(JUnit4.class)
public final class TracePropagatingExecutorServiceTest {

    private final TracePropagatingExecutorService executor = newTestExecutorService();

    // We store the original trace to not alter any tracing behavior of other tests.
    @Nullable private ActiveTrace originalTrace;

    @Before
    public void setUp() {
        // Stop tracing to start all tests in a known state.
        originalTrace = stopTracing();
    }

    @After
    public void tearDown() {
        TracingLogger.setActiveTrace(originalTrace);

        // Although we're not using threads, we shutdown the executor just in case we ever do. Not
        // doing
        // so would cause TF to hang while it waits for threads to terminate.
        MoreExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(5));
    }

    @Test
    public void noTracePropagatesToTask() throws Exception {
        stopTracing();

        Future<ActiveTrace> f = executor.submit(new TraceCaptureTask());

        assertThatFutureValue(f).isNull();
    }

    @Test
    public void activeTracePropagatesToTask() throws Exception {
        ActiveTrace expected = startTracing();

        Future<ActiveTrace> f = executor.submit(new TraceCaptureTask());

        assertThatFutureValue(f).isSameInstanceAs(expected);
    }

    @Test
    public void activeTracePropagatesToSubTask() throws Exception {
        Callable<ActiveTrace> subTask = new TraceCaptureTask();
        ActiveTrace expected = startTracing();

        Future<ActiveTrace> f =
                executor.submit(
                        () -> {
                            return executor.submit(subTask).get();
                        });

        assertThatFutureValue(f).isSameInstanceAs(expected);
    }

    @Test
    public void callerActiveTraceRestored() throws Exception {
        ActiveTrace expected = startTracing();

        Future<?> f =
                executor.submit(
                        () -> {
                            startTracing();
                        });
        f.get();

        assertThat(TracingLogger.getActiveTrace()).isSameInstanceAs(expected);
    }

    private static TracePropagatingExecutorService newTestExecutorService() {
        return TracePropagatingExecutorService.create(MoreExecutors.newDirectExecutorService());
    }

    private static ActiveTrace startTracing() {
        ActiveTrace trace = new ActiveTrace(0, 0);
        TracingLogger.setActiveTrace(trace);
        return trace;
    }

    private static ActiveTrace stopTracing() {
        ActiveTrace trace = TracingLogger.getActiveTrace();
        TracingLogger.setActiveTrace(null);
        return trace;
    }

    private static <T> Subject assertThatFutureValue(Future<T> f)
            throws InterruptedException, ExecutionException {
        return assertThat(f.get());
    }

    private static final class TraceCaptureTask implements Callable<ActiveTrace> {
        @Override
        public ActiveTrace call() {
            return TracingLogger.getActiveTrace();
        }
    }
}
