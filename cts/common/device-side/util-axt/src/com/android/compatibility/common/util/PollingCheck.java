/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.compatibility.common.util;

import junit.framework.Assert;

import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class PollingCheck {
    private static final long TIME_SLICE = 50;
    private static final long DEFAULT_TIMEOUT = 3_000;
    private static final String DEFAULT_ERROR_MESSAGE = "unexpected timeout";

    private final long mTimeout;
    private final String mErrorMessage;

    public static interface PollingCheckCondition {
        boolean canProceed();
    }

    public PollingCheck() {
        this(DEFAULT_TIMEOUT, DEFAULT_ERROR_MESSAGE);
    }

    public PollingCheck(long timeout) {
        this(timeout, DEFAULT_ERROR_MESSAGE);
    }

    public PollingCheck(long timeout, String errorMessage) {
        mTimeout = timeout;
        mErrorMessage = errorMessage;
    }

    public PollingCheck(String errorMessage) {
        this(DEFAULT_TIMEOUT, errorMessage);
    }

    protected abstract boolean check();

    public void run() {
        if (check()) {
            return;
        }

        long timeout = mTimeout;
        while (timeout > 0) {
            try {
                Thread.sleep(TIME_SLICE);
            } catch (InterruptedException e) {
                Assert.fail("unexpected InterruptedException");
            }

            if (check()) {
                return;
            }

            timeout -= TIME_SLICE;
        }

        Assert.assertTrue(mErrorMessage, check());
    }

    public <E> E runWaitAndReturnResult(Supplier<E> supplier, Function<E, Boolean> condition) {
        E output = supplier.get();
        if (condition.apply(output)) {
            return output;
        }
        long timeout = mTimeout;
        while (timeout > 0) {
            try {
                Thread.sleep(TIME_SLICE);
            } catch (InterruptedException e) {
                Assert.fail("unexpected InterruptedException");
            }

            output = supplier.get();
            if (condition.apply(output)) {
                return output;
            }

            timeout -= TIME_SLICE;
        }

        return output;
    }

    public static void check(CharSequence message, long timeout, Callable<Boolean> condition)
            throws Exception {
        while (timeout > 0) {
            if (condition.call()) {
                return;
            }

            Thread.sleep(TIME_SLICE);
            timeout -= TIME_SLICE;
        }

        Assert.fail(message.toString());
    }

    public static void waitFor(final PollingCheckCondition condition) {
        new PollingCheck() {
            @Override
            protected boolean check() {
                return condition.canProceed();
            }
        }.run();
    }

    public static void waitFor(final PollingCheckCondition condition, String errorMessage) {
        new PollingCheck(errorMessage) {
            @Override
            protected boolean check() {
                return condition.canProceed();
            }
        }.run();
    }

    public static void waitFor(long timeout, final PollingCheckCondition condition) {
        new PollingCheck(timeout) {
            @Override
            protected boolean check() {
                return condition.canProceed();
            }
        }.run();
    }

    public static void waitFor(long timeout, BooleanSupplier condition, String errorMessage) {
        new PollingCheck(timeout, errorMessage) {
            @Override
            protected boolean check() {
                return condition.getAsBoolean();
            }
        }.run();
    }

    public static <E> E waitFor(long timeout, Supplier<E> supplier,
            Function<E, Boolean> condition) {
        return new PollingCheck(timeout) {
            // Not used in the corresponding runWaitAndReturnResult function, Added just to provide
            // default implementation of abstract check function.
            @Override
            protected boolean check() {
                return true;
            }
        }.runWaitAndReturnResult(supplier, condition);
    }
}
