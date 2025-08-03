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

import android.os.Build;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Skips the test on AOSP targets. */
public class SkipOnAospRule implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
        // Run the test if we are not on an AOSP target.
        if (!Build.PRODUCT.startsWith("aosp_")) return base;

        // The test will be skipped upon start.
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                throw new AssumptionViolatedException("Skipping the test on AOSP");
            }
        };
    }
}
