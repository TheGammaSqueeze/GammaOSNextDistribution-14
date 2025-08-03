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

import static com.google.common.truth.Truth.assertThat;

/** An additional set of assertion methods useful for writing tests. */
// TODO(hzalek): Move this into a dedicated testing library.
public final class MoreAsserts {

    /**
     * Facilitates the use of assertThrows from Java 8 by allowing method references to void methods
     * that declare checked exceptions and is not meant to be directly implemented.
     */
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Asserts that {@code runnable} throws an exception of type {@code expectedThrowable} when
     * executed. If it does, the exception object is returned. Otherwise, if it does not throw an
     * exception or does but of the unexpected type, an {@link AssertionError} is thrown.
     *
     * <p>This is mostly intended as a drop-in replacement of assertThrows that is only available in
     * JUnit 4.13 and later.
     *
     * @param runnable a function that is expected to throw an exception when executed
     * @return the exception thrown by {@code runnable}
     */
    public static <T extends Throwable> T assertThrows(
            Class<T> expectedClass, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(expectedClass);
            return expectedClass.cast(e);
        }
        throw new AssertionError(
                "Did not throw any when expected instance of: " + expectedClass.getName());
    }

    private MoreAsserts() {}
}
