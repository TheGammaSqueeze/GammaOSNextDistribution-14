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

import androidx.annotation.Nullable;

// TODO(b/302757068): add unit tests (and/or convert tests from OutcomeReceiverForTestsTest)
/**
 * Custom {@link SyncCallback} where the error type is an exception.
 *
 * <p>Callers typically call {@link #assertSuccess()} or {@link #assertFailure(Class)} to assert the
 * expected result.
 *
 * <p>Modeled on {@link android.os.OutcomeReceiver}, so {@link OutcomeReceiverForTests} just extends
 * it.
 */
public abstract class ExceptionFailureSyncCallback<T> extends SyncCallback<T, Exception> {

    /**
     * Default constructor, uses {@link #DEFAULT_TIMEOUT_MS} for timeout and fails if the {@code
     * inject...} method is called in the main thread.
     */
    public ExceptionFailureSyncCallback() {
        super();
    }

    /** Constructor with a custom timeout to wait for the outcome. */
    public ExceptionFailureSyncCallback(int timeoutMs) {
        super(timeoutMs);
    }

    /** Constructor with custom settings. */
    protected ExceptionFailureSyncCallback(int timeoutMs, boolean failIfCalledOnMainThread) {
        super(timeoutMs, failIfCalledOnMainThread);
    }

    /**
     * Sets a successful result as outcome.
     *
     * @throws IllegalStateException if {@link #onResult(Object)} or {@link #injectError(Object)}
     *     was already called.
     */
    public void onResult(@Nullable T result) {
        injectResult(result);
    }

    /**
     * Sets an error result as outcome.
     *
     * @throws IllegalStateException if {@link #onResult(Object)} or {@link #onError(Object)} was
     *     already called.
     */
    public void onError(@Nullable Exception error) {
        injectError(error);
    }

    /**
     * Returns the maximum time the {@code assert...} methods will wait for an outcome before
     * failing.
     */
    public int getTimeoutMs() {
        return getMaxTimeoutMs();
    }

    /**
     * Asserts that {@link #onResult(Object)} was called, waiting up to {@link #getTimeoutMs()}
     * milliseconds before failing (if not called).
     *
     * @return the result
     */
    public T assertSuccess() throws InterruptedException {
        return assertResultReceived();
    }

    /**
     * Asserts that {@link #onError(Exception)} was called, waiting up to {@link #getTimeoutMs()}
     * milliseconds before failing (if not called).
     *
     * @return the error
     */
    public <E extends Exception> E assertFailure(Class<E> expectedClass)
            throws InterruptedException {
        return assertErrorReceived(expectedClass);
    }

    /**
     * Asserts that either {@link #onResult(Object)} or {@link #onError(Exception)} was called,
     * waiting up to {@link #getTimeoutMs()} milliseconds before failing (if not called).
     */
    public void assertCalled() throws InterruptedException {
        assertReceived();
    }

    /** Gets the error returned by {@link #onError(Exception)}. */
    public @Nullable Exception getError() {
        return getErrorReceived();
    }

    /** Gets the result returned by {@link #onResult(Object)}. */
    public T getResult() {
        return getResultReceived();
    }
}
