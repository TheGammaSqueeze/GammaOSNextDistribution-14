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

package com.android.tools.metalava.compatibility

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.ANDROID_SYSTEM_API
import com.android.tools.metalava.ARG_HIDE_PACKAGE
import com.android.tools.metalava.ARG_SHOW_ANNOTATION
import com.android.tools.metalava.ARG_SHOW_UNANNOTATED
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.androidxNonNullSource
import com.android.tools.metalava.cli.common.ARG_ERROR_CATEGORY
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.ApiClassResolution
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.nonNullSource
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.restrictToSource
import com.android.tools.metalava.suppressLintSource
import com.android.tools.metalava.systemApiSource
import com.android.tools.metalava.testApiSource
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class CompatibilityCheckTest : DriverTest() {
    @Test
    fun `Change between class and interface`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.MyTest1 changed class/interface declaration [ChangedClass]
                load-api.txt:5: error: Class test.pkg.MyTest2 changed class/interface declaration [ChangedClass]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MyTest1 {
                  }
                  public interface MyTest2 {
                  }
                  public class MyTest3 {
                  }
                  public interface MyTest4 {
                  }
                }
                """,
            // MyTest1 and MyTest2 reversed from class to interface or vice versa, MyTest3 and
            // MyTest4 unchanged
            signatureSource =
                """
                package test.pkg {
                  public interface MyTest1 {
                  }
                  public class MyTest2 {
                  }
                  public class MyTest3 {
                  }
                  public interface MyTest4 {
                  }
                }
                """
        )
    }

    @Test
    fun `Interfaces should not be dropped`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.MyTest1 changed class/interface declaration [ChangedClass]
                load-api.txt:5: error: Class test.pkg.MyTest2 changed class/interface declaration [ChangedClass]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MyTest1 {
                  }
                  public interface MyTest2 {
                  }
                  public class MyTest3 {
                  }
                  public interface MyTest4 {
                  }
                }
                """,
            // MyTest1 and MyTest2 reversed from class to interface or vice versa, MyTest3 and
            // MyTest4 unchanged
            signatureSource =
                """
                package test.pkg {
                  public interface MyTest1 {
                  }
                  public class MyTest2 {
                  }
                  public class MyTest3 {
                  }
                  public interface MyTest4 {
                  }
                }
                """
        )
    }

    @Test
    fun `Ensure warnings for removed APIs`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed method test.pkg.MyTest1.method(Float) [RemovedMethod]
                released-api.txt:5: error: Removed field test.pkg.MyTest1.field [RemovedField]
                released-api.txt:7: error: Removed class test.pkg.MyTest2 [RemovedClass]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MyTest1 {
                    method public Double method(Float);
                    field public Double field;
                  }
                  public class MyTest2 {
                    method public Double method(Float);
                    field public Double field;
                  }
                }
                package test.pkg.other {
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public class MyTest1 {
                  }
                }
                """
        )
    }

    @Test
    fun `Flag invalid nullness changes`() {
        check(
            expectedIssues =
                """
                load-api.txt:6: error: Attempted to remove @Nullable annotation from method test.pkg.MyTest.convert3(Float) [InvalidNullConversion]
                load-api.txt:6: error: Attempted to remove @Nullable annotation from parameter arg1 in test.pkg.MyTest.convert3(Float arg1) [InvalidNullConversion]
                load-api.txt:7: error: Attempted to remove @NonNull annotation from method test.pkg.MyTest.convert4(Float) [InvalidNullConversion]
                load-api.txt:7: error: Attempted to remove @NonNull annotation from parameter arg1 in test.pkg.MyTest.convert4(Float arg1) [InvalidNullConversion]
                load-api.txt:8: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter arg1 in test.pkg.MyTest.convert5(Float arg1) [InvalidNullConversion]
                load-api.txt:9: error: Attempted to change method return from @NonNull to @Nullable: incompatible change for method test.pkg.MyTest.convert6(Float) [InvalidNullConversion]
                """,
            format = FileFormat.V2,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MyTest {
                    method public Double convert1(Float);
                    method public Double convert2(Float);
                    method @Nullable public Double convert3(@Nullable Float);
                    method @NonNull public Double convert4(@NonNull Float);
                    method @Nullable public Double convert5(@Nullable Float);
                    method @NonNull public Double convert6(@NonNull Float);
                    // booleans cannot reasonably be annotated with @Nullable/@NonNull but
                    // the compiler accepts it and we had a few of these accidentally annotated
                    // that way in API 28, such as Boolean.getBoolean. Make sure we don't flag
                    // these as incompatible changes when they're dropped.
                    method public void convert7(@NonNull boolean);
                  }
                }
                """,
            // Changes: +nullness, -nullness, nullable->nonnull, nonnull->nullable
            signatureSource =
                """
                package test.pkg {
                  public class MyTest {
                    method @Nullable public Double convert1(@Nullable Float);
                    method @NonNull public Double convert2(@NonNull Float);
                    method public Double convert3(Float);
                    method public Double convert4(Float);
                    method @NonNull public Double convert5(@NonNull Float);
                    method @Nullable public Double convert6(@Nullable Float);
                    method public void convert7(boolean);
                  }
                }
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Kotlin Nullness`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Outer.kt:5: error: Attempted to change method return from @NonNull to @Nullable: incompatible change for method test.pkg.Outer.method2(String,String) [InvalidNullConversion]
                src/test/pkg/Outer.kt:5: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter string in test.pkg.Outer.method2(String string, String maybeString) [InvalidNullConversion]
                src/test/pkg/Outer.kt:6: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter string in test.pkg.Outer.method3(String maybeString, String string) [InvalidNullConversion]
                src/test/pkg/Outer.kt:8: error: Attempted to change method return from @NonNull to @Nullable: incompatible change for method test.pkg.Outer.Inner.method2(String,String) [InvalidNullConversion]
                src/test/pkg/Outer.kt:8: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter string in test.pkg.Outer.Inner.method2(String string, String maybeString) [InvalidNullConversion]
                src/test/pkg/Outer.kt:9: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter string in test.pkg.Outer.Inner.method3(String maybeString, String string) [InvalidNullConversion]
                """,
            format = FileFormat.V2,
            checkCompatibilityApiReleased =
                """
                    // Signature format: 3.0
                    package test.pkg {
                      public final class Outer {
                        ctor public Outer();
                        method public final String? method1(String, String?);
                        method public final String method2(String?, String);
                        method public final String? method3(String, String?);
                      }
                      public static final class Outer.Inner {
                        ctor public Outer.Inner();
                        method public final String method2(String?, String);
                        method public final String? method3(String, String?);
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    class Outer {
                        fun method1(string: String, maybeString: String?): String? = null
                        fun method2(string: String, maybeString: String?): String? = null
                        fun method3(maybeString: String?, string : String): String = ""
                        class Inner {
                            fun method2(string: String, maybeString: String?): String? = null
                            fun method3(maybeString: String?, string : String): String = ""
                        }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Kotlin Coroutines`() {
        check(
            expectedIssues = "",
            format = FileFormat.V2,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package test.pkg {
                  public final class TestKt {
                    ctor public TestKt();
                    method public static suspend inline java.lang.Object hello(kotlin.coroutines.experimental.Continuation<? super kotlin.Unit>);
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 3.0
                package test.pkg {
                  public final class TestKt {
                    ctor public TestKt();
                    method public static suspend inline Object hello(@NonNull kotlin.coroutines.Continuation<? super kotlin.Unit> p);
                  }
                }
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Remove operator`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.kt:4: error: Cannot remove `operator` modifier from method test.pkg.Foo.plus(String): Incompatible change [OperatorRemoval]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method public final operator void plus(String s);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    class Foo {
                        fun plus(s: String) { }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Remove vararg`() {
        check(
            expectedIssues =
                """
                src/test/pkg/test.kt:3: error: Changing from varargs to array is an incompatible change: parameter x in test.pkg.TestKt.method2(int[] x) [VarargRemoval]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class TestKt {
                    method public static final void method1(int[] x);
                    method public static final void method2(int... x);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg
                    fun method1(vararg x: Int) { }
                    fun method2(x: IntArray) { }
                    """
                    )
                )
        )
    }

    @Test
    fun `Removed method from classpath`() {
        check(
            apiClassResolution = ApiClassResolution.API_CLASSPATH,
            classpath =
                arrayOf(
                    /* The following source file, compiled, then ran
                    assertEquals("", toBase64gzip(File("path/to/lib1.jar")))

                        package test.pkg;

                        public interface FacetProvider {
                          Object getFacet(Class<?> facetClass);
                        }
                     */
                    base64gzip(
                        "libs/lib1.jar",
                        "" +
                            "H4sIAAAAAAAA/wvwZmYRYeDg4GDwKMgPZ0ACnAwsDL6uIY66nn5u+v9OMTAw" +
                            "MwR4s3OApJigSgJwahYBYrhmX0c/TzfX4BA9X7fPvmdO+3jr6l3k9dbVOnfm" +
                            "/OYggyvGD54W6Xn56nj6XixdxcI147XkC0nNjB/iqmrPl2hZPBcXfSKuOo1B" +
                            "NPtT0cciRrAbRCZqCTgBbXBCcYMamhtkgLgktbhEvyA7Xd8tMTm1JKAovywz" +
                            "JbVILzknsbjY+mv+dTs2NrZotrwyNjU3to2TrjwS+tv0pqR2+5EnVyY1LPqz" +
                            "6cyUK0plbGJubI1rjmxy+TvnyJ6S2v9L1lx5IuTG1vflitAGJze2UF75lj3F" +
                            "fkmFG7cu49/Fl+3Gdu7BmS97jky6tCjEjY2Xx9YsquzG1kLW59PFVJfvSn3G" +
                            "8JVzoYUf/5I5vRMbJzbOZGSRaMxLTU1g/nSz0UaNjU+hW/jMIyawN6/4uhXN" +
                            "BXriHdibjEwiDKiBDYsGUEyhApR4Q9eKHHoiKNpsccQasgmgUEZ2mAyKCQcJ" +
                            "hHmANysbSB0zEB4H0isYQTwAofA0RIUCAAA="
                    ),
                    /* The following source file, compiled, then ran
                    assertEquals("", toBase64gzip(File("path/to/lib2.jar")))

                        package test.pkg;

                        public interface FacetProviderAdapter {
                          FacetProvider getFacetProvider(int type);
                        }
                     */
                    base64gzip(
                        "libs/lib2.jar",
                        "" +
                            "H4sIAAAAAAAA/wvwZmYRYeDg4GDwK8gPZ0ACnAwsDL6uIY66nn5u+v9OMTAw" +
                            "MwR4s3OApJigSgJwahYBYrhmX0c/TzfX4BA9X7fPvmdO+3jr6l3k9dbVOnfm" +
                            "/OYggyvGD54W6Xn56nj6XixdxcI147XkC0nNjB/iqmrPl2hZPBcXfSKuOo1B" +
                            "NPtT0cciRrAbRCZqCTgBbXBCcYMamhuUgbgktbhEvyA7Xd8tMTm1JKAovywz" +
                            "JbXIMSWxoCS1SC85J7G42Ppr/nU7Nja2aDa/MjY1N7abk648Evrb9KakdvuR" +
                            "J1cmNSz6s+nMlCtKx6ccaZp0RamMTcyNrXHNkU0uf+cc2VNS+3/JmitPhBYE" +
                            "VGVxdlW5sWXy+vvKv2mLNDYqYH0+XUx1+a7UZ0uMjDySNnvxp3BLKzMrMxsz" +
                            "cxgw5aalJjBvlLjRqCLMzA72E+ufzUeagS7eDfYTI5MIA2rIwsIcFC2oACWS" +
                            "0LUiB5UIijZbHFGEbAIoSJEdpoxiwkHiAjjAm5UNpJwZCM8B6amMIB4AmZLm" +
                            "53kCAAA="
                    ),
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                          package test.pkg;

                          public class FacetProviderAdapterImpl implements FacetProviderAdapter {
                            private FacetProvider mProvider;
                            @Override
                            public FacetProvider getFacetProvider(int type) {
                                return mProvider;
                            }

                            public static class FacetProviderImpl implements FacetProvider {
                              private Object mItem;
                              @Override
                              public Object getFacet(Class<?> facetClass) {
                                  return mItem;
                              }
                            }
                          }
                        """
                    )
                ),
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package test.pkg {
                  public interface FacetProvider {
                    method public Object! getFacet(Class<?>!);
                  }
                  public interface FacetProviderAdapter {
                    method public test.pkg.FacetProvider! getFacetProvider(int);
                  }
                  public class FacetProviderAdapterImpl implements test.pkg.FacetProviderAdapter {
                    method public test.pkg.FacetProvider? getFacetProvider(int);
                  }
                  public class FacetProviderAdapterImpl.FacetProviderImpl implements test.pkg.FacetProvider {
                    method public Object? getFacet(Class<?>?);
                  }
                }
                """,
            expectedIssues =
                """
                released-api.txt:3: error: Removed class test.pkg.FacetProvider [RemovedInterface]
                released-api.txt:6: error: Removed class test.pkg.FacetProviderAdapter [RemovedInterface]
                src/test/pkg/FacetProviderAdapterImpl.java:6: error: Attempted to remove @Nullable annotation from method test.pkg.FacetProviderAdapterImpl.getFacetProvider(int) [InvalidNullConversion]
                src/test/pkg/FacetProviderAdapterImpl.java:13: error: Attempted to remove @Nullable annotation from method test.pkg.FacetProviderAdapterImpl.FacetProviderImpl.getFacet(Class<?>) [InvalidNullConversion]
                src/test/pkg/FacetProviderAdapterImpl.java:13: error: Attempted to remove @Nullable annotation from parameter facetClass in test.pkg.FacetProviderAdapterImpl.FacetProviderImpl.getFacet(Class<?> facetClass) [InvalidNullConversion]
            """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Add final to class that can be extended`() {
        // Adding final on a class is incompatible.
        check(
            // Make AddedFinalInstantiable an error, so it is reported as an issue.
            extraArguments = arrayOf("--error", Issues.ADDED_FINAL_UNINSTANTIABLE.name),
            expectedIssues =
                """
                src/test/pkg/Java.java:2: error: Class test.pkg.Java added 'final' qualifier [AddedFinal]
                src/test/pkg/Java.java:3: error: Constructor test.pkg.Java has added 'final' qualifier [AddedFinal]
                src/test/pkg/Java.java:4: error: Method test.pkg.Java.method has added 'final' qualifier [AddedFinal]
                src/test/pkg/Kotlin.kt:3: error: Class test.pkg.Kotlin added 'final' qualifier [AddedFinal]
                src/test/pkg/Kotlin.kt:3: error: Constructor test.pkg.Kotlin has added 'final' qualifier [AddedFinal]
                src/test/pkg/Kotlin.kt:4: error: Method test.pkg.Kotlin.method has added 'final' qualifier [AddedFinal]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Java {
                    ctor public Java();
                    method public void method(int);
                  }
                  public class Kotlin {
                    ctor public Kotlin();
                    method public void method(String s);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    class Kotlin {
                        fun method(s: String) { }
                    }
                    """
                    ),
                    java(
                        """
                        package test.pkg;
                        public final class Java {
                            public Java() { }
                            public void method(int parameter) { }
                        }
                        """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Add final to class that cannot be extended`() {
        // Adding final on a class is incompatible unless the class could not be extended.
        check(
            // Make AddedFinalInstantiable an error, so it is reported as an issue.
            extraArguments = arrayOf("--error", Issues.ADDED_FINAL_UNINSTANTIABLE.name),
            expectedIssues =
                """
                src/test/pkg/Java.java:2: error: Class test.pkg.Java added 'final' qualifier but was previously uninstantiable and therefore could not be subclassed [AddedFinalUninstantiable]
                src/test/pkg/Java.java:4: error: Method test.pkg.Java.method added 'final' qualifier but containing class test.pkg.Java was previously uninstantiable and therefore could not be subclassed [AddedFinalUninstantiable]
                src/test/pkg/Kotlin.kt:3: error: Class test.pkg.Kotlin added 'final' qualifier but was previously uninstantiable and therefore could not be subclassed [AddedFinalUninstantiable]
                src/test/pkg/Kotlin.kt:5: error: Method test.pkg.Kotlin.method added 'final' qualifier but containing class test.pkg.Kotlin was previously uninstantiable and therefore could not be subclassed [AddedFinalUninstantiable]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Java {
                    method public void method(int);
                  }
                  public class Kotlin {
                    method public void method(String s);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    class Kotlin
                    private constructor() {
                        fun method(s: String) { }
                    }
                    """
                    ),
                    java(
                        """
                        package test.pkg;
                        public final class Java {
                            private Java() { }
                            public void method(int parameter) { }
                        }
                        """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Add final to method of class that can be extended`() {
        // Adding final on a method is incompatible.
        check(
            // Make AddedFinalInstantiable an error, so it is reported as an issue.
            extraArguments = arrayOf("--error", Issues.ADDED_FINAL_UNINSTANTIABLE.name),
            expectedIssues =
                """
                src/test/pkg/Java.java:4: error: Method test.pkg.Java.method has added 'final' qualifier [AddedFinal]
                src/test/pkg/Kotlin.kt:4: error: Method test.pkg.Kotlin.method has added 'final' qualifier [AddedFinal]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Java {
                    ctor public Java();
                    method public void method(int);
                  }
                  public class Kotlin {
                    ctor public Kotlin();
                    method public void method(String s);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    open class Kotlin {
                        fun method(s: String) { }
                    }
                    """
                    ),
                    java(
                        """
                        package test.pkg;
                        public class Java {
                            public Java() { }
                            public final void method(final int parameter) { }
                        }
                        """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Add final to method of class that cannot be extended`() {
        // Adding final on a method is incompatible unless the containing class could not be
        // extended.
        check(
            // Make AddedFinalInstantiable an error, so it is reported as an issue.
            extraArguments = arrayOf("--error", Issues.ADDED_FINAL_UNINSTANTIABLE.name),
            expectedIssues =
                """
                src/test/pkg/Java.java:4: error: Method test.pkg.Java.method added 'final' qualifier but containing class test.pkg.Java was previously uninstantiable and therefore could not be subclassed [AddedFinalUninstantiable]
                src/test/pkg/Kotlin.kt:5: error: Method test.pkg.Kotlin.method added 'final' qualifier but containing class test.pkg.Kotlin was previously uninstantiable and therefore could not be subclassed [AddedFinalUninstantiable]
            """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Java {
                    method public void method(int);
                  }
                  public class Kotlin {
                    method public void method(String s);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    open class Kotlin
                    private constructor() {
                        fun method(s: String) { }
                    }
                    """
                    ),
                    java(
                        """
                        package test.pkg;
                        public class Java {
                            private Java() { }
                            public final void method(final int parameter) { }
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Add final to method parameter`() {
        // Adding final on a method parameter is fine.
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Java {
                    ctor public Java();
                    method public void method(int);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        public class Java {
                            public Java() { }
                            public void method(final int parameter) { }
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Inherited final`() {
        // Make sure that we correctly compare effectively final (inherited from surrounding class)
        // between the signature file codebase and the real codebase
        check(
            expectedIssues = """
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class Cls extends test.pkg.Parent {
                  }
                  public class Parent {
                    method public void method(int);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        public final class Cls extends Parent {
                            private Cls() { }
                            @Override public void method(final int parameter) { }
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;
                        public class Parent {
                            private Parent() { }
                            public void method(final int parameter) { }
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Implicit concrete`() {
        // Doclava signature files sometimes leave out overridden methods of
        // abstract methods. We don't want to list these as having changed
        // their abstractness.
        check(
            expectedIssues = """
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class Cls extends test.pkg.Parent {
                  }
                  public class Parent {
                    method public abstract void method(int);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        public final class Cls extends Parent {
                            private Cls() { }
                            @Override public void method(final int parameter) { }
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;
                        public class Parent {
                            private Parent() { }
                            public abstract void method(final int parameter);
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Implicit modifiers from inherited super classes`() {
        check(
            expectedIssues = """
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class Cls implements test.pkg.Interface {
                    method public void method(int);
                    method public final void method2(int);
                  }
                  public interface Interface {
                    method public void method2(int);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        public final class Cls extends HiddenParent implements Interface {
                            private Cls() { }
                            @Override public void method(final int parameter) { }
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;
                        class HiddenParent {
                            private HiddenParent() { }
                            public abstract void method(final int parameter) { }
                            public final void method2(final int parameter) { }
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;
                        public interface Interface {
                            void method2(final int parameter) { }
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Wildcard comparisons`() {
        // Doclava signature files sometimes leave out overridden methods of
        // abstract methods. We don't want to list these as having changed
        // their abstractness.
        check(
            expectedIssues = """
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class AbstractMap<K, V> implements java.util.Map {
                    method public java.util.Set<K> keySet();
                    method public V put(K, V);
                    method public void putAll(java.util.Map<? extends K, ? extends V>);
                  }
                  public abstract class EnumMap<K extends java.lang.Enum<K>, V> extends test.pkg.AbstractMap  {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"ConstantConditions", "NullableProblems"})
                        public abstract class AbstractMap<K, V> implements java.util.Map {
                            private AbstractMap() { }
                            public V put(K k, V v) { return null; }
                            public java.util.Set<K> keySet() { return null; }
                            public void putAll(java.util.Map<? extends K, ? extends V> x) { }
                        }
                        """
                    ),
                    java(
                        """
                        package test.pkg;
                        public abstract class EnumMap<K extends java.lang.Enum<K>, V> extends test.pkg.AbstractMap  {
                            private EnumMap() { }
                            public V put(K k, V v) { return null; }
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Added constructor`() {
        // Regression test for issue 116619591
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class AbstractMap<K, V> implements java.util.Map {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"ConstantConditions", "NullableProblems"})
                        public abstract class AbstractMap<K, V> implements java.util.Map {
                        }
                        """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Remove infix`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.kt:5: error: Cannot remove `infix` modifier from method test.pkg.Foo.add2(String): Incompatible change [InfixRemoval]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method public final void add1(String s);
                    method public final infix void add2(String s);
                    method public final infix void add3(String s);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    class Foo {
                        infix fun add1(s: String) { }
                        fun add2(s: String) { }
                        infix fun add3(s: String) { }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Add seal`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.kt:2: error: Cannot add 'sealed' modifier to class test.pkg.Foo: Incompatible change [AddSealed]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg
                    sealed class Foo
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Remove default parameter`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.kt:3: error: Attempted to remove default value from parameter s1 in test.pkg.Foo [DefaultValueChange]
                src/test/pkg/Foo.kt:7: error: Attempted to remove default value from parameter s1 in test.pkg.Foo.method4 [DefaultValueChange]

                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package test.pkg {
                  public final class Foo {
                    ctor public Foo(String? s1 = null);
                    method public final void method1(boolean b, String? s1);
                    method public final void method2(boolean b, String? s1);
                    method public final void method3(boolean b, String? s1 = "null");
                    method public final void method4(boolean b, String? s1 = "null");
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    class Foo(s1: String?) {
                        fun method1(b: Boolean, s1: String?) { }         // No change
                        fun method2(b: Boolean, s1: String? = null) { }  // Adding: OK
                        fun method3(b: Boolean, s1: String? = null) { }  // No change
                        fun method4(b: Boolean, s1: String?) { }         // Removed
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Remove optional parameter`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.kt:3: error: Attempted to remove default value from parameter s1 in test.pkg.Foo [DefaultValueChange]
                src/test/pkg/Foo.kt:7: error: Attempted to remove default value from parameter s1 in test.pkg.Foo.method4 [DefaultValueChange]
                """,
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package test.pkg {
                  public final class Foo {
                    ctor public Foo(optional String? s1);
                    method public final void method1(boolean b, String? s1);
                    method public final void method2(boolean b, String? s1);
                    method public final void method3(boolean b, optional String? s1);
                    method public final void method4(boolean b, optional String? s1);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    class Foo(s1: String?) {                             // Removed
                        fun method1(b: Boolean, s1: String?) { }         // No change
                        fun method2(b: Boolean, s1: String? = null) { }  // Adding: OK
                        fun method3(b: Boolean, s1: String? = null) { }  // No change
                        fun method4(b: Boolean, s1: String?) { }         // Removed
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Removing method or field when still available via inheritance is OK`() {
        check(
            expectedIssues = """
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Child extends test.pkg.Parent {
                    ctor public Child();
                    field public int field1;
                    method public void method1();
                  }
                  public class Parent {
                    ctor public Parent();
                    field public int field1;
                    field public int field2;
                    method public void method1();
                    method public void method2();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Parent {
                        public int field1 = 0;
                        public int field2 = 0;
                        public void method1() { }
                        public void method2() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class Child extends Parent {
                        public int field1 = 0;
                        @Override public void method1() { } // NO CHANGE
                        //@Override public void method2() { } // REMOVED OK: Still inherited
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Change field constant value, change field type`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Parent.java:5: error: Field test.pkg.Parent.field2 has changed value from 2 to 42 [ChangedValue]
                src/test/pkg/Parent.java:6: error: Field test.pkg.Parent.field3 has changed type from int to char [ChangedType]
                src/test/pkg/Parent.java:7: error: Field test.pkg.Parent.field4 has added 'final' qualifier [AddedFinal]
                src/test/pkg/Parent.java:8: error: Field test.pkg.Parent.field5 has changed 'static' qualifier [ChangedStatic]
                src/test/pkg/Parent.java:10: error: Field test.pkg.Parent.field7 has changed 'volatile' qualifier [ChangedVolatile]
                src/test/pkg/Parent.java:20: error: Field test.pkg.Parent.field94 has changed value from 1 to 42 [ChangedValue]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Parent {
                    ctor public Parent();
                    field public static final int field1 = 1; // 0x1
                    field public static final int field2 = 2; // 0x2
                    field public int field3;
                    field public int field4 = 4; // 0x4
                    field public int field5;
                    field public int field6;
                    field public int field7;
                    field public deprecated int field8;
                    field public int field9;
                    field public static final int field91 = 1; // 0x1
                    field public static final int field92 = 1; // 0x1
                    field public static final int field93 = 1; // 0x1
                    field public static final int field94 = 1; // 0x1
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SuppressLint;
                    public class Parent {
                        public static final int field1 = 1;  // UNCHANGED
                        public static final int field2 = 42; // CHANGED VALUE
                        public char field3 = 3;              // CHANGED TYPE
                        public final int field4 = 4;         // ADDED FINAL
                        public static int field5 = 5;        // ADDED STATIC
                        public transient int field6 = 6;     // ADDED TRANSIENT
                        public volatile int field7 = 7;      // ADDED VOLATILE
                        public int field8 = 8;               // REMOVED DEPRECATED
                        /** @deprecated */ @Deprecated public int field9 = 8;  // ADDED DEPRECATED
                        @SuppressLint("ChangedValue")
                        public static final int field91 = 42;// CHANGED VALUE: Suppressed
                        @SuppressLint("ChangedValue:Field test.pkg.Parent.field92 has changed value from 1 to 42")
                        public static final int field92 = 42;// CHANGED VALUE: Suppressed with same message
                        @SuppressLint("ChangedValue: Field test.pkg.Parent.field93 has changed value from 1 to 42")
                        public static final int field93 = 42;// CHANGED VALUE: Suppressed with same message
                        @SuppressLint("ChangedValue:Field test.pkg.Parent.field94 has changed value from 10 to 1")
                        public static final int field94 = 42;// CHANGED VALUE: Suppressed but with different message
                    }
                    """
                    ),
                    suppressLintSource
                ),
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "android.annotation")
        )
    }

    @Test
    fun `Change annotation default method value change`() {
        check(
            expectedIssues =
                """
                src/test/pkg/ExportedProperty.java:15: error: Method test.pkg.ExportedProperty.category has changed value from "" to nothing [ChangedValue]
                src/test/pkg/ExportedProperty.java:14: error: Method test.pkg.ExportedProperty.floating has changed value from 1.0f to 1.1f [ChangedValue]
                src/test/pkg/ExportedProperty.java:13: error: Method test.pkg.ExportedProperty.prefix has changed value from "" to "hello" [ChangedValue]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package test.pkg {
                  public @interface ExportedProperty {
                    method public abstract boolean resolveId() default false;
                    method public abstract float floating() default 1.0f;
                    method public abstract String! prefix() default "";
                    method public abstract String! category() default "";
                    method public abstract boolean formatToHexString();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;
                    import static java.lang.annotation.RetentionPolicy.SOURCE;

                    @Target({ElementType.FIELD, ElementType.METHOD})
                    @Retention(RetentionPolicy.RUNTIME)
                    public @interface ExportedProperty {
                        boolean resolveId() default false;            // UNCHANGED
                        String prefix() default "hello";              // CHANGED VALUE
                        float floating() default 1.1f;                // CHANGED VALUE
                        String category();                            // REMOVED VALUE
                        boolean formatToHexString() default false;    // ADDED VALUE
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Incompatible class change -- class to interface`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Parent.java:3: error: Class test.pkg.Parent changed class/interface declaration [ChangedClass]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Parent {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public interface Parent {
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Incompatible class change -- change implemented interfaces`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Parent.java:3: error: Class test.pkg.Parent no longer implements java.io.Closeable [RemovedInterface]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class Parent implements java.io.Closeable, java.util.Map {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class Parent implements java.util.Map, java.util.List {
                        private Parent() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Incompatible class change -- change qualifiers`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Parent.java:3: error: Class test.pkg.Parent changed 'abstract' qualifier [ChangedAbstract]
                src/test/pkg/Parent.java:3: error: Class test.pkg.Parent changed 'static' qualifier [ChangedStatic]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Parent {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract static class Parent {
                        private Parent() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Incompatible class change -- final`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Class1.java:3: error: Class test.pkg.Class1 added 'final' qualifier [AddedFinal]
                released-api.txt:4: error: Removed constructor test.pkg.Class1() [RemovedMethod]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Class1 {
                      ctor public Class1();
                  }
                  public class Class2 {
                  }
                  public final class Class3 {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public final class Class1 {
                        private Class1() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public final class Class2 {
                        private Class2() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class Class3 {
                        private Class3() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Incompatible class change -- visibility`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Class1.java:3: error: Class test.pkg.Class1 changed visibility from protected to public [ChangedScope]
                src/test/pkg/Class2.java:3: error: Class test.pkg.Class2 changed visibility from public to protected [ChangedScope]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  protected class Class1 {
                  }
                  public class Class2 {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Class1 {
                        private Class1() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    protected class Class2 {
                        private Class2() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Incompatible class change -- superclass`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Class3.java:3: error: Class test.pkg.Class3 superclass changed from java.lang.Char to java.lang.Number [ChangedSuperclass]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class Class1 {
                  }
                  public abstract class Class2 extends java.lang.Number {
                  }
                  public abstract class Class3 extends java.lang.Char {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class Class1 extends java.lang.Short {
                        private Class1() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public abstract class Class2 extends java.lang.Float {
                        private Class2() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public abstract class Class3 extends java.lang.Number {
                        private Class3() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `allow adding first type parameter`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo {
                    }
                }
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Foo<T> {
                    }
                }
            """
        )
    }

    @Test
    fun `disallow removing type parameter`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.Foo changed number of type parameters from 1 to 0 [ChangedType]
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo<T> {
                    }
                }
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Foo {
                    }
                }
            """
        )
    }

    @Test
    fun `disallow changing number of type parameters`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.Foo changed number of type parameters from 1 to 2 [ChangedType]
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo<A> {
                    }
                }
            """,
            signatureSource =
                """
                package test.pkg {
                    public class Foo<A,B> {
                    }
                }
            """
        )
    }

    @Test
    fun `Incompatible method change -- modifiers`() {
        check(
            expectedIssues =
                """
                src/test/pkg/MyClass.java:5: error: Method test.pkg.MyClass.myMethod2 has changed 'abstract' qualifier [ChangedAbstract]
                src/test/pkg/MyClass.java:6: error: Method test.pkg.MyClass.myMethod3 has changed 'static' qualifier [ChangedStatic]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyClass {
                      method public void myMethod2();
                      method public void myMethod3();
                      method deprecated public void myMethod4();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class MyClass {
                        private MyClass() {}
                        public native abstract void myMethod2(); // Note that Errors.CHANGE_NATIVE is hidden by default
                        public static void myMethod3() {}
                        public void myMethod4() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Incompatible method change -- final`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Outer.java:7: error: Method test.pkg.Outer.Class1.method1 has added 'final' qualifier [AddedFinal]
                src/test/pkg/Outer.java:19: error: Method test.pkg.Outer.Class4.method4 has removed 'final' qualifier [RemovedFinalStrict]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class Outer {
                  }
                  public class Outer.Class1 {
                    ctor public Class1();
                    method public void method1();
                  }
                  public final class Outer.Class2 {
                    method public void method2();
                  }
                  public final class Outer.Class3 {
                    method public void method3();
                  }
                  public class Outer.Class4 {
                    method public final void method4();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class Outer {
                        private Outer() {}
                        public class Class1 {
                            public Class1() {}
                            public final void method1() { } // Added final
                        }
                        public final class Class2 {
                            private Class2() {}
                            public final void method2() { } // Added final but class is effectively final so no change
                        }
                        public final class Class3 {
                            private Class3() {}
                            public void method3() { } // Removed final but is still effectively final
                        }
                        public class Class4 {
                            private Class4() {}
                            public void method4() { } // Removed final
                        }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Incompatible method change -- visibility`() {
        check(
            expectedIssues =
                """
                src/test/pkg/MyClass.java:6: error: Method test.pkg.MyClass.myMethod2 changed visibility from public to protected [ChangedScope]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyClass {
                      method protected void myMethod1();
                      method public void myMethod2();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class MyClass {
                        private MyClass() {}
                        public void myMethod1() {}
                        protected void myMethod2() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Incompatible method change -- return types`() {
        check(
            expectedIssues =
                """
                src/test/pkg/MyClass.java:5: error: Method test.pkg.MyClass.method1 has changed return type from float to int [ChangedType]
                src/test/pkg/MyClass.java:6: error: Method test.pkg.MyClass.method2 has changed return type from java.util.List<java.lang.Number> to java.util.List<java.lang.Integer> [ChangedType]
                src/test/pkg/MyClass.java:7: error: Method test.pkg.MyClass.method3 has changed return type from java.util.List<java.lang.Integer> to java.util.List<java.lang.Number> [ChangedType]
                src/test/pkg/MyClass.java:8: error: Method test.pkg.MyClass.method4 has changed return type from java.lang.String to java.lang.String[] [ChangedType]
                src/test/pkg/MyClass.java:9: error: Method test.pkg.MyClass.method5 has changed return type from java.lang.String[] to java.lang.String[][] [ChangedType]
                src/test/pkg/MyClass.java:11: error: Method test.pkg.MyClass.method7 has changed return type from T (extends java.lang.Number) to java.lang.Number [ChangedType]
                src/test/pkg/MyClass.java:13: error: Method test.pkg.MyClass.method9 has changed return type from X (extends java.lang.Throwable) to U (extends java.lang.Number) [ChangedType]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyClass<T extends Number> {
                      method public float method1();
                      method public java.util.List<java.lang.Number> method2();
                      method public java.util.List<java.lang.Integer> method3();
                      method public String method4();
                      method public String[] method5();
                      method public <X extends java.lang.Throwable> T method6(java.util.function.Supplier<? extends X>);
                      method public <X extends java.lang.Throwable> T method7(java.util.function.Supplier<? extends X>);
                      method public <X extends java.lang.Throwable> Number method8(java.util.function.Supplier<? extends X>);
                      method public <X extends java.lang.Throwable> X method9(java.util.function.Supplier<? extends X>);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class MyClass<U extends Number> { // Changing type variable name is fine/compatible
                        private MyClass() {}
                        public int method1() { return 0; }
                        public java.util.List<Integer> method2() { return null; }
                        public java.util.List<Number> method3() { return null; }
                        public String[] method4() { return null; }
                        public String[][] method5() { return null; }
                        public <X extends java.lang.Throwable> U method6(java.util.function.Supplier<? extends X> arg) { return null; }
                        public <X extends java.lang.Throwable> Number method7(java.util.function.Supplier<? extends X> arg) { return null; }
                        public <X extends java.lang.Throwable> U method8(java.util.function.Supplier<? extends X> arg) { return null; }
                        public <X extends java.lang.Throwable> U method9(java.util.function.Supplier<? extends X> arg) { return null; }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Incompatible field change -- visibility and removing final`() {
        check(
            expectedIssues =
                """
                src/test/pkg/MyClass.java:6: error: Field test.pkg.MyClass.myField2 changed visibility from public to protected [ChangedScope]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyClass {
                      field protected int myField1;
                      field public int myField2;
                      field public final int myField3;
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class MyClass {
                        private MyClass() {}
                        public int myField1 = 1;
                        protected int myField2 = 1;
                        public int myField3 = 1;
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Adding classes, interfaces and packages, and removing these`() {
        check(
            expectedIssues =
                """
                released-api.txt:3: error: Removed class test.pkg.MyOldClass [RemovedClass]
                released-api.txt:6: error: Removed package test.pkg3 [RemovedPackage]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyOldClass {
                  }
                }
                package test.pkg3 {
                  public abstract class MyOldClass {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class MyClass {
                        private MyClass() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public interface MyInterface {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg2;

                    public abstract class MyClass2 {
                        private MyClass2() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test removing public constructor`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed constructor test.pkg.MyClass() [RemovedMethod]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyClass {
                    ctor public MyClass();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class MyClass {
                        private MyClass() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test type variables from text signature files`() {
        check(
            expectedIssues =
                """
                src/test/pkg/MyClass.java:8: error: Method test.pkg.MyClass.myMethod4 has changed return type from S (extends java.lang.Object) to S (extends java.lang.Float) [ChangedType]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyClass<T extends test.pkg.Number,T_SPLITR> {
                    method public T myMethod1();
                    method public <S extends test.pkg.Number> S myMethod2();
                    method public <S> S myMethod3();
                    method public <S> S myMethod4();
                    method public java.util.List<byte[]> myMethod5();
                    method public T_SPLITR[] myMethod6();
                    method public String myMethod7();
                  }
                  public class Number {
                    ctor public Number();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class MyClass<T extends Number,T_SPLITR> {
                        private MyClass() {}
                        public T myMethod1() { return null; }
                        public <S extends Number> S myMethod2() { return null; }
                        public <S> S myMethod3() { return null; }
                        public <S extends Float> S myMethod4() { return null; }
                        public java.util.List<byte[]> myMethod5() { return null; }
                        public T_SPLITR[] myMethod6() { return null; }
                        public <S extends String> S myMethod7() { return null; }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class Number {
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test fields with type variable types are correctly parsed as type variables`() {
        check(
            expectedIssues =
                """
                src/test/pkg/MyClass.java:5: error: Field test.pkg.MyClass.myField has changed type from String to java.lang.String [ChangedType]
                """,
            // If MyClass did not have a type parameter named String, myField would be parsed as
            // type java.lang.String
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyClass<String> {
                    field public String myField;
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class MyClass<String> {
                        private MyClass() {}
                        public java.lang.String myField;
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test Kotlin extensions`() {
        check(
            format = FileFormat.V2,
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package androidx.content {
                  public final class ContentValuesKt {
                    method public static android.content.ContentValues contentValuesOf(kotlin.Pair<String,?>... pairs);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        "src/androidx/content/ContentValues.kt",
                        """
                    package androidx.content

                    import android.content.ContentValues

                    fun contentValuesOf(vararg pairs: Pair<String, Any?>) = ContentValues(pairs.size).apply {
                        for ((key, value) in pairs) {
                            when (value) {
                                null -> putNull(key)
                                is String -> put(key, value)
                                is Int -> put(key, value)
                                is Long -> put(key, value)
                                is Boolean -> put(key, value)
                                is Float -> put(key, value)
                                is Double -> put(key, value)
                                is ByteArray -> put(key, value)
                                is Byte -> put(key, value)
                                is Short -> put(key, value)
                                else -> {
                                    val valueType = value.javaClass.canonicalName
                                    throw IllegalArgumentException("Illegal value type")
                                }
                            }
                        }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test Kotlin type bounds`() {
        check(
            format = FileFormat.V2,
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package androidx.navigation {
                  public final class NavDestination {
                    ctor public NavDestination();
                  }
                  public class NavDestinationBuilder<D extends androidx.navigation.NavDestination> {
                    ctor public NavDestinationBuilder(int id);
                    method public D build();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package androidx.navigation

                    open class NavDestinationBuilder<out D : NavDestination>(
                            id: Int
                    ) {
                        open fun build(): D {
                            TODO()
                        }
                    }

                    class NavDestination
                    """
                    )
                )
        )
    }

    @Test
    fun `Test inherited methods`() {
        check(
            expectedIssues = """
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Child1 extends test.pkg.Parent {
                  }
                  public class Child2 extends test.pkg.Parent {
                    method public void method0(java.lang.String, int);
                    method public void method4(java.lang.String, int);
                  }
                  public class Child3 extends test.pkg.Parent {
                    method public void method1(java.lang.String, int);
                    method public void method2(java.lang.String, int);
                  }
                  public class Parent {
                    method public void method1(java.lang.String, int);
                    method public void method2(java.lang.String, int);
                    method public void method3(java.lang.String, int);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Child1 extends Parent {
                        private Child1() {}
                        @Override
                        public void method1(String first, int second) {
                        }
                        @Override
                        public void method2(String first, int second) {
                        }
                        @Override
                        public void method3(String first, int second) {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class Child2 extends Parent {
                        private Child2() {}
                        @Override
                        public void method0(String first, int second) {
                        }
                        @Override
                        public void method1(String first, int second) {
                        }
                        @Override
                        public void method2(String first, int second) {
                        }
                        @Override
                        public void method3(String first, int second) {
                        }
                        @Override
                        public void method4(String first, int second) {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class Child3 extends Parent {
                        private Child3() {}
                        @Override
                        public void method1(String first, int second) {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class Parent {
                        private Parent() { }
                        public void method1(String first, int second) {
                        }
                        public void method2(String first, int second) {
                        }
                        public void method3(String first, int second) {
                        }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Partial text file which references inner classes not listed elsewhere`() {
        // This happens in system and test files where we only include APIs that differ
        // from the base API. When parsing these code bases we need to gracefully handle
        // references to inner classes.
        check(
            includeSystemApiAnnotations = true,
            expectedIssues =
                """
                released-api.txt:5: error: Removed method test.pkg.Bar.Inner1.Inner2.removedMethod() [RemovedMethod]
                """,
            sourceFiles =
                arrayOf(
                    java(
                            """
                    package other.pkg;

                    public class MyClass {
                        public class MyInterface {
                            public void test() { }
                        }
                    }
                    """
                        )
                        .indented(),
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;

                    public class Bar {
                        public class Inner1 {
                            private Inner1() { }
                            @SuppressWarnings("JavaDoc")
                            public class Inner2 {
                                private Inner2() { }

                                /**
                                 * @hide
                                 */
                                @SystemApi
                                public void method() { }

                                /**
                                 * @hide
                                 */
                                @SystemApi
                                public void addedMethod() { }
                            }
                        }
                    }
                    """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Bar.Inner1.Inner2 {
                    method public void method();
                    method public void removedMethod();
                  }
                }
                """
        )
    }

    @Test
    fun `Incompatible Changes in Released System API `() {
        // Incompatible changes to a released System API should be detected
        // In this case removing final and changing value of constant
        check(
            includeSystemApiAnnotations = true,
            expectedIssues =
                """
                src/android/rolecontrollerservice/RoleControllerService.java:8: error: Method android.rolecontrollerservice.RoleControllerService.sendNetworkScore has removed 'final' qualifier [RemovedFinalStrict]
                src/android/rolecontrollerservice/RoleControllerService.java:9: error: Field android.rolecontrollerservice.RoleControllerService.APP_RETURN_UNWANTED has changed value from 1 to 0 [ChangedValue]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.rolecontrollerservice;
                    import android.annotation.SystemApi;

                    /** @hide */
                    @SystemApi
                    public abstract class RoleControllerService {
                        public abstract void onGrantDefaultRoles();
                        public void sendNetworkScore();
                        public static final int APP_RETURN_UNWANTED = 0;
                    }
                    """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.TestApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            checkCompatibilityApiReleased =
                """
                package android.rolecontrollerservice {
                  public abstract class RoleControllerService {
                    ctor public RoleControllerService();
                    method public abstract void onGrantDefaultRoles();
                    method public final void sendNetworkScore();
                    field public static final int APP_RETURN_UNWANTED = 1;
                  }
                }
                """
        )
    }

    @Test
    fun `Incompatible changes to released API signature codebase`() {
        // Incompatible changes to a released System API should be detected
        // in case of partial files
        check(
            expectedIssues =
                """
                released-api.txt:6: error: Removed method test.pkg.Foo.method2() [RemovedMethod]
                """,
            signatureSource =
                """
                // Signature format: 3.0
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method public void method1();
                  }
                }
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method public void method1();
                    method public void method2();
                    method public void method3();
                  }
                }
                """,
            checkCompatibilityBaseApi =
                """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method public void method3();
                  }
                }
                """,
        )
    }

    @Test
    fun `Partial text file which adds methods to show-annotation API`() {
        // This happens in system and test files where we only include APIs that differ
        // from the base IDE. When parsing these code bases we need to gracefully handle
        // references to inner classes.
        check(
            includeSystemApiAnnotations = true,
            expectedIssues =
                """
                released-api.txt:5: error: Removed method android.rolecontrollerservice.RoleControllerService.onClearRoleHolders() [RemovedMethod]
                """,
            sourceFiles =
                arrayOf(
                    java(
                            """
                    package android.rolecontrollerservice;

                    public class Service {
                    }
                    """
                        )
                        .indented(),
                    java(
                        """
                    package android.rolecontrollerservice;
                    import android.annotation.SystemApi;

                    /** @hide */
                    @SystemApi
                    public abstract class RoleControllerService extends Service {
                        public abstract void onGrantDefaultRoles();
                    }
                    """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.TestApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            checkCompatibilityApiReleased =
                """
                package android.rolecontrollerservice {
                  public abstract class RoleControllerService extends android.rolecontrollerservice.Service {
                    ctor public RoleControllerService();
                    method public abstract void onClearRoleHolders();
                  }
                }
                """
        )
    }

    @Test
    fun `Regression test for bug 120847535`() {
        // Regression test for
        // 120847535: check-api doesn't fail on method that is in current.txt, but marked @hide
        // @TestApi
        check(
            expectedIssues =
                """
                released-api.txt:7: error: Removed method test.view.ViewTreeObserver.registerFrameCommitCallback(Runnable) [RemovedMethod]
                """,
            sourceFiles =
                arrayOf(
                    java(
                            """
                    package test.view;
                    import android.annotation.TestApi;
                    public final class ViewTreeObserver {
                         /**
                         * @hide
                         */
                        @TestApi
                        public void registerFrameCommitCallback(Runnable callback) {
                        }
                    }
                    """
                        )
                        .indented(),
                    java(
                            """
                    package test.view;
                    public final class View {
                        private View() { }
                    }
                    """
                        )
                        .indented(),
                    testApiSource
                ),
            api =
                """
                package test.view {
                  public final class View {
                  }
                  public final class ViewTreeObserver {
                    ctor public ViewTreeObserver();
                  }
                }
            """,
            extraArguments =
                arrayOf(
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            checkCompatibilityApiReleased =
                """
                package test.view {
                  public final class View {
                  }
                  public final class ViewTreeObserver {
                    ctor public ViewTreeObserver();
                    method public void registerFrameCommitCallback(java.lang.Runnable);
                  }
                }
                """
        )
    }

    @Test
    fun `Test release compatibility checking`() {
        // Different checks are enforced for current vs release API comparisons:
        // we don't flag AddedClasses etc. Removed classes *are* enforced.
        check(
            expectedIssues =
                """
                src/test/pkg/Class1.java:3: error: Class test.pkg.Class1 added 'final' qualifier [AddedFinal]
                released-api.txt:4: error: Removed constructor test.pkg.Class1() [RemovedMethod]
                src/test/pkg/MyClass.java:5: error: Method test.pkg.MyClass.myMethod2 has changed 'abstract' qualifier [ChangedAbstract]
                src/test/pkg/MyClass.java:6: error: Method test.pkg.MyClass.myMethod3 has changed 'static' qualifier [ChangedStatic]
                released-api.txt:15: error: Removed class test.pkg.MyOldClass [RemovedClass]
                released-api.txt:18: error: Removed package test.pkg3 [RemovedPackage]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Class1 {
                      ctor public Class1();
                  }
                  public class Class2 {
                  }
                  public final class Class3 {
                  }
                  public abstract class MyClass {
                      method public void myMethod2();
                      method public void myMethod3();
                      method deprecated public void myMethod4();
                  }
                  public abstract class MyOldClass {
                  }
                }
                package test.pkg3 {
                  public abstract class MyOldClass {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public final class Class1 {
                        private Class1() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public final class Class2 {
                        private Class2() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class Class3 {
                        private Class3() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public abstract class MyNewClass {
                        private MyNewClass() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public abstract class MyClass {
                        private MyClass() {}
                        public native abstract void myMethod2(); // Note that Errors.CHANGE_NATIVE is hidden by default
                        public static void myMethod3() {}
                        public void myMethod4() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test remove deprecated API is an error`() {
        // Regression test for b/145745855
        check(
            expectedIssues =
                """
                released-api.txt:7: error: Removed deprecated class test.pkg.DeprecatedClass [RemovedDeprecatedClass]
                released-api.txt:4: error: Removed deprecated constructor test.pkg.SomeClass() [RemovedDeprecatedMethod]
                released-api.txt:5: error: Removed deprecated method test.pkg.SomeClass.deprecatedMethod() [RemovedDeprecatedMethod]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class SomeClass {
                      ctor deprecated public SomeClass();
                      method deprecated public void deprecatedMethod();
                  }
                  deprecated public class DeprecatedClass {
                      ctor deprecated public DeprecatedClass();
                      method deprecated public void deprecatedMethod();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class SomeClass {
                        private SomeClass() {}
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test check release with base api`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class SomeClass {
                      method public static void publicMethodA();
                      method public static void publicMethodB();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class SomeClass {
                      public static void publicMethodA();
                    }
                    """
                    )
                ),
            checkCompatibilityBaseApi =
                """
                package test.pkg {
                  public class SomeClass {
                      method public static void publicMethodB();
                  }
                }
            """
        )
    }

    @Test
    fun `Test check a class moving from the released api to the base api`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class SomeClass1 {
                    method public void method1();
                  }
                  public class SomeClass2 {
                    method public void oldMethod();
                  }
                }
                """,
            checkCompatibilityBaseApi =
                """
                package test.pkg {
                  public class SomeClass2 {
                    method public void newMethod();
                  }
                }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class SomeClass1 {
                        public void method1();
                    }
                    """
                    )
                ),
            expectedIssues =
                """
            released-api.txt:7: error: Removed method test.pkg.SomeClass2.oldMethod() [RemovedMethod]
            """
                    .trimIndent()
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Implicit nullness`() {
        check(
            checkCompatibilityApiReleased =
                """
                // Signature format: 2.0
                package androidx.annotation {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target({java.lang.annotation.ElementType.ANNOTATION_TYPE, java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.PACKAGE}) public @interface RestrictTo {
                    method public abstract androidx.annotation.RestrictTo.Scope[] value();
                  }

                  public enum RestrictTo.Scope {
                    enum_constant @Deprecated public static final androidx.annotation.RestrictTo.Scope GROUP_ID;
                    enum_constant public static final androidx.annotation.RestrictTo.Scope LIBRARY;
                    enum_constant public static final androidx.annotation.RestrictTo.Scope LIBRARY_GROUP;
                    enum_constant public static final androidx.annotation.RestrictTo.Scope LIBRARY_GROUP_PREFIX;
                    enum_constant public static final androidx.annotation.RestrictTo.Scope SUBCLASSES;
                    enum_constant public static final androidx.annotation.RestrictTo.Scope TESTS;
                  }
                }
                """,
            sourceFiles = arrayOf(restrictToSource)
        )
    }

    @Test
    fun `Java String constants`() {
        check(
            checkCompatibilityApiReleased =
                """
                // Signature format: 2.0
                package androidx.browser.browseractions {
                  public class BrowserActionsIntent {
                    field public static final String EXTRA_APP_ID = "androidx.browser.browseractions.APP_ID";
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                            """
                     package androidx.browser.browseractions;
                     public class BrowserActionsIntent {
                        private BrowserActionsIntent() { }
                        public static final String EXTRA_APP_ID = "androidx.browser.browseractions.APP_ID";

                     }
                    """
                        )
                        .indented()
                )
        )
    }

    @Test
    fun `Classes with maps`() {
        check(
            checkCompatibilityApiReleased =
                """
                // Signature format: 2.0
                package androidx.collection {
                  public class SimpleArrayMap<K, V> {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                            """
                    package androidx.collection;

                    public class SimpleArrayMap<K, V> {
                        private SimpleArrayMap() { }
                    }
                    """
                        )
                        .indented()
                )
        )
    }

    @Test
    fun `Referencing type parameters in types`() {
        check(
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package androidx.collection {
                  public class MyMap<Key, Value> {
                    ctor public MyMap();
                    field public Key! myField;
                    method public Key! getReplacement(Key!);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                            """
                    package androidx.collection;

                    public class MyMap<Key, Value> {
                        public Key getReplacement(Key key) { return null; }
                        public Key myField = null;
                    }
                    """
                        )
                        .indented()
                )
        )
    }

    @Test
    fun `Insignificant type formatting differences`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class UsageStatsManager {
                    method public java.util.Map<java.lang.String, java.lang.Integer> getAppStandbyBuckets();
                    method public void setAppStandbyBuckets(java.util.Map<java.lang.String, java.lang.Integer>);
                    field public java.util.Map<java.lang.String, java.lang.Integer> map;
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public final class UsageStatsManager {
                    method public java.util.Map<java.lang.String,java.lang.Integer> getAppStandbyBuckets();
                    method public void setAppStandbyBuckets(java.util.Map<java.lang.String,java.lang.Integer>);
                    field public java.util.Map<java.lang.String,java.lang.Integer> map;
                  }
                }
                """
        )
    }

    @Test
    fun `Compare signatures with Kotlin nullability from signature`() {
        check(
            expectedIssues =
                """
            load-api.txt:5: error: Attempted to remove @NonNull annotation from parameter str in test.pkg.Foo.method1(int p, Integer int2, int p1, String str, java.lang.String... args) [InvalidNullConversion]
            load-api.txt:7: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter str in test.pkg.Foo.method3(String str, int p, int int2) [InvalidNullConversion]
            """
                    .trimIndent(),
            format = FileFormat.V3,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method public void method1(int p = 42, Integer? int2 = null, int p1 = 42, String str = "hello world", java.lang.String... args);
                    method public void method2(int p, int int2 = (2 * int) * some.other.pkg.Constants.Misc.SIZE);
                    method public void method3(String? str, int p, int int2 = double(int) + str.length);
                    field public static final test.pkg.Foo.Companion! Companion;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 3.0
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method public void method1(int p = 42, Integer? int2 = null, int p1 = 42, String! str = "hello world", java.lang.String... args);
                    method public void method2(int p, int int2 = (2 * int) * some.other.pkg.Constants.Misc.SIZE);
                    method public void method3(String str, int p, int int2 = double(int) + str.length);
                    field public static final test.pkg.Foo.Companion! Companion;
                  }
                }
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Compare signatures with Kotlin nullability from source`() {
        check(
            expectedIssues =
                """
            src/test/pkg/test.kt:4: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter str1 in test.pkg.TestKt.fun1(String str1, String str2, java.util.List<java.lang.String> list) [InvalidNullConversion]
            """
                    .trimIndent(),
            format = FileFormat.V3,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package test.pkg {
                  public final class TestKt {
                    method public static void fun1(String? str1, String str2, java.util.List<java.lang.String!> list);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        import java.util.List

                        fun fun1(str1: String, str2: String?, list: List<String?>) { }

                    """
                            .trimIndent()
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Adding and removing reified`() {
        check(
            expectedIssues =
                """
                src/test/pkg/test.kt:5: error: Method test.pkg.TestKt.add made type variable T reified: incompatible change [AddedReified]
                src/test/pkg/test.kt:8: error: Method test.pkg.TestKt.two made type variable S reified: incompatible change [AddedReified]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package test.pkg {
                  public final class TestKt {
                    method public static inline <T> void add(T! t);
                    method public static inline <reified T> void remove(T! t);
                    method public static inline <reified T> void unchanged(T! t);
                    method public static inline <S, reified T> void two(S! s, T! t);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                            """
                    @file:Suppress("NOTHING_TO_INLINE", "RedundantVisibilityModifier", "unused")

                    package test.pkg

                    inline fun <reified T> add(t: T) { }
                    inline fun <T> remove(t: T) { }
                    inline fun <reified T> unchanged(t: T) { }
                    inline fun <reified S, T> two(s: S, t: T) { }
                    """
                        )
                        .indented()
                )
        )
    }

    @Test
    fun `Empty prev api with @hide and --show-annotation`() {
        check(
            checkCompatibilityApiReleased = """
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.media;

                    /**
                     * @hide
                     */
                    public class SubtitleController {
                        public interface Listener {
                            void onSubtitleTrackSelected() { }
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.media;
                    import android.annotation.SystemApi;

                    /**
                     * @hide
                     */
                    @SystemApi
                    @SuppressWarnings("HiddenSuperclass")
                    public class MediaPlayer implements SubtitleController.Listener {
                    }
                    """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            expectedIssues = ""
        )
    }

    @Test
    fun `Inherited systemApi method in an inner class`() {
        check(
            checkCompatibilityApiReleased =
                """
                package android.telephony {
                  public class MmTelFeature.Capabilities {
                    method public boolean isCapable(int);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.telephony;

                    /**
                     * @hide
                     */
                    @android.annotation.SystemApi
                    public class MmTelFeature {
                        public static class Capabilities extends ParentCapabilities {
                            @Override
                            boolean isCapable(int argument) { return true; }
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.telephony;

                    /**
                     * @hide
                     */
                    @android.annotation.SystemApi
                    public class Parent {
                        public static class ParentCapabilities {
                            public boolean isCapable(int argument) { return false; }
                        }
                    }
                    """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            expectedIssues = ""
        )
    }

    @Test
    fun `Moving removed api back to public api`() {
        check(
            checkCompatibilityRemovedApiReleased =
                """
                package android.content {
                  public class ContextWrapper {
                    method public void createContextForSplit();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.content;

                    public class ContextWrapper extends Parent {
                        /** @removed */
                        @Override
                        public void getSharedPreferences() { }

                        /** @hide */
                        @Override
                        public void createContextForSplit() { }
                    }
                    """
                    ),
                    java(
                        """
                    package android.content;

                    public abstract class Parent {
                        /** @hide */
                        @Override
                        public void getSharedPreferences() { }

                        public abstract void createContextForSplit() { }
                    }
                    """
                    )
                ),
            expectedIssues = ""
        )
    }

    @Test
    fun `Inherited nullability annotations`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class SAXException extends test.pkg.Parent {
                  }
                  public final class Parent extends test.pkg.Grandparent {
                  }
                  public final class Grandparent {
                    method @Nullable public String getMessage();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public final class SAXException extends Parent {
                        @Override public String getMessage() {
                            return "sample";
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public final class Parent extends Grandparent {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public final class Grandparent {
                        public String getMessage() {
                            return "sample";
                        }
                    }
                    """
                    )
                ),
            mergeJavaStubAnnotations =
                """
                package test.pkg;

                public class Grandparent implements java.io.Serializable {
                    @libcore.util.Nullable public test.pkg.String getMessage() { throw new RuntimeException("Stub!"); }
                }
            """,
            expectedIssues = """
                """
        )
    }

    @Test
    fun `Inherited @removed fields`() {
        check(
            checkCompatibilityRemovedApiReleased =
                """
                package android.provider {

                  public static final class StreamItems implements android.provider.BaseColumns {
                    field public static final String _COUNT = "_count";
                    field public static final String _ID = "_id";
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.provider;

                    /**
                     * @removed
                     */
                    public static final class StreamItems implements BaseColumns {
                    }
                    """
                    ),
                    java(
                        """
                    package android.provider;

                    public interface BaseColumns {
                        public static final String _ID = "_id";
                        public static final String _COUNT = "_count";
                    }
                    """
                    )
                ),
            expectedIssues = """
                """
        )
    }

    @Test
    fun `Inherited deprecated protected @removed method`() {
        check(
            checkCompatibilityApiReleased =
                """
                package android.icu.util {
                  public class SpecificCalendar {
                    method @Deprecated protected void validateField();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.icu.util;
                    import java.text.Format;

                    public class SpecificCalendar extends Calendar {
                        /**
                         * @deprecated for this test
                         * @hide
                         */
                        @Override
                        @Deprecated
                        protected void validateField() {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.icu.util;

                    public class Calendar {
                        protected void validateField() {
                        }
                    }
                    """
                    )
                ),
            expectedIssues = """
                """
        )
    }

    @Test
    fun `Move class from SystemApi to public and then remove a method`() {
        check(
            checkCompatibilityApiReleased =
                """
                package android.hardware.lights {
                  public static final class LightsRequest.Builder {
                    ctor public LightsRequest.Builder();
                    method public void clearLight();
                    method public void setLight();
                  }

                  public final class LightsManager {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.hardware.lights;

                    import android.annotation.SystemApi;

                    public class LightsRequest {
                        public static final class Builder {
                            void clearLight() { }
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.hardware.lights;

                    import android.annotation.SystemApi;

                    /**
                     * @hide
                     */
                    @SystemApi
                    public class LightsManager {
                    }
                    """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            expectedIssues =
                """
                released-api.txt:6: error: Removed method android.hardware.lights.LightsRequest.Builder.setLight() [RemovedMethod]
                """
        )
    }

    @Test
    fun `Change item in nested SystemApi`() {
        check(
            checkCompatibilityApiReleased =
                """
                package android.foobar {
                  public static class Foo.Nested {
                    ctor public Foo.Nested();
                    method public void existing();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.foobar;

                    import android.annotation.SystemApi;

                    public class Foo {
                        /** @hide */
                        @SystemApi
                        public static final class Nested {
                            public final int existing();
                        }
                    }
                    """
                    ),
                    systemApiSource
                ),
            showAnnotations = arrayOf(ANDROID_SYSTEM_API),
            expectedIssues =
                """
                src/android/foobar/Foo.java:8: error: Class android.foobar.Foo.Nested added 'final' qualifier [AddedFinal]
                src/android/foobar/Foo.java:8: error: Constructor android.foobar.Foo.Nested has added 'final' qualifier [AddedFinal]
                src/android/foobar/Foo.java:9: error: Method android.foobar.Foo.Nested.existing has changed return type from void to int [ChangedType]
                src/android/foobar/Foo.java:9: error: Method android.foobar.Foo.Nested.existing has added 'final' qualifier [AddedFinal]
                """
        )
    }

    @Test
    fun `Moving a field from SystemApi to public`() {
        check(
            checkCompatibilityApiReleased =
                """
                package android.content {
                  public class Context {
                    field public static final String BUGREPORT_SERVICE = "bugreport";
                    method public java.io.File getPreloadsFileCache();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.content;

                    import android.annotation.SystemApi;
                    import java.io.File;

                    public class Context {
                        public static final String BUGREPORT_SERVICE = "bugreport";

                        /**
                         * @hide
                         */
                        @SystemApi
                        public File getPreloadsFileCache() { return null; }
                    }
                    """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            expectedIssues = """
                """
        )
    }

    @Test
    fun `Compare interfaces when Object is redefined`() {
        check(
            checkCompatibilityApiReleased =
                """
                package java.lang {
                  public class Object {
                    method public final void wait();
                  }
                }
                package test.pkg {
                  public interface SomeInterface {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public interface SomeInterface {
                    }
                    """
                    )
                ),
            // it's not quite right to say that java.lang was removed, but it's better than also
            // saying that SomeInterface no longer implements wait()
            expectedIssues =
                """
                released-api.txt:2: error: Removed package java.lang [RemovedPackage]
                """
        )
    }

    @Test
    fun `Overriding method without redeclaring nullability`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Child extends test.pkg.Parent {
                  }
                  public class Parent {
                    method public void sample(@Nullable String);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Child extends Parent {
                        public void sample(String arg) {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class Parent {
                        public void sample(@Nullable String arg) {
                        }
                    }
                    """
                    )
                ),
            // The correct behavior would be for this test to fail, because of the removal of
            // nullability annotations on the child class. However, when we generate signature
            // files,
            // we omit methods having the same signature as super methods, so if we were to generate
            // a signature file for this source, we would generate the given signature file. So,
            // we temporarily allow (and expect) this to pass without errors
            // expectedIssues = "src/test/pkg/Child.java:4: error: Attempted to remove @Nullable
            // annotation from parameter arg in test.pkg.Child.sample(String arg)
            // [InvalidNullConversion]"
            expectedIssues = ""
        )
    }

    @Test
    fun `Final class inherits a method`() {
        check(
            checkCompatibilityApiReleased =
                """
                package java.security {
                  public abstract class BasicPermission extends java.security.Permission {
                    method public boolean implies(java.security.Permission);
                  }
                  public abstract class Permission {
                    method public abstract boolean implies(java.security.Permission);
                  }
                }
                package javax.security.auth {
                  public final class AuthPermission extends java.security.BasicPermission {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package javax.security.auth;

                    public final class AuthPermission extends java.security.BasicPermission {
                    }
                    """
                    ),
                    java(
                        """
                    package java.security;

                    public abstract class BasicPermission extends Permission {
                        public boolean implies(Permission p) {
                            return true;
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package java.security;
                    public abstract class Permission {
                        public abstract boolean implies(Permission permission);
                    }
                    """
                    )
                ),
            expectedIssues = ""
        )
    }

    @Test
    fun `Implementing undefined interface`() {
        check(
            checkCompatibilityApiReleased =
                """
                package org.apache.http.conn.scheme {
                  @Deprecated public final class PlainSocketFactory implements org.apache.http.conn.scheme.SocketFactory {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package org.apache.http.conn.scheme;

                    /** @deprecated */
                    @Deprecated
                    public final class PlainSocketFactory implements SocketFactory {
                    }
                    """
                    )
                ),
            expectedIssues = ""
        )
    }

    @Test
    fun `Inherited abstract method`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MeasureFormat {
                      method public test.pkg.MeasureFormat parse();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class MeasureFormat extends UFormat {
                        private MeasureFormat() { }
                        /** @hide */
                        public MeasureFormat parse();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;

                    public abstract class UFormat {
                        public abstract UFormat parse() {
                        }
                    }
                    """
                    ),
                    systemApiSource
                ),
            expectedIssues = ""
        )
    }

    @Test
    fun `Ignore hidden references`() {
        check(
            expectedIssues = """
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MyClass {
                    ctor public MyClass();
                    method public void method1(test.pkg.Hidden);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class MyClass {
                        public void method1(Hidden hidden) { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    /** @hide */
                    public class Hidden {
                    }
                    """
                    )
                ),
            extraArguments =
                arrayOf(
                    ARG_HIDE,
                    "ReferencesHidden",
                    ARG_HIDE,
                    "UnavailableSymbol",
                    ARG_HIDE,
                    "HiddenTypeParameter"
                )
        )
    }

    @Test
    fun `Empty bundle files`() {
        // Regression test for 124333557
        // Makes sure we properly handle conflicting definitions of a java file in separate source
        // roots
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package com.android.location.provider {
                  public class LocationProviderBase1 {
                    ctor public LocationProviderBase1();
                    method public void onGetStatus(android.os.Bundle!);
                  }
                  public class LocationProviderBase2 {
                    ctor public LocationProviderBase2();
                    method public void onGetStatus(android.os.Bundle!);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        "src2/com/android/location/provider/LocationProviderBase1.java",
                        """
                    /** Something */
                    package com.android.location.provider;
                    """
                    ),
                    java(
                        "src/com/android/location/provider/LocationProviderBase1.java",
                        """
                    package com.android.location.provider;
                    import android.os.Bundle;

                    public class LocationProviderBase1 {
                        public void onGetStatus(Bundle bundle) { }
                    }
                    """
                    ),
                    // Try both combinations (empty java file both first on the source path
                    // and second on the source path)
                    java(
                        "src/com/android/location/provider/LocationProviderBase2.java",
                        """
                    /** Something */
                    package com.android.location.provider;
                    """
                    ),
                    java(
                        "src/com/android/location/provider/LocationProviderBase2.java",
                        """
                    package com.android.location.provider;
                    import android.os.Bundle;

                    public class LocationProviderBase2 {
                        public void onGetStatus(Bundle bundle) { }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check parameterized return type nullability`() {
        // Regression test for 130567941
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package androidx.coordinatorlayout.widget {
                  public class CoordinatorLayout {
                    ctor public CoordinatorLayout();
                    method public java.util.List<android.view.View!> getDependencies();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package androidx.coordinatorlayout.widget;

                    import java.util.List;
                    import androidx.annotation.NonNull;
                    import android.view.View;

                    public class CoordinatorLayout {
                        @NonNull
                        public List<View> getDependencies() {
                            throw Exception("Not implemented");
                        }
                    }
                    """
                    ),
                    androidxNonNullSource
                ),
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation")
        )
    }

    @Test
    fun `Check return type changing package`() {
        // Regression test for 130567941
        check(
            expectedIssues =
                """
            load-api.txt:7: error: Method test.pkg.sample.SampleClass.convert1 has changed return type from Number (extends java.lang.Object) to java.lang.Number [ChangedType]
            """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package test.pkg.sample {
                  public abstract class SampleClass {
                    method public <Number> Number! convert(Number);
                    method public <Number> Number! convert1(Number);
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 3.0
                package test.pkg.sample {
                  public abstract class SampleClass {
                    // Here the generic type parameter applies to both the function argument and the function return type
                    method public <Number> Number! convert(Number);
                    // Here the generic type parameter applies to the function argument but not the function return type
                    method public <Number> java.lang.Number! convert1(Number);
                  }
                }
            """
        )
    }

    @Test
    fun `Check generic type argument when showUnannotated is explicitly enabled`() {
        // Regression test for 130567941
        check(
            expectedIssues = """
            """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package androidx.versionedparcelable {
                  public abstract class VersionedParcel {
                    method public <T> T![]! readArray();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package androidx.versionedparcelable;

                    public abstract class VersionedParcel {
                        private VersionedParcel() { }

                        public <T> T[] readArray() { return null; }
                    }
                    """
                    )
                ),
            extraArguments =
                arrayOf(ARG_SHOW_UNANNOTATED, ARG_SHOW_ANNOTATION, "androidx.annotation.RestrictTo")
        )
    }

    @Test
    fun `Check using parameterized arrays as type parameters`() {
        check(
            format = FileFormat.V3,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import java.util.ArrayList;
                    import java.lang.Exception;

                    public class SampleArray<D extends ArrayList> extends ArrayList<D[]> {
                        public D[] get(int index) {
                            throw Exception("Not implemented");
                        }
                    }
                    """
                    )
                ),
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package test.pkg {
                  public class SampleArray<D extends java.util.ArrayList> extends java.util.ArrayList<D[]> {
                    ctor public SampleArray();
                    method public D![]! get(int);
                  }
                }
                """
        )
    }

    @Test
    fun `New default method on annotation`() {
        // Regression test for 134754815
        check(
            expectedIssues =
                """
            src/androidx/room/Relation.java:5: error: Added method androidx.room.Relation.IHaveNoDefault() [AddedAbstractMethod]
            """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package androidx.room {
                  public @interface Relation {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package androidx.room;

                    public @interface Relation {
                        String IHaveADefault() default "";
                        String IHaveNoDefault();
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Changing static qualifier on inner classes with no public constructors`() {
        check(
            expectedIssues =
                """
                load-api.txt:12: error: Class test.pkg.ParentClass.AnotherBadInnerClass changed 'static' qualifier [ChangedStatic]
                load-api.txt:9: error: Class test.pkg.ParentClass.BadInnerClass changed 'static' qualifier [ChangedStatic]
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class ParentClass {
                  }
                  public static class ParentClass.OkInnerClass {
                  }
                  public class ParentClass.AnotherOkInnerClass {
                  }
                  public static class ParentClass.BadInnerClass {
                    ctor public BadInnerClass();
                  }
                  public class ParentClass.AnotherBadInnerClass {
                    ctor public AnotherBadInnerClass();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public class ParentClass {
                  }
                  public class ParentClass.OkInnerClass {
                  }
                  public static class ParentClass.AnotherOkInnerClass {
                  }
                  public class ParentClass.BadInnerClass {
                    ctor public BadInnerClass();
                  }
                  public static class ParentClass.AnotherBadInnerClass {
                    ctor public AnotherBadInnerClass();
                  }
                }
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Remove fun modifier from interface`() {
        check(
            expectedIssues =
                """
                src/test/pkg/FunctionalInterface.kt:3: error: Cannot remove 'fun' modifier from class test.pkg.FunctionalInterface: source incompatible change [FunRemoval]
                """,
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package test.pkg {
                  public fun interface FunctionalInterface {
                    method public boolean methodOne(int number);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    interface FunctionalInterface {
                        fun methodOne(number: Int): Boolean
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Remove fun modifier from interface signature files`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Cannot remove 'fun' modifier from class test.pkg.FunctionalInterface: source incompatible change [FunRemoval]
                """,
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package test.pkg {
                  public fun interface FunctionalInterface {
                    method public boolean methodOne(int number);
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 4.0
                package test.pkg {
                  public interface FunctionalInterface {
                    method public boolean methodOne(int number);
                  }
                }
            """
                    .trimIndent()
        )
    }

    @Test
    fun `Adding default value to annotation parameter`() {
        check(
            expectedIssues = "",
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package androidx.annotation.experimental {
                  public @interface UseExperimental {
                    method public abstract Class<?> markerClass();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package androidx.annotation.experimental;
                    public @interface UseExperimental {
                        Class<?> markerClass() default void.class;
                    }
                """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `adding methods to interfaces`() {
        check(
            expectedIssues =
                """
                src/test/pkg/JavaInterface.java:4: error: Added method test.pkg.JavaInterface.noDefault() [AddedAbstractMethod]
                src/test/pkg/KotlinInterface.kt:5: error: Added method test.pkg.KotlinInterface.hasDefault() [AddedAbstractMethod]
                src/test/pkg/KotlinInterface.kt:4: error: Added method test.pkg.KotlinInterface.noDefault() [AddedAbstractMethod]
            """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 3.0
                package test.pkg {
                  public interface JavaInterface {
                  }
                  public interface KotlinInterface {
                  }
                }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public interface JavaInterface {
                            void noDefault();
                            default boolean hasDefault() {
                                return true;
                            }
                            static void newStatic();
                        }
                    """
                    ),
                    kotlin(
                        """
                        package test.pkg

                        interface KotlinInterface {
                            fun noDefault()
                            fun hasDefault(): Boolean = true
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `Changing visibility from public to private`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.Foo changed visibility from public to private [ChangedScope]
            """
                    .trimIndent(),
            signatureSource =
                """
                package test.pkg {
                  private class Foo {}
                }
            """
                    .trimIndent(),
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {}
                }
            """
                    .trimIndent()
        )
    }

    @Test
    fun `Changing class kind`() {
        check(
            expectedIssues =
                """
                load-api.txt:12: error: Class test.pkg.AnnotationToClass changed class/interface declaration [ChangedClass]
                load-api.txt:14: error: Class test.pkg.AnnotationToEnum changed class/interface declaration [ChangedClass]
                load-api.txt:13: error: Class test.pkg.AnnotationToInterface changed class/interface declaration [ChangedClass]
                load-api.txt:5: error: Class test.pkg.ClassToAnnotation changed class/interface declaration [ChangedClass]
                load-api.txt:3: error: Class test.pkg.ClassToEnum changed class/interface declaration [ChangedClass]
                load-api.txt:4: error: Class test.pkg.ClassToInterface changed class/interface declaration [ChangedClass]
                load-api.txt:8: error: Class test.pkg.EnumToAnnotation changed class/interface declaration [ChangedClass]
                load-api.txt:6: error: Class test.pkg.EnumToClass changed class/interface declaration [ChangedClass]
                load-api.txt:7: error: Class test.pkg.EnumToInterface changed class/interface declaration [ChangedClass]
                load-api.txt:11: error: Class test.pkg.InterfaceToAnnotation changed class/interface declaration [ChangedClass]
                load-api.txt:9: error: Class test.pkg.InterfaceToClass changed class/interface declaration [ChangedClass]
                load-api.txt:10: error: Class test.pkg.InterfaceToEnum changed class/interface declaration [ChangedClass]
            """
                    .trimIndent(),
            signatureSource =
                """
                package test.pkg {
                  public enum ClassToEnum {}
                  public interface ClassToInterface {}
                  public @interface ClassToAnnotation {}
                  public class EnumToClass {}
                  public interface EnumToInterface {}
                  public @interface EnumToAnnotation {}
                  public class InterfaceToClass {}
                  public enum InterfaceToEnum {}
                  public @interface InterfaceToAnnotation {}
                  public class  AnnotationToClass {}
                  public interface AnnotationToInterface {}
                  public enum AnnotationToEnum {}
                }
            """
                    .trimIndent(),
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class ClassToEnum {}
                  public class ClassToInterface {}
                  public class ClassToAnnotation {}
                  public enum EnumToClass {}
                  public enum EnumToInterface {}
                  public enum EnumToAnnotation {}
                  public interface InterfaceToClass {}
                  public interface InterfaceToEnum {}
                  public interface InterfaceToAnnotation {}
                  public @interface  AnnotationToClass {}
                  public @interface AnnotationToInterface {}
                  public @interface AnnotationToEnum {}
                }
            """
                    .trimIndent()
        )
    }

    @Test
    fun `Allow increased field access for classes`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    field public int bar;
                    field protected int baz;
                    field protected int spam;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    field protected int bar;
                    field private int baz;
                    field internal int spam;
                  }
                }
            """
        )
    }

    @Test
    fun `Block decreased field access in classes`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Field test.pkg.Foo.bar changed visibility from public to protected [ChangedScope]
                load-api.txt:5: error: Field test.pkg.Foo.baz changed visibility from protected to private [ChangedScope]
                load-api.txt:6: error: Field test.pkg.Foo.spam changed visibility from protected to internal [ChangedScope]
            """,
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    field protected int bar;
                    field private int baz;
                    field internal int spam;
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    field public int bar;
                    field protected int baz;
                    field protected int spam;
                  }
                }
            """
        )
    }

    @Test
    fun `Allow increased access`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method public void bar();
                    method protected void baz();
                    method protected void spam();
                  }
                }
            """,
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method protected void bar();
                    method private void baz();
                    method internal void spam();
                  }
                }
            """
        )
    }

    @Test
    fun `Block decreased access`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Method test.pkg.Foo.bar changed visibility from public to protected [ChangedScope]
                load-api.txt:5: error: Method test.pkg.Foo.baz changed visibility from protected to private [ChangedScope]
                load-api.txt:6: error: Method test.pkg.Foo.spam changed visibility from protected to internal [ChangedScope]
            """,
            signatureSource =
                """
                package test.pkg {
                  class Foo {
                    method protected void bar();
                    method private void baz();
                    method internal void spam();
                  }
                }
            """,
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  class Foo {
                    method public void bar();
                    method protected void baz();
                    method protected void spam();
                  }
                }
            """
        )
    }

    @Test
    fun `configuring issue severity`() {
        check(
            extraArguments = arrayOf(ARG_HIDE, Issues.REMOVED_METHOD.name),
            signatureSource =
                """
                package test.pkg {
                    public class Foo {
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo {
                        ctor public Foo();
                        method public void bar();
                    }
                }
            """
        )
    }

    @Test
    fun `block changing open to abstract`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.Foo changed 'abstract' qualifier [ChangedAbstract]
                load-api.txt:5: error: Method test.pkg.Foo.bar has changed 'abstract' qualifier [ChangedAbstract]
            """,
            signatureSource =
                """
                package test.pkg {
                    public abstract class Foo {
                        ctor public Foo();
                        method public abstract void bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public class Foo {
                        ctor public Foo();
                        method public void bar();
                    }
                }
            """
        )
    }

    @Test
    fun `allow changing abstract to open`() {
        check(
            signatureSource =
                """
                package test.pkg {
                    public class Foo {
                        ctor public Foo();
                        method public void bar();
                    }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public abstract class Foo {
                        ctor public Foo();
                        method public abstract void bar();
                    }
                }
            """
        )
    }

    @Test
    fun `Change default to abstract`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Method test.pkg.Foo.bar has changed 'default' qualifier [ChangedDefault]
            """,
            signatureSource =
                """
                package test.pkg {
                  interface Foo {
                    method abstract public void bar(Int);
                  }
                }
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  interface Foo {
                    method default public void bar(Int);
                    }
                  }
              """
        )
    }

    @Test
    fun `Allow change from non-final to final in sealed class`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  sealed class Foo {
                    method final public void bar(Int);
                  }
                }
            """,
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  sealed class Foo {
                    method public void bar(Int);
                  }
                }
            """
        )
    }

    @Test
    fun `unchanged self-referencing type parameter is compatible`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public abstract class Foo<T extends test.pkg.Foo<T>> {
                            method public static <T extends test.pkg.Foo<T>> T valueOf(Class<T>, String);
                    }
                }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.NonNull;
                    public abstract class Foo<T extends Foo<T>> {
                        @NonNull
                        public static <T extends Foo<T>> T valueOf(@NonNull Class<T> fooType, @NonNull String name) {}
                    }
                    """
                    ),
                    nonNullSource
                )
        )
    }

    @Test
    fun `adding a method to an abstract class with hidden constructor`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                    public abstract class Foo {
                    }
                }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public abstract class Foo {
                        /**
                        * @hide
                        */
                        public Foo() {}
                        public abstract void newAbstractMethod();
                    }
                    """
                    ),
                )
        )
    }

    @Test
    fun `Allow incompatible changes to unchecked APIs`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @test.pkg.MetaAnnotatedDoNotCheckCompat
                  public class MyTest1 {
                    method public Double method(Float);
                    field public Double field;
                  }
                  @test.pkg.MetaAnnotatedDoNotCheckCompat
                  public class MyTest2 {
                  }
                  @test.pkg.MetaDoNotCheckCompat public @interface MetaAnnotatedDoNotCheckCompat {
                  }
                  @test.pkg.MetaDoNotCheckCompat public @interface MetaDoNotCheckCompat {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public class MyTest1 {
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.MetaDoNotCheckCompat")
        )
    }

    @Test
    fun `Allow changing API from unchecked to checked`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @test.pkg.MetaAnnotatedDoNotCheckCompat
                  public class MyTest1 {
                  }
                  @test.pkg.MetaAnnotatedDoNotCheckCompat
                  public class MyTest2 {
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.MetaDoNotCheckCompat public @interface MetaAnnotatedDoNotCheckCompat {
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.MetaDoNotCheckCompat public @interface MetaDoNotCheckCompat {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public class MyTest1 {
                  }
                  @test.pkg.MetaAnnotatedDoNotCheckCompat
                  public class MyTest2 {
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.MetaDoNotCheckCompat public @interface MetaAnnotatedDoNotCheckCompat {
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.MetaDoNotCheckCompat public @interface MetaDoNotCheckCompat {
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.MetaDoNotCheckCompat")
        )
    }

    @Test
    fun `Doesn't crash when checking annotations with BINARY retention`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package androidx.wear.watchface {
                  @androidx.wear.watchface.complications.data.ComplicationExperimental public final class BoundingArc {
                    ctor public BoundingArc(float startAngle, float totalAngle, @Px float thickness);
                    method public float getStartAngle();
                    method public float getThickness();
                    method public float getTotalAngle();
                    method public boolean hitTest(android.graphics.Rect rect, @Px float x, @Px float y);
                    method public void setStartAngle(float);
                    method public void setThickness(float);
                    method public void setTotalAngle(float);
                    property public final float startAngle;
                    property public final float thickness;
                    property public final float totalAngle;
                  }
                }
                package androidx.wear.watchface.complications.data {
                  @kotlin.RequiresOptIn(message="This is an experimental API that may change or be removed without warning.") @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface ComplicationExperimental {
                  }
                }
                """,
            signatureSource =
                """
                package androidx.wear.watchface {
                  @androidx.wear.watchface.complications.data.ComplicationExperimental public final class BoundingArc {
                    ctor public BoundingArc(float startAngle, float totalAngle, @Px float thickness);
                    method public float getStartAngle();
                    method public float getThickness();
                    method public float getTotalAngle();
                    method public boolean hitTest(android.graphics.Rect rect, @Px float x, @Px float y);
                    property public final float startAngle;
                    property public final float thickness;
                    property public final float totalAngle;
                  }
                }
                package androidx.wear.watchface.complications.data {
                  @kotlin.RequiresOptIn(message="This is an experimental API that may change or be removed without warning.") @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface ComplicationExperimental {
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn")
        )
    }

    @Test
    fun `Fail when changing API from checked to unchecked`() {
        check(
            expectedIssues =
                """
                released-api.txt:3: error: Removed class test.pkg.MyTest1 from compatibility checked API surface [BecameUnchecked]
            """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class MyTest1 {
                  }
                  @test.pkg.MetaAnnotatedDoNotCheckCompat
                  public class MyTest2 {
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.MetaDoNotCheckCompat public @interface MetaAnnotatedDoNotCheckCompat {
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.MetaDoNotCheckCompat public @interface MetaDoNotCheckCompat {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @test.pkg.MetaAnnotatedDoNotCheckCompat
                  public class MyTest1 {
                  }
                  @test.pkg.MetaAnnotatedDoNotCheckCompat
                  public class MyTest2 {
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.MetaDoNotCheckCompat public @interface MetaAnnotatedDoNotCheckCompat {
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.MetaDoNotCheckCompat public @interface MetaDoNotCheckCompat {
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.MetaDoNotCheckCompat")
        )
    }

    @Test
    fun `Conversion from AutoCloseable to Closeable is not API-breaking`() {
        // Closeable implements AutoCloseable
        check(
            apiClassResolution = ApiClassResolution.API_CLASSPATH,
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class Foo implements java.lang.AutoCloseable {
                    method public void close();
                  }
                }
            """,
            signatureSource =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class Foo implements java.io.Closeable {
                    method public void close();
                  }
                }
            """
        )
    }

    @Test
    fun `Conversion from Closeable to AutoCloseable is API-breaking`() {
        // AutoCloseable does not implement Closeable
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.Foo no longer implements java.io.Closeable [RemovedInterface]
            """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class Foo implements java.io.Closeable {
                    method public void close();
                  }
                }
            """,
            signatureSource =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class Foo implements java.lang.AutoCloseable {
                    method public void close();
                  }
                }
            """
        )
    }

    @Test
    fun `Conversion from MutableCollection to AbstractMutableCollection is not API-breaking`() {
        check(
            apiClassResolution = ApiClassResolution.API_CLASSPATH,
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class MyCollection<E> implements java.util.Collection<E> {
                    ctor public MyCollection();
                    method public boolean add(E! e);
                    method public boolean addAll(java.util.Collection<? extends E> c);
                    method public void clear();
                    method public boolean contains(Object! o);
                    method public boolean containsAll(java.util.Collection<?> c);
                    method public boolean isEmpty();
                    method public java.util.Iterator<E> iterator();
                    method public boolean remove(Object! o);
                    method public boolean removeAll(java.util.Collection<?> c);
                    method public boolean retainAll(java.util.Collection<?> c);
                    method public int size();
                    method public Object![] toArray();
                    method public <T> T![] toArray(T[] a);
                  }
                }
            """,
            signatureSource =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class MyCollection<E> extends java.util.AbstractCollection<E> {
                    ctor public MyCollection();
                    method public java.util.Iterator<E> iterator();
                    method public int size();
                  }
                }
            """
        )
    }

    @Test
    fun `Expected API changes converting collections to Kotlin`() {
        check(
            apiClassResolution = ApiClassResolution.API_CLASSPATH,
            // The parameter names are different between java.util.Collection and
            // kotlin.collections.Collection
            // Methods not defined in kotlin.collections.Collection appear abstract as they are not
            // listed in the API file
            expectedIssues =
                """
                error: Method test.pkg.MyCollection.add has changed 'abstract' qualifier [ChangedAbstract]
                error: Attempted to remove parameter name from parameter p in test.pkg.MyCollection.add [ParameterNameChange]
                error: Method test.pkg.MyCollection.addAll has changed 'abstract' qualifier [ChangedAbstract]
                error: Attempted to remove parameter name from parameter p in test.pkg.MyCollection.addAll [ParameterNameChange]
                error: Method test.pkg.MyCollection.clear has changed 'abstract' qualifier [ChangedAbstract]
                load-api.txt:5: error: Attempted to change parameter name from o to element in method test.pkg.MyCollection.contains [ParameterNameChange]
                load-api.txt:5: error: Attempted to change parameter name from o to element in method test.pkg.MyCollection.contains [ParameterNameChange]
                load-api.txt:6: error: Attempted to change parameter name from c to elements in method test.pkg.MyCollection.containsAll [ParameterNameChange]
                load-api.txt:6: error: Attempted to change parameter name from c to elements in method test.pkg.MyCollection.containsAll [ParameterNameChange]
                error: Method test.pkg.MyCollection.remove has changed 'abstract' qualifier [ChangedAbstract]
                error: Attempted to remove parameter name from parameter p in test.pkg.MyCollection.remove [ParameterNameChange]
                error: Method test.pkg.MyCollection.removeAll has changed 'abstract' qualifier [ChangedAbstract]
                error: Attempted to remove parameter name from parameter p in test.pkg.MyCollection.removeAll [ParameterNameChange]
                error: Method test.pkg.MyCollection.retainAll has changed 'abstract' qualifier [ChangedAbstract]
                error: Attempted to remove parameter name from parameter p in test.pkg.MyCollection.retainAll [ParameterNameChange]
                error: Method test.pkg.MyCollection.size has changed 'abstract' qualifier [ChangedAbstract]
                error: Method test.pkg.MyCollection.toArray has changed 'abstract' qualifier [ChangedAbstract]
                error: Method test.pkg.MyCollection.toArray has changed 'abstract' qualifier [ChangedAbstract]
                error: Attempted to remove parameter name from parameter p in test.pkg.MyCollection.toArray [ParameterNameChange]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class MyCollection<E> implements java.util.Collection<E> {
                    ctor public MyCollection();
                    method public boolean add(E! e);
                    method public boolean addAll(java.util.Collection<? extends E> c);
                    method public void clear();
                    method public boolean contains(Object! o);
                    method public boolean containsAll(java.util.Collection<?> c);
                    method public boolean isEmpty();
                    method public java.util.Iterator<E> iterator();
                    method public boolean remove(Object! o);
                    method public boolean removeAll(java.util.Collection<?> c);
                    method public boolean retainAll(java.util.Collection<?> c);
                    method public int size();
                    method public Object![] toArray();
                    method public <T> T![] toArray(T[] a);
                  }
                }
            """,
            signatureSource =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class MyCollection<E> implements java.util.Collection<E> kotlin.jvm.internal.markers.KMappedMarker {
                    ctor public MyCollection();
                    method public boolean contains(E element);
                    method public boolean containsAll(java.util.Collection<E!> elements);
                    method public int getSize();
                    method public boolean isEmpty();
                    method public java.util.Iterator<E> iterator();
                    property public int size;
                  }
                }
            """
        )
    }

    @Test
    fun `No issues using the same classpath class twice`() {
        check(
            apiClassResolution = ApiClassResolution.API_CLASSPATH,
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package test.pkg {
                    public class String1 extends java.lang.String {
                        method public boolean isEmpty();
                    }
                    public class String2 extends java.lang.String {
                        method public boolean isEmpty();
                    }
                }
            """,
            signatureSource =
                """
                // Signature format: 4.0
                package test.pkg {
                    public class String1 extends java.lang.String {}
                    public class String2 extends java.lang.String {}
                }
            """
        )
    }

    @Test
    fun `Avoid stack overflow for self-referential and cyclical annotation usage`() {
        val signature =
            """
            package test.pkg {
              @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.SelfReferenceAnnotation public @interface SelfReferenceAnnotation {}
              @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.CyclicalReferenceAnnotationB public @interface CyclicalReferenceAnnotationA {}
              @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @test.pkg.CyclicalReferenceAnnotationA public @interface CyclicalReferenceAnnotationB {}
              @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface MetaSuppressCompatibility {}
              @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface MetaHide {}
            }
            """
        check(
            checkCompatibilityApiReleased = signature,
            signatureSource = signature,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.MetaSuppressCompatibility"),
        )
    }

    @Test
    fun `Set all issues in the Compatibility category to error-level`() {
        check(
            expectedIssues =
                """
                load-api.txt:2: error: Added package test.pkg [AddedPackage]
            """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
            """,
            signatureSource =
                """
                // Signature format: 4.0
                package test.pkg {
                    public class String1 extends java.lang.String {}
                }
            """,
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility")
        )
    }

    @Test
    fun `Synthetic suppress compatibility annotation allows incompatible changes`() {
        check(
            checkCompatibilityApiReleased =
                """
                package androidx.benchmark.macro.junit4 {
                  @RequiresApi(28) @SuppressCompatibility @androidx.benchmark.macro.ExperimentalBaselineProfilesApi public final class BaselineProfileRule implements org.junit.rules.TestRule {
                    ctor public BaselineProfileRule();
                    method public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base, org.junit.runner.Description description);
                    method public void collectBaselineProfile(String packageName, kotlin.jvm.functions.Function1<? super androidx.benchmark.macro.MacrobenchmarkScope,kotlin.Unit> profileBlock);
                  }
                }
                """,
            signatureSource =
                """
                package androidx.benchmark.macro.junit4 {
                  @RequiresApi(28) public final class BaselineProfileRule implements org.junit.rules.TestRule {
                    ctor public BaselineProfileRule();
                    method public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement base, org.junit.runner.Description description);
                    method public void collect(String packageName, kotlin.jvm.functions.Function1<? super androidx.benchmark.macro.MacrobenchmarkScope,kotlin.Unit> profileBlock);
                  }
                }
                package androidx.benchmark.macro {
                  @kotlin.RequiresOptIn(message="The Baseline profile generation API is experimental.") @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.CLASS, kotlin.annotation.AnnotationTarget.FUNCTION}) public @interface ExperimentalBaselineProfilesApi {
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn")
        )
    }

    @Test
    fun `Removing @JvmDefaultWithCompatibility is an incompatible change`() {
        check(
            expectedIssues =
                "load-api.txt:3: error: Cannot remove @kotlin.jvm.JvmDefaultWithCompatibility annotation from class test.pkg.AnnotationRemoved: Incompatible change [RemovedJvmDefaultWithCompatibility]",
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package test.pkg {
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface AnnotationRemoved {
                    method public default void foo();
                  }
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface AnnotationStays {
                    method public default void foo();
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 4.0
                package test.pkg {
                  public interface AnnotationRemoved {
                    method public default void foo();
                  }
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface AnnotationStays {
                    method public default void foo();
                  }
                }
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `@JvmDefaultWithCompatibility check works with source files`() {
        check(
            expectedIssues =
                "src/test/pkg/AnnotationRemoved.kt:3: error: Cannot remove @kotlin.jvm.JvmDefaultWithCompatibility annotation from class test.pkg.AnnotationRemoved: Incompatible change [RemovedJvmDefaultWithCompatibility]",
            checkCompatibilityApiReleased =
                """
                // Signature format: 4.0
                package test.pkg {
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface AnnotationRemoved {
                    method public default void foo();
                  }
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface AnnotationStays {
                    method public default void foo();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface AnnotationRemoved {
                            fun foo() {}
                        }

                        @JvmDefaultWithCompatibility
                        interface AnnotationStays {
                            fun foo() {}
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `Changing return type to variable with equal bounds is compatible`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                // Signature format: 2.0
                package test.pkg {
                  public final class Foo {
                    method public <A extends java.lang.annotation.Annotation> A getAnnotation();
                    method public <A extends java.lang.annotation.Annotation> A[] getAnnotationArray();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public final class Foo {
                            public <T extends java.lang.annotation.Annotation> T getAnnotation() { return null; }
                            public <T extends java.lang.annotation.Annotation> T[] getAnnotationArray() { return null; }
                        }
                    """
                    )
                ),
        )
    }

    @Test
    fun `Changing return type to variable with unequal bounds is incompatible`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:4: error: Method test.pkg.Foo.getAnnotation has changed return type from A (extends java.lang.annotation.Annotation) to A (extends java.lang.String) [ChangedType]
                src/test/pkg/Foo.java:5: error: Method test.pkg.Foo.getAnnotationArray has changed return type from A (extends java.lang.annotation.Annotation)[] to A (extends java.lang.String)[] [ChangedType]
            """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 2.0
                package test.pkg {
                  public final class Foo {
                    method public <A extends java.lang.annotation.Annotation> A getAnnotation();
                    method public <A extends java.lang.annotation.Annotation> A[] getAnnotationArray();
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public final class Foo {
                            public <A extends java.lang.String> A getAnnotation() { return null; }
                            public <A extends java.lang.String> A[] getAnnotationArray() { return null; }
                        }
                    """
                    )
                ),
        )
    }

    @Test
    fun `Check compatibility against overridden method with type variable substitution`() {
        check(
            // The remove method isn't listed in this file, but it exists on Properties with return
            // type `java.lang.Object`.
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public class Properties extends test.pkg.Hashtable<java.lang.Object,java.lang.Object> {
                      }

                      public class Hashtable<K, V> {
                        method public V remove(Object);
                      }
                    }
                """,
            signatureSource =
                """
                    package test.pkg {
                      public class Properties extends test.pkg.Hashtable<java.lang.Object,java.lang.Object> {
                        method public Object remove(Object);
                      }

                      public class Hashtable<K, V> extends java.util.Dictionary<K,V> implements java.lang.Cloneable java.util.Map<K,V> java.io.Serializable {
                        method public V remove(Object);
                      }
                    }
                """,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test adding method with same name as method with type parameter`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class TestKt {
                        method public static <T> T! foo(T! target);
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            fun <T> foo(target: T) = target
                            fun foo(target: String) = target
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test that parent method with type parameter matches child override`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Child extends test.pkg.Parent<java.lang.Integer> {
                        method public void foo(Integer t);
                      }
                      public class Parent<T> {
                        method public void foo(T! t);
                      }
                    }
                """,
            signatureSource =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Child extends test.pkg.Parent<java.lang.Integer> {
                      }
                      public class Parent<T> {
                        method public void foo(T! t);
                      }
                    }
                """
        )
    }

    // TODO: Check method signatures changing incompatibly (look especially out for adding new
    // overloaded methods and comparator getting confused!)
    //   ..equals on the method items should actually be very useful!
}
