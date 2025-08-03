/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.stub

import com.android.tools.metalava.ARG_HIDE_PACKAGE
import com.android.tools.metalava.ARG_PASS_THROUGH_ANNOTATION
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.restrictToSource
import com.android.tools.metalava.testing.html
import com.android.tools.metalava.testing.java
import org.junit.Test

@SuppressWarnings("ALL")
class StubsPackageInfoTest : AbstractStubsTest() {
    @Test
    fun `Check writing package info file`() {
        checkStubs(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    @androidx.annotation.Nullable
                    package test.pkg;
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class Test {
                    }
                    """
                    ),
                    androidxNullableSource
                ),
            warnings = "",
            api =
                """
                package @Nullable test.pkg {
                  public class Test {
                    ctor public Test();
                  }
                }
            """, // WRONG: I should include package annotations in the signature file!
            source =
                """
                @androidx.annotation.Nullable
                package test.pkg;
                """,
            extraArguments =
                arrayOf(
                    ARG_HIDE_PACKAGE,
                    "androidx.annotation",
                    // By default metalava rewrites androidx.annotation.Nullable to
                    // android.annotation.Nullable, but the latter does not have target PACKAGE thus
                    // fails to compile. This forces stubs keep the androidx annotation.
                    ARG_PASS_THROUGH_ANNOTATION,
                    "androidx.annotation.Nullable"
                )
        )
    }

    @Test
    fun `Test package-info documentation in stubs`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                            """
                      /** My package docs */
                      package test.pkg;
                      """
                        )
                        .indented(),
                    java("""package test.pkg; public abstract class Class1 { }""")
                ),
            api =
                """
                package test.pkg {
                  public abstract class Class1 {
                    ctor public Class1();
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    /** My package docs */
                    package test.pkg;
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class Class1 {
                    public Class1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                ),
        )
    }

    @Test
    fun `Test package-info documentation in doc stubs`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                            """
                      /** My package docs */
                      package test.pkg;
                      """
                        )
                        .indented(),
                    java("""package test.pkg; public abstract class Class1 { }""")
                ),
            api =
                """
                package test.pkg {
                  public abstract class Class1 {
                    ctor public Class1();
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    /** My package docs */
                    package test.pkg;
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class Class1 {
                    public Class1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                ),
            docStubs = true
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test package-info annotations`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                            """
                      @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                      package test.pkg;

                      import androidx.annotation.RestrictTo;
                      """
                        )
                        .indented(),
                    java("""package test.pkg; public abstract class Class1 { }"""),
                    restrictToSource
                ),
            api =
                """
                package @RestrictTo(androidx.annotation.RestrictTo.Scope.SUBCLASSES) test.pkg {
                  public abstract class Class1 {
                    ctor public Class1();
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java("""
                    package test.pkg;
                    """),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class Class1 {
                    public Class1() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                ),
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation")
        )
    }

    @Test
    fun `Check writing package info from package html file`() {
        checkStubs(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    html(
                        "src/test/pkg/package.html",
                        """
                    <HTML>
                    <BODY>
                    Summary.
                    <p>
                    Body.
                    </BODY>
                    </HTML>
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class Test {
                    }
                    """
                    ),
                ),
            warnings = "",
            api =
                """
                package test.pkg {
                  public class Test {
                    ctor public Test();
                  }
                }
            """,
            source =
                @Suppress("DanglingJavadoc")
                """
                /**
                 * Summary.
                 * <p>
                 * Body.
                 */
                package test.pkg;
                """,
        )
    }
}
