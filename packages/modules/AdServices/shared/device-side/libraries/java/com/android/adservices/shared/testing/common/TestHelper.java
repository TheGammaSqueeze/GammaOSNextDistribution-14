/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adservices.shared.testing.common;

import androidx.annotation.Nullable;

import com.android.adservices.shared.util.LogUtil;

import org.junit.runner.Description;

import java.lang.annotation.Annotation;
import java.util.Objects;

/** Provides helpers for generic test-related tasks. */
public final class TestHelper {
    private static final boolean VERBOSE = false; // Should NEVER be merged as true

    // TODO(b/315339283): use in other places
    /** Gets the given annotation from the test, its class, or its ancestors. */
    @Nullable
    public static <T extends Annotation> T getAnnotation(
            Description test, Class<T> annotationClass) {
        T annotation =
                Objects.requireNonNull(test, "test cannot be null")
                        .getAnnotation(
                                Objects.requireNonNull(
                                        annotationClass, "annotationClass cannot be null"));
        if (annotation != null) {
            if (VERBOSE) {
                LogUtil.v(
                        "getAnnotation(%s): returning annotation (%s) from test itself (%s)",
                        test, annotation, getTestName(test));
            }
            return annotation;
        }
        Class<?> testClass = test.getTestClass();
        while (testClass != null) {
            annotation = testClass.getAnnotation(annotationClass);
            if (annotation != null) {
                if (VERBOSE) {
                    LogUtil.v(
                            "getAnnotation(%s): returning annotation (%s) from test class (%s)",
                            test, annotation, testClass.getSimpleName());
                }
                return annotation;
            }
            if (VERBOSE) {
                LogUtil.v(
                        "getAnnotation(%s): not found on class %s, will try superclass (%s)",
                        test, testClass, testClass.getSuperclass());
            }
            testClass = testClass.getSuperclass();
        }
        if (VERBOSE) {
            LogUtil.v("getAnnotation(" + test + "): returning null");
        }
        return null;
    }

    // TODO(b/315339283): use in other places
    /** Gests a user-friendly name for the test. */
    public static String getTestName(Description test) {
        StringBuilder testName = new StringBuilder(test.getTestClass().getSimpleName());
        String methodName = test.getMethodName();
        if (methodName != null) {
            testName.append('#').append(methodName).append("()");
        }
        return testName.toString();
    }

    private TestHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
