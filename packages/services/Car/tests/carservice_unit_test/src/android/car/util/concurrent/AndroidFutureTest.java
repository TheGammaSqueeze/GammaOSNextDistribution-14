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

package android.car.util.concurrent;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Parcel;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

public final class AndroidFutureTest {

    private static final String STRING_VALUE = "test-future-string";
    private static final String EXCEPTION_MESSAGE = "An exception was thrown!";
    private static final long TIMEOUT_MS = 100;

    private AndroidFuture<String> mUncompletedFuture = new AndroidFuture<>();
    private AndroidFuture<String> mCompletedFuture = AndroidFuture.completedFuture(STRING_VALUE);

    private CountDownLatch mLatch = new CountDownLatch(1);
    private Parcel mParcel = Parcel.obtain();

    @Test
    public void testComplete_uncompleted() throws Exception {
        boolean changed = mUncompletedFuture.complete(STRING_VALUE);

        assertThat(changed).isTrue();
        assertThat(mUncompletedFuture.get()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void testComplete_alreadyCompleted() throws Exception {
        AndroidFuture<String> completedFuture = AndroidFuture.completedFuture(STRING_VALUE);

        boolean changed = completedFuture.complete(STRING_VALUE);

        assertThat(changed).isFalse();
        assertThat(completedFuture.get()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void testCompleteExceptionally_uncompleted() throws Exception {
        Exception origException = new UnsupportedOperationException();
        boolean changed = mUncompletedFuture.completeExceptionally(origException);

        assertThat(changed).isTrue();
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> mUncompletedFuture.get());
        assertThat(thrown.getCause()).isSameInstanceAs(origException);
    }

    @Test
    public void testCompleteExceptionally_alreadyCompleted() throws Exception {
        boolean changed = mCompletedFuture.completeExceptionally(
                new RuntimeException("throw this"));

        assertThat(changed).isFalse();
        assertThat(mCompletedFuture.get()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void testCancel_uncompleted() throws Exception {
        boolean changed = mUncompletedFuture.cancel(/* mayInterruptIfRunning= */true);

        assertThat(changed).isTrue();
    }

    @Test
    public void testCancel_alreadyCompleted() throws Exception {
        boolean changed = mCompletedFuture.cancel(/* mayInterruptIfRunning= */true);

        assertThat(changed).isFalse();
        assertThat(mCompletedFuture.get()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void testWhenComplete_alreadyCompleted() throws Exception {
        mCompletedFuture.whenComplete((obj, err) -> {
            assertThat(obj).isEqualTo(STRING_VALUE);
            assertThat(err).isNull();
            mLatch.countDown();
        });
        mLatch.await();
    }

    @Test
    public void testWhenComplete_uncompleted() throws Exception {
        mUncompletedFuture.whenComplete((obj, err) -> {
            assertThat(obj).isEqualTo(STRING_VALUE);
            assertThat(err).isNull();
            mLatch.countDown();
        });
        assertThat(mLatch.getCount()).isEqualTo(1);
        mUncompletedFuture.complete(STRING_VALUE);
        mLatch.await();
        assertThat(mLatch.getCount()).isEqualTo(0);
    }

    @Test
    public void testWhenComplete_completeExceptionally() throws Exception {
        Exception origException = new UnsupportedOperationException(EXCEPTION_MESSAGE);
        mUncompletedFuture.completeExceptionally(origException);

        mUncompletedFuture.whenComplete((obj, err) -> {
            assertThat(obj).isNull();
            assertThat(err).isSameInstanceAs(origException);
            mLatch.countDown();
        });
        mLatch.await();
    }

    @Test
    public void testWhenComplete_nullAction() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mUncompletedFuture.whenComplete(/* action= */null));
    }

    @Test
    public void testWhenCompleteAsync_nullExecutor() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mUncompletedFuture.whenCompleteAsync((o, e) -> {}, /* executor= */null));
    }

    @Test
    public void testOrTimeout_completed() throws Exception {
        mCompletedFuture.orTimeout(TIMEOUT_MS, MILLISECONDS);

        assertThat(mCompletedFuture.get()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void testOrTimeout_uncompleted_timesOut() throws Exception {
        mUncompletedFuture.orTimeout(TIMEOUT_MS, MILLISECONDS);

        Throwable exception = assertThrows(Exception.class,
                () -> mUncompletedFuture.get(TIMEOUT_MS * 2, MILLISECONDS));

        // In most cases, an ExecutionException is thrown with its cause set to a TimingException,
        // or depending on the timing just a TimingException is thrown. Should handle both case to
        // avoid test flakyness.
        if (exception instanceof ExecutionException) {
            exception = exception.getCause();
        }

        assertThat(exception).isInstanceOf(TimeoutException.class);
    }

    @Test
    public void testSetTimeoutHandler_nullHandler() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mUncompletedFuture.setTimeoutHandler(/* handler= */null));
    }

    @Test
    public void testWriteToParcel_completed() throws Exception {
        Parcel parcel = Parcel.obtain();
        mCompletedFuture.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        AndroidFuture fromParcel = AndroidFuture.CREATOR.createFromParcel(parcel);

        assertThat(fromParcel.get()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void testWriteToParcel_completedExceptionally() throws Exception {
        AndroidFuture<Integer> original = new AndroidFuture<>();
        UnsupportedOperationException exception = new UnsupportedOperationException(
                EXCEPTION_MESSAGE);
        original.completeExceptionally(exception);
        original.writeToParcel(mParcel, /* flags = */0);

        mParcel.setDataPosition(0);
        AndroidFuture fromParcel = AndroidFuture.CREATOR.createFromParcel(mParcel);

        ExecutionException thrown = assertThrows(ExecutionException.class, () -> fromParcel.get());
        assertThat(thrown.getCause()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(thrown.getMessage()).contains(EXCEPTION_MESSAGE);
    }

    @Test
    public void testWriteToParcel_uncompleted() throws Exception {
        mUncompletedFuture.writeToParcel(mParcel, /* flags= */0);

        mParcel.setDataPosition(0);
        AndroidFuture fromParcel = AndroidFuture.CREATOR.createFromParcel(mParcel);

        fromParcel.complete(STRING_VALUE);
        assertThat(mUncompletedFuture.get()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void testWriteToParcel_uncompleted_Exception() throws Exception {
        mUncompletedFuture.writeToParcel(mParcel, /* flags= */0);

        mParcel.setDataPosition(0);
        AndroidFuture fromParcel = AndroidFuture.CREATOR.createFromParcel(mParcel);
        UnsupportedOperationException exception = new UnsupportedOperationException(
                EXCEPTION_MESSAGE);
        fromParcel.completeExceptionally(exception);
        ExecutionException thrown =
                assertThrows(ExecutionException.class, () -> mUncompletedFuture.get());
        assertThat(thrown.getCause()).isSameInstanceAs(exception);
    }

    @Test
    public void testSupply() throws Exception {
        AndroidFuture<String> suppliedFuture = AndroidFuture.supply(() -> STRING_VALUE);

        assertThat(suppliedFuture.get()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void testSupply_futureThrowingException() throws Exception {
        UnsupportedOperationException exception = new UnsupportedOperationException(
                EXCEPTION_MESSAGE);
        AndroidFuture<String> future = AndroidFuture.supply(() -> {
            throw exception;
        });

        ExecutionException thrown = assertThrows(ExecutionException.class, () -> future.get());

        assertThat(thrown.getCause()).isSameInstanceAs(exception);
    }

    @Test
    public void testThenApply() throws Exception {
        String appendString = " future is here";
        AndroidFuture<String> farFuture = mUncompletedFuture.thenApply(s -> s + appendString);

        mUncompletedFuture.complete(STRING_VALUE);
        String expectedResult = STRING_VALUE + appendString;

        assertThat(farFuture.get()).isEqualTo(expectedResult);
    }

    @Test
    public void testThenApply_functionThrowingException() throws Exception {
        UnsupportedOperationException exception = new UnsupportedOperationException(
                EXCEPTION_MESSAGE);
        AndroidFuture<String> farFuture = mUncompletedFuture.thenApply(s -> {
            throw exception;
        });

        mUncompletedFuture.complete(STRING_VALUE);
        ExecutionException thrown = assertThrows(ExecutionException.class, () -> farFuture.get());

        assertThat(thrown.getCause()).isSameInstanceAs(exception);
    }

    @Test
    public void testThenCompose() throws Exception {
        String appendString = " future is here";
        AndroidFuture<String> composedFuture = mUncompletedFuture.thenCompose(
                s -> AndroidFuture.supply(() -> s + appendString));

        mUncompletedFuture.complete(STRING_VALUE);
        String expectedResult = STRING_VALUE + appendString;

        assertThat(composedFuture.get()).isEqualTo(expectedResult);
    }

    @Test
    public void testThenCompose_functionThrowingException() throws Exception {
        UnsupportedOperationException exception = new UnsupportedOperationException(
                EXCEPTION_MESSAGE);
        AndroidFuture<String> throwingFuture = AndroidFuture.supply(() -> {
            throw exception;
        });
        AndroidFuture<String> composedFuture = mUncompletedFuture.thenCompose(s -> throwingFuture);

        mUncompletedFuture.complete(STRING_VALUE);
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> composedFuture.get());

        assertThat(thrown.getCause()).isSameInstanceAs(exception);
    }

    @Test
    public void testThenCombine() throws Exception {
        String nearFutureString = "near future comes";
        AndroidFuture<String> nearFuture = AndroidFuture.supply(() -> nearFutureString);
        String farFutureString = " before far future.";
        AndroidFuture<String> farFuture = AndroidFuture.supply(() -> farFutureString);
        AndroidFuture<String> combinedFuture =
                nearFuture.thenCombine(farFuture, ((s1, s2) -> s1 + s2));

        assertThat(combinedFuture.get()).isEqualTo(nearFutureString + farFutureString);
    }

    @Test
    public void testThenCombine_functionThrowingException() throws Exception {
        String nearFutureString = "near future comes";
        AndroidFuture<String> nearFuture = AndroidFuture.supply(() -> nearFutureString);
        String farFutureString = " before far future.";
        AndroidFuture<String> farFuture = AndroidFuture.supply(() -> farFutureString);
        UnsupportedOperationException exception = new UnsupportedOperationException(
                EXCEPTION_MESSAGE);
        BiFunction<String, String, String> throwingFunction = (s1, s2) -> {
            throw exception;
        };
        AndroidFuture<String> combinedFuture = nearFuture.thenCombine(farFuture, throwingFunction);

        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> combinedFuture.get());

        assertThat(thrown.getCause()).isSameInstanceAs(exception);
    }
}
