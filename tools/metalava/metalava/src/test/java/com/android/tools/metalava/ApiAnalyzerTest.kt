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

import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class ApiAnalyzerTest : DriverTest() {
    @Test
    fun `Hidden abstract method with show @SystemApi`() {
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            expectedIssues =
                """
                src/test/pkg/SystemApiClass.java:7: error: badAbstractHiddenMethod cannot be hidden and abstract when SystemApiClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                src/test/pkg/PublicClass.java:5: error: badAbstractHiddenMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                src/test/pkg/PublicClass.java:6: error: badPackagePrivateMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    public abstract class PublicClass {
                        /** @hide */
                        public abstract boolean badAbstractHiddenMethod() { return true; }
                        abstract void badPackagePrivateMethod() { }
                        /**
                         * This method does not fail because it is visible due to showAnnotations,
                         * instead it will fail when running analysis on public API. See test below.
                         * @hide
                         */
                        @SystemApi
                        public abstract boolean goodAbstractSystemHiddenMethod() { return true; }
                    }
                """
                    ),
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    public abstract class PublicClassWithHiddenConstructor {
                        private PublicClassWithHiddenConstructor() { }
                        /** @hide */
                        public abstract boolean goodAbstractHiddenMethod() { return true; }
                    }
                """
                    ),
                    java(
                        """
                   package test.pkg;
                   import android.annotation.SystemApi;
                   /** @hide */
                   @SystemApi
                   public abstract class SystemApiClass {
                        /** @hide */
                        public abstract boolean badAbstractHiddenMethod() { return true; }
                        /**
                         * This method is OK, because it matches visibility of the class
                         * @hide
                         */
                        @SystemApi
                        public abstract boolean goodAbstractSystemHiddenMethod() { return true; }
                        public abstract boolean goodAbstractPublicMethod() { return true; }
                   }
               """
                    ),
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    /** This class is OK because it is all hidden @hide */
                    public abstract class HiddenClass {
                        public abstract boolean goodAbstractHiddenMethod() { return true; }
                    }
                """
                    ),
                    systemApiSource
                )
        )
    }

    @Test
    fun `Hidden abstract method for public API`() {
        check(
            expectedIssues =
                """
                src/test/pkg/PublicClass.java:5: error: badAbstractHiddenMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                src/test/pkg/PublicClass.java:6: error: badPackagePrivateMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
                src/test/pkg/PublicClass.java:9: error: badAbstractSystemHiddenMethod cannot be hidden and abstract when PublicClass has a visible constructor, in case a third-party attempts to subclass it. [HiddenAbstractMethod]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    public abstract class PublicClass {
                        /** @hide */
                        public abstract boolean badAbstractHiddenMethod() { return true; }
                        abstract void badPackagePrivateMethod() { }
                        /** @hide */
                        @SystemApi
                        public abstract boolean badAbstractSystemHiddenMethod() { return true; }
                    }
                """
                    ),
                    systemApiSource
                )
        )
    }

    @Test
    fun `Deprecation mismatch check look at inherited docs for overriding methods`() {
        check(
            expectedIssues =
                """
                src/test/pkg/MyClass.java:20: error: Method test.pkg.MyClass.inheritedNoCommentInParent(): @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
                src/test/pkg/MyClass.java:23: error: Method test.pkg.MyClass.notInheritedNoComment(): @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
                src/test/pkg/MyInterface.java:17: error: Method test.pkg.MyInterface.inheritedNoCommentInParent(): @Deprecated annotation (present) and @deprecated doc tag (not present) do not match [DeprecationMismatch]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface MyInterface {
                                /** @deprecated Use XYZ instead. */
                                @Deprecated
                                void inheritedNoComment();

                                /** @deprecated Use XYZ instead. */
                                @Deprecated
                                void inheritedWithComment();

                                /** @deprecated Use XYZ instead. */
                                @Deprecated
                                void inheritedWithInheritDoc();

                                @Deprecated
                                void inheritedNoCommentInParent();
                            }
                            """,
                    ),
                    java(
                        """
                            package test.pkg;

                            public class MyClass implements MyInterface {
                                @Deprecated
                                @Override
                                public void inheritedNoComment() {}

                                /** @deprecated Use XYZ instead. */
                                @Deprecated
                                @Override
                                public void inheritedWithComment() {}

                                /** {@inheritDoc} */
                                @Deprecated
                                @Override
                                public void inheritedWithInheritDoc() {}

                                @Deprecated
                                @Override
                                public void inheritedNoCommentInParent() {}

                                @Deprecated
                                public void notInheritedNoComment() {}
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test that usage of a hidden class as type parameter of an outer class is flagged`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:3: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden1 [ReferencesHidden]
                src/test/pkg/Foo.java:4: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden2 [ReferencesHidden]
                src/test/pkg/Foo.java:5: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden3 [ReferencesHidden]
                src/test/pkg/Foo.java:6: error: Class test.pkg.Hidden is hidden but was referenced (in field type) from public field test.pkg.Foo.fieldReferencesHidden4 [ReferencesHidden]
                src/test/pkg/Foo.java:3: warning: Field Foo.fieldReferencesHidden1 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                src/test/pkg/Foo.java:4: warning: Field Foo.fieldReferencesHidden2 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                src/test/pkg/Foo.java:5: warning: Field Foo.fieldReferencesHidden3 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
                src/test/pkg/Foo.java:6: warning: Field Foo.fieldReferencesHidden4 references hidden type test.pkg.Hidden. [HiddenTypeParameter]
            """
                    .trimIndent(),
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        /** @hide */
                        public class Hidden {}
                    """
                            .trimIndent()
                    ),
                    java(
                        """
                        package test.pkg;
                        public class Outer<P1> {
                            public class Inner<P2> {}
                        }
                    """
                            .trimIndent()
                    ),
                    java(
                        """
                        package test.pkg;
                        public class Foo {
                            public Hidden fieldReferencesHidden1;
                            public Outer<Hidden> fieldReferencesHidden2;
                            public Outer<Foo>.Inner<Hidden> fieldReferencesHidden3;
                            public Outer<Hidden>.Inner<Foo> fieldReferencesHidden4;
                        }
                    """
                            .trimIndent()
                    )
                )
        )
    }

    @Test
    fun `Test inheriting methods from hidden class preserves deprecated status`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            class Hidden {
                                /** @deprecated */
                                public <T> void foo(@Deprecated T t) {}

                                /** @deprecated */
                                public void bar() {}

                                public void baz(@Deprecated int i) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public class Concrete extends Hidden<String> {
                            }
                        """
                    ),
                ),
            format = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Concrete {
                        ctor public Concrete();
                        method @Deprecated public void bar();
                        method public void baz(@Deprecated int);
                        method @Deprecated public <T> void foo(@Deprecated T);
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Concrete {
                            public Concrete() { throw new RuntimeException("Stub!"); }
                            /** @deprecated */
                            @Deprecated
                            public void bar() { throw new RuntimeException("Stub!"); }
                            /** @deprecated */
                            @Deprecated
                            public <T> void foo(@Deprecated T t) { throw new RuntimeException("Stub!"); }
                            public void baz(@Deprecated int i) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test inheriting methods from hidden generic class preserves deprecated status`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            class Hidden<T> {
                                /** @deprecated */
                                public void foo(@Deprecated T t) {}

                                /** @deprecated */
                                public void bar() {}

                                public void baz(@Deprecated int i) {}
                            }

                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public class Concrete extends Hidden<String> {
                            }
                        """
                    ),
                ),
            format = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Concrete {
                        ctor public Concrete();
                        method @Deprecated public void bar();
                        method public void baz(@Deprecated int);
                        method @Deprecated public void foo(@Deprecated String);
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Concrete {
                            public Concrete() { throw new RuntimeException("Stub!"); }
                            /** @deprecated */
                            @Deprecated
                            public void bar() { throw new RuntimeException("Stub!"); }
                            /** @deprecated */
                            @Deprecated
                            public void foo(@Deprecated java.lang.String t) { throw new RuntimeException("Stub!"); }
                            public void baz(@Deprecated int i) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test deprecated class and parameters are output in kotlin`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            @Deprecated
                            class Foo(
                                @Deprecated var i: Int,
                                @Deprecated var b: Boolean,
                            )
                        """
                    ),
                ),
            format = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @Deprecated public final class Foo {
                        ctor @Deprecated public Foo(@Deprecated int i, @Deprecated boolean b);
                        method @Deprecated public boolean getB();
                        method @Deprecated public int getI();
                        method @Deprecated public void setB(boolean);
                        method @Deprecated public void setI(int);
                        property @Deprecated public final boolean b;
                        property @Deprecated public final int i;
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test inherited method from hidden class into deprecated class inherits deprecated status`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            class Hidden {
                                public void bar() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            /** @deprecated */
                            public class Concrete extends Hidden<String> {
                            }
                        """
                    ),
                ),
            format = FileFormat.V2,
            api =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @Deprecated public class Concrete {
                        ctor @Deprecated public Concrete();
                        method @Deprecated public void bar();
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @deprecated */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @Deprecated
                            public class Concrete {
                            @Deprecated
                            public Concrete() { throw new RuntimeException("Stub!"); }
                            @Deprecated
                            public void bar() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test deprecated status not propagated to removed items`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            /**
                             * @deprecated
                             * @removed
                             */
                            public class Concrete {
                                public void bar() {}
                            }
                        """
                    ),
                ),
            format = FileFormat.V2,
            api = """
                    // Signature format: 2.0
                """,
            removedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @Deprecated public class Concrete {
                        ctor public Concrete();
                        method public void bar();
                      }
                    }
                """,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test warnings for usage of hidden interface type`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            /** @suppress */
                            interface HiddenInterface
                            class PublicClass {
                                fun returnsHiddenInterface(): HiddenInterface = TODO()
                            }
                        """
                    )
                ),
            api =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class PublicClass {
                        ctor public PublicClass();
                        method public test.pkg.HiddenInterface returnsHiddenInterface();
                      }
                    }
                """,
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/HiddenInterface.kt:5: error: Class test.pkg.HiddenInterface is hidden but was referenced (in return type) from public method test.pkg.PublicClass.returnsHiddenInterface() [ReferencesHidden]
                    src/test/pkg/HiddenInterface.kt:5: warning: Return type of unavailable type test.pkg.HiddenInterface in test.pkg.PublicClass.returnsHiddenInterface() [UnavailableSymbol]
                    src/test/pkg/HiddenInterface.kt:5: warning: Method test.pkg.PublicClass.returnsHiddenInterface() references hidden type test.pkg.HiddenInterface. [HiddenTypeParameter]
                """,
        )
    }

    @Test
    fun `Test PrivateSuperclass for inner class`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        public class Container {
                            private class PrivateInnerClass {}
                            public class PublicInnerClass extends PrivateInnerClass {}
                        }
                    """
                            .trimIndent()
                    )
                ),
            api =
                """
                package test.pkg {
                  public class Container {
                    ctor public Container();
                  }
                  public class Container.PublicInnerClass {
                    ctor public Container.PublicInnerClass();
                  }
                }
            """
                    .trimIndent(),
            expectedIssues =
                "src/test/pkg/Container.java:4: warning: Public class test.pkg.Container.PublicInnerClass extends private class test.pkg.Container.PrivateInnerClass [PrivateSuperclass]"
        )
    }

    @Test
    fun `Test references deprecated errors do not apply to inner class of deprecated class`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        /** @deprecated */
                        @Deprecated
                        public class DeprecatedOuterClass {
                            public class EffectivelyDeprecatedInnerClass extends DeprecatedOuterClass {
                                public void usesDeprecatedOuterClass(DeprecatedOuterClass doc) {}
                            }
                        }
                    """
                    ),
                    java(
                        """
                        package test.pkg;
                        public class NotDeprecatedClass extends DeprecatedOuterClass {
                            public void usesDeprecatedOuterClass(DeprecatedOuterClass doc) {}
                        }
                    """
                    )
                ),
            api =
                """
                    package test.pkg {
                      @Deprecated public class DeprecatedOuterClass {
                        ctor @Deprecated public DeprecatedOuterClass();
                      }
                      @Deprecated public class DeprecatedOuterClass.EffectivelyDeprecatedInnerClass extends test.pkg.DeprecatedOuterClass {
                        ctor @Deprecated public DeprecatedOuterClass.EffectivelyDeprecatedInnerClass();
                        method @Deprecated public void usesDeprecatedOuterClass(test.pkg.DeprecatedOuterClass!);
                      }
                      public class NotDeprecatedClass extends test.pkg.DeprecatedOuterClass {
                        ctor public NotDeprecatedClass();
                        method public void usesDeprecatedOuterClass(test.pkg.DeprecatedOuterClass!);
                      }
                    }
                """,
            extraArguments =
                arrayOf(ARG_ERROR, "ReferencesDeprecated", ARG_ERROR, "ExtendsDeprecated"),
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/NotDeprecatedClass.java:3: error: Parameter references deprecated type test.pkg.DeprecatedOuterClass in test.pkg.NotDeprecatedClass.usesDeprecatedOuterClass(): this method should also be deprecated [ReferencesDeprecated]
                    src/test/pkg/NotDeprecatedClass.java:2: error: Extending deprecated super class class test.pkg.DeprecatedOuterClass from test.pkg.NotDeprecatedClass: this class should also be deprecated [ExtendsDeprecated]
                """,
        )
    }

    @Test
    fun `Test that usage of effectively deprecated class is flagged`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        /** @deprecated */
                        @Deprecated
                        public class DeprecatedOuterClass {
                            public class EffectivelyDeprecatedInnerClass {}
                        }
                    """
                    ),
                    java(
                        """
                        package test.pkg;
                        public class NotDeprecatedClass extends DeprecatedOuterClass.EffectivelyDeprecatedInnerClass {
                            public void usesEffectivelyDeprecatedInnerClass(DeprecatedOuterClass.EffectivelyDeprecatedInnerClass edic) {}
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  @Deprecated public class DeprecatedOuterClass {
                    ctor @Deprecated public DeprecatedOuterClass();
                  }
                  @Deprecated public class DeprecatedOuterClass.EffectivelyDeprecatedInnerClass {
                    ctor @Deprecated public DeprecatedOuterClass.EffectivelyDeprecatedInnerClass();
                  }
                  public class NotDeprecatedClass extends test.pkg.DeprecatedOuterClass.EffectivelyDeprecatedInnerClass {
                    ctor public NotDeprecatedClass();
                    method public void usesEffectivelyDeprecatedInnerClass(test.pkg.DeprecatedOuterClass.EffectivelyDeprecatedInnerClass!);
                  }
                }
            """,
            extraArguments =
                arrayOf(ARG_ERROR, "ReferencesDeprecated", ARG_ERROR, "ExtendsDeprecated"),
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                src/test/pkg/NotDeprecatedClass.java:3: error: Parameter references deprecated type test.pkg.DeprecatedOuterClass.EffectivelyDeprecatedInnerClass in test.pkg.NotDeprecatedClass.usesEffectivelyDeprecatedInnerClass(): this method should also be deprecated [ReferencesDeprecated]
                src/test/pkg/NotDeprecatedClass.java:3: error: Parameter references deprecated type test.pkg.DeprecatedOuterClass in test.pkg.NotDeprecatedClass.usesEffectivelyDeprecatedInnerClass(): this method should also be deprecated [ReferencesDeprecated]
                src/test/pkg/NotDeprecatedClass.java:2: error: Extending deprecated super class class test.pkg.DeprecatedOuterClass.EffectivelyDeprecatedInnerClass from test.pkg.NotDeprecatedClass: this class should also be deprecated [ExtendsDeprecated]
            """,
        )
    }

    @Test
    fun `Test usage of deprecated type `() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @deprecated */
                            @Deprecated
                            public class DeprecatedClass {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import java.util.List;
                            public class NotDeprecatedClass {
                                public List<DeprecatedClass> usesDeprecated(List<DeprecatedClass> list) {
                                    return list;
                                }
                            }
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  @Deprecated public class DeprecatedClass {
                    ctor @Deprecated public DeprecatedClass();
                  }
                  public class NotDeprecatedClass {
                    ctor public NotDeprecatedClass();
                    method public java.util.List<test.pkg.DeprecatedClass!>! usesDeprecated(java.util.List<test.pkg.DeprecatedClass!>!);
                  }
                }
            """
                    .trimIndent(),
            extraArguments = arrayOf(ARG_ERROR, "ReferencesDeprecated"),
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                src/test/pkg/NotDeprecatedClass.java:4: error: Return type references deprecated type test.pkg.DeprecatedClass in test.pkg.NotDeprecatedClass.usesDeprecated(): this method should also be deprecated [ReferencesDeprecated]
                src/test/pkg/NotDeprecatedClass.java:4: error: Parameter references deprecated type test.pkg.DeprecatedClass in test.pkg.NotDeprecatedClass.usesDeprecated(): this method should also be deprecated [ReferencesDeprecated]
            """
                    .trimIndent()
        )
    }
}
