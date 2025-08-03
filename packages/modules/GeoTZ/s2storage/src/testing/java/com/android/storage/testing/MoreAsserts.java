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

package com.android.storage.testing;

public final class MoreAsserts {

    private MoreAsserts() {}

    @FunctionalInterface
    public interface ThrowingRunnable {
        /** The logic to run. */
        void run() throws Throwable;
    }

    // Remove once JUnit is updated to 4.13
    /** Asserts the supplied {@link ThrowingRunnable} throws the specific throwable. */
    public static <T extends Throwable> T assertThrows(
            Class<T> expectedThrowable,
            ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable actualThrown) {
            if (expectedThrowable.isInstance(actualThrown)) {
                @SuppressWarnings("unchecked") T retVal = (T) actualThrown;
                return retVal;
            } else {
                String expected = formatClass(expectedThrowable);
                Class<? extends Throwable> actualThrowable = actualThrown.getClass();
                String actual = formatClass(actualThrowable);

                String mismatchExceptionMessage = "Unexpected exception type thrown: " + actual
                        + ", expected:" + expected;
                throw new AssertionError(mismatchExceptionMessage, actualThrown);
            }
        }
        String notThrownMessage = "Expected " + formatClass(expectedThrowable)
                + " to be thrown, but nothing was thrown";
        throw new AssertionError(notThrownMessage);
    }

    private static String formatClass(Class<?> value) {
        String className = value.getCanonicalName();
        return className == null ? value.getName() : className;
    }
}
