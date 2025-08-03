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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.java
import org.junit.Test

class ShowForStubPurposesAnnotationTest : DriverTest() {
    val HIDE_ANNOTATION = "test.annotation.Hide"
    val MODULE_API = "test.annotation.ModuleApi" // @ModuleApi is assumed to "contain" @SystemApi
    val SYSTEM_API = "test.annotation.SystemApi"

    val EXTRA_ARGS =
        arrayOf(
            ARG_ERROR,
            Issues.UNAVAILABLE_SYMBOL.name,
            ARG_ERROR,
            Issues.HIDDEN_TYPE_PARAMETER.name,
            ARG_ERROR,
            Issues.REFERENCES_HIDDEN.name
        )

    val SOURCE1 =
        """
            package test.pkg;

            @test.annotation.Hide
            @test.annotation.SystemApi
            public class SystemClass {
                public void noAnnotation() {
                }

                @test.annotation.Hide
                public void hidden() {
                }

                @test.annotation.Hide
                @test.annotation.SystemApi
                public void system() {
                }

                @test.annotation.Hide
                @test.annotation.ModuleApi
                public void module() {
                }

                @test.annotation.Hide
                @test.annotation.ModuleApi
                @test.annotation.SystemApi
                public void moduleAndSystem() {
                }

                public static class NestedDefault {
                    public void noAnnotation() {
                    }

                    @test.annotation.Hide
                    public void hidden() {
                    }

                    @test.annotation.Hide
                    @test.annotation.SystemApi
                    public void system() {
                    }

                    @test.annotation.Hide
                    @test.annotation.ModuleApi
                    public void module() {
                    }
                }

                @test.annotation.Hide
                public static class NestedHidden {
                    public void noAnnotation() {
                    }

                    @test.annotation.Hide
                    public void hidden() {
                    }
                }

                @test.annotation.Hide
                @test.annotation.ModuleApi
                public static class NestedModule {
                    public void noAnnotation() {
                    }

                    @test.annotation.Hide
                    public void hidden() {
                    }

                    @test.annotation.Hide
                    @test.annotation.ModuleApi
                    public void module() {
                    }
                }

                @test.annotation.Hide
                @test.annotation.ModuleApi
                public void referFromModuleToSystem(SystemClass2 arg) {
                }
            }
            """

    val SOURCE2 =
        """
            package test.pkg;

            public class PublicClass {
                public void noAnnotation() {
                }

                @test.annotation.Hide
                public void hidden() {
                }

                @test.annotation.Hide
                @test.annotation.SystemApi
                public void system() {
                }

                @test.annotation.Hide
                @test.annotation.ModuleApi
                public void module() {
                }

                public static class NestedDefault {
                    public void noAnnotation() {
                    }

                    @test.annotation.Hide
                    public void hidden() {
                    }

                    @test.annotation.Hide
                    @test.annotation.SystemApi
                    public void system() {
                    }

                    @test.annotation.Hide
                    @test.annotation.ModuleApi
                    public void module() {
                    }
                }

                @test.annotation.Hide
                public static class NestedHidden {
                    public void noAnnotation() {
                    }

                    @test.annotation.Hide
                    public void hidden() {
                    }
                }

                @test.annotation.Hide
                @test.annotation.ModuleApi
                public static class NestedModule {
                    public void noAnnotation() {
                    }

                    @test.annotation.Hide
                    public void hidden() {
                    }

                    @test.annotation.Hide
                    @test.annotation.ModuleApi
                    public void module() {
                    }
                }
            }
            """

    val SOURCE3 =
        """
            package test.pkg;

            @test.annotation.Hide
            @test.annotation.SystemApi
            public class SystemClass2 {
            }
            """

    val SOURCE4 =
        """
            package test.pkg;

            @test.annotation.Hide
            @test.annotation.ModuleApi
            public class ModuleClass {
                public void noAnnotation() {
                }

                @test.annotation.Hide
                public void hidden() {
                }

                @test.annotation.Hide
                @test.annotation.ModuleApi
                public void module() {
                }
            }
            """

    val SOURCE5 =
        """
            package test.pkg;

            @test.annotation.Hide
            public class HiddenClass {
            }
            """

    val SOURCE_FILES_1_2_3_4_5 =
        arrayOf<TestFile>(java(SOURCE1), java(SOURCE2), java(SOURCE3), java(SOURCE4), java(SOURCE5))

    @Test
    fun `Hierarchy test - SystemApi + ModuleApi`() {
        check(
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            hideAnnotations = arrayOf(HIDE_ANNOTATION),
            showAnnotations = arrayOf(MODULE_API, SYSTEM_API),
            sourceFiles = SOURCE_FILES_1_2_3_4_5,
            api =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ModuleClass {
                    ctor public ModuleClass();
                    method public void module();
                    method public void noAnnotation();
                  }
                  public class PublicClass {
                    method public void module();
                    method public void system();
                  }
                  public static class PublicClass.NestedDefault {
                    method public void module();
                    method public void system();
                  }
                  public static class PublicClass.NestedModule {
                    ctor public PublicClass.NestedModule();
                    method public void module();
                    method public void noAnnotation();
                  }
                  public class SystemClass {
                    ctor public SystemClass();
                    method public void module();
                    method public void moduleAndSystem();
                    method public void noAnnotation();
                    method public void referFromModuleToSystem(test.pkg.SystemClass2);
                    method public void system();
                  }
                  public static class SystemClass.NestedDefault {
                    ctor public SystemClass.NestedDefault();
                    method public void module();
                    method public void noAnnotation();
                    method public void system();
                  }
                  public static class SystemClass.NestedModule {
                    ctor public SystemClass.NestedModule();
                    method public void module();
                    method public void noAnnotation();
                  }
                  public class SystemClass2 {
                    ctor public SystemClass2();
                  }
                }
                """
        )
    }

    @Test
    fun `Hierarchy test - SystemApi only`() {
        check(
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            hideAnnotations = arrayOf(HIDE_ANNOTATION),
            showAnnotations = arrayOf(SYSTEM_API),
            sourceFiles = SOURCE_FILES_1_2_3_4_5,
            api =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class PublicClass {
                    method public void system();
                  }
                  public static class PublicClass.NestedDefault {
                    method public void system();
                  }
                  public class SystemClass {
                    ctor public SystemClass();
                    method public void moduleAndSystem();
                    method public void noAnnotation();
                    method public void system();
                  }
                  public static class SystemClass.NestedDefault {
                    ctor public SystemClass.NestedDefault();
                    method public void noAnnotation();
                    method public void system();
                  }
                  public class SystemClass2 {
                    ctor public SystemClass2();
                  }
                }
                """
        )
    }

    @Test
    fun `Hierarchy test - ModuleApi Only, also check the stub files`() {
        check(
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            hideAnnotations = arrayOf(HIDE_ANNOTATION),
            showAnnotations = arrayOf(MODULE_API),
            showForStubPurposesAnnotations = arrayOf(SYSTEM_API),
            sourceFiles = SOURCE_FILES_1_2_3_4_5,
            api =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ModuleClass {
                    ctor public ModuleClass();
                    method public void module();
                    method public void noAnnotation();
                  }
                  public class PublicClass {
                    method public void module();
                  }
                  public static class PublicClass.NestedDefault {
                    method public void module();
                  }
                  public static class PublicClass.NestedModule {
                    ctor public PublicClass.NestedModule();
                    method public void module();
                    method public void noAnnotation();
                  }
                  public class SystemClass {
                    method public void module();
                    method public void moduleAndSystem();
                    method public void referFromModuleToSystem(test.pkg.SystemClass2);
                  }
                  public static class SystemClass.NestedDefault {
                    method public void module();
                  }
                  public static class SystemClass.NestedModule {
                    ctor public SystemClass.NestedModule();
                    method public void module();
                    method public void noAnnotation();
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class SystemClass {
                    public SystemClass() { throw new RuntimeException("Stub!"); }
                    public void noAnnotation() { throw new RuntimeException("Stub!"); }
                    public void system() { throw new RuntimeException("Stub!"); }
                    public void module() { throw new RuntimeException("Stub!"); }
                    public void moduleAndSystem() { throw new RuntimeException("Stub!"); }
                    public void referFromModuleToSystem(test.pkg.SystemClass2 arg) { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class NestedDefault {
                    public NestedDefault() { throw new RuntimeException("Stub!"); }
                    public void noAnnotation() { throw new RuntimeException("Stub!"); }
                    public void system() { throw new RuntimeException("Stub!"); }
                    public void module() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class NestedModule {
                    public NestedModule() { throw new RuntimeException("Stub!"); }
                    public void noAnnotation() { throw new RuntimeException("Stub!"); }
                    public void module() { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicClass {
                    public PublicClass() { throw new RuntimeException("Stub!"); }
                    public void noAnnotation() { throw new RuntimeException("Stub!"); }
                    public void system() { throw new RuntimeException("Stub!"); }
                    public void module() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class NestedDefault {
                    public NestedDefault() { throw new RuntimeException("Stub!"); }
                    public void noAnnotation() { throw new RuntimeException("Stub!"); }
                    public void system() { throw new RuntimeException("Stub!"); }
                    public void module() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class NestedModule {
                    public NestedModule() { throw new RuntimeException("Stub!"); }
                    public void noAnnotation() { throw new RuntimeException("Stub!"); }
                    public void module() { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class SystemClass2 {
                    public SystemClass2() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class ModuleClass {
                    public ModuleClass() { throw new RuntimeException("Stub!"); }
                    public void noAnnotation() { throw new RuntimeException("Stub!"); }
                    public void module() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Hierarchy test - Can't refer from system to module`() {
        check(
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            hideAnnotations = arrayOf(HIDE_ANNOTATION),
            showAnnotations = arrayOf(SYSTEM_API),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @test.annotation.Hide
                    @test.annotation.SystemApi
                    public class SystemClass {
                        public void foo(ModuleClass arg) {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    @test.annotation.Hide
                    @test.annotation.ModuleApi
                    public class ModuleClass {
                    }
                    """
                    )
                ),
            expectedIssues =
                """
                src/test/pkg/SystemClass.java:6: error: Class test.pkg.ModuleClass is hidden but was referenced (in parameter type) from public parameter arg in test.pkg.SystemClass.foo(test.pkg.ModuleClass arg) [ReferencesHidden]
                src/test/pkg/SystemClass.java:6: error: Parameter of unavailable type test.pkg.ModuleClass in test.pkg.SystemClass.foo() [UnavailableSymbol]
                src/test/pkg/SystemClass.java:6: error: Parameter arg references hidden type test.pkg.ModuleClass. [HiddenTypeParameter]
                """,
            expectedFail = DefaultLintErrorMessage,
        )
    }

    val SOURCE6 =
        """
            package test.pkg;

            @test.annotation.Hide
            @test.annotation.ModuleApi
            public class ModuleClass {
                public void foo() {
                }
            }
        """
    val SOURCE7 =
        """
            package test.pkg;

            @test.annotation.Hide
            @test.annotation.ModuleApi
            public class ModuleClassExtendingPublic extends PublicSubClass {
                public void bar() {
                }

                // This is a @SystemApi, so this shouldn't show up in the signature file.
                @Override
                public void abstractMethodOverridden() {
                }
            }
        """
    // Note all the methods in the following classes are "@SystemApi", so
    // none of them should show up in the MODULE_API signature file.
    val SOURCE8 =
        """
            package test.pkg;

            public class PublicClass {
                @test.annotation.Hide
                @test.annotation.SystemApi
                public abstract void abstractMethodOverridden();

                @test.annotation.Hide
                @test.annotation.SystemApi
                public abstract void abstractMethodNotOverridden();

                @test.annotation.Hide
                @test.annotation.SystemApi
                public abstract void abstractMethodOverriddenByAbstract();

                @test.annotation.Hide
                @test.annotation.SystemApi
                public void systemMethodOverridden() {
                }

                @test.annotation.Hide
                @test.annotation.SystemApi
                public void systemMethodNotOverridden() {
                }

                @test.annotation.Hide
                @test.annotation.SystemApi
                public void systemMethodHiddenInSub() {
                }
            }
        """
    val SOURCE9 =
        """
            package test.pkg;

            public class PublicSubClass extends PublicClass {
                @Override
                public void abstractMethodOverridden() {
                }

                @Override
                public abstract void abstractMethodOverriddenByAbstract();

                @Override
                public void systemMethodOverridden() {
                }

                @test.annotation.Hide
                public void systemMethodHiddenInSub() {
                }

                @test.annotation.Hide
                @test.annotation.SystemApi
                public void subMethod() {
                }
            }
        """

    val SOURCE_FILES_6_7_8_9 =
        arrayOf<TestFile>(java(SOURCE6), java(SOURCE7), java(SOURCE8), java(SOURCE9))

    @Test
    fun `Complecated case - SystemApi + ModuleApi`() {
        check(
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            hideAnnotations = arrayOf(HIDE_ANNOTATION),
            showAnnotations = arrayOf(SYSTEM_API, MODULE_API),
            sourceFiles = SOURCE_FILES_6_7_8_9,
            api =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ModuleClass {
                    ctor public ModuleClass();
                    method public void foo();
                  }
                  public class ModuleClassExtendingPublic extends test.pkg.PublicSubClass {
                    ctor public ModuleClassExtendingPublic();
                    method public void bar();
                  }
                  public class PublicClass {
                    method public abstract void abstractMethodNotOverridden();
                    method public abstract void abstractMethodOverridden();
                    method public abstract void abstractMethodOverriddenByAbstract();
                    method public void systemMethodHiddenInSub();
                    method public void systemMethodNotOverridden();
                    method public void systemMethodOverridden();
                  }
                  public class PublicSubClass extends test.pkg.PublicClass {
                    method public void abstractMethodOverridden();
                    method public void subMethod();
                  }
                }
                """
        )
    }

    @Test
    fun `Complecated case - SystemApi only`() {
        check(
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            hideAnnotations = arrayOf(HIDE_ANNOTATION),
            showAnnotations = arrayOf(SYSTEM_API),
            sourceFiles = SOURCE_FILES_6_7_8_9,
            api =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class PublicClass {
                    method public abstract void abstractMethodNotOverridden();
                    method public abstract void abstractMethodOverridden();
                    method public abstract void abstractMethodOverriddenByAbstract();
                    method public void systemMethodHiddenInSub();
                    method public void systemMethodNotOverridden();
                    method public void systemMethodOverridden();
                  }
                  public class PublicSubClass extends test.pkg.PublicClass {
                    method public void abstractMethodOverridden();
                    method public void subMethod();
                  }
                }
                """
        )
    }

    @Test
    fun `Hierarchy test - Module API only`() {
        check(
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            hideAnnotations = arrayOf(HIDE_ANNOTATION),
            showAnnotations = arrayOf(MODULE_API),
            showForStubPurposesAnnotations = arrayOf(SYSTEM_API),
            sourceFiles = SOURCE_FILES_6_7_8_9,
            api =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ModuleClass {
                    ctor public ModuleClass();
                    method public void foo();
                  }
                  public class ModuleClassExtendingPublic extends test.pkg.PublicSubClass {
                    ctor public ModuleClassExtendingPublic();
                    method public void bar();
                  }
                }
                """
        )
    }

    @Test
    fun `Hierarchy test - Module API only with lint`() {
        check(
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            hideAnnotations = arrayOf(HIDE_ANNOTATION),
            showAnnotations = arrayOf(MODULE_API),
            showForStubPurposesAnnotations = arrayOf(SYSTEM_API),

            // The methods are missing nullability anotations, the lint shouldn't report for
            // the SystemApi one.
            sourceFiles =
                arrayOf(
                    java(
                        """
                package test.pkg;

                @test.annotation.Hide
                @test.annotation.SystemApi
                public class ModuleClassExtendingPublic extends PublicSubClass {
                    public Object method1() {
                    }

                    @test.annotation.Hide
                    @test.annotation.ModuleApi
                    public Object method2() {
                    }
                }
            """
                    )
                ),
            api =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class ModuleClassExtendingPublic {
                    method public Object method2();
                  }
                }
                """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                src/test/pkg/ModuleClassExtendingPublic.java:11: error: Missing nullability on method `method2` return [MissingNullability]
                """
        )
    }
}
