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

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.concurrency.AdServicesExecutors;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class SingletonRunnerTest {
    private final ListeningExecutorService mRunnerExecutor =
            AdServicesExecutors.getBlockingExecutor();

    @Test
    public void testShouldRunTask() throws Exception {
        final AtomicInteger invocationCount = new AtomicInteger(0);
        SingletonRunner<Integer> singletonRunner =
                new SingletonRunner<>(
                        "Test task",
                        shouldStop ->
                                FluentFuture.from(
                                        mRunnerExecutor.submit(invocationCount::incrementAndGet)));
        singletonRunner.runSingleInstance().get();
        assertThat(invocationCount.get()).isEqualTo(1);
    }

    @Test
    public void testShouldReturnSameFutureIfTaskIsNotCompleted() throws Exception {
        TestTaskRunner testTaskRunner = new TestTaskRunner();

        SingletonRunner<Integer> singletonRunner = new SingletonRunner<>("Test task", testTaskRunner);
        assertThat(singletonRunner.runSingleInstance())
                .isSameInstanceAs(singletonRunner.runSingleInstance());
    }

    @Test
    public void testShouldReturnDifferentFuturesIfTaskIsCompleted() throws Exception {
        TestTaskRunner testTaskRunner = new TestTaskRunner();

        SingletonRunner<Integer> singletonRunner = new SingletonRunner<>("Test task", testTaskRunner);
        FluentFuture<Integer> firstCall = singletonRunner.runSingleInstance();
        testTaskRunner.mTestHasDecidedToStop.countDown();
        // Waiting for first run to complete
        firstCall.get();

        assertThat(firstCall).isNotSameInstanceAs(singletonRunner.runSingleInstance());
    }

    @Test
    public void testShouldRunOnce() throws Exception {
        TestTaskRunner testTaskRunner = new TestTaskRunner();

        SingletonRunner<Integer> singletonRunner = new SingletonRunner<>("Test task", testTaskRunner);
        FluentFuture<Integer> firstRunResult = singletonRunner.runSingleInstance();
        singletonRunner.runSingleInstance();
        testTaskRunner.mTestHasDecidedToStop.countDown();

        assertThat(firstRunResult.get()).isEqualTo(1);
        assertThat(testTaskRunner.mInvocationCount.get()).isEqualTo(1);
    }

    @Test
    public void testShouldRunOnceMultipleStartStops() throws Exception {
        TestTaskRunner testTaskRunner = new TestTaskRunner();
        SingletonRunner<Integer> singletonRunner = new SingletonRunner<>("Test task", testTaskRunner);

        FluentFuture<Integer> firstRunResult = singletonRunner.runSingleInstance();
        FluentFuture<Integer> secondRunResult = singletonRunner.runSingleInstance();
        singletonRunner.stopWork();
        FluentFuture<Integer> thirdRunResult = singletonRunner.runSingleInstance();

        // The first task will stop only after this latch count has reached zero.
        testTaskRunner.mTestHasDecidedToStop.countDown();

        assertThat(firstRunResult).isSameInstanceAs(secondRunResult);
        assertThat(secondRunResult).isSameInstanceAs(thirdRunResult);
        assertThat(firstRunResult.get()).isEqualTo(1);
        assertThat(testTaskRunner.mInvocationCount.get()).isEqualTo(1);
    }

    @Test
    public void testShouldRunAgain() throws Exception {
        TestTaskRunner testTaskRunner = new TestTaskRunner();
        SingletonRunner<Integer> singletonRunner = new SingletonRunner<>("Test task", testTaskRunner);

        FluentFuture<Integer> firstRunResult = singletonRunner.runSingleInstance();
        testTaskRunner.mTestHasDecidedToStop.countDown();

        assertThat(firstRunResult.get()).isEqualTo(1);

        // once the first runner completed the second can start
        FluentFuture<Integer> secondRunResult = singletonRunner.runSingleInstance();

        // Same runnable invoked twice maintaining state
        assertThat(secondRunResult.get()).isEqualTo(2);
    }

    @Test
    public void testShouldStopTask() throws Exception {
        final CountDownLatch testHasDecidedToStop = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger(0);
        SingletonRunner<Integer> singletonRunner =
                new SingletonRunner<>(
                        "",
                        shouldStop ->
                                FluentFuture.from(
                                        mRunnerExecutor.submit(
                                                () -> {
                                                    counter.incrementAndGet();

                                                    try {
                                                        testHasDecidedToStop.await();
                                                    } catch (InterruptedException e) {
                                                        // Intentionally ignored
                                                    }

                                                    if (!shouldStop.get()) {
                                                        counter.incrementAndGet();
                                                    }

                                                    return counter.get();
                                                })));
        FluentFuture<Integer> taskResult = singletonRunner.runSingleInstance();

        singletonRunner.stopWork();
        testHasDecidedToStop.countDown();

        assertThat(taskResult.get()).isEqualTo(1);
    }

    class TestTaskRunner implements SingletonRunner.InterruptableTaskRunner<Integer> {
        final AtomicInteger mInvocationCount = new AtomicInteger(0);
        final CountDownLatch mTestHasDecidedToStop = new CountDownLatch(1);

        @Override
        public FluentFuture<Integer> run(Supplier<Boolean> stopFlagChecker) {
            return FluentFuture.from(
                    mRunnerExecutor.submit(
                            () -> {
                                mInvocationCount.incrementAndGet();
                                try {
                                    mTestHasDecidedToStop.await();
                                } catch (InterruptedException e) {
                                    // Ignored
                                }
                                return mInvocationCount.get();
                            }));
        }
    }
}
