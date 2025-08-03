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

package com.android.tools.metalava

import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class DefaultReporterTest : DriverTest() {
    @Test
    fun `Errors are sent to stderr`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/foo.java:2: error: Class must start with uppercase char: foo [StartWithUpper]
                src/test/pkg/foo.java:4: warning: If min/max could change in future, make them dynamic methods: test.pkg.foo#MAX_BAR [MinMaxConstant]
            """,
            errorSeverityExpectedIssues =
                """
                src/test/pkg/foo.java:2: error: Class must start with uppercase char: foo [StartWithUpper]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class foo {
                        private foo() {}
                        public static final int MAX_BAR = 0;
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test suppression annotations`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/Bar.kt:10: error: Method name must start with lowercase char: Unsuppressed [StartWithLower]
                src/test/pkg/Foo.java:10: error: Method name must start with lowercase char: Unsuppressed [StartWithLower]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SuppressLint;

                    public class Foo {
                        @SuppressLint("StartWithLower")
                        public void SuppressedWithSuppressLint() { }
                        @SuppressWarnings("StartWithLower")
                        public void SuppressedWithSuppressWarnings() { }

                        public void Unsuppressed() { }
                    }
                """
                    ),
                    kotlin(
                        """
                    package test.pkg
                    import android.annotation.SuppressLint

                    class Bar {
                        @SuppressLint("StartWithLower")
                        fun SuppressedWithSuppressLint() { }
                        @Suppress("StartWithLower")
                        fun SuppressedWithSuppress() { }

                        fun Unsuppressed() { }
                    }
                """
                    ),
                    suppressLintSource
                )
        )
    }

    @Test
    fun `Test suppressing infos`() {
        check(
            apiLint = "",
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SuppressLint;

                    public class Foo {
                        @SuppressLint("KotlinOperator")
                        public int get(int i) { return i + 1; }
                    }
                """
                    ),
                    suppressLintSource
                )
        )
    }

    @Test
    fun `Test repeat errors with 1 error`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/Foo.java:4: error: Missing nullability on parameter `a` in method `foo1` [MissingNullability]
            """,
            expectedFail =
                """
                Error: metalava detected the following problems:
                src/test/pkg/Foo.java:4: error: Missing nullability on parameter `a` in method `foo1` [MissingNullability]

            """
                    .trimIndent() + DefaultLintErrorMessage,
            repeatErrorsMax = 5,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Foo {
                        public void foo1(String a) {}
                    }
                """
                    ),
                    suppressLintSource
                )
        )
    }

    @Test
    fun `Test repeat errors with 5 errors`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/Foo.java:4: error: Missing nullability on parameter `a` in method `foo1` [MissingNullability]
                src/test/pkg/Foo.java:5: error: Missing nullability on parameter `a` in method `foo2` [MissingNullability]
                src/test/pkg/Foo.java:6: error: Missing nullability on parameter `a` in method `foo3` [MissingNullability]
                src/test/pkg/Foo.java:7: error: Missing nullability on parameter `a` in method `foo4` [MissingNullability]
                src/test/pkg/Foo.java:8: error: Missing nullability on parameter `a` in method `foo5` [MissingNullability]
            """,
            expectedFail =
                """
                Error: metalava detected the following problems:
                src/test/pkg/Foo.java:4: error: Missing nullability on parameter `a` in method `foo1` [MissingNullability]
                src/test/pkg/Foo.java:5: error: Missing nullability on parameter `a` in method `foo2` [MissingNullability]
                src/test/pkg/Foo.java:6: error: Missing nullability on parameter `a` in method `foo3` [MissingNullability]
                src/test/pkg/Foo.java:7: error: Missing nullability on parameter `a` in method `foo4` [MissingNullability]
                src/test/pkg/Foo.java:8: error: Missing nullability on parameter `a` in method `foo5` [MissingNullability]

            """
                    .trimIndent() + DefaultLintErrorMessage,
            repeatErrorsMax = 5,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Foo {
                        public void foo1(String a) {}
                        public void foo2(String a) {}
                        public void foo3(String a) {}
                        public void foo4(String a) {}
                        public void foo5(String a) {}
                    }
                """
                    ),
                    suppressLintSource
                )
        )
    }

    @Test
    fun `Test repeat errors with 6 errors`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/Foo.java:4: error: Missing nullability on parameter `a` in method `foo1` [MissingNullability]
                src/test/pkg/Foo.java:5: error: Missing nullability on parameter `a` in method `foo2` [MissingNullability]
                src/test/pkg/Foo.java:6: error: Missing nullability on parameter `a` in method `foo3` [MissingNullability]
                src/test/pkg/Foo.java:7: error: Missing nullability on parameter `a` in method `foo4` [MissingNullability]
                src/test/pkg/Foo.java:8: error: Missing nullability on parameter `a` in method `foo5` [MissingNullability]
                src/test/pkg/Foo.java:9: error: Missing nullability on parameter `a` in method `foo6` [MissingNullability]
            """,
            expectedFail =
                """
                Error: metalava detected the following problems:
                src/test/pkg/Foo.java:4: error: Missing nullability on parameter `a` in method `foo1` [MissingNullability]
                src/test/pkg/Foo.java:5: error: Missing nullability on parameter `a` in method `foo2` [MissingNullability]
                src/test/pkg/Foo.java:6: error: Missing nullability on parameter `a` in method `foo3` [MissingNullability]
                src/test/pkg/Foo.java:7: error: Missing nullability on parameter `a` in method `foo4` [MissingNullability]
                src/test/pkg/Foo.java:8: error: Missing nullability on parameter `a` in method `foo5` [MissingNullability]
                1 more error(s) omitted. Search the log for 'error:' to find all of them.

            """
                    .trimIndent() + DefaultLintErrorMessage,
            repeatErrorsMax = 5,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Foo {
                        public void foo1(String a) {}
                        public void foo2(String a) {}
                        public void foo3(String a) {}
                        public void foo4(String a) {}
                        public void foo5(String a) {}
                        public void foo6(String a) {}
                    }
                """
                    ),
                    suppressLintSource
                )
        )
    }
}
