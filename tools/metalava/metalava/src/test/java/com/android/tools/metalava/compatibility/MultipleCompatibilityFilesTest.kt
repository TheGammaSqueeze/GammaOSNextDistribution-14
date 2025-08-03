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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import org.junit.Test

class MultipleCompatibilityFilesTest : DriverTest() {

    private val previouslyReleasedPublicApi =
        """
            // Signature format: 2.0
            package test.pkg {
              public class Bar extends IllegalStateException {
              }
              public class Foo {
                method public void foo() throws test.pkg.Bar;
              }
            }
        """

    private val previouslyReleasedSystemApiDelta =
        """
            // Signature format: 2.0
            package test.pkg {
              public class Baz extends test.pkg.Bar {
              }
              public class Foo {
                method public void foo() throws test.pkg.Baz;
              }
            }
        """

    /**
     * The current and complete system api which will be tested for compatibility against some
     * combination of the above previously released APIs.
     */
    private val currentCompleteSystemApi =
        """
            package test.pkg {
              public class Bar extends IllegalStateException {
              }
              public class Baz extends test.pkg.Bar {
              }
              public class Foo {
                method public void foo() throws test.pkg.Baz;
              }
            }
        """

    @Test
    fun `Test public only`() {
        check(
            checkCompatibilityApiReleasedList = listOf(previouslyReleasedPublicApi),
            signatureSource = currentCompleteSystemApi,
            // This fails because the previous system API is not provided.
            expectedIssues =
                """
                    load-api.txt:8: error: Method test.pkg.Foo.foo added thrown exception test.pkg.Baz [ChangedThrows]
                """,
        )
    }

    @Test
    fun `Test system only`() {
        check(
            checkCompatibilityApiReleasedList = listOf(previouslyReleasedSystemApiDelta),
            signatureSource = currentCompleteSystemApi,
        )
    }

    @Test
    fun `Test multiple compatibility files (public first)`() {
        check(
            checkCompatibilityApiReleasedList =
                listOf(previouslyReleasedPublicApi, previouslyReleasedSystemApiDelta),
            signatureSource = currentCompleteSystemApi,
        )
    }

    @Test
    fun `Test multiple compatibility files (system first)`() {
        check(
            checkCompatibilityApiReleasedList =
                listOf(previouslyReleasedSystemApiDelta, previouslyReleasedPublicApi),
            signatureSource = currentCompleteSystemApi,
            // This fails because the previous system API is provided first and the
            // CompatibilityCheck assumes that the second one should override the first, so it is
            // using the public definition of the `foo()` method and the public `throws` list which
            // is why it is getting the same errors as when public only is provided.
            expectedIssues =
                """
                    load-api.txt:8: error: Method test.pkg.Foo.foo added thrown exception test.pkg.Baz [ChangedThrows]
                """,
        )
    }
}
