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

import com.android.bedstead.nene.exceptions.NeneException;

import java.time.Duration;
import java.util.function.Function;

/**
 * Version of {@link Retry} when we don't care about a return value.
 */
public final class VoidRetry {

    private final Retry<Void> mRetry;

    VoidRetry(VoidRunnable runnable) {
        mRetry = Retry.logic(() -> {
            runnable.run();
            return null;
        });
    }

    /** Change the default timeout before the check is considered failed (default 30 seconds). */
    public VoidRetry timeout(Duration timeout) {
        mRetry.timeout(timeout);
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
    public VoidRetry terminalException(Function<Throwable, Boolean> terminalChecker) {
        mRetry.terminalException(terminalChecker);
        return this;
    }

    /**
     * Run the logic, retrying on exception.
     *
     * <p>This will retry fetching until it succeeds without an exception or the
     * timeout expires.
     */
    public void run() throws Throwable {
        mRetry.run();
    }

    /** Interface for supplying logic to {@link VoidRetry}. */
    public interface VoidRunnable {
        /** Run the method and optionally throw exceptions. */
        void run() throws Throwable;
    }

    /**
     * {@link #run()} but all exceptions are wrapped in a {@link NeneException} so they don't need
     * to be caught.
     */
    public void runAndWrapException() throws NeneException {
        try {
            run();
        } catch (Throwable t) {
            throw new NeneException(t);
        }
    }
}
