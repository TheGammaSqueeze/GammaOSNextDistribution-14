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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.exceptions.NeneException;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Objects;

@RunWith(BedsteadJUnit4.class)
public class PollTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final String VALUE_1 = "Value1";
    private static final String VALUE_2 = "Value2";
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(1);
    private static final RuntimeException EXCEPTION = new RuntimeException("Test");

    @Test
    public void await_toBeEqualTo_alreadyEqual_returns() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toBeEqualTo(VALUE_1)
                .await()).isEqualTo(VALUE_1);
    }

    @Test
    public void await_toBeEqualTo_becomesEqual_returns() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2, VALUE_1, 5);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toBeEqualTo(VALUE_1)
                .await()).isEqualTo(VALUE_1);
    }

    @Test
    public void await_toBeEqualTo_doesNotBecomeEqual_returnIncorrectValue() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toBeEqualTo(VALUE_1)
                .timeout(SHORT_TIMEOUT)
                .await()).isEqualTo(VALUE_2);
    }

    @Test
    public void await_toBeEqualTo_errorOnFailure_doesNotBecomeEqual_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2);

        NeneException expected = expectThrows(NeneException.class, () ->
                Poll.forValue("tester value", valueTester::get)
                        .toBeEqualTo(VALUE_1)
                        .timeout(SHORT_TIMEOUT)
                        .errorOnFail()
                        .await());
        assertThat(expected).hasMessageThat()
                .startsWith("Expected tester value to be equal to Value1. Was Value2");
    }

    @Test
    public void await_toBeEqualTo_errorOnFailure_doesNotBecomeEqual_customError_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2);

        NeneException expected = expectThrows(NeneException.class, () ->
                Poll.forValue("tester value", valueTester::get)
                        .toBeEqualTo(VALUE_1)
                        .timeout(SHORT_TIMEOUT)
                        .errorOnFail("Custom error")
                        .await());
        assertThat(expected).hasMessageThat()
                .startsWith("Custom error. tester value was Value2");
    }

    @Test
    public void await_toNotBeEqualTo_alreadyNotEqual_returns() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toNotBeEqualTo(VALUE_2)
                .await()).isEqualTo(VALUE_1);
    }

    @Test
    public void await_toNotBeEqualTo_becomesNotEqual_returns() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2, VALUE_1, 5);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toNotBeEqualTo(VALUE_2)
                .await()).isEqualTo(VALUE_1);
    }

    @Test
    public void await_toNotBeEqualTo_doesNotBecomeNotEqual_returnIncorrectValue() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toNotBeEqualTo(VALUE_2)
                .timeout(SHORT_TIMEOUT)
                .await()).isEqualTo(VALUE_2);
    }

    @Test
    public void await_toNotBeEqualTo_errorOnFailure_doesNotBecomeNotEqual_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2);

        NeneException expected = expectThrows(NeneException.class, () ->
                Poll.forValue("tester value", valueTester::get)
                        .toNotBeEqualTo(VALUE_2)
                        .timeout(SHORT_TIMEOUT)
                        .errorOnFail()
                        .await());
        assertThat(expected).hasMessageThat()
                .startsWith("Expected tester value to not be equal to Value2. Was Value2");
    }

    @Test
    public void await_toNotBeEqualTo_errorOnFailure_doesNotBecomeNotEqual_customError_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2);

        NeneException expected = expectThrows(NeneException.class, () ->
                Poll.forValue("tester value", valueTester::get)
                        .toNotBeEqualTo(VALUE_2)
                        .timeout(SHORT_TIMEOUT)
                        .errorOnFail("Custom error")
                        .await());
        assertThat(expected).hasMessageThat()
                .startsWith("Custom error. tester value was Value2");
    }

    @Test
    public void await_toBeNull_alreadyNull_returns() {
        ValueTester<String> valueTester = new ValueTester<>(null);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toBeNull()
                .await()).isNull();
    }

    @Test
    public void await_toBeNull_becomesNull_returns() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2, null, 5);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toBeNull()
                .await()).isNull();
    }

    @Test
    public void await_toBeNull_doesNotBecomeNull_returnIncorrectValue() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toBeNull()
                .timeout(SHORT_TIMEOUT)
                .await()).isEqualTo(VALUE_2);
    }

    @Test
    public void await_toBeNull_errorOnFailure_doesNotBecomeNull_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2);

        NeneException expected = expectThrows(NeneException.class, () ->
                Poll.forValue("tester value", valueTester::get)
                        .toBeNull()
                        .timeout(SHORT_TIMEOUT)
                        .errorOnFail()
                        .await());
        assertThat(expected).hasMessageThat()
                .startsWith("Expected tester value to be null. Was Value2");
    }

    @Test
    public void await_toBeNull_errorOnFailure_doesNotBecomeNull_customError_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_2);

        NeneException expected = expectThrows(NeneException.class, () ->
                Poll.forValue("tester value", valueTester::get)
                        .toBeNull()
                        .timeout(SHORT_TIMEOUT)
                        .errorOnFail("Custom error")
                        .await());
        assertThat(expected).hasMessageThat()
                .startsWith("Custom error. tester value was Value2");
    }

    @Test
    public void await_toNotBeNull_alreadyNotNull_returns() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toNotBeNull()
                .await()).isNotNull();
    }

    @Test
    public void await_toNotBeNull_becomesNotNull_returns() {
        ValueTester<String> valueTester = new ValueTester<>(null, VALUE_1, 5);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toNotBeNull()
                .await()).isNotNull();
    }

    @Test
    public void await_toNotBeNull_doesNotBecomeNotNull_returnIncorrectValue() {
        ValueTester<String> valueTester = new ValueTester<>(null);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toNotBeNull()
                .timeout(SHORT_TIMEOUT)
                .await()).isNull();
    }

    @Test
    public void await_toNotBeNull_errorOnFailure_doesNotBecomeNotNull_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(null);

        NeneException expected = expectThrows(NeneException.class, () ->
                Poll.forValue("tester value", valueTester::get)
                        .toNotBeNull()
                        .timeout(SHORT_TIMEOUT)
                        .errorOnFail()
                        .await());
        assertThat(expected).hasMessageThat()
                .startsWith("Expected tester value to not be null. Was null");
    }

    @Test
    public void await_toNotBeNull_errorOnFailure_doesNotBecomeNotNull_customError_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(null);

        NeneException expected = expectThrows(NeneException.class, () ->
                Poll.forValue("tester value", valueTester::get)
                        .toNotBeNull()
                        .timeout(SHORT_TIMEOUT)
                        .errorOnFail("Custom error")
                        .await());
        assertThat(expected).hasMessageThat()
                .startsWith("Custom error. tester value was null");
    }

    @Test
    public void await_toMeet_alreadyMeets_returns() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toMeet(Objects::nonNull)
                .await()).isNotNull();
    }

    @Test
    public void await_toMeet_becomesMeets_returns() {
        ValueTester<String> valueTester = new ValueTester<>(null, VALUE_1, 5);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toMeet(Objects::nonNull)
                .await()).isNotNull();
    }

    @Test
    public void await_toMeet_doesNotMeet_returnIncorrectValue() {
        ValueTester<String> valueTester = new ValueTester<>(null);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toMeet(Objects::nonNull)
                .timeout(SHORT_TIMEOUT)
                .await()).isNull();
    }

    @Test
    public void await_toMeet_errorOnFailure_doesNotMeet_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(null);

        NeneException expected = expectThrows(NeneException.class, () ->
                Poll.forValue("tester value", valueTester::get)
                        .toMeet(Objects::nonNull)
                        .timeout(SHORT_TIMEOUT)
                        .errorOnFail()
                        .await());
        assertThat(expected).hasMessageThat()
                .startsWith("Expected tester value to meet checker function. Was null");
    }

    @Test
    public void await_toMeet_errorOnFailure_doesNotMeet_customError_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(null);

        NeneException expected = expectThrows(NeneException.class, () ->
                Poll.forValue("tester value", valueTester::get)
                        .toMeet(Objects::nonNull)
                        .timeout(SHORT_TIMEOUT)
                        .errorOnFail("Custom error")
                        .await());
        assertThat(expected).hasMessageThat()
                .startsWith("Custom error. tester value was null");
    }

    @Test
    public void await_supplierThrowsExceptionSometimes_stillWorks() {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION, VALUE_1, 5);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toMeet(Objects::nonNull)
                .await()).isEqualTo(VALUE_1);
    }

    @Test
    public void await_supplierThrowsExceptionAlways_returnsNull() {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toMeet(Objects::nonNull)
                .timeout(SHORT_TIMEOUT)
                .await()).isNull();
    }

    @Test
    public void await_supplierThrowsExceptionAlways_errorOnFail_throwsExceptionWithSameCause() {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION);

        NeneException exception =
                expectThrows(NeneException.class, () ->
                        Poll.forValue("tester value", valueTester::get)
                                .toMeet(Objects::nonNull)
                                .timeout(SHORT_TIMEOUT)
                                .errorOnFail()
                                .await());

        assertThat(exception).hasCauseThat().isSameInstanceAs(EXCEPTION);
    }

    @Test
    public void await_checkerThrowsExceptionSometimes_stillWorks() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);
        ValueTester<Boolean> checker = new ValueTester<>(EXCEPTION, true, 5);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toMeet(c -> checker.get())
                .await()).isEqualTo(VALUE_1);
    }

    @Test
    public void await_checkerThrowsExceptionAlways_returnsNonMatchingValue() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);
        ValueTester<Boolean> checker = new ValueTester<>(EXCEPTION);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .toMeet(c -> checker.get())
                .timeout(SHORT_TIMEOUT)
                .await()).isEqualTo(VALUE_1);
    }

    @Test
    public void await_checkerThrowsExceptionAlways_errorOnFail_throwsExceptionWithSameCause() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);
        ValueTester<Boolean> checker = new ValueTester<>(EXCEPTION);

        NeneException exception =
                expectThrows(NeneException.class, () ->
                        Poll.forValue("tester value", valueTester::get)
                                .toMeet(c -> checker.get())
                                .timeout(SHORT_TIMEOUT)
                                .errorOnFail()
                                .await());

        assertThat(exception).hasCauseThat().isSameInstanceAs(EXCEPTION);
    }

    @Test
    public void terminalValue_hasTerminalValue_exitsImmediately() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);

        Poll.forValue("tester value", valueTester::get)
                .toBeEqualTo(VALUE_2)
                .terminalValue(c -> c.equals(VALUE_1))
                .await();

        assertThat(valueTester.mNumberOfTimesGetCalled).isEqualTo(1);
    }

    @Test
    public void terminalValue_hasNonTerminalValue_doesNotExitImmediately() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);

        Poll.forValue("tester value", valueTester::get)
                .toBeEqualTo(VALUE_2)
                .timeout(SHORT_TIMEOUT)
                .terminalValue(c -> c.equals(VALUE_2))
                .await();

        assertThat(valueTester.mNumberOfTimesGetCalled).isGreaterThan(1);
    }

    @Test
    public void terminalValue_hasExceptionWithNoValue_doesNotExitImmediately() {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION);

        Poll.forValue("tester value", valueTester::get)
                .toBeEqualTo(VALUE_2)
                .timeout(SHORT_TIMEOUT)
                .terminalValue(c -> c.equals(VALUE_2))
                .await();

        assertThat(valueTester.mNumberOfTimesGetCalled).isGreaterThan(1);
    }

    @Test
    public void terminalException_hasTerminalException_exitsImmediately() {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION);

        Poll.forValue("tester value", valueTester::get)
                .toBeEqualTo(VALUE_2)
                .terminalException(c -> true)
                .await();

        assertThat(valueTester.mNumberOfTimesGetCalled).isEqualTo(1);
    }

    @Test
    public void terminalException_hasNonTerminalException_doesNotExitImmediately() {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION);

        Poll.forValue("tester value", valueTester::get)
                .toBeEqualTo(VALUE_2)
                .terminalException(c -> false)
                .await();

        assertThat(valueTester.mNumberOfTimesGetCalled).isGreaterThan(1);
    }

    @Test
    public void terminal_hasValue_exitsImmediately() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);

        Poll.forValue("tester value", valueTester::get)
                .toBeEqualTo(VALUE_2)
                .terminal(() -> true)
                .await();

        assertThat(valueTester.mNumberOfTimesGetCalled).isEqualTo(1);
    }

    @Test
    public void terminal_hasException_exitsImmediately() {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION);

        Poll.forValue("tester value", valueTester::get)
                .toBeEqualTo(VALUE_2)
                .terminal(() -> true)
                .await();

        assertThat(valueTester.mNumberOfTimesGetCalled).isEqualTo(1);
    }

    @Test
    public void await_noChecker_returnsFirstValue() {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .await()).isEqualTo(VALUE_1);
    }

    @Test
    public void await_noChecker_hasExceptions_returnsFirstValue() {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION, VALUE_1, 5);

        assertThat(Poll.forValue("tester value", valueTester::get)
                .await()).isEqualTo(VALUE_1);
    }

    @Test
    public void await_noChecker_onlyHasExceptions_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION);

        assertThrows(NeneException.class,
                () -> Poll.forValue("tester value", valueTester::get)
                        .errorOnFail()
                        .await());
    }

    private static final class ValueTester<E> {
        private final Throwable mOriginalThrowable;
        private final E mOriginalValue;
        private final E mChangedValue;
        private int mNumberOfIterations;
        int mNumberOfTimesGetCalled = 0;

        ValueTester(E originalValue, E changedValue, int numberOfIterations) {
            mOriginalValue = originalValue;
            mChangedValue = changedValue;
            mNumberOfIterations = numberOfIterations;
            mOriginalThrowable = null;
        }

        ValueTester(E onlyValue) {
            this(onlyValue, onlyValue, 1);
        }

        ValueTester(Throwable originalThrowable, E changedValue, int numberOfIterations) {
            mOriginalValue = null;
            mOriginalThrowable = originalThrowable;
            mChangedValue = changedValue;
            mNumberOfIterations = numberOfIterations;
        }

        ValueTester(Throwable throwable) {
            this(throwable, null, Integer.MAX_VALUE);
        }

        E get() throws Throwable {
            mNumberOfTimesGetCalled++;
            if (mNumberOfIterations-- >= 0) {
                if (mOriginalThrowable != null) {
                    throw mOriginalThrowable;
                }
                return mOriginalValue;
            }
            return mChangedValue;
        }
    }
}
