/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import org.junit.Test

class CompatibilityCheckBaselineTest : DriverTest() {
    @Test
    fun `Test released-API check, with error message`() {
        // Global baseline works on released api check.
        check(
            expectedIssues =
                """
                released-api.txt:2: error: Removed package test.pkg [RemovedPackage]
                """,
            errorMessageCheckCompatibilityReleased = "*** release-api check failed ***",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MyTest1 {
                  }
                }
                """,
            signatureSource = """
                """,
            expectedFail =
                """
                Aborting: Found compatibility problems checking the public API (TESTROOT/project/load-api.txt) against the API in TESTROOT/project/released-api.txt
                *** release-api check failed ***
                """
        )
    }

    @Test
    fun `Test released-API check, with global baseline`() {
        // Global baseline works on released api check.
        check(
            expectedIssues = """
                """,
            baselineTestInfo =
                BaselineTestInfo(
                    inputContents =
                        """
                            // Baseline format: 1.0
                            ChangedScope: test.pkg.MyTest1:
                                Class test.pkg.MyTest1 changed visibility from public to private
                        """,
                ),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MyTest1 {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  private class MyTest1 { // visibility changed
                  }
                }
                """
        )
    }

    @Test
    fun `Test released-API check, with compatibility-released baseline`() {
        // Use released-API check baseline, which should work in released-API check.
        check(
            expectedIssues = """
                """,
            baselineCheckCompatibilityReleasedTestInfo =
                BaselineTestInfo(
                    inputContents =
                        """
                            // Baseline format: 1.0
                            ChangedScope: test.pkg.MyTest1:
                                Class test.pkg.MyTest1 changed visibility from public to private
                        """,
                ),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MyTest1 {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  private class MyTest1 { // visibility changed
                  }
                }
                """
        )
    }

    @Test
    fun `Test released-API check, with compatibility-released baseline, and update baseline`() {
        // Use released-API check baseline, which should work in released-API check.
        check(
            expectedIssues = "",
            baselineCheckCompatibilityReleasedTestInfo =
                BaselineTestInfo(
                    inputContents = "",
                    expectedOutputContents =
                        """
                            // Baseline format: 1.0
                            ChangedScope: test.pkg.MyTest1:
                                Class test.pkg.MyTest1 changed visibility from public to private
                        """,
                ),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MyTest1 {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  private class MyTest1 { // visibility changed
                  }
                }
                """
        )
    }
}
