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
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility class for polling for some state to be reached.
 *
 * <p>To use, you first use {@link #forValue(String, ValueSupplier)} to supply the value to be
 * polled on. It is recommended you provide a descriptive name of the source of the value to improve
 * failure messages.
 *
 * <p>Then you specify the criteria you are polling for, simple criteria are provided
 * (e.g. {@link #toBeNull()}, {@link #toBeEqualTo(Object)}, etc.) and these should be preferred when
 * possible as they provide good failure messages by default. If your state cannot be queried using
 * a simple matcher, you can use {@link #toMeet(ValueChecker)} and pass in an arbitrary function to
 * check the value.
 *
 * <p>By default, this will poll up to {@link #timeout(Duration)} (defaulting to 30 seconds), and
 * will return after the timeout whatever the value is at that time. If you'd rather a
 * {@link NeneException} is thrown, you can use {@link #errorOnFail()}.
 *
 * <p>You can add more context to failures using the overloaded versions of {@link #errorOnFail()}.
 * In particular, you should do this if you're using {@link #toMeet(ValueChecker)} as otherwise the
 * failure message is not helpful.
 *
 * <p>Any exceptions thrown when getting the value or when checking it will result in that check
 * failing and a retry happening. If this is the final iteration the exception will be thrown
 * wrapped in a {@link NeneException}.
 *
 * <p>You should not use this class to retry some state changing logic until it succeeds - it should
 * only be used for polling a value until it reaches the value you want.
 */
public final class Poll<E> {

    private static final String LOG_TAG = Poll.class.getName();

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    private static final long SLEEP_MILLIS = 200; // TODO(b/223768343): Allow configuring
    private final String mValueName;
    private final ValueSupplier<E> mSupplier;
    private ValueChecker<E> mChecker = (v) -> true;
    private Function<E, Boolean> mTerminalValueChecker;
    private Function<Throwable, Boolean> mTerminalExceptionChecker;
    private Function2<String, E, String> mErrorSupplier =
            (valueName, value) -> "Expected "
                    + valueName + " to meet checker function. Was " + value;
    private Duration mTimeout = DEFAULT_TIMEOUT;
    private boolean mErrorOnFail = false;

    private Poll(String valueName, ValueSupplier<E> supplier) {
        mValueName = valueName;
        mSupplier = supplier;
    }

    /**
     * Begin polling for the given value.
     *
     * <p>In general, this method should only be used when you're using the
     * {@link #errorOnFail(Function)} method, otherwise {@link #forValue(String, ValueSupplier)}
     * will mean better error messages.
     */
    public static <E> Poll<E> forValue(ValueSupplier<E> supplier) {
        return forValue("value", supplier);
    }

    /**
     * Begin polling for the given value.
     *
     * <p>The {@code valueName} will be used in error messages.
     */
    public static <E> Poll<E> forValue(String valueName, ValueSupplier<E> supplier) {
        return new Poll<>(valueName, supplier);
    }

    /** Expect the value to be null. */
    public Poll<E> toBeNull() {
        toMeet(Objects::isNull);
        softErrorOnFail((valueName, value) ->
                "Expected " + valueName + " to be null. Was " + value);
        return this;
    }

    /** Expect the value to not be null. */
    public Poll<E> toNotBeNull() {
        toMeet(Objects::nonNull);
        softErrorOnFail((valueName, value) ->
                "Expected " + valueName + " to not be null. Was " + value);
        return this;
    }

    /** Expect the value to be equal to {@code other}. */
    public Poll<E> toBeEqualTo(E other) {
        toMeet(v -> Objects.equals(v, other));
        softErrorOnFail((valueName, value) ->
                "Expected " + valueName + " to be equal to " + other + ". Was " + value);
        return this;
    }

    /** Expect the value to not be equal to {@code other}. */
    public Poll<E> toNotBeEqualTo(E other) {
        toMeet(v -> !Objects.equals(v, other));
        softErrorOnFail((valueName, value) ->
                "Expected " + valueName + " to not be equal to " + other + ". Was " + value);
        return this;
    }

    /**
     * Expect the value to meet the requirements specified by {@code checker}.
     *
     * <p>If this method throws an exception, or returns false, then the value will be considered
     * to not have met the requirements. If true is returned then the value will be considered to
     * have met the requirements.
     */
    public Poll<E> toMeet(ValueChecker<E> checker) {
        mChecker = checker;
        return this;
    }

    /** Throw an exception on failure instead of returning the incorrect value. */
    public Poll<E> errorOnFail() {
        mErrorOnFail = true;
        return this;
    }

    /**
     * Throw an exception on failure instead of returning the incorrect value.
     *
     * <p>The {@code errorSupplier} will be passed the latest value. If you do not want to include
     * the latest value in the error message (and have it auto-provided) use
     * {@link #errorOnFail(String)}.
     */
    public Poll<E> errorOnFail(Function<E, String> errorSupplier) {
        softErrorOnFail((vn, v) -> errorSupplier.apply(v));
        mErrorOnFail = true;
        return this;
    }

    /**
     * Throw an exception on failure instead of returning the incorrect value.
     *
     * <p>The {@code error} will be used as the failure message, with the latest value added.
     */
    public Poll<E> errorOnFail(String error) {
        softErrorOnFail((vn, v) -> error + ". " + vn + " was " + v);
        mErrorOnFail = true;
        return this;
    }

    private void softErrorOnFail(Function2<String, E, String> errorSupplier) {
        mErrorSupplier = errorSupplier;
    }

    /** Change the default timeout before the check is considered failed (default 30 seconds). */
    public Poll<E> timeout(Duration timeout) {
        mTimeout = timeout;
        return this;
    }

    /**
     * Await the value meeting the requirements.
     *
     * <p>This will retry fetching and checking the value until it meets the requirements or the
     * timeout expires.
     *
     * <p>By default, the most recent value will be returned even after timeout.
     * See {@link #errorOnFail()} to change this behavior.
     */
    public E await() {
        Instant startTime = Instant.now();
        Instant endTime = startTime.plus(mTimeout);

        E value = null;
        int tries = 0;

        while (!Duration.between(Instant.now(), endTime).isNegative()) {
            tries++;
            try {
                value = mSupplier.get();
                if (mChecker.apply(value)) {
                    return value;
                }
                if (mTerminalValueChecker != null && mTerminalValueChecker.apply(value)) {
                    break;
                }
            } catch (Throwable e) {
                // Eat the exception until the timeout
                Log.e(LOG_TAG, "Exception during retries", e);
                if (mTerminalExceptionChecker != null && mTerminalExceptionChecker.apply(e)) {
                    break;
                }
            }

            try {
                Thread.sleep(SLEEP_MILLIS);
            } catch (InterruptedException e) {
                throw new PollValueFailedException("Interrupted while awaiting", e);
            }
        }

        if (!mErrorOnFail) {
            return value;
        }

        // We call again to allow exceptions to be thrown - if it passes here we can still return
        try {
            value = mSupplier.get();
        } catch (Throwable e) {
            long seconds = Duration.between(startTime, Instant.now()).toMillis() / 1000;
            throw new PollValueFailedException(mErrorSupplier.apply(mValueName, value)
                    + " - Exception when getting value (checked " + tries + " times in "
                    + seconds + " seconds)", e);
        }

        try {
            if (mChecker.apply(value)) {
                return value;
            }

            long seconds = Duration.between(startTime, Instant.now()).toMillis() / 1000;
            throw new PollValueFailedException(
                    mErrorSupplier.apply(mValueName, value) + " (checked " + tries + " times in "
                            + seconds + " seconds)");
        } catch (Throwable e) {
            long seconds = Duration.between(startTime, Instant.now()).toMillis() / 1000;
            throw new PollValueFailedException(
                    mErrorSupplier.apply(mValueName, value) + " (checked " + tries + " times in "
                            + seconds + " seconds)", e);

        }
    }

    /**
     * Set a method which, after a value fails the check, can tell if the failure is terminal.
     *
     * <p>This method will only be called after the value check fails. It will be passed the most
     * recent value and should return true if this value is terminal.
     *
     * <p>If true is returned, then no more retries will be attempted, otherwise retries will
     * continue until timeout.
     */
    public Poll<E> terminalValue(Function<E, Boolean> terminalChecker) {
        mTerminalValueChecker = terminalChecker;
        return this;
    }

    /**
     * Set a method which, after a value fails the check, can tell if the failure is terminal.
     *
     * <p>This method will only be called after the value check fails with an exception. It will be
     * passed the exception return true if this exception is terminal.
     *
     * <p>If true is returned, then no more retries will be attempted, otherwise retries will
     * continue until timeout.
     */
    public Poll<E> terminalException(Function<Throwable, Boolean> terminalChecker) {
        mTerminalExceptionChecker = terminalChecker;
        return this;
    }

    /**
     * Set a method which, after a value fails the check, can tell if the failure is terminal.
     *
     * <p>This method will only be called after the value check fails. It should return true if this
     * state is terminal.
     *
     * <p>If true is returned, then no more retries will be attempted, otherwise retries will
     * continue until timeout.
     */
    public Poll<E> terminal(Supplier<Boolean> terminalChecker) {
        terminalValue((e) -> terminalChecker.get());
        terminalException((e) -> terminalChecker.get());
        return this;
    }

    /** Interface for supplying values to {@link Poll}. */
    public interface ValueSupplier<E> {
        E get() throws Throwable;
    }

    /** Interface for checking values for {@link Poll}. */
    public interface ValueChecker<E> {
        boolean apply(E e) throws Throwable;
    }

    /** Interface for supplying errors for {@link Poll}. */
    public interface Function2<E, F, G> {
        G apply(E e, F f);
    }
}
