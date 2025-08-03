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

package android.platform.test.util;

import android.platform.uiautomator_helpers.WaitUtils;

import androidx.test.uiautomator.StaleObjectException;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/** Helper class for writing health tests. */
public class HealthTestingUtils {

    private static final String TAG = "HealthTestingUtils";
    private static final int WAIT_TIME_MS = 10000;
    private static final int DEFAULT_SETTLE_TIME_MS = 3000;

    private HealthTestingUtils() {}

    /** Supplier of a boolean that can throw an exception. */
    public interface Condition {
        boolean isTrue() throws Throwable;
    }

    /**
     * Waits for a diagnostics to become null within 10 sec.
     *
     * @param diagnostics Supplier of the error message. It should return null in case of success.
     *     The method repeatedly calls this provider while it's returning non-null. If it keeps
     *     returning non-null after 10 sec, the method throws an exception using the last string
     *     returned by the provider. Otherwise, it succeeds.
     */
    public static void waitForNullDiag(Supplier<String> diagnostics) {
        final String[] lastDiag = new String[1];
        waitForCondition(() -> lastDiag[0], () -> (lastDiag[0] = diagnostics.get()) == null);
    }

    /**
     * Waits for a value producer to produce a result that's isPresent() and fails if it doesn't
     * happen within 10 sec.
     *
     * @param message Supplier of the error message.
     * @param resultProducer Result producer.
     * @return The present value returned by the producer.
     */
    public static <T> T waitForValuePresent(
            Supplier<String> message, Supplier<Optional<T>> resultProducer) {
        class ResultHolder {
            public T value;
        }
        final ResultHolder result = new ResultHolder();

        waitForCondition(
                message,
                () -> {
                    final Optional<T> optionalResult = resultProducer.get();
                    if (optionalResult.isPresent()) {
                        result.value = optionalResult.get();
                        return true;
                    } else {
                        return false;
                    }
                });

        return result.value;
    }

    /**
     * Waits for a result to be produced without throwing a {@link StaleObjectException}.
     *
     * <p>This is useful in case of dealing with containers that change or go out of screen during
     * the test, to reduce flakiness.
     *
     * @param errorMessage message thrown when resultProduces fails after the maximum number of
     *     retries.
     * @param resultProducer produces the output. Might throw {@link StaleObjectException}.
     */
    public static <T> T waitForValueCatchingStaleObjectExceptions(
            Supplier<String> errorMessage, Supplier<T> resultProducer) {
        return waitForValuePresent(
                errorMessage,
                () -> {
                    try {
                        return Optional.ofNullable(resultProducer.get());
                    } catch (StaleObjectException e) {
                        return Optional.empty();
                    }
                });
    }

    /**
     * Waits for a condition and fails if it doesn't become true within 10 sec.
     *
     * @param message Supplier of the error message.
     * @param condition Condition.
     */
    public static void waitForCondition(Supplier<String> message, Condition condition) {
        waitForCondition(message, condition, WAIT_TIME_MS);
    }

    /**
     * Waits for a condition and fails if it doesn't become true within specified time period.
     *
     * @param message Supplier of the error message.
     * @param condition Condition.
     * @param timeoutMs Timeout.
     */
    public static void waitForCondition(
            Supplier<String> message, Condition condition, long timeoutMs) {

        WaitUtils.ensureThat(
                "waitForCondition",
                /* timeout= */ Duration.ofMillis(timeoutMs),
                /* errorProvider= */ message::get,
                /* condition= */ () -> {
                    try {
                        return condition.isTrue();
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                });
    }

    /** @see HealthTestingUtils#waitForValueToSettle */
    public static <T> T waitForValueToSettle(Supplier<String> errorMessage, Supplier<T> supplier) {
        return waitForValueToSettle(
                errorMessage,
                supplier,
                /* minimumSettleTime= */ DEFAULT_SETTLE_TIME_MS,
                /* timeoutMs= */ WAIT_TIME_MS);
    }

    /**
     * Waits for the supplier to return the same value for a specified time period. If the value
     * changes, the timer gets restarted. Fails when reaching the timeout. The minimum running time
     * of this method is the settle time.
     *
     * @return the settled value. Fails if it doesn't settle.
     */
    public static <T> T waitForValueToSettle(
            Supplier<String> errorMessage,
            Supplier<T> supplier,
            long minimumSettleTime,
            long timeoutMs) {
        return WaitUtils.waitForNullableValueToSettle(
                "waitForValueToSettle",
                /* minimumSettleTime= */ Duration.ofMillis(minimumSettleTime),
                /* timeout= */ Duration.ofMillis(timeoutMs),
                /* errorProvider= */ errorMessage::get,
                /* supplier */ supplier::get);
    }
}
