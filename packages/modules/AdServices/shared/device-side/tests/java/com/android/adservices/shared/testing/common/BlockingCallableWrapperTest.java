/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.shared.testing.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class BlockingCallableWrapperTest extends AdServicesUnitTestCase {

    private final ListeningExecutorService mLightWeightExecutor =
            AdServicesExecutors.getLightWeightExecutor();

    @Test
    public void testRunsDelegate() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createNonBlockableInstance(() -> 1);

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);

        assertThat(asyncWork.get(100, TimeUnit.MILLISECONDS)).isEqualTo(1);
    }

    @Test
    public void testMarksTaskAsCompleted() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createNonBlockableInstance(() -> 1);

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);

        asyncWork.get(100, TimeUnit.MILLISECONDS);

        assertThat(toTest.isWorkCompleted()).isTrue();
    }

    @Test
    public void testMarksCompletedIsFalseUntilWorkIsDone() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createNonBlockableInstance(() -> 1);

        assertThat(toTest.isWorkCompleted()).isFalse();

        toTest.call();

        assertThat(toTest.isWorkCompleted()).isTrue();
    }

    @Test
    public void testCommandExecutionDoesNotStartsIfNotTriggered() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createBlockableInstance(() -> 1);

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);

        try {
            assertThrows(TimeoutException.class, () -> asyncWork.get(100, TimeUnit.MILLISECONDS));

            assertThat(asyncWork.isDone()).isFalse();
            assertThat(toTest.isWorkCompleted()).isFalse();
        } finally {
            asyncWork.cancel(true);
        }
    }

    @Test
    public void testCommandExecutionStartsWhenTriggered() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createBlockableInstance(() -> 1);

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);
        try {
            assertThrows(TimeoutException.class, () -> asyncWork.get(100, TimeUnit.MILLISECONDS));
            assertThat(asyncWork.isDone()).isFalse();

            toTest.startWork();
            assertThat(asyncWork.get(100, TimeUnit.MILLISECONDS)).isEqualTo(1);
        } finally {
            asyncWork.cancel(true);
        }
    }

    @Test
    public void testBlocksCallerUntilCompleted() throws Exception {
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createNonBlockableInstance(
                        () -> {
                            Thread.sleep(100);
                            return 1;
                        });

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);

        assertThat(toTest.waitForCompletion(Duration.ofHours(1))).isTrue();
        assertThat(asyncWork.isDone()).isTrue();
    }

    @Test
    public void testBlocksCallerUntilTimeoutIfNotCompleted() throws Exception {
        CountDownLatch completionLatch = new CountDownLatch(1);
        BlockingCallableWrapper<Integer> toTest =
                BlockingCallableWrapper.createNonBlockableInstance(
                        () -> {
                            completionLatch.await();
                            return 1;
                        });

        ListenableFuture<Integer> asyncWork = mLightWeightExecutor.submit(toTest);

        try {
            assertThat(toTest.waitForCompletion(Duration.ofMillis(100))).isFalse();
            assertThat(asyncWork.isDone()).isFalse();
        } finally {
            asyncWork.cancel(true);
        }
    }
}
