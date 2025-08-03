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

import com.android.tools.metalava.ARG_EXCLUDE_ALL_ANNOTATIONS
import com.android.tools.metalava.ARG_EXCLUDE_ANNOTATION
import com.android.tools.metalava.ARG_HIDE_PACKAGE
import com.android.tools.metalava.ARG_PASS_THROUGH_ANNOTATION
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.libcoreNonNullSource
import com.android.tools.metalava.model.SUPPORT_TYPE_USE_ANNOTATIONS
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.requiresApiSource
import com.android.tools.metalava.supportParameterName
import com.android.tools.metalava.testing.java
import org.junit.Test

@SuppressWarnings("ALL")
class StubsAnnotationTest : AbstractStubsTest() {
    @Test
    fun `Generate stubs for annotation type`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private"
        // methods
        // in the interface are taken to be public etc)
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import static java.lang.annotation.ElementType.*;
                    import java.lang.annotation.*;
                    @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE})
                    @Retention(RetentionPolicy.CLASS)
                    public @interface Foo {
                        String value();
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.LOCAL_VARIABLE})
                public @interface Foo {
                public java.lang.String value();
                }
                """
        )
    }

    @Test
    fun `Remove Hidden Annotations`() {
        // When APIs reference annotations that are hidden, make sure the're excluded from the stubs
        // and
        // signature files
        checkStubs(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Foo {
                        public void foo(int p1, @MyAnnotation("publicParameterName") java.util.Map<java.lang.String, @MyAnnotation("Something") String> p2) {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import java.lang.annotation.*;
                    import static java.lang.annotation.ElementType.*;
                    import static java.lang.annotation.RetentionPolicy.SOURCE;
                    /** @hide */
                    @SuppressWarnings("WeakerAccess")
                    @Retention(SOURCE)
                    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
                    public @interface MyAnnotation {
                        String value();
                    }
                    """
                    )
                ),
            api =
                if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                    """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void foo(int, java.util.Map<java.lang.String!,java.lang.String!>!);
                  }
                }
                """
                } else {
                    """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void foo(int, java.util.Map<java.lang.String,java.lang.String>);
                  }
                }
                """
                },
            source =
                if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                    """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                public void foo(int p1, java.util.Map<java.lang.String, java.lang.String> p2) { throw new RuntimeException("Stub!"); }
                }
                """
                } else {
                    """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                public void foo(int p1, java.util.Map<java.lang.String,java.lang.String> p2) { throw new RuntimeException("Stub!"); }
                }
                """
                }
        )
    }

    @Test
    fun `Rewrite unknown nullability annotations as sdk stubs`() {
        check(
            format = FileFormat.V2,
            checkCompilation = true,
            sourceFiles =
                arrayOf(
                    java(
                        "package my.pkg;\n" +
                            "public class String {\n" +
                            "public String(@other.NonNull char[] value) { throw new RuntimeException(\"Stub!\"); }\n" +
                            "}\n"
                    )
                ),
            expectedIssues = "",
            api =
                """
                    package my.pkg {
                      public class String {
                        ctor public String(@NonNull char[]);
                      }
                    }
                    """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package my.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class String {
                    public String(@android.annotation.NonNull char[] value) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Rewrite unknown nullability annotations as doc stubs`() {
        check(
            format = FileFormat.V2,
            checkCompilation = true,
            sourceFiles =
                arrayOf(
                    java(
                        "package my.pkg;\n" +
                            "public class String {\n" +
                            "public String(@other.NonNull char[] value) { throw new RuntimeException(\"Stub!\"); }\n" +
                            "}\n"
                    )
                ),
            expectedIssues = "",
            api =
                """
                    package my.pkg {
                      public class String {
                        ctor public String(@NonNull char[]);
                      }
                    }
                    """,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                        package my.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class String {
                        public String(@androidx.annotation.NonNull char[] value) { throw new RuntimeException("Stub!"); }
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `Rewrite libcore annotations`() {
        check(
            checkCompilation = true,
            sourceFiles =
                arrayOf(
                    java(
                        "package my.pkg;\n" +
                            "public class String {\n" +
                            "public String(char @libcore.util.NonNull [] value) { throw new RuntimeException(\"Stub!\"); }\n" +
                            "}\n"
                    )
                ),
            expectedIssues = "",
            api =
                """
                    package my.pkg {
                      public class String {
                        ctor public String(char[]);
                      }
                    }
                    """,
            stubFiles =
                if (SUPPORT_TYPE_USE_ANNOTATIONS) {
                    arrayOf(
                        java(
                            """
                        package my.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class String {
                        public String(char @androidx.annotation.NonNull [] value) { throw new RuntimeException("Stub!"); }
                        }
                        """
                        )
                    )
                } else {
                    arrayOf(
                        java(
                            """
                        package my.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class String {
                        public String(char[] value) { throw new RuntimeException("Stub!"); }
                        }
                        """
                        )
                    )
                }
        )
    }

    @Test
    fun `Pass through libcore annotations`() {
        check(
            format = FileFormat.V2,
            checkCompilation = true,
            extraArguments = arrayOf(ARG_PASS_THROUGH_ANNOTATION, "libcore.util.NonNull"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package my.pkg;
                    public class String {
                    public String(@libcore.util.NonNull char[] value) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    libcoreNonNullSource
                ),
            expectedIssues = "",
            api =
                """
                    package libcore.util {
                      @java.lang.annotation.Documented @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE}) public @interface NonNull {
                      }
                    }
                    package my.pkg {
                      public class String {
                        ctor public String(@libcore.util.NonNull char[]);
                      }
                    }
                    """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package my.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class String {
                    public String(@libcore.util.NonNull char[] value) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Pass through multiple annotations`() {
        checkStubs(
            extraArguments =
                arrayOf(
                    ARG_PASS_THROUGH_ANNOTATION,
                    "androidx.annotation.RequiresApi,androidx.annotation.Nullable",
                    ARG_HIDE_PACKAGE,
                    "androidx.annotation"
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package my.pkg;
                    public class MyClass {
                        @androidx.annotation.RequiresApi(21)
                        public void testMethod() {}
                        @androidx.annotation.Nullable
                        public String anotherTestMethod() { return null; }
                    }
                    """
                    ),
                    supportParameterName,
                    requiresApiSource,
                    androidxNullableSource
                ),
            source =
                """
                package my.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass {
                public MyClass() { throw new RuntimeException("Stub!"); }
                @androidx.annotation.RequiresApi(21)
                public void testMethod() { throw new RuntimeException("Stub!"); }
                @androidx.annotation.Nullable
                public java.lang.String anotherTestMethod() { throw new RuntimeException("Stub!"); }
                }
                 """
        )
    }

    @Test
    fun `Skip RequiresApi annotation`() {
        check(
            extraArguments = arrayOf(ARG_EXCLUDE_ANNOTATION, "androidx.annotation.RequiresApi"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package my.pkg;
                    public class MyClass {
                        @androidx.annotation.RequiresApi(21)
                        public void testMethod() {}
                    }
                    """
                    ),
                    requiresApiSource
                ),
            expectedIssues = "",
            api =
                """
                    package androidx.annotation {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.CONSTRUCTOR}) public @interface RequiresApi {
                        method public abstract int api() default 1;
                        method public abstract int value() default 1;
                      }
                    }
                    package my.pkg {
                      public class MyClass {
                        ctor public MyClass();
                        method public void testMethod();
                      }
                    }
                    """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package my.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    public void testMethod() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Annotation default values`() {
        checkStubs(
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

                    /**
                     * This annotation can be used to mark fields and methods to be dumped by
                     * the view server. Only non-void methods with no arguments can be annotated
                     * by this annotation.
                     */
                    @Target({ElementType.FIELD, ElementType.METHOD})
                    @Retention(RetentionPolicy.RUNTIME)
                    public @interface ExportedProperty {
                        /**
                         * When resolveId is true, and if the annotated field/method return value
                         * is an int, the value is converted to an Android's resource name.
                         *
                         * @return true if the property's value must be transformed into an Android
                         * resource name, false otherwise
                         */
                        boolean resolveId() default false;
                        String prefix() default "";
                        String category() default "";
                        boolean formatToHexString() default false;
                        boolean hasAdjacentMapping() default false;
                        Class<? extends Number> myCls() default Integer.class;
                        char[] letters1() default {};
                        char[] letters2() default {'a', 'b', 'c'};
                        double from() default Double.NEGATIVE_INFINITY;
                        double fromWithCast() default (double)Float.NEGATIVE_INFINITY;
                        InnerAnnotation value() default @InnerAnnotation;
                        char letter() default 'a';
                        int integer() default 1;
                        long large_integer() default 1L;
                        float floating() default 1.0f;
                        double large_floating() default 1.0;
                        byte small() default 1;
                        short medium() default 1;
                        int math() default 1+2*3;
                        @InnerAnnotation
                        int unit() default PX;
                        int DP = 0;
                        int PX = 1;
                        int SP = 2;
                        @Retention(SOURCE)
                        @interface InnerAnnotation {
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD}) public @interface ExportedProperty {
                    method public abstract String category() default "";
                    method public abstract float floating() default 1.0f;
                    method public abstract boolean formatToHexString() default false;
                    method public abstract double from() default java.lang.Double.NEGATIVE_INFINITY;
                    method public abstract double fromWithCast() default (double)java.lang.Float.NEGATIVE_INFINITY;
                    method public abstract boolean hasAdjacentMapping() default false;
                    method public abstract int integer() default 1;
                    method public abstract double large_floating() default 1.0;
                    method public abstract long large_integer() default 1L;
                    method public abstract char letter() default 'a';
                    method public abstract char[] letters1() default {};
                    method public abstract char[] letters2() default {'a', 'b', 'c'};
                    method public abstract int math() default 7;
                    method public abstract short medium() default 1;
                    method public abstract Class<? extends java.lang.Number!> myCls() default java.lang.Integer.class;
                    method public abstract String prefix() default "";
                    method public abstract boolean resolveId() default false;
                    method public abstract byte small() default 1;
                    method @test.pkg.ExportedProperty.InnerAnnotation public abstract int unit() default test.pkg.ExportedProperty.PX;
                    method public abstract test.pkg.ExportedProperty.InnerAnnotation value() default @test.pkg.ExportedProperty.InnerAnnotation;
                    field public static final int DP = 0; // 0x0
                    field public static final int PX = 1; // 0x1
                    field public static final int SP = 2; // 0x2
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) public static @interface ExportedProperty.InnerAnnotation {
                  }
                }
            """,
            source =
                """
                package test.pkg;
                /**
                 * This annotation can be used to mark fields and methods to be dumped by
                 * the view server. Only non-void methods with no arguments can be annotated
                 * by this annotation.
                 */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                @java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD})
                public @interface ExportedProperty {
                /**
                 * When resolveId is true, and if the annotated field/method return value
                 * is an int, the value is converted to an Android's resource name.
                 *
                 * @return true if the property's value must be transformed into an Android
                 * resource name, false otherwise
                 */
                public boolean resolveId() default false;
                public java.lang.String prefix() default "";
                public java.lang.String category() default "";
                public boolean formatToHexString() default false;
                public boolean hasAdjacentMapping() default false;
                public java.lang.Class<? extends java.lang.Number> myCls() default java.lang.Integer.class;
                public char[] letters1() default {};
                public char[] letters2() default {'a', 'b', 'c'};
                public double from() default java.lang.Double.NEGATIVE_INFINITY;
                public double fromWithCast() default (double)java.lang.Float.NEGATIVE_INFINITY;
                public test.pkg.ExportedProperty.InnerAnnotation value() default @test.pkg.ExportedProperty.InnerAnnotation;
                public char letter() default 'a';
                public int integer() default 1;
                public long large_integer() default 1L;
                public float floating() default 1.0f;
                public double large_floating() default 1.0;
                public byte small() default 1;
                public short medium() default 1;
                public int math() default 7;
                public int unit() default test.pkg.ExportedProperty.PX;
                public static final int DP = 0; // 0x0
                public static final int PX = 1; // 0x1
                public static final int SP = 2; // 0x2
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
                public static @interface InnerAnnotation {
                }
                }
                """
        )
    }

    @Test
    fun `Annotation metadata in stubs`() {
        checkStubs(
            skipEmitPackages = emptyList(),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package com.android.metalava.test;

                    import java.lang.annotation.*;

                    @Target(ElementType.METHOD)
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface MyAnnotation {
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                package com.android.metalava.test;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)
                @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
                public @interface MyAnnotation {
                }
                """
        )
    }

    @Test
    fun `Ensure we emit both deprecated javadoc and annotation with exclude-all-annotations`() {
        check(
            extraArguments = arrayOf(ARG_EXCLUDE_ALL_ANNOTATIONS),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class Foo {
                        /**
                         * @deprecated Use checkPermission instead.
                         */
                        @Deprecated
                        protected boolean inClass(String name) {
                            return false;
                        }
                    }
                    """
                    )
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    /**
                     * @deprecated Use checkPermission instead.
                     */
                    @Deprecated
                    protected boolean inClass(java.lang.String name) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Ensure we emit runtime and deprecated annotations in stubs with exclude-annotations`() {
        check(
            extraArguments = arrayOf(ARG_EXCLUDE_ALL_ANNOTATIONS),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /** @deprecated */
                    @MySourceRetentionAnnotation
                    @MyClassRetentionAnnotation
                    @MyRuntimeRetentionAnnotation
                    @Deprecated
                    public class Foo {
                        private Foo() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.SOURCE;
                    @Retention(SOURCE)
                    public @interface MySourceRetentionAnnotation {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.CLASS;
                    @Retention(CLASS)
                    public @interface MyClassRetentionAnnotation {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.RUNTIME;
                    @Retention(RUNTIME)
                    public @interface MyRuntimeRetentionAnnotation {
                    }
                    """
                    )
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /** @deprecated */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @Deprecated
                    @test.pkg.MyRuntimeRetentionAnnotation
                    public class Foo {
                    Foo() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Ensure we include class and runtime and not source annotations in stubs with include-annotations`() {
        check(
            extraArguments = arrayOf("--include-annotations"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /** @deprecated */
                    @MySourceRetentionAnnotation
                    @MyClassRetentionAnnotation
                    @MyRuntimeRetentionAnnotation
                    @Deprecated
                    public class Foo {
                        private Foo() {}
                        protected int foo;
                        public void bar();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.SOURCE;
                    @Retention(SOURCE)
                    public @interface MySourceRetentionAnnotation {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.CLASS;
                    @Retention(CLASS)
                    public @interface MyClassRetentionAnnotation {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import static java.lang.annotation.RetentionPolicy.RUNTIME;
                    @Retention(RUNTIME)
                    public @interface MyRuntimeRetentionAnnotation {
                    }
                    """
                    )
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    /** @deprecated */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @Deprecated
                    @test.pkg.MyClassRetentionAnnotation
                    @test.pkg.MyRuntimeRetentionAnnotation
                    public class Foo {
                    Foo() { throw new RuntimeException("Stub!"); }
                    @Deprecated
                    public void bar() { throw new RuntimeException("Stub!"); }
                    @Deprecated protected int foo;
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Annotation nested rewriting`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import android.view.Gravity;

                    public class ActionBar {
                        @ViewDebug.ExportedProperty(category = "layout", mapping = {
                                @ViewDebug.IntToString(from =  -1,                       to = "NONE"),
                                @ViewDebug.IntToString(from = Gravity.NO_GRAVITY,        to = "NONE"),
                                @ViewDebug.IntToString(from = Gravity.TOP,               to = "TOP"),
                                @ViewDebug.IntToString(from = Gravity.BOTTOM,            to = "BOTTOM"),
                        })
                        public int gravity = Gravity.NO_GRAVITY;
                    }
                    """
                    ),
                    java(
                        """
                    package android.view;

                    public class Gravity {
                        public static final int NO_GRAVITY = 0;
                        public static final int TOP = 1;
                        public static final int BOTTOM = 2;
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.lang.annotation.Target;

                    public class ViewDebug {
                        @Target({ElementType.FIELD, ElementType.METHOD})
                        @Retention(RetentionPolicy.RUNTIME)
                        public @interface ExportedProperty {
                            boolean resolveId() default false;
                            IntToString[] mapping() default {};
                            IntToString[] indexMapping() default {};
                            boolean deepExport() default false;
                            String prefix() default "";
                            String category() default "";
                            boolean formatToHexString() default false;
                            boolean hasAdjacentMapping() default false;
                        }
                        @Target({ElementType.TYPE})
                        @Retention(RetentionPolicy.RUNTIME)
                        public @interface IntToString {
                            int from();
                            String to();
                        }
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class ActionBar {
                public ActionBar() { throw new RuntimeException("Stub!"); }
                @test.pkg.ViewDebug.ExportedProperty(category="layout", mapping={@test.pkg.ViewDebug.IntToString(from=0xffffffff, to="NONE"), @test.pkg.ViewDebug.IntToString(from=android.view.Gravity.NO_GRAVITY, to="NONE"), @test.pkg.ViewDebug.IntToString(from=android.view.Gravity.TOP, to="TOP"), @test.pkg.ViewDebug.IntToString(from=android.view.Gravity.BOTTOM, to="BOTTOM")}) public int gravity = 0; // 0x0
                }
                """
        )
    }
}
