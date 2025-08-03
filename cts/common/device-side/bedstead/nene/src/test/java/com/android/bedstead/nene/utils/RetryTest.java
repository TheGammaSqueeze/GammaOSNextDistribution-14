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

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(BedsteadJUnit4.class)
public class RetryTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final String VALUE_1 = "Value1";
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(1);
    private static final RuntimeException EXCEPTION = new RuntimeException("Test");

    @Test
    public void run_noExceptions_returnsValue() throws Throwable {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);

        assertThat(Retry.logic(valueTester::get).run()).isEqualTo(VALUE_1);
    }

    @Test
    public void runVoid_noExceptions_runs() throws Throwable {
        ValueTester<String> valueTester = new ValueTester<>(VALUE_1);

        Retry.logic(valueTester::throwOrNot).run();
    }

    @Test
    public void run_exceptions_throwsException() throws Throwable {
        ValueTester<String> valueTester = new ValueTester<>(new RuntimeException("test"));

        assertThrows(RuntimeException.class,
                () -> Retry.logic(valueTester::get).timeout(SHORT_TIMEOUT).run());
    }

    @Test
    public void runVoid_exceptions_throwsException() throws Throwable {
        ValueTester<String> valueTester = new ValueTester<>(new RuntimeException("test"));

        assertThrows(RuntimeException.class,
                () -> Retry.logic(valueTester::throwOrNot).timeout(SHORT_TIMEOUT).run());
    }

    @Test
    public void run_temporaryExceptions_returnsValue() throws Throwable {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION, VALUE_1, 5);

        assertThat(Retry.logic(valueTester::get).run()).isEqualTo(VALUE_1);
    }

    @Test
    public void runVoid_temporaryExceptions_runs() throws Throwable {
        ValueTester<String> valueTester = new ValueTester<>(EXCEPTION, VALUE_1, 5);

        Retry.logic(valueTester::throwOrNot).run();
    }

    @Test
    public void run_terminalException_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(
                new RuntimeException("test"), VALUE_1, 5);

        assertThrows(RuntimeException.class,
                () -> Retry.logic(valueTester::get).terminalException(e -> true).run());
    }

    @Test
    public void runVoid_terminalException_throwsException() {
        ValueTester<String> valueTester = new ValueTester<>(
                new RuntimeException("test"), VALUE_1, 5);

        assertThrows(RuntimeException.class,
                () -> Retry.logic(valueTester::throwOrNot).terminalException(e -> true).run());
    }

    @Test
    public void run_nonTerminalException_returnsValue() throws Throwable {
        ValueTester<String> valueTester = new ValueTester<>(
                new RuntimeException("test"), VALUE_1, 5);

        assertThat(Retry.logic(valueTester::get).terminalException(e -> false).run())
                .isEqualTo(VALUE_1);
    }

    @Test
    public void runVoid_nonTerminalException_runs() throws Throwable {
        ValueTester<String> valueTester = new ValueTester<>(
                new RuntimeException("test"), VALUE_1, 5);

        Retry.logic(valueTester::throwOrNot).terminalException(e -> false).run();
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

        void throwOrNot() throws Throwable {
            if (mNumberOfIterations-- >= 0) {
                if (mOriginalThrowable != null) {
                    throw mOriginalThrowable;
                }
            }
        }
    }
}
