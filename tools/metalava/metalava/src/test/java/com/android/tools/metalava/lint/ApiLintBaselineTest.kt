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

package com.android.tools.metalava.lint

import com.android.tools.metalava.ARG_HIDE_PACKAGE
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.androidxNonNullSource
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.restrictToSource
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Test for [ApiLint] specifically with baseline arguments. */
class ApiLintBaselineTest : DriverTest() {
    @Test
    fun `Test with global baseline`() {
        check(
            apiLint = "", // enabled
            baselineTestInfo =
                BaselineTestInfo(
                    inputContents =
                        """
                            // Baseline format: 1.0
                            Enum: android.pkg.MyEnum:
                                Enums are discouraged in Android APIs
                        """,
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public enum MyEnum {
                       FOO, BAR
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test with api-lint specific baseline`() {
        check(
            apiLint = "", // enabled
            baselineApiLintTestInfo =
                BaselineTestInfo(
                    inputContents =
                        """
                            // Baseline format: 1.0
                            Enum: android.pkg.MyEnum:
                                Enums are discouraged in Android APIs
                        """,
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public enum MyEnum {
                       FOO, BAR
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test with api-lint specific baseline with update`() {
        check(
            apiLint = "", // enabled
            baselineApiLintTestInfo =
                BaselineTestInfo(
                    inputContents = "",
                    expectedOutputContents =
                        """
                            // Baseline format: 1.0
                            Enum: android.pkg.MyEnum:
                                Enums are discouraged in Android APIs
                        """,
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public enum MyEnum {
                       FOO, BAR
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test with non-api-lint specific baseline`() {
        check(
            apiLint = "", // enabled
            baselineCheckCompatibilityReleasedTestInfo =
                BaselineTestInfo(
                    inputContents =
                        """
                            // Baseline format: 1.0
                            Enum: android.pkg.MyEnum:
                                Enums are discouraged in Android APIs
                        """,
                ),
            expectedIssues =
                """
                src/android/pkg/MyEnum.java:3: error: Enums are discouraged in Android APIs [Enum]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public enum MyEnum {
                       FOO, BAR
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test api-lint error message`() {
        check(
            apiLint = "", // enabled
            baselineApiLintTestInfo = BaselineTestInfo(inputContents = ""),
            errorMessageApiLint = "*** api-lint failed ***",
            expectedIssues =
                """
                src/android/pkg/MyClassImpl.java:3: error: Don't expose your implementation details: `MyClassImpl` ends with `Impl` [EndsWithImpl]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MyClassImpl {
                    }
                    """
                    )
                ),
            expectedFail = """
                *** api-lint failed ***
            """,
            expectedOutput = """
                *** api-lint failed ***
                """
        )
    }

    @Test
    fun `Test no api-lint error message`() {
        check(
            apiLint = "", // enabled
            baselineApiLintTestInfo = BaselineTestInfo(inputContents = ""),
            expectedIssues =
                """
                src/android/pkg/MyClassImpl.java:3: error: Don't expose your implementation details: `MyClassImpl` ends with `Impl` [EndsWithImpl]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class MyClassImpl {
                    }
                    """
                    )
                ),
            expectedFail = DefaultLintErrorMessage,
            expectedOutput = DefaultLintErrorMessage
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check generic builders with synthesized setter`() {
        check(
            apiLint = "", // enabled
            baselineApiLintTestInfo =
                BaselineTestInfo(
                    inputContents = "",
                    expectedOutputContents =
                        """
                            // Baseline format: 1.0
                            MissingGetterMatchingBuilder: test.Foo.Builder#setField(int):
                                test.Foo does not declare a `getField()` method matching method test.Foo.Builder.setField(int)
                        """,
                ),
            hideAnnotations =
                arrayOf(
                    "androidx.annotation.RestrictTo",
                    "androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY)"
                ),
            extraArguments =
                arrayOf(
                    ARG_HIDE_PACKAGE,
                    "androidx",
                    ARG_HIDE,
                    "HiddenSuperclass",
                    ARG_HIDE,
                    "ProtectedMember",
                    ARG_HIDE,
                    "GetterOnBuilder"
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test;
                    import androidx.annotation.NonNull;
                    import androidx.annotation.RestrictTo;
                    @RestrictTo(RestrictTo.Scope.LIBRARY)
                    public class Base {
                        public static abstract class BaseBuilder<B extends BaseBuilder<B>> {
                            protected int field;

                            protected BaseBuilder() {}

                            @NonNull
                            protected abstract B getThis();

                            @NonNull
                            public B setField(int i) {
                                this.field = i;
                                return getThis();
                            }
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test;
                    import androidx.annotation.NonNull;
                    public class Foo extends Base {
                        private final int field;
                        private Foo(int i) {
                            this.field = i;
                        }

                        public static final class Builder extends BaseBuilder<Builder> {
                            public Builder() {}

                            @RestrictTo(RestrictTo.Scope.LIBRARY)
                            @NonNull
                            @Override
                            protected Builder getThis() { return this; }

                            @NonNull
                            public Foo build() {
                                return new Foo(field);
                            }
                        }
                    }
                    """
                    ),
                    androidxNonNullSource,
                    androidxNullableSource,
                    restrictToSource,
                ),
        )
    }
}
