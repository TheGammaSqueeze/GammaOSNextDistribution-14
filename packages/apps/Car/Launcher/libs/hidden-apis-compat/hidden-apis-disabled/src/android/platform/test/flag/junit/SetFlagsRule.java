/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.platform.test.flag.junit;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Fake implementation of SetFlagRule for gradle builds
 */
public class SetFlagsRule implements TestRule {

    /**
     * Do nothing function. To enable the feature use cmd before running the tests.
     * <p>
     * Called for gradle builds only
     */
    public void enableFlags(String featureFlag) {
        System.out.println(
                "Gradle builds: Ensure that flagDockFeature is enabled before running the tests, "
                        + "otherwise the tests might fail prematurely");
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return base;
    }

}
