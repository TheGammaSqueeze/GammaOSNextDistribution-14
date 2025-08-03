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

package com.android.bedstead.nene.utils;

import android.util.Log;

import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.exceptions.PollValueFailedException;

import java.time.Duration;
import java.util.function.Function;

/**
 * Retry some logic.
 *
 * <p>Use by calling {@link Retry#logic} with a lambda representing the logic to be retried.
 *
 * <p>If any exception is thrown by the logic, then it will be logged and re-attempted. By default,
 * this will try up to {@link #timeout(Duration)} (defaulting to 30 seconds), and if the final
 * attempt still throws an exception that exception will be thrown.
 *
 * @param <E> return type of logic
 */
public final class Retry<E> {

    private static final String LOG_TAG = "Retry";

    private final Poll<E> mPoll;

    /**
     * Begin retrying the given logic.
     */
    public static <E> Retry<E> logic(Poll.ValueSupplier<E> supplier) {
        return new Retry<>(supplier);
    }

    /**
     * Begin retrying the given logic.
     */
    public static VoidRetry logic(VoidRetry.VoidRunnable runnable) {
        return new VoidRetry(runnable);
    }

    private Retry(Poll.ValueSupplier<E> supplier) {
        mPoll = Poll.forValue(supplier)
                .errorOnFail();
    }

    /** Change the default timeout before the check is considered failed (default 30 seconds). */
    public Retry<E> timeout(Duration timeout) {
        mPoll.timeout(timeout);
        return this;
    }

    /**
     * Set a method which, after an exception is thrown, can tell if the failure is terminal.
     *
     * <p>This method will be passed the exception return true if this exception is terminal.
     *
     * <p>If true is returned, then no more retries will be attempted, otherwise retries will
     * continue until timeout.
     */
    public Retry<E> terminalException(Function<Throwable, Boolean> terminalChecker) {
        mPoll.terminalException(terminalChecker);
        return this;
    }

    /**
     * Run the logic, retrying on exception.
     *
     * <p>This will retry fetching until it succeeds without an exception or the
     * timeout expires.
     */
    public E run() throws Throwable {
        try {
            return mPoll.await();
        } catch (PollValueFailedException e) {
            // We know there will be an exception cause because we aren't validating the value
            Log.e(LOG_TAG, "Failure in retries", e);
            throw e.getCause();
        }
    }

    /**
     * {@link #run()} but all exceptions are wrapped in a {@link NeneException} so they don't need
     * to be caught.
     */
    public E runAndWrapException() throws NeneException {
        try {
            return run();
        } catch (Throwable t) {
            throw new NeneException(t);
        }
    }
}
