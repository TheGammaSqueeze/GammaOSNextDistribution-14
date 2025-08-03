/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.csuite.testing;

import static com.android.csuite.testing.MoreAsserts.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MoreAssertsTest {

    @Test(expected = AssertionError.class)
    public void assertThrows_noExceptionThrown_fails() {
        assertThrows(
                Throwable.class,
                () -> {
                    /* This comment works around a presubmit hook warning that complains about a
                     * missing whitespace after the '{' which if added results in another warning
                     * about the file being badly formatted. */
                });
    }

    @Test(expected = AssertionError.class)
    public void assertThrows_differentExceptionTypeThrown_fails() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    throw new IllegalStateException();
                });
    }

    @Test(expected = AssertionError.class)
    public void assertThrows_superTypeOfExpectedExceptionTypeThrown_fails() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    throw new RuntimeException();
                });
    }

    @Test
    public void assertThrows_expectedExceptionTypeThrown_returnsSameObject() {
        IllegalArgumentException expected = new IllegalArgumentException();

        IllegalArgumentException actual =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            throw expected;
                        });

        assertThat(actual).isSameInstanceAs(expected);
    }

    @Test
    public void assertThrows_subTypeOfExpectedExceptionTypeThrown_returnsSameObject() {
        IllegalArgumentException expected = new IllegalArgumentException();

        RuntimeException actual =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            throw expected;
                        });

        assertThat(actual).isSameInstanceAs(expected);
    }
}
