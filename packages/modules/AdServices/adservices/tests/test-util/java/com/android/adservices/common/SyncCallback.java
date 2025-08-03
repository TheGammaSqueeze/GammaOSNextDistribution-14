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
package com.android.adservices.common;

import static com.android.adservices.shared.util.Preconditions.checkState;
import static com.android.internal.util.Preconditions.checkArgument;

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.adservices.shared.testing.common.Identifiable;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Optional;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper used to block until a success (or failure) callback is received.
 *
 * <p>It's a concrete class so it can be used on simple cases (where usually people uses a {@link
 * CountDownLatch}), but typically the tests need to use an existing callback (like {@code
 * OutcomeReceiver}), in which case the actual test artifact would be a subclasses. In fact, your
 * test most likely won't extend this class directly, but subclasses like {@link
 * ExceptionFailureSyncCallback} or {@link IntFailureSyncCallback}.
 *
 * <p>Callers typically call {@link #assertResultReceived()} or {@link #assertFailureReceived()} to
 * assert the expected outcome.
 *
 * @param <T> type of the object received on success.
 * @param <E> type of the object received on error.
 */
public class SyncCallback<T, E> implements Identifiable {

    @VisibleForTesting static final String TAG = SyncCallback.class.getSimpleName();

    private static final boolean DEFAULT_BEHAVIOR_FOR_FAIL_IF_CALLED_ON_MAIN_THREAD = true;

    /** Default time (used when not specified by constructor). */
    public static final int DEFAULT_TIMEOUT_MS = 5_000;

    @VisibleForTesting
    static final String MSG_WRONG_ERROR_RECEIVED = "expected error of type %s, but received %s";

    private static final AtomicInteger sNextId = new AtomicInteger();

    private final String mId = getClass().getSimpleName() + "#" + sNextId.incrementAndGet();

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final int mTimeoutMs;
    private final long mEpoch = SystemClock.elapsedRealtime();

    private @Nullable E mError;
    private @Nullable Optional<T> mResult;
    private @Nullable String mMethodCalled;
    private @Nullable RuntimeException mInternalFailure;
    private final boolean mFailIfCalledOnMainThread;

    /**
     * Default constructor, uses {@link #DEFAULT_TIMEOUT_MS} for timeout and fails if the {@code
     * inject...} method is called in the main thread.
     */
    protected SyncCallback() {
        this(DEFAULT_TIMEOUT_MS, DEFAULT_BEHAVIOR_FOR_FAIL_IF_CALLED_ON_MAIN_THREAD);
    }

    /** Constructor with a custom timeout to wait for the outcome. */
    protected SyncCallback(int timeoutMs) {
        this(timeoutMs, DEFAULT_BEHAVIOR_FOR_FAIL_IF_CALLED_ON_MAIN_THREAD);
    }

    /** Constructor with custom settings. */
    protected SyncCallback(int timeoutMs, boolean failIfCalledOnMainThread) {
        mTimeoutMs = timeoutMs;
        mFailIfCalledOnMainThread = failIfCalledOnMainThread;
    }

    @Override
    public final String getId() {
        return mId;
    }

    @VisibleForTesting
    boolean isFailIfCalledOnMainThread() {
        return mFailIfCalledOnMainThread;
    }

    /**
     * Returns the maximum time the {@code assert...} methods will wait for an outcome before
     * failing.
     */
    protected int getMaxTimeoutMs() {
        return mTimeoutMs;
    }

    /**
     * Sets a successful result as outcome.
     *
     * @throws IllegalStateException if {@link #injectResult(T)} or {@link #injectError(E)} was
     *     already called.
     */
    public final void injectResult(@Nullable T result) {
        mResult = Optional.fromNullable(result);
        setMethodCalled("injectResult", result);
    }

    /**
     * Sets an error result as outcome.
     *
     * @throws IllegalStateException if {@link #injectResult(T)} or {@link #injectError(E)} was
     *     already called.
     */
    public final void injectError(E error) {
        mError = error;
        setMethodCalled("injectError", error);
    }

    /**
     * Asserts that {@link #injectResult(Object)} was called, waiting up to {@link
     * #getMaxTimeoutMs()} milliseconds before failing (if not called).
     *
     * @return the result
     */
    public final @Nullable T assertResultReceived() throws InterruptedException {
        assertReceived();

        assertWithMessage("result received").that(mResult).isNotNull();
        return getResult();
    }

    /**
     * Asserts that {@link #injectError(Exception)} was called, waiting up to {@link
     * #getMaxTimeoutMs()} milliseconds before failing (if not called).
     *
     * @return the error
     */
    public final E assertErrorReceived() throws InterruptedException {
        assertReceived();

        assertWithMessage("error").that(mError).isNotNull();
        return mError;
    }

    /**
     * Asserts that {@link #injectError(Object)} was called with a class of type {@code S}, waiting
     * up to {@link #getMaxTimeoutMs()} milliseconds before failing (if not called).
     *
     * @return the error
     */
    public final <S extends E> S assertErrorReceived(Class<S> expectedClass)
            throws InterruptedException {
        checkArgument(expectedClass != null, "expectedClass cannot be null");
        E error = assertErrorReceived();
        checkState(expectedClass.isInstance(error), MSG_WRONG_ERROR_RECEIVED, expectedClass, error);
        return expectedClass.cast(error);
    }

    /**
     * Asserts that either {@link #injectResult(Object)} or {@link #injectError(Exception)} was
     * called, waiting up to {@link #getMaxTimeoutMs()} milliseconds before failing (if not called).
     */
    public final void assertReceived() throws InterruptedException {
        logV(
                "assertReceived(): waiting up to %d ms on %s until called",
                mTimeoutMs, Thread.currentThread());
        boolean called = mLatch.await(mTimeoutMs, TimeUnit.MILLISECONDS);
        if (!called) {
            throw new IllegalStateException(
                    String.format("Callback not received (on %s) in %d ms", this, mTimeoutMs));
        }
        if (mInternalFailure != null) {
            throw mInternalFailure;
        }
    }

    /**
     * Gets the error returned by {@link #injectError(E)} (or {@code null} if it was not called
     * yet).
     */
    public final @Nullable E getErrorReceived() {
        return mError;
    }

    /**
     * Gets the result returned by {@link #injectResult(T)} (or {@code null} if it was not called
     * yet).
     */
    public final @Nullable T getResultReceived() {
        return getResult();
    }

    /**
     * Convenience method to log a verbose message - it includes an unique identifier for the
     * callback.
     */
    @FormatMethod
    protected final void logV(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        Log.v(TAG, "[" + getId() + "] " + String.format(msgFmt, msgArgs));
    }

    /**
     * Convenience method to log an error message - it includes an unique identifier for the
     * callback.
     */
    @FormatMethod
    protected final void logE(@FormatString String msgFmt, @Nullable Object... msgArgs) {
        Log.e(TAG, "[" + getId() + "] " + String.format(msgFmt, msgArgs));
    }

    @Override
    public String toString() {
        return getId()
                + "[latch="
                + mLatch
                + ", timeoutMs="
                + mTimeoutMs
                + ", epoch="
                + mEpoch
                + ", error="
                + mError
                + ", result="
                + getResult()
                + ", methodCalled="
                + mMethodCalled
                + ", internalFailure="
                + mInternalFailure
                + ", failIfCalledOnMainThread="
                + mFailIfCalledOnMainThread
                + "]";
    }

    private void setMethodCalled(String method, Object arg) {
        String methodCalled = method + "(" + arg + ")";
        long delta = SystemClock.elapsedRealtime() - mEpoch;
        Thread currentThread = Thread.currentThread();
        logV("%s called in %d ms on %s", methodCalled, delta, currentThread);
        String errorMsg = null;
        if (mMethodCalled != null) {
            errorMsg = methodCalled + " called after " + mMethodCalled;
        }
        if (mFailIfCalledOnMainThread
                && Looper.getMainLooper() != null
                && Looper.getMainLooper().isCurrentThread()) {
            errorMsg = methodCalled + " called on main thread (" + currentThread + ")";
        }
        if (errorMsg != null) {
            logE(
                    "Illegal state when %s was called on %s: %s",
                    methodCalled, currentThread, errorMsg);
            mInternalFailure = new IllegalStateException(errorMsg);
        }
        mMethodCalled = methodCalled;
        mLatch.countDown();
    }

    private @Nullable T getResult() {
        return mResult == null || !mResult.isPresent() ? null : mResult.get();
    }
}
