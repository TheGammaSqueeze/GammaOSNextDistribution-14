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

import com.android.tools.metalava.ARG_HIDE_PACKAGE
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.ApiClassResolution
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.supportParameterName
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class ParameterNameChangeTest : DriverTest() {

    @Test
    fun `Change formal parameter name class method (Incompatible)`() {
        check(
            expectedIssues =
                """
                    load-api.txt:4: error: Attempted to change parameter name from bread to toast in method test.pkg.Foo.bar [ParameterNameChange]
                """,
            signatureSource =
                """
                    package test.pkg {
                      class Foo {
                        method public void bar(Int toast);
                      }
                    }
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      class Foo {
                        method public void bar(Int bread);
                      }
                    }
                """,
        )
    }

    @Test
    fun `Change formal parameter name interface method (Incompatible)`() {
        check(
            expectedIssues =
                """
                    load-api.txt:4: error: Attempted to change parameter name from bread to toast in method test.pkg.Foo.bar [ParameterNameChange]
                """,
            signatureSource =
                """
                    package test.pkg {
                      interface Foo {
                        method public void bar(int toast);
                      }
                    }
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      interface Foo {
                        method public void bar(int bread);
                      }
                    }
                """,
        )
    }

    @Test
    fun `Flag renaming a parameter from the classpath`() {
        check(
            apiClassResolution = ApiClassResolution.API_CLASSPATH,
            expectedIssues =
                """
                    error: Attempted to change parameter name from prefix to suffix in method test.pkg.MyString.endsWith [ParameterNameChange]
                    load-api.txt:4: error: Attempted to change parameter name from prefix to suffix in method test.pkg.MyString.startsWith [ParameterNameChange]
                """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 4.0
                    package test.pkg {
                        public class MyString extends java.lang.String {
                            method public boolean endsWith(String prefix);
                        }
                    }
                """,
            signatureSource =
                """
                    // Signature format: 4.0
                    package test.pkg {
                        public class MyString extends java.lang.String {
                            method public boolean startsWith(String suffix);
                        }
                    }
                """,
        )
    }

    @Test
    fun `Java Parameter Name Change`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/JavaClass.java:6: error: Attempted to remove parameter name from parameter newName in test.pkg.JavaClass.method1 [ParameterNameChange]
                    src/test/pkg/JavaClass.java:7: error: Attempted to change parameter name from secondParameter to newName in method test.pkg.JavaClass.method2 [ParameterNameChange]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public class JavaClass {
                        ctor public JavaClass();
                        method public String method1(String parameterName);
                        method public String method2(String firstParameter, String secondParameter);
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            @Suppress("all")
                            package test.pkg;
                            import androidx.annotation.ParameterName;

                            public class JavaClass {
                                public String method1(String newName) { return null; }
                                public String method2(@ParameterName("firstParameter") String s, @ParameterName("newName") String prevName) { return null; }
                            }
                        """
                    ),
                    supportParameterName
                ),
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation"),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Kotlin Parameter Name Change`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/KotlinClass.kt:4: error: Attempted to change parameter name from prevName to newName in method test.pkg.KotlinClass.method1 [ParameterNameChange]
                """,
            format = FileFormat.V2,
            checkCompatibilityApiReleased =
                """
                    // Signature format: 3.0
                    package test.pkg {
                      public final class KotlinClass {
                        ctor public KotlinClass();
                        method public final String? method1(String prevName);
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            class KotlinClass {
                                fun method1(newName: String): String? = null
                            }
                        """
                    )
                ),
        )
    }
}
