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
package com.android.adservices.mockito;

import static com.android.adservices.shared.testing.common.TestHelper.getAnnotation;
import static com.android.adservices.shared.testing.common.TestHelper.getTestName;

import android.util.Log;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

// TODO(b/315339283): move to shared infra or module-utils (and in the case of the latter,
// refactor the ExtendedMockito.Builder.dontClearInlineMocks() to something like
// setClearInlineMocks(Mode))
// TODO(b/315339283): add unit tests
/**
 * Rule used to clear Mockito inline mocks after a test is run.
 *
 * <p>Typically used as a {@code ClassRule} in together with {@link AdServicesExtendedMockitoRule}.
 */
public final class ExtendedMockitoInlineCleanerRule implements TestRule {

    private static final String TAG = ExtendedMockitoInlineCleanerRule.class.getSimpleName();

    private final Mode mDefaultMode;

    public ExtendedMockitoInlineCleanerRule() {
        this(Mode.DONT_CLEAR_AT_ALL);
    }

    public ExtendedMockitoInlineCleanerRule(Mode defaultMode) {
        mDefaultMode = Objects.requireNonNull(defaultMode);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } finally {
                    clearInlineMocksAfterTest(description);
                }
            }
        };
    }

    private Mode getMode(Description description) {
        Mode mode = mDefaultMode;
        String testName = getTestName(description);
        ClearInlineMocksMode annotation = getAnnotation(description, ClearInlineMocksMode.class);
        if (annotation != null) {
            mode = annotation.value();
            Log.v(TAG, "getMode(" + testName + "): returning mode from annotation (" + mode + ")");
        }
        Log.v(TAG, "getMode(" + testName + "): returning default mode (" + mode + ")");
        return mode;
    }

    private void clearInlineMocksAfterTest(Description description) {
        Mode mode = getMode(description);
        boolean shouldClear = shouldClearInlineMocksAfterTest(description, mode);
        Log.d(
                TAG,
                "clearInlineMocksAfterTest("
                        + getTestName(description)
                        + "): mode="
                        + mode
                        + ", shouldClear="
                        + shouldClear);
        if (shouldClear) {
            clearInlineMocks();
        }
    }

    private void clearInlineMocks() {
        Log.i(TAG, "Calling Mockito.framework().clearInlineMocks()");
        Mockito.framework().clearInlineMocks();
    }

    /**
     * Gets whether the inline mocks should be cleared after the given test, based on whether the
     * test represents a method or class and the given mode.
     */
    public static boolean shouldClearInlineMocksAfterTest(Description description, Mode mode) {
        switch (mode) {
            case DONT_CLEAR_AT_ALL:
                return false;
            case CLEAR_AFTER_TEST_CLASS:
                return description.isSuite();
            case CLEAR_AFTER_TEST_METHOD:
                return !description.isSuite();
            default:
                throw new IllegalArgumentException("unsupported mode: " + mode);
        }
    }

    /** Defines when the inline mocks should be cleared. */
    public enum Mode {
        DONT_CLEAR_AT_ALL,
        CLEAR_AFTER_TEST_METHOD,
        CLEAR_AFTER_TEST_CLASS
    }

    /** Annotation used to defines when the inline mocks should be cleared. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface ClearInlineMocksMode {
        Mode value();
    }
}
