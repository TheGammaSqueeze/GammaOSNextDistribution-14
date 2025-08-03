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

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.IBinder;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

// TODO(b/302757068): add unit tests (and/or convert tests from OutcomeReceiverForTestsTest)
/**
 * Custom {@link SyncCallback} where the error type is an {@code int}.
 *
 * <p>Callers typically call {@link #assertSuccess()} or {@link #assertFailure(int)} to assert the
 * expected result.
 *
 * <p>Modeled on {@link android.adservices.topics.IGetTopicsCallback}, so {@link
 * SyncGetTopicsCallback} just extends it.
 */
public abstract class IntFailureSyncCallback<T> extends SyncCallback<T, Integer> {

    @VisibleForTesting
    static final String ERROR_WRONG_EXCEPTION_RECEIVED =
            "expected exception of type %s, but received %s";

    /**
     * Default constructor, uses {@link #DEFAULT_TIMEOUT_MS} for timeout and fails if the {@code
     * inject...} method is called in the main thread.
     */
    public IntFailureSyncCallback() {
        super();
    }

    /** Constructor with a custom timeout to wait for the outcome. */
    public IntFailureSyncCallback(int timeoutMs) {
        super(timeoutMs);
    }

    /** Constructor with custom settings. */
    protected IntFailureSyncCallback(int timeoutMs, boolean failIfCalledOnMainThread) {
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
     * @throws IllegalStateException if {@link #onResult(Object)} or {@link #onFailure(int)} was
     *     already called.
     */
    public void onFailure(int code) {
        injectError(code);
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
     */
    public void assertFailed(int expectedCode) throws InterruptedException {
        int actualCode = assertErrorReceived();
        assertWithMessage("failure code").that(actualCode).isEqualTo(expectedCode);
    }

    /**
     * Bogus method (returns {@code null} to make it easier to extend this class for binder
     * implementations.
     */
    public IBinder asBinder() {
        return null;
    }

    /**
     * Asserts that either {@link #onResult(Object)} or {@link #onError(Exception)} was called,
     * waiting up to {@link #getTimeoutMs()} milliseconds before failing (if not called).
     */
    public void assertCalled() throws InterruptedException {
        assertReceived();
    }

    /** Gets the error returned by {@link #onError(Exception)}. */
    public int getFailure() {
        return getErrorReceived();
    }

    /** Gets the result returned by {@link #onResult(Object)}. */
    public T getResult() {
        return getResultReceived();
    }
}
