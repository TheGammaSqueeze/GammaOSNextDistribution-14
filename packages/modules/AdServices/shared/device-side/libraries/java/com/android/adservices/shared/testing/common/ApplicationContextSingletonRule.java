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

import static com.android.adservices.shared.util.LogUtil.DEBUG;
import static com.android.adservices.shared.util.LogUtil.VERBOSE;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.util.LogUtil;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Objects;

/**
 * Rule used to properly set / reset the {@link ApplicationContextSingleton} for tests - it will set
 * it before the test, and reset to the previous value afterwards.
 *
 * <p>It also provide convenience methods to {@link #get() get} / {@link #set(Context) set} the
 * context during tests, so the test doesn't need to explicitly deal with {@link
 * ApplicationContextSingleton}.
 */
public final class ApplicationContextSingletonRule implements TestRule {
    private final Context mContext;
    private final boolean mRestoreAfter;

    /**
     * Default constructor, sets the singleton as the target context of the instrumented app and
     * whether the previous context should be restored after the test.
     */
    public ApplicationContextSingletonRule(boolean restoreAfter) {
        this(ApplicationProvider.getApplicationContext(), restoreAfter);
    }

    /**
     * Sets the singleton using the given {@code context} and whether the previous context should be
     * restored after the test.
     */
    public ApplicationContextSingletonRule(Context context, boolean restoreAfter) {
        mContext = Objects.requireNonNull(context, "context cannot be null");
        mRestoreAfter = restoreAfter;
        if (VERBOSE) {
            LogUtil.v("Constructing with %s and restoreAfter=%s", context, restoreAfter);
        }
    }

    /** Convenience method to get the current {@link ApplicationContextSingleton}. */
    public Context get() {
        Context context = ApplicationContextSingleton.getForTests();
        if (VERBOSE) {
            LogUtil.v("get(): %s", context);
        }
        return context;
    }

    /** Convenience method to set the {@link ApplicationContextSingleton}. */
    public void set(Context context) {
        if (VERBOSE) {
            LogUtil.v("set(%s)", context);
        }
        ApplicationContextSingleton.setForTests(context);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Context previousContext = ApplicationContextSingleton.getForTests();
                String testName =
                        description.getTestClass().getSimpleName()
                                + "#"
                                + description.getMethodName();
                if (DEBUG) {
                    LogUtil.d(
                            "Changing ApplicationContextSingleton from %s to %s on %s",
                            previousContext, mContext, testName);
                }
                ApplicationContextSingleton.setForTests(mContext);
                try {
                    base.evaluate();
                } finally {
                    if (mRestoreAfter) {
                        if (DEBUG) {
                            LogUtil.d(
                                    "Restoring ApplicationContextSingleton to %s on %s",
                                    previousContext, testName);
                        }
                        ApplicationContextSingleton.setForTests(previousContext);
                    } else {
                        if (DEBUG) {
                            LogUtil.d(
                                    "NOT restoring ApplicationContextSingleton to previous context"
                                            + " (%s) on %s",
                                    previousContext, testName);
                        }
                    }
                }
            }
        };
    }
}
