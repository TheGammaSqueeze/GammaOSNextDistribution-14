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

package android.healthconnect.cts.utils;

import static org.junit.Assume.assumeTrue;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.function.BooleanSupplier;

/**
 * Rule which checks an assumption, and skips the test if it evaluates false. Primarily useful for
 * short-circuiting rule evaluations.
 */
public class AssumptionCheckerRule implements TestRule {

    private final String mAssumptionDescription;
    private final BooleanSupplier mShouldRunTestSupplier;

    /**
     * Initialize the rule with a supplier to assume on and a description.
     *
     * @param shouldRunTestSupplier - Evaluated prior to each test statement, and if false, test is
     *     skipped.
     * @param description - Message for failed assumption if test is skipped.
     */
    public AssumptionCheckerRule(BooleanSupplier shouldRunTestSupplier, String description) {
        mShouldRunTestSupplier = shouldRunTestSupplier;
        mAssumptionDescription = description;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assumeTrue(mAssumptionDescription, mShouldRunTestSupplier.getAsBoolean());
                base.evaluate();
            }
        };
    }

    @Override
    public String toString() {
        return "AssumptionCheckerRule["
                + mAssumptionDescription
                + ", "
                + mShouldRunTestSupplier
                + "]";
    }
}
