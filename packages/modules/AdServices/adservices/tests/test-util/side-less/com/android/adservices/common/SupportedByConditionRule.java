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

package com.android.adservices.common;

import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Objects;
import java.util.concurrent.Callable;

// TODO(b/299707674) - Add tests for this rule

/**
 * Rule backed by a condition that is specified by the caller.
 *
 * <p>This rule doesn't have any dependency on Android code, so it can be used both on device-side
 * and host-side tests.
 */
public class SupportedByConditionRule implements TestRule {
    private final Callable<Boolean> mCondition;

    private final String mReason;

    /** Default constructor. */
    public SupportedByConditionRule(String message, Callable<Boolean> condition) {
        mCondition = Objects.requireNonNull(condition);
        mReason = Objects.requireNonNull(message);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Assume.assumeTrue(mReason, mCondition.call());
                base.evaluate();
            }
        };
    }
}
