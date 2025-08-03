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

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.cli.common.ARG_WARNING
import com.android.tools.metalava.flaggedApiSource
import com.android.tools.metalava.systemApiSource
import com.android.tools.metalava.testing.java
import org.junit.Test

class FlaggedApiLintTest : DriverTest() {

    @Test
    fun `Dont require @FlaggedApi on methods that get elided from signature files`() {
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            expectedIssues = "",
            apiLint =
                """
                package android.foobar {
                  public class ExistingSystemApi {
                      ctor public ExistingSystemApi();
                  }
                  public class Existing {
                      method public int existingSystemApi();
                  }
                }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.foobar;

                        import android.annotation.SystemApi;
                        import android.annotation.FlaggedApi;

                        /** @hide */
                        @SystemApi
                        public class ExistingSystemApi extends Existing {
                            /** exactly matches Object.equals, not emitted */
                            @Override
                            public boolean equals(Object other) { return false; }
                            /** exactly matches Object.hashCode, not emitted */
                            @Override
                            public int hashCode() { return 0; }
                            /** exactly matches ExistingPublicApi.existingPublicApi, not emitted */
                            @Override
                            public int existingPublicApi() { return 0; }
                            @Override
                            public int existingSystemApi() { return 0; }
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        import android.annotation.SystemApi;
                        import android.annotation.FlaggedApi;

                        public class Existing {
                            public int existingPublicApi() { return 0; }
                            /** @hide */
                            @SystemApi
                            public int existingSystemApi() { return 0; }
                        }
                    """
                    ),
                    flaggedApiSource,
                    systemApiSource,
                ),
            extraArguments = arrayOf("--warning", "UnflaggedApi")
        )
    }

    @Test
    fun `Require @FlaggedApi on new APIs`() {
        check(
            expectedIssues =
                """
                src/android/foobar/Bad.java:3: warning: New API must be flagged with @FlaggedApi: class android.foobar.Bad [UnflaggedApi]
                src/android/foobar/Bad.java:3: warning: New API must be flagged with @FlaggedApi: constructor android.foobar.Bad() [UnflaggedApi]
                src/android/foobar/Bad.java:5: warning: New API must be flagged with @FlaggedApi: method android.foobar.Bad.bad() [UnflaggedApi]
                src/android/foobar/BadHiddenSuperClass.java:5: warning: New API must be flagged with @FlaggedApi: method android.foobar.Bad.inheritedBad() [UnflaggedApi]
                src/android/foobar/Bad.java:4: warning: New API must be flagged with @FlaggedApi: field android.foobar.Bad.BAD [UnflaggedApi]
                src/android/foobar/BadHiddenSuperClass.java:4: warning: New API must be flagged with @FlaggedApi: field android.foobar.Bad.INHERITED_BAD [UnflaggedApi]
                src/android/foobar/Bad.java:7: warning: New API must be flagged with @FlaggedApi: class android.foobar.Bad.BadAnnotation [UnflaggedApi]
                src/android/foobar/Bad.java:6: warning: New API must be flagged with @FlaggedApi: class android.foobar.Bad.BadInterface [UnflaggedApi]
                src/android/foobar/ExistingClass.java:10: warning: New API must be flagged with @FlaggedApi: method android.foobar.ExistingClass.bad() [UnflaggedApi]
                src/android/foobar/BadHiddenSuperClass.java:5: warning: New API must be flagged with @FlaggedApi: method android.foobar.ExistingClass.inheritedBad() [UnflaggedApi]
                src/android/foobar/ExistingClass.java:9: warning: New API must be flagged with @FlaggedApi: field android.foobar.ExistingClass.BAD [UnflaggedApi]
                src/android/foobar/BadHiddenSuperClass.java:4: warning: New API must be flagged with @FlaggedApi: field android.foobar.ExistingClass.INHERITED_BAD [UnflaggedApi]
                """
                    .trimIndent(),
            apiLint =
                """
                package android.foobar {
                  public class ExistingClass {
                      ctor ExistingClass();
                      field public static final String EXISTING_FIELD = "foo";
                      method public void existingMethod();
                  }
                  public interface ExistingInterface {
                      field public static final String EXISTING_INTERFACE_FIELD = "foo";
                      method public void existingInterfaceMethod();
                  }
                  public class ExistingSuperClass {
                      ctor public ExistingSuperClass();
                      field public static final String EXISTING_SUPER_FIELD = "foo";
                      method public void existingSuperMethod();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.foobar;

                        import android.annotation.FlaggedApi;

                        public interface ExistingInterface {
                            public static final String EXISTING_INTERFACE_FIELD = "foo";
                            public default void existingInterfaceMethod() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        import android.annotation.FlaggedApi;

                        public class ExistingSuperClass {
                            public static final String EXISTING_SUPER_FIELD = "foo";
                            public void existingSuperMethod() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        import android.annotation.FlaggedApi;

                        public class ExistingClass extends BadHiddenSuperClass implements BadHiddenSuperInterface {
                            public static final String EXISTING_FIELD = "foo";
                            public void existingMethod() {}

                            public static final String BAD = "bar";
                            public void bad() {}

                            @FlaggedApi("foo/bar")
                            public static final String OK = "baz";

                            @FlaggedApi("foo/bar")
                            public void ok() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        class BadHiddenSuperClass {
                            public static final String INHERITED_BAD = "bar";
                            public void inheritedBad() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        interface BadHiddenSuperInterface {
                            public static final String INHERITED_BAD = "bar";
                            public void inheritedBad() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        public class Bad extends BadHiddenSuperClass implements BadHiddenSuperInterface {
                            public static final String BAD = "bar";
                            public void bad() {}
                            public interface BadInterface {}
                            public @interface BadAnnotation {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        import android.annotation.FlaggedApi;

                        @FlaggedApi("foo/bar")
                        public class Ok extends ExistingSuperClass implements ExistingInterface {
                            public static final String OK = "bar";
                            public void ok() {}
                            public interface OkInterface {}
                            public @interface OkAnnotation {}
                        }
                    """
                    ),
                    flaggedApiSource
                ),
            extraArguments = arrayOf(ARG_WARNING, "UnflaggedApi", ARG_HIDE, "HiddenSuperclass")
        )
    }

    @Test
    fun `Dont require @FlaggedApi on existing items in nested SystemApi classes`() {
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            expectedIssues = "",
            apiLint =
                """
                package android.foobar {
                  public class Existing.Inner {
                      method int existing();
                  }
                }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.foobar;

                        import android.annotation.SystemApi;

                        public class Existing {
                            public class Inner {
                                /** @hide */
                                @SystemApi
                                public int existing() {}
                            }
                        }
                    """
                    ),
                    flaggedApiSource,
                    systemApiSource,
                ),
            extraArguments = arrayOf("--warning", "UnflaggedApi")
        )
    }

    @Test
    fun `Dont require @FlaggedApi on existing items inherited into new SystemApi classes`() {
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            expectedIssues =
                """
                src/android/foobar/BadHiddenSuperClass.java:5: warning: New API must be flagged with @FlaggedApi: method android.foobar.Bad.badInherited() [UnflaggedApi]
                src/android/foobar/BadHiddenSuperClass.java:4: warning: New API must be flagged with @FlaggedApi: field android.foobar.Bad.BAD_INHERITED [UnflaggedApi]
            """,
            apiLint =
                """
                package android.foobar {
                  public interface ExistingSystemInterface {
                      field public static final String EXISTING_SYSTEM_INTERFACE_FIELD = "foo";
                      method public void existingSystemInterfaceMethod();
                  }
                  public class ExistingSystemSuperClass {
                      ctor public ExistingSystemSuperClass();
                      field public static final String EXISTING_SYSTEM_SUPER_FIELD = "foo";
                      method public void existingSystemSuperMethod();
                  }
                  public class Existing {
                  }
                }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.foobar;

                        import android.annotation.FlaggedApi;
                        import android.annotation.SystemApi;

                        /** @hide */
                        @SystemApi
                        public interface ExistingSystemInterface {
                            public static final String EXISTING_SYSTEM_INTERFACE_FIELD = "foo";
                            public default void existingSystemInterfaceMethod() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        import android.annotation.FlaggedApi;
                        import android.annotation.SystemApi;

                        /** @hide */
                        @SystemApi
                        public class ExistingSystemSuperClass {
                            public static final String EXISTING_SYSTEM_SUPER_FIELD = "foo";
                            public void existingSystemSuperMethod() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        public interface ExistingPublicInterface {
                            public static final String EXISTING_PUBLIC_INTERFACE_FIELD = "foo";
                            public default void existingPublicInterfaceMethod() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        class BadHiddenSuperClass {
                            public static final String BAD_INHERITED = "foo";
                            public default void badInherited() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        public class ExistingPublicSuperClass {
                            public static final String EXISTING_PUBLIC_SUPER_FIELD = "foo";
                            public void existingPublicSuperMethod() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        import android.annotation.SystemApi;

                        /** @hide */
                        @SystemApi
                        @SuppressWarnings("UnflaggedApi")  // Ignore the class itself for this test.
                        public class Ok extends ExistingSystemSuperClass implements ExistingSystemInterface {
                            private Ok() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        import android.annotation.SystemApi;

                        /** @hide */
                        @SystemApi
                        @SuppressWarnings("UnflaggedApi")  // Ignore the class itself for this test.
                        public class Bad extends BadHiddenSuperClass {
                            private Bad() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        import android.annotation.SystemApi;

                        /** @hide */
                        @SystemApi
                        @SuppressWarnings("UnflaggedApi")  // Ignore the class itself for this test.
                        public class Ok2 extends ExistingPublicSuperClass implements ExistingPublicInterface {
                            private Ok2() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        import android.annotation.SystemApi;

                        /** @hide */
                        @SystemApi
                        public class Existing extends ExistingPublicSuperClass implements ExistingPublicInterface {
                            private Existing() {}
                        }
                    """
                    ),
                    flaggedApiSource,
                    systemApiSource,
                ),
            extraArguments = arrayOf(ARG_WARNING, "UnflaggedApi", ARG_HIDE, "HiddenSuperclass"),
            checkCompilation = true
        )
    }

    @Test
    fun `Require @FlaggedApi to reference generated fields`() {
        check(
            expectedIssues =
                """
                src/android/foobar/Bad.java:6: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                src/android/foobar/Bad.java:10: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                src/android/foobar/Bad.java:17: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (furthermore, the current flag literal seems to be malformed). [FlaggedApiLiteral]
                src/android/foobar/Bad.java:19: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_NONEXISTENT_FLAG, however this flag doesn't seem to exist). [FlaggedApiLiteral]
                src/android/foobar/Bad.java:21: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.baz.Flags.FLAG_NON_EXISTENT_PACKAGE, however this flag doesn't seem to exist). [FlaggedApiLiteral]
                src/android/foobar/Bad.java:8: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                src/android/foobar/Bad.java:14: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                src/android/foobar/Bad.java:12: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                """
                    .trimIndent(),
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.foobar;

                        import android.annotation.FlaggedApi;

                        @FlaggedApi("android.foobar.my_feature")
                        public class Bad {
                            @FlaggedApi("android.foobar.my_feature")
                            public static final String BAD = "bar";
                            @FlaggedApi("android.foobar.my_feature")
                            public void bad() {}
                            @FlaggedApi("android.foobar.my_feature")
                            public interface BadInterface {}
                            @FlaggedApi("android.foobar.my_feature")
                            public @interface BadAnnotation {}

                            @FlaggedApi("malformed/flag")
                            public void malformed() {}
                            @FlaggedApi("android.foobar.nonexistent_flag")
                            public void nonexistentFlag() {}
                            @FlaggedApi("android.baz.non_existent_package")
                            public void nonexistentPackage() {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        import android.annotation.FlaggedApi;

                        @FlaggedApi(android.foobar.Flags.FLAG_MY_FEATURE)
                        public class Ok {
                            @FlaggedApi(android.foobar.Flags.FLAG_MY_FEATURE)
                            public static final String OK = "bar";
                            @FlaggedApi(android.foobar.Flags.FLAG_MY_FEATURE)
                            public void ok() {}
                            @FlaggedApi(android.foobar.Flags.FLAG_MY_FEATURE)
                            public interface OkInterface {}
                            @FlaggedApi(android.foobar.Flags.FLAG_MY_FEATURE)
                            public @interface OkAnnotation {}
                        }
                    """
                    ),
                    java(
                        """
                        package android.foobar;

                        /** @hide */
                        public class Flags {
                            public static final String FLAG_MY_FEATURE = "android.foobar.my_feature";
                        }
                    """
                    ),
                    flaggedApiSource
                ),
            extraArguments = arrayOf(ARG_WARNING, "FlaggedApiLiteral")
        )
    }
}
