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

package com.android.bedstead.harrier;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import com.android.bedstead.harrier.annotations.FailureMode;

import org.junit.AssumptionViolatedException;

/**
 * Utilities for use by {@link AnnotationExecutor} subclasses.
 */
public final class AnnotationExecutorUtil {

    private AnnotationExecutorUtil() {

    }

    /**
     * {@link #failOrSkip(String, FailureMode)} if {@code value} is true.
     */
    public static void checkFailOrSkip(String message, boolean value, FailureMode failureMode) {
        if (failureMode.equals(FailureMode.FAIL)) {
            assertWithMessage(message).that(value).isTrue();
        } else if (failureMode.equals(FailureMode.SKIP)) {
            assumeTrue(message, value);
        } else {
            throw new IllegalStateException("Unknown failure mode: " + failureMode);
        }
    }

    /**
     * Either fail or skip the current test depending on the value of {@code failureMode}.
     */
    public static void failOrSkip(String message, FailureMode failureMode) {
        switch (failureMode) {
            case FAIL:
                throw new AssertionError(message);
            case SKIP:
                throw new AssumptionViolatedException(message);
            default:
                throw new IllegalStateException("Unknown failure mode: " + failureMode);
        }
    }
}
