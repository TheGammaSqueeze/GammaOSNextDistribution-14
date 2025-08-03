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

package com.android.tools.metalava.testing

import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

private const val GRADLEW_UPDATE_MODEL_TEST_SUITE_BASELINE =
    "`scripts/refresh-baselines.py` to update the baseline"

/**
 * A JUnit [TestRule] that uses information from the [ModelTestSuiteBaseline] to ignore tests.
 *
 * @param baselineOwner the name of the owner of the baseline, used for error reporting.
 */
class BaselineTestRule(
    private val baselineOwner: String,
    resourcePath: String,
) : TestRule {

    /**
     * The [ModelTestSuiteBaseline] that indicates whether the tests are expected to fail or not.
     */
    private val baseline = BaselineFile.fromResource(resourcePath)

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val expectedFailure =
                    baseline.isExpectedFailure(description.className, description.methodName)
                try {
                    // Run the test even if it is expected to fail as a change that fixes one test
                    // may fix more. Instead, this will just discard any failure.
                    base.evaluate()
                } catch (e: AssumptionViolatedException) {
                    // Catch and rethrow but do not treat as a failure.
                    throw e
                } catch (e: Throwable) {
                    if (expectedFailure) {
                        // If this was expected to fail then throw an AssumptionViolatedException
                        // that way it is not treated as either a pass or fail. Indent the exception
                        // output and include it in the message instead of chaining the exception as
                        // that reads better than the default formatting of chained exceptions.
                        val actualErrorStackTrace = e.stackTraceToString().prependIndent("    ")
                        throw AssumptionViolatedException(
                            "Test skipped since it is listed in the baseline file for $baselineOwner.\n$actualErrorStackTrace"
                        )
                    } else {
                        // Inform the developer on how to ignore this failing test.
                        System.err.println(
                            "Failing tests can be ignored by running $GRADLEW_UPDATE_MODEL_TEST_SUITE_BASELINE"
                        )

                        // Rethrow the error
                        throw e
                    }
                }

                // Perform this check outside the try...catch block otherwise the exception gets
                // caught, making it look like an actual failing test.
                if (expectedFailure) {
                    // If a test that was expected to fail passes then updating the baseline
                    // will remove that test from the expected test failures. Fail the test so
                    // that the developer will be forced to clean it up.
                    throw IllegalStateException(
                        """
                            **************************************************************************************************
                                Test was listed in the baseline file as it was expected to fail but it passed, please run:
                                    $GRADLEW_UPDATE_MODEL_TEST_SUITE_BASELINE
                            **************************************************************************************************

                        """
                            .trimIndent()
                    )
                }
            }
        }
    }
}
