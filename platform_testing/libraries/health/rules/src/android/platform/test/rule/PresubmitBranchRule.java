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

package android.platform.test.rule;

import androidx.test.InstrumentationRegistry;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Arrays;

/** Rule that skips presubmits on main branch. */
public class PresubmitBranchRule implements TestRule {
    private static final Branch BRANCH = Branch.MAIN;

    public static boolean runningInPresubmit() {
        // We run in presubmit when there is a parameter to exclude postsubmits.
        final String nonAnnotationArgument =
                androidx.test.platform.app.InstrumentationRegistry.getArguments().getString(
                        "notAnnotation", "");
        return Arrays.stream(nonAnnotationArgument.split(","))
                .anyMatch("android.platform.test.annotations.Postsubmit"::equals);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        // If the test suite isn't running with
        // "exclude-annotation": "android.platform.test.annotations.Postsubmit", then this is not
        // a presubmit test, and the rule is not applicable.
        if (!runningInPresubmit()) {
            return base;
        }

        if (BRANCH == Branch.MAIN) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    throw new AssumptionViolatedException("Skipping the test on MAIN");
                }
            };
        }

        return base;
    }

    public enum Branch {
        TM_DEV,
        MAIN
    }
}
