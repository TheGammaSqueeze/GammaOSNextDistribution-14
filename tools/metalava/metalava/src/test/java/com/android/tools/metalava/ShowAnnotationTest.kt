package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

/** Tests for the --show-annotation functionality */
class ShowAnnotationTest : DriverTest() {

    @Test
    fun `Basic showAnnotation test`() {
        check(
            includeSystemApiAnnotations = true,
            expectedIssues =
                "src/test/pkg/Foo.java:18: error: @SystemApi APIs must also be marked @hide: method test.pkg.Foo.method4() [UnhiddenSystemApi]",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    public class Foo {
                        public void method1() { }

                        /**
                         * @hide Only for use by WebViewProvider implementations
                         */
                        @SystemApi
                        public void method2() { }

                        /**
                         * @hide Always hidden
                         */
                        public void method3() { }

                        @SystemApi
                        public void method4() { }

                    }
                    """
                    ),
                    java(
                        """
                    package foo.bar;
                    public class Bar {
                    }
                """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            api =
                """
                package test.pkg {
                  public class Foo {
                    method public void method2();
                    method public void method4();
                  }
                }
                """
        )
    }

    @Test
    fun `Basic showAnnotation with showUnannotated test`() {
        check(
            includeSystemApiAnnotations = true,
            showUnannotated = true,
            expectedIssues =
                "src/test/pkg/Foo.java:18: error: @SystemApi APIs must also be marked @hide: method test.pkg.Foo.method4() [UnhiddenSystemApi]",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    public class Foo {
                        public void method1() { }

                        /**
                         * @hide Only for use by WebViewProvider implementations
                         */
                        @SystemApi
                        public void method2() { }

                        /**
                         * @hide Always hidden
                         */
                        public void method3() { }

                        @SystemApi
                        public void method4() { }

                    }
                    """
                    ),
                    java(
                        """
                    package foo.bar;
                    public class Bar {
                    }
                """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            api =
                """
                package foo.bar {
                  public class Bar {
                    ctor public Bar();
                  }
                }
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void method1();
                    method public void method2();
                    method public void method4();
                  }
                }
                """
        )
    }

    @Test
    fun `Check @TestApi handling`() {
        check(
            includeSystemApiAnnotations = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.TestApi;

                    /**
                     * Blah blah blah
                     * @hide
                     */
                    @TestApi
                    public class Bar {
                        public void test() {
                        }
                    }
                    """
                    ),

                    // This isn't necessary for this test, but doclava will ignore @hide classes
                    // marked
                    // with an annotation unless there is a public reference it to it from
                    // elsewhere.
                    // Include this here such that the checkDoclava1=true step produces any output.
                    java(
                        """
                    package test.pkg;
                    public class Usage {
                        public Bar bar;
                    }
                    """
                    ),
                    testApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.TestApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            api =
                """
                package test.pkg {
                  public class Bar {
                    ctor public Bar();
                    method public void test();
                  }
                }
                """
        )
    }

    @Test
    fun `Include interface-inherited fields in stubs`() {
        // When applying an annotations filter (-showAnnotation X), doclava
        // deliberately made the signature files *only* include annotated
        // elements, e.g. they're just showing the "diffs" between the base API
        // and the additional API made visible with annotations. However,
        // in the *stubs*, we have to include everything.
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg2;

                    import test.pkg1.MyParent;
                    public class MyChild extends MyParent {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg1;
                    import java.io.Closeable;
                    @SuppressWarnings("WeakerAccess")
                    public class MyParent implements MyConstants, Closeable {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg1;
                    interface MyConstants {
                        long CONSTANT1 = 12345;
                        long CONSTANT2 = 67890;
                        long CONSTANT3 = 42;
                    }
                    """
                    )
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg2;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyChild extends test.pkg1.MyParent {
                    public MyChild() { throw new RuntimeException("Stub!"); }
                    public static final long CONSTANT1 = 12345L; // 0x3039L
                    public static final long CONSTANT2 = 67890L; // 0x10932L
                    public static final long CONSTANT3 = 42L; // 0x2aL
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg1;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyParent implements java.io.Closeable {
                    public MyParent() { throw new RuntimeException("Stub!"); }
                    public static final long CONSTANT1 = 12345L; // 0x3039L
                    public static final long CONSTANT2 = 67890L; // 0x10932L
                    public static final long CONSTANT3 = 42L; // 0x2aL
                    }
                    """
                    )
                ),
            // Empty API: showUnannotated=false
            api =
                """
            """
                    .trimIndent(),
            includeSystemApiAnnotations = true,
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.TestApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                )
        )
    }

    @Test
    fun `No UnhiddenSystemApi warning for --show-single-annotations`() {
        check(
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    public class Foo {
                        public void method1() { }

                        /**
                         * @hide Only for use by WebViewProvider implementations
                         */
                        @SystemApi
                        public void method2() { }

                        /**
                         * @hide Always hidden
                         */
                        public void method3() { }

                        @SystemApi
                        public void method4() { }

                    }
                    """
                    ),
                    java(
                        """
                    package foo.bar;
                    public class Bar {
                    }
                """
                    ),
                    systemApiSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_SINGLE_ANNOTATION,
                    "android.annotation.SystemApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            api =
                """
                package test.pkg {
                  public class Foo {
                    method public void method2();
                    method public void method4();
                  }
                }
                """
        )
    }

    @Test
    fun `Can't expose item from a hidden parent`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;

                    /** @hide */
                    public class Class1 {
                        /** @hide */
                        @SystemApi
                        public void method1() { }

                        /** @hide */
                        @SystemApi
                        public static class InnerClass1 {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;

                    /** @hide */
                    @SystemApi
                    public class Class2 {
                        /** @hide */
                        public static class InnerClass2 {
                            /** @hide */
                            @SystemApi
                            public void method2() { }
                        }
                    }
                    """
                    ),
                    systemApiSource
                ),
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            expectedIssues =
                """
                    src/test/pkg/Class1.java:8: error: Attempting to unhide method test.pkg.Class1.method1(), but surrounding class test.pkg.Class1 is hidden and should also be annotated with @android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS) [ShowingMemberInHiddenClass]
                    src/test/pkg/Class1.java:12: error: Attempting to unhide class test.pkg.Class1.InnerClass1, but surrounding class test.pkg.Class1 is hidden and should also be annotated with @android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS) [ShowingMemberInHiddenClass]
                    src/test/pkg/Class2.java:11: error: Attempting to unhide method test.pkg.Class2.InnerClass2.method2(), but surrounding class test.pkg.Class2.InnerClass2 is hidden and should also be annotated with @android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS) [ShowingMemberInHiddenClass]
                """,
            expectedFail = DefaultLintErrorMessage,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `showAnnotation with parameters`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import androidx.annotation.RestrictTo;
                    import static androidx.annotation.RestrictTo.Scope.LIBRARY;
                    import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

                    public class Foo {
                        public void method1() { }

                        /**
                         * @hide restricted to this library group
                         */
                        @RestrictTo(LIBRARY_GROUP)
                        public void method2() { }

                        /**
                         * @hide restricted to this library
                         */
                        @RestrictTo(LIBRARY)
                        public void method3() { }

                        public void method4() { }
                    }
                    """
                    ),
                    restrictToSource
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_UNANNOTATED,
                    ARG_SHOW_ANNOTATION,
                    "androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)",
                    ARG_HIDE_PACKAGE,
                    "androidx.annotation"
                ),
            api =
                """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void method1();
                    method @RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP) public void method2();
                    method public void method4();
                  }
                }
                """
        )
    }

    @Test
    fun `showAnnotation with default parameters`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import test.annotation.Api;
                    import static test.annotation.Api.Type.A;
                    import static test.annotation.Api.Type.B;

                    public class Foo {
                        public void method1() { }

                        /** @hide */
                        @Api
                        public void method2() { }

                        /** @hide */
                        @Api(type=A)
                        public void method3() { }

                        /** @hide */
                        @Api(type=B)
                        public void method4() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.annotation;
                    public @interface Api {
                        enum Type {A, B}
                        Type type() default Type.A;
                    }
                    """
                    )
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_UNANNOTATED,
                    ARG_SHOW_ANNOTATION,
                    "test.annotation.Api(type=test.annotation.Api.Type.A)",
                    ARG_HIDE_PACKAGE,
                    "test.annotation"
                ),
            api =
                """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void method1();
                    method public void method2();
                    method public void method3();
                  }
                }
                """
        )
    }

    @Test
    fun `Testing parsing an annotation whose attribute references the annotated class`() {
        check(
            format = FileFormat.V3,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package androidx.room;

                    import androidx.annotation.IntDef;

                    @IntDef(OnConflictStrategy.REPLACE)
                    public @interface OnConflictStrategy {
                        int REPLACE = 1;
                    }
                    """
                    )
                ),
            expectedIssues =
                """
                src/androidx/room/OnConflictStrategy.java:3: info: Unresolved import: `androidx.annotation.IntDef` [UnresolvedImport]
                """,
            api =
                """
                // Signature format: 3.0
                package androidx.room {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface OnConflictStrategy {
                    field public static final int REPLACE = 1; // 0x1
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_ANNOTATION, "androidx.annotation.IntDef")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Testing that file order does not affect output`() {
        check(
            format = FileFormat.V3,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package a;

                    import androidx.annotation.RestrictTo;
                    import androidx.annotation.RestrictTo.Scope;

                    /**
                     * @hide
                     */
                    @RestrictTo(Scope.LIBRARY_GROUP)
                    public class Example1<T> {
                        public class Child<T> {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    /**
                     * @hide
                     */
                    package a;
                    """
                    ),
                    java(
                        """
                    package a;

                    import androidx.annotation.RestrictTo;
                    import androidx.annotation.RestrictTo.Scope;

                    /**
                     * @hide
                     */
                    @RestrictTo(Scope.LIBRARY_GROUP)
                    public class Example2<T> {
                        public class Child<T> {
                        }
                    }
                    """
                    ),
                    restrictToSource
                ),
            expectedIssues = null,
            api =
                """
                // Signature format: 3.0
                package a {
                  @RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP) public class Example1<T> {
                    ctor public Example1();
                  }
                  public class Example1.Child<T> {
                    ctor public Example1.Child();
                  }
                  @RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP) public class Example2<T> {
                    ctor public Example2();
                  }
                  public class Example2.Child<T> {
                    ctor public Example2.Child();
                  }
                }
                """,
            extraArguments =
                arrayOf(
                    ARG_SHOW_ANNOTATION,
                    "androidx.annotation.RestrictTo",
                    ARG_HIDE_PACKAGE,
                    "androidx.annotation",
                    ARG_SHOW_UNANNOTATED
                )
        )
    }

    @Test
    fun `new class in the same package while parsing a class`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import static test.pkg.AClass.SOME_VALUE;
                    import test.annotation.Api;
                    /** @hide */
                    @Api(SOME_VALUE)
                    public class Foo {
                        public void foo() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.annotation;

                    public @interface Api {
                        String value();
                    }
                    """
                    )
                ),
            classpath =
                arrayOf(
                    /* The following source file, compiled, and root folder jar'ed and stored as base64 gzip:
                       package test.pkg;
                       public class AClass {
                           public static final String SOME_VALUE = "some_value";
                       }
                    */
                    base64gzip(
                        "test.jar",
                        "" +
                            "H4sICDVE/F0AA3Rlc3QuamFyAAvwZmYRYeDg4GB4kDrFnwEJcDKwMPi6hjjq" +
                            "evq56f87xcDAzBDgzc4BkmKCKgnAqVkEiOGafR39PN1cg0P0fN0++5457eOt" +
                            "q3eR11tX69yZ85uDDK4YP3hapOflq+Ppe7F0FQtnxAvJI9JREq+WPX++/PmS" +
                            "V9NmamfsaJ7JNa/yZNWsPTF7YsCuaPPd8c8FaIcH1BVcDAxAl91AcwUrEJek" +
                            "Fpfo41bCCVNSkJ2uj/APujJRZGWOzjmJxcV6ySDS1W9j0CEHkTviCgcZmkpE" +
                            "xMqyEqWsZdNn8IQEchnoFh+cfSZxh8VO7v+pR3ta5R+4G+7pZj377nbt57L7" +
                            "9z8+Fz+gfEwxagePAY+BUsvpo69z5vpN/i6ZOCfnaJxL64XKLVumXvopnCgw" +
                            "/VbEwlytrqiyQtcP86eGfWu9sGuvN+fbHX8mtCQu2lKzunzmmjAl7SXc+q+z" +
                            "lJ+f6rxnotdiyL9uUb+e3o9DXsmL15+3+XVf0O05u9jNsDyd9hlF6terw/uk" +
                            "K/7YOMxY0Mu3+fwp5wPCO32OTZj72o157m/mF7stXxj+6n1p+E1abg44tP1r" +
                            "E34/BIUCIyi0GZlEGFBjnQkemCwMqAAlAaFrRY5EERRttjiSD8gELgbckY0A" +
                            "hxFRj1sLJ4qWZ6hJAeFWkDbkIBBF0cbLiCNpBHizskEcxsqgBVRkCA4mAAHr" +
                            "K7BxAwAA"
                    )
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_UNANNOTATED,
                    ARG_SHOW_ANNOTATION,
                    "test.annotation.Api",
                    ARG_HIDE_PACKAGE,
                    "test.annotation"
                ),
            api =
                """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void foo();
                  }
                }
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check @PublishedApi handling`() {
        check(
            format = FileFormat.V3,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg
                    /**
                    * @suppress
                    */
                    @PublishedApi
                    internal class WeAreSoCool()
                    """
                    ),
                    publishedApiSource
                ),
            extraArguments = arrayOf(ARG_SHOW_ANNOTATION, "kotlin.PublishedApi"),
            api =
                """
                // Signature format: 3.0
                package test.pkg {
                  @kotlin.PublishedApi internal final class WeAreSoCool {
                    ctor public WeAreSoCool();
                  }
                }
                """
        )
    }

    @Test
    fun `Methods inherit showAnnotations but fields and classes don't`() {
        // "ShowAnnotations" are implicitly inherited between functions, but between
        // fields or classes. In this test:
        // - Class2.member() is implicitly a @SystemApi, so the stub class includes it.
        // (Though it's not included in the API file because it's redundant.)
        // - However, there's no inheritance for fields, so Class2.FIELD is *not* in the stub class,
        // and if a client refers to Class2.FIELD, that resolves to Class*1*.FIELD.
        // - Class3 is (very naturally) hidden even though the super class is visible.
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;

                    /** @hide */
                    @SystemApi
                    public class Class1 {
                        /** @hide */
                        @SystemApi
                        public static final String FIELD = "Class1.FIELD";

                        /** @hide */
                        @SystemApi
                        public void member() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;

                    /** @hide */
                    @SystemApi
                    public class Class2 extends Class1 {
                        /** @hide */
                        public static final String FIELD = "Class2.FIELD";

                        /** @hide */
                        public void member() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;

                    /** @hide */
                    public class Class3 extends Class1 {
                    }
                    """
                    ),
                    systemApiSource
                ),
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            expectedIssues = """
                """,
            api =
                """
                package test.pkg {
                  public class Class1 {
                    ctor public Class1();
                    method public void member();
                    field public static final String FIELD = "Class1.FIELD";
                  }
                  public class Class2 extends test.pkg.Class1 {
                    ctor public Class2();
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Class1 {
                    public Class1() { throw new RuntimeException("Stub!"); }
                    /** @hide */
                    public void member() { throw new RuntimeException("Stub!"); }
                    /** @hide */
                    public static final java.lang.String FIELD = "Class1.FIELD";
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Class2 extends test.pkg.Class1 {
                    public Class2() { throw new RuntimeException("Stub!"); }
                    /** @hide */
                    public void member() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Mixing for stubs only and single show annotations`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.SystemApi;
                    import android.annotation.TestApi;
                    public class Foo {
                        /**
                         * @hide
                         */
                        @SystemApi
                        @TestApi
                        public void method1() { }

                        /**
                         * @hide
                         */
                        @TestApi
                        @SystemApi
                        public void method2() { }
                    }
                    """
                    ),
                    systemApiSource,
                    testApiSource,
                ),
            extraArguments =
                arrayOf(
                    ARG_SHOW_SINGLE_ANNOTATION,
                    "android.annotation.SystemApi",
                    ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION,
                    "android.annotation.TestApi",
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            api =
                """
                package test.pkg {
                  public class Foo {
                    method public void method1();
                    method public void method2();
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        "test/pkg/Foo.java",
                        """
                  package test.pkg;
                  @SuppressWarnings({"unchecked", "deprecation", "all"})
                  public class Foo {
                  public Foo() { throw new RuntimeException("Stub!"); }
                  /**
                   * @hide
                   */
                  public void method1() { throw new RuntimeException("Stub!"); }
                  /**
                   * @hide
                   */
                  public void method2() { throw new RuntimeException("Stub!"); }
                  }
              """
                            .trimIndent()
                    ),
                ),
        )
    }
}
