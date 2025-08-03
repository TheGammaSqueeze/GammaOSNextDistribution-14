/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.test.mocks;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(MockitoJUnitRunner.class)
public final class JavaMockitoHelperTest {

    private static final String TAG = JavaMockitoHelperTest.class.getSimpleName();

    // Make sure TIMEOUT_MS is at least 1s, but not same as ASYNC_TIMEOUT_MS
    private static final long TIMEOUT_MS = Math.max(1_000L, JavaMockitoHelper.ASYNC_TIMEOUT_MS) + 1;

    private static final String DEFAULT_TIMEOUT_MSG = JavaMockitoHelper.ASYNC_TIMEOUT_MS + "ms";
    private static final String CUSTOM_TIMEOUT_MSG = TIMEOUT_MS + "ms";

    @Mock
    private Future<String> mFuture;

    @Test
    public void testAwait_Semaphore() throws Exception {
        Semaphore semaphore = new Semaphore(1);

        JavaMockitoHelper.await(semaphore, TIMEOUT_MS);

        assertThat(semaphore.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testAwait_CountDownLatch() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> latch.countDown(), "testAwait_CountDownLatch").start();

        JavaMockitoHelper.await(latch, TIMEOUT_MS);

        assertThat(latch.getCount()).isEqualTo(0);
    }

    @Test
    public void testSilentAwait_notCalled() {
        CountDownLatch latch = new CountDownLatch(1);

        assertThat(JavaMockitoHelper.silentAwait(latch, 5L)).isFalse();
        assertThat(latch.getCount()).isEqualTo(1);
    }

    @Test
    public void testSilentAwait_called() {
        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> latch.countDown(), "testSilentAwait_called").start();

        assertThat(JavaMockitoHelper.silentAwait(latch, TIMEOUT_MS)).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
    }

    @Test
    public void testGetResult_nullFuture() throws Exception {
        assertThrows(NullPointerException.class, ()->JavaMockitoHelper.getResult(null, "D'OH!"));
    }

    @Test
    public void testGetResult_nullMessageFormat() throws Exception {
        assertThrows(NullPointerException.class, ()->JavaMockitoHelper.getResult(mFuture, null));
    }

    @Test
    public void testGetResult_ok() throws Exception {
        when(mFuture.get(anyLong(), any())).thenReturn("done");

        assertThat(JavaMockitoHelper.getResult(mFuture, "I am number %d!", 4)).isEqualTo("done");
    }

    @Test
    public void testGetResult_timeoutException() throws Exception {
        TimeoutException cause = new TimeoutException("D'OH!");
        when(mFuture.get(anyLong(), any())).thenThrow(cause);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> JavaMockitoHelper.getResult(mFuture, "I am number %d!", 4));

        assertThat(exception).hasCauseThat().isSameInstanceAs(cause);
        assertThat(exception).hasMessageThat().contains("I am number 4!");
        assertThat(exception).hasMessageThat().contains(DEFAULT_TIMEOUT_MSG);
    }

    @Test
    public void testGetResult_executionException() throws Exception {
        ExecutionException cause = new ExecutionException(new Exception("Double D'OH!"));
        when(mFuture.get(anyLong(), any())).thenThrow(cause);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> JavaMockitoHelper.getResult(mFuture, "I am number %d!", 4));

        assertThat(exception).hasCauseThat().isSameInstanceAs(cause);
        assertThat(exception).hasMessageThat().contains("I am number 4!");
    }

    @Test
    public void testGetResult_interruptedException() throws Exception {
        InterruptedException cause = new InterruptedException("D'OH!");
        when(mFuture.get(anyLong(), any())).thenThrow(cause);
        Thread thread = getCurrentThreadIninterrupted();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> JavaMockitoHelper.getResult(mFuture, "I am number %d!", 4));

        assertThat(exception).hasCauseThat().isSameInstanceAs(cause);
        assertThat(exception).hasMessageThat().contains("I am number 4!");
        assertWithMessage("thread %s interrupted ", thread).that(thread.isInterrupted()).isTrue();
    }

    @Test
    public void testGetResult_withCustomTimeout_nullFuture() throws Exception {
        assertThrows(NullPointerException.class,
                () -> JavaMockitoHelper.getResult(null, TIMEOUT_MS, "D'OH!"));
    }

    @Test
    public void testGetResult_withCustomTimeout_nullMessageFormat() throws Exception {
        assertThrows(NullPointerException.class,
                () -> JavaMockitoHelper.getResult(mFuture, TIMEOUT_MS, null));
    }

    @Test
    public void testGetResult_withCustomTimeout_ok() throws Exception {
        when(mFuture.get(anyLong(), any(TimeUnit.class))).thenReturn("done");

        assertThat(JavaMockitoHelper.getResult(mFuture, TIMEOUT_MS, "I am number %d!", 4))
                .isEqualTo("done");
        verify(mFuture).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }


    @Test
    public void testGetResult_withCustomTimeout_timeoutException() throws Exception {
        TimeoutException cause = new TimeoutException("D'OH!");
        when(mFuture.get(anyLong(), any())).thenThrow(cause);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> JavaMockitoHelper.getResult(mFuture, TIMEOUT_MS, "I am number %d!", 4));

        assertThat(exception).hasCauseThat().isSameInstanceAs(cause);
        assertThat(exception).hasMessageThat().contains("I am number 4!");
        assertThat(exception).hasMessageThat().contains(CUSTOM_TIMEOUT_MSG);
    }

    @Test
    public void testGetResult_withCustomTimeout_executionException() throws Exception {
        ExecutionException cause = new ExecutionException(new Exception("Double D'OH!"));
        when(mFuture.get(anyLong(), any())).thenThrow(cause);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> JavaMockitoHelper.getResult(mFuture, TIMEOUT_MS, "I am number %d!", 4));

        assertThat(exception).hasCauseThat().isSameInstanceAs(cause);
        assertThat(exception).hasMessageThat().contains("I am number 4!");
    }

    @Test
    public void testGetResult_withCustomTimeout_interruptedException() throws Exception {
        InterruptedException cause = new InterruptedException("D'OH!");
        when(mFuture.get(anyLong(), any())).thenThrow(cause);
        Thread thread = getCurrentThreadIninterrupted();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> JavaMockitoHelper.getResult(mFuture, TIMEOUT_MS, "I am number %d!", 4));

        assertThat(exception).hasCauseThat().isSameInstanceAs(cause);
        assertThat(exception).hasMessageThat().contains("I am number 4!");
        assertWithMessage("thread %s interrupted ", thread).that(thread.isInterrupted()).isTrue();
    }

    private Thread getCurrentThreadIninterrupted() {
        Thread thread = Thread.currentThread();
        if (Thread.interrupted()) {
            Log.w(TAG, "Thread " + thread + " was not interrupted");
            // call to interrupted() interrupts it, so check again...
            if (!thread.isInterrupted()) {
                throw new IllegalStateException("Could not reset interrupted state of  " + thread);
            }
        }
        return thread;
    }
}
