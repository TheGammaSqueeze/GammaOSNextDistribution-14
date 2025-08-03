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

package com.android.tools.metalava.lint

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.testing.java
import java.util.Locale
import org.junit.Test
import org.junit.runners.Parameterized

class DeprecatedLintTest(private val deprecatedState: DeprecatedState) : DriverTest() {

    enum class DeprecatedState(val context: Context, val expectedFail: String) {
        NOT_DEPRECATED(
            context = Context("", ""),
            expectedFail = DefaultLintErrorMessage,
        ),
        DEPRECATED_MEMBER(
            context = Context("", "/** @deprecated */"),
            expectedFail = "",
        ),
        DEPRECATED_CLASS(
            context = Context("/** @deprecated */", ""),
            expectedFail = "",
        ),
        ;

        override fun toString(): String {
            return name.lowercase(Locale.US).replace("_", "-")
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun testParameters() = DeprecatedState.values()
    }

    data class Context(val deprecatedClass: String, val deprecatedMember: String)

    private fun checkDeprecationHidesIssue(
        expectedUndeprecatedIssues: String,
        sourceGenerator: Context.() -> TestFile,
    ) {
        val expectedFail = deprecatedState.expectedFail
        check(
            expectedFail = expectedFail,
            expectedIssues = if (expectedFail != "") expectedUndeprecatedIssues else "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    sourceGenerator(deprecatedState.context),
                )
        )
    }

    @Test
    fun `Test issue on deprecated field is ignored`() {
        checkDeprecationHidesIssue(
            expectedUndeprecatedIssues =
                """
                    src/test/pkg/Foo.java:7: error: Missing nullability on field `field` in class `class test.pkg.Foo` [MissingNullability]
                """
                    .trimIndent(),
        ) {
            java(
                """
                    package test.pkg;

                    $deprecatedClass
                    public class Foo {
                        $deprecatedMember
                        @SuppressWarnings("MutableBareField")
                        public String field;
                    }
                """
            )
        }
    }

    @Test
    fun `Test issue on deprecated method is ignored`() {
        checkDeprecationHidesIssue(
            expectedUndeprecatedIssues =
                """
                    src/test/pkg/Foo.java:6: error: Missing nullability on method `method` return [MissingNullability]
                """
                    .trimIndent(),
        ) {
            java(
                """
                    package test.pkg;

                    $deprecatedClass
                    public class Foo {
                        $deprecatedMember
                        public String method() {return "";}
                    }
                """
            )
        }
    }

    @Test
    fun `Test issue on deprecated method parameter is ignored`() {
        checkDeprecationHidesIssue(
            expectedUndeprecatedIssues =
                """
                    src/test/pkg/Foo.java:6: error: Missing nullability on parameter `s` in method `method` [MissingNullability]
                """
                    .trimIndent(),
        ) {
            java(
                """
                    package test.pkg;

                    $deprecatedClass
                    public class Foo {
                        $deprecatedMember
                        public void method(String s) {}
                    }
                """
            )
        }
    }
}
