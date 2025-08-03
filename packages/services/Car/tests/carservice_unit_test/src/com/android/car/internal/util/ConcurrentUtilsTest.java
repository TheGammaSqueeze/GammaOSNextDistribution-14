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

package com.android.car.internal.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class ConcurrentUtilsTest {

    @Test
    public void testWaitForFutureNoInterruptNormal() {
        ExecutorService service = ConcurrentUtils.newFixedThreadPool(1, "test pool",
                /* linuxThreadPriority= */ 0);
        Future<Boolean> future = service.submit(() -> {
            return true;
        });
        Boolean result = ConcurrentUtils.waitForFutureNoInterrupt(future, "wait for result");
        assertThat(result).isTrue();
    }

    @Test
    public void testWaitForFutureNoInterruptInterrupted() throws Exception {
        ExecutorService service = ConcurrentUtils.newFixedThreadPool(1, "test pool",
                /* linuxThreadPriority= */ 0);
        Future<Boolean> mockFuture = mock(Future.class);
        when(mockFuture.get()).thenThrow(new InterruptedException());

        assertThrows(IllegalStateException.class,
                () -> ConcurrentUtils.waitForFutureNoInterrupt(mockFuture, "wait for result"));
    }

    @Test
    public void testWaitForFutureNoInterruptRuntimeException() {
        ExecutorService service = ConcurrentUtils.newFixedThreadPool(1, "test pool",
                /* linuxThreadPriority= */ 0);
        Future<Boolean> future = service.submit(() -> {
            throw new Exception();
        });
        assertThrows(RuntimeException.class,
                () -> ConcurrentUtils.waitForFutureNoInterrupt(future, "wait for result"));
    }

    @Test
    public void testWaitForCountDownNoInterruptNormal() {
        CountDownLatch count = new CountDownLatch(2);
        ExecutorService service = ConcurrentUtils.newFixedThreadPool(1, "test pool",
                /* linuxThreadPriority= */ 0);
        service.submit(() -> {
            count.countDown();
            count.countDown();
        });

        ConcurrentUtils.waitForCountDownNoInterrupt(count, 1000, "wait for count");
    }

    @Test
    public void testWaitForCountDownNoInterruptTimeout() {
        CountDownLatch count = new CountDownLatch(2);
        assertThrows(IllegalStateException.class,
                () -> ConcurrentUtils.waitForCountDownNoInterrupt(count, 100, "wait for count"));
    }

    @Test
    public void testWaitForCountDownNoInterruptInterrupted() {
        CountDownLatch count = new CountDownLatch(2);
        // This would set the interrupt flag on the current thread and cause future.get() to
        // throw InterruptedException.
        Thread.currentThread().interrupt();
        assertThrows(IllegalStateException.class,
                () -> ConcurrentUtils.waitForCountDownNoInterrupt(count, 100, "wait for count"));
    }

    @Test
    public void testDirectExecutor() {
        CountDownLatch count = new CountDownLatch(1);
        ConcurrentUtils.DIRECT_EXECUTOR.execute(() -> {
            count.countDown();
        });
        assertThat(count.getCount()).isEqualTo(0);
    }
}
