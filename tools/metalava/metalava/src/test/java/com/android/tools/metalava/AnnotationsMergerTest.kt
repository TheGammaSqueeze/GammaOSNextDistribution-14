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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.ARG_WARNING
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.java
import org.junit.Test

class AnnotationsMergerTest : DriverTest() {

    // TODO: Test what happens when we have conflicting data
    //   - NULLABLE_SOURCE on one non null on the other
    //   - annotation specified with different parameters (e.g @Size(4) vs @Size(6))
    // Test with jar file

    @Test
    fun `Signature files contain annotations`() {
        check(
            format = FileFormat.V2,
            includeSystemApiAnnotations = false,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.NonNull;
                    import androidx.annotation.Nullable;
                    import android.annotation.IntRange;
                    import androidx.annotation.UiThread;

                    @UiThread
                    public class MyTest {
                        public @Nullable Number myNumber;
                        public @Nullable Double convert(@NonNull Float f) { return null; }
                        public @IntRange(from=10,to=20) int clamp(int i) { return 10; }
                    }"""
                    ),
                    uiThreadSource,
                    intRangeAnnotationSource,
                    androidxNonNullSource,
                    androidxNullableSource
                ),
            // Skip the annotations themselves from the output
            extraArguments =
                arrayOf(
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                    ARG_HIDE_PACKAGE,
                    "androidx.annotation",
                ),
            api =
                """
                package test.pkg {
                  @UiThread public class MyTest {
                    ctor public MyTest();
                    method @IntRange(from=10, to=20) public int clamp(int);
                    method @Nullable public Double convert(@NonNull Float);
                    field @Nullable public Number myNumber;
                  }
                }
                """
        )
    }

    @Test
    fun `Merged class and method annotations with no arguments`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class MyTest {
                        public Number myNumber;
                        public Double convert(Float f) { return null; }
                        public int clamp(int i) { return 10; }
                    }
                    """
                    )
                ),
            mergeXmlAnnotations =
                """<?xml version="1.0" encoding="UTF-8"?>
                <root>
                  <item name="test.pkg.MyTest">
                    <annotation name="androidx.annotation.UiThread" />
                  </item>
                  <item name="test.pkg.MyTest java.lang.Double convert(java.lang.Float)">
                    <annotation name="androidx.annotation.Nullable" />
                  </item>
                  <item name="test.pkg.MyTest java.lang.Double convert(java.lang.Float) 0">
                    <annotation name="androidx.annotation.NonNull" />
                  </item>
                  <item name="test.pkg.MyTest myNumber">
                    <annotation name="androidx.annotation.Nullable" />
                  </item>
                  <item name="test.pkg.MyTest int clamp(int)">
                    <annotation name="androidx.annotation.IntRange">
                      <val name="from" val="10" />
                      <val name="to" val="20" />
                    </annotation>
                  </item>
                  <item name="test.pkg.MyTest int clamp(int) 0">
                    <annotation name='org.jetbrains.annotations.Range'>
                      <val name="from" val="-1"/>
                      <val name="to" val="java.lang.Integer.MAX_VALUE"/>
                    </annotation>
                  </item>
                  </root>
                """,
            api =
                """
                package test.pkg {
                  @UiThread public class MyTest {
                    ctor public MyTest();
                    method @IntRange(from=10, to=20) public int clamp(@IntRange(from=-1L, to=java.lang.Integer.MAX_VALUE) int);
                    method @Nullable public Double convert(@NonNull Float);
                    field @Nullable public Number myNumber;
                  }
                }
                """
        )
    }

    @Test
    fun `Merge signature files`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public interface Appendable {
                        Appendable append(CharSequence csq) throws IOException;
                    }
                    """
                    )
                ),
            mergeSignatureAnnotations =
                """
                // Signature format: 3.0
                package test.pkg {
                  public interface Appendable {
                    method public test.pkg.Appendable append(java.lang.CharSequence?);
                    method public test.pkg.Appendable append2(java.lang.CharSequence?);
                    method @Deprecated public java.lang.String! reverse(java.lang.String!);
                  }
                  @Deprecated public interface RandomClass {
                    method @Deprecated public test.pkg.Appendable append(java.lang.CharSequence);
                  }
                }
                """,
            api =
                """
                package test.pkg {
                  public interface Appendable {
                    method @NonNull public test.pkg.Appendable append(@Nullable CharSequence);
                  }
                }
                """,
            expectedIssues =
                """
                merged-annotations.txt:5: warning: qualifier annotations were given for method test.pkg.Appendable.append2(CharSequence) but no matching item was found [UnmatchedMergeAnnotation]
                merged-annotations.txt:6: warning: qualifier annotations were given for method test.pkg.Appendable.reverse(String) but no matching item was found [UnmatchedMergeAnnotation]
                merged-annotations.txt:8: warning: qualifier annotations were given for class test.pkg.RandomClass but no matching item was found [UnmatchedMergeAnnotation]
            """,
            extraArguments = arrayOf(ARG_WARNING, Issues.UNMATCHED_MERGE_ANNOTATION.name)
        )
    }

    @Test
    fun `Merge qualifier annotations from Java stub files`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public interface Appendable {
                        Appendable append(CharSequence csq) throws IOException;
                    }
                    """
                    ),
                    libcoreNonNullSource,
                    libcoreNullableSource
                ),
            mergeJavaStubAnnotations =
                """
                package test.pkg;

                import libcore.util.NonNull;
                import libcore.util.Nullable;

                public interface Appendable {
                    @NonNull Appendable append(@Nullable java.lang.CharSequence csq);
                    @NonNull String notPresentWithAnnotations();
                    void notPresentWithoutAnnotations();
                }
                """,
            api =
                """
                package test.pkg {
                  public interface Appendable {
                    method @NonNull public test.pkg.Appendable append(@Nullable CharSequence);
                  }
                }
                """,
            extraArguments =
                arrayOf(
                    ARG_HIDE_PACKAGE,
                    "libcore.util",
                    ARG_WARNING,
                    Issues.UNMATCHED_MERGE_ANNOTATION.name,
                ),
            expectedIssues =
                """
                    qualifier/test/pkg/Appendable.java:8: warning: qualifier annotations were given for method test.pkg.Appendable.notPresentWithAnnotations() but no matching item was found [UnmatchedMergeAnnotation]
                """,
        )
    }

    @Test
    fun `Merge qualifier annotations from Java stub files onto stubs that are not in the API signature file`() {
        check(
            format = FileFormat.V2,
            includeSystemApiAnnotations = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public interface Appendable {
                        Appendable append(CharSequence csq) throws IOException;
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    /** @hide */
                    @android.annotation.TestApi
                    public interface ForTesting {
                        void foo();
                    }
                    """
                    ),
                    libcoreNonNullSource,
                    libcoreNullableSource
                ),
            mergeJavaStubAnnotations =
                """
                package test.pkg;

                import libcore.util.NonNull;
                import libcore.util.Nullable;

                public interface Appendable {
                    @NonNull Appendable append(@Nullable java.lang.CharSequence csq);
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface Appendable {
                    @android.annotation.NonNull
                    public test.pkg.Appendable append(@android.annotation.Nullable java.lang.CharSequence csq);
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface ForTesting {
                    public void foo();
                    }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public interface ForTesting {
                    method public void foo();
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "libcore.util")
        )
    }

    @Test
    fun `Merge type use qualifier annotations from Java stub files`() {
        // See b/123223339
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                package test.pkg;

                public class Test {
                    private Test() { }
                    public void foo(Object... args) { }
                }
                """
                    ),
                    libcoreNonNullSource,
                    libcoreNullableSource
                ),
            mergeJavaStubAnnotations =
                """
                package test.pkg;

                public class Test {
                    public void foo(java.lang.@libcore.util.Nullable Object @libcore.util.NonNull ... args) { throw new RuntimeException("Stub!"); }
                }
                """,
            api =
                """
                package test.pkg {
                  public class Test {
                    method public void foo(@NonNull java.lang.Object...);
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "libcore.util")
        )
    }

    @Test
    fun `Merge qualifier annotations from Java stub files making sure they apply to public members of hidden superclasses`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    class HiddenSuperClass {
                        @Override public String publicMethod(Object object) {return "";}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class PublicClass extends HiddenSuperClass {
                    }
                    """
                    ),
                    libcoreNonNullSource,
                    libcoreNullableSource
                ),
            mergeJavaStubAnnotations =
                """
                package test.pkg;

                import libcore.util.NonNull;
                import libcore.util.Nullable;

                public class PublicClass {
                    @NonNull public @NonNull String publicMethod(@Nullable Object object) {return "";}
                }
                """,
            api =
                """
                package test.pkg {
                  public class PublicClass {
                    ctor public PublicClass();
                    method @NonNull public String publicMethod(@Nullable Object);
                  }
                }
                """,
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "libcore.util")
        )
    }

    @Test
    fun `Merge inclusion annotations from Java stub files`() {
        check(
            format = FileFormat.V2,
            expectedIssues =
                """
                    inclusion/src/test/pkg/Example.java:13: warning: inclusion annotations were given for method test.pkg.HiddenExample.notPresentWithAnnotations() but no matching item was found [UnmatchedMergeAnnotation]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        "src/test/pkg/Example.annotated.java",
                        """
                    package test.pkg;

                    public interface Example {
                        void aNotAnnotated();
                        void bHidden();
                        void cShown();
                    }
                    """
                    ),
                    java(
                        "src/test/pkg/HiddenExample.annotated.java",
                        """
                    package test.pkg;

                    public interface HiddenExample {
                        void method();
                    }
                    """
                    )
                ),
            hideAnnotations = arrayOf("test.annotation.Hide"),
            showAnnotations = arrayOf("test.annotation.Show"),
            showUnannotated = true,
            mergeInclusionAnnotations =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface Example {
                                void aNotAnnotated();
                                @test.annotation.Hide void bHidden();
                                @test.annotation.Hide @test.annotation.Show void cShown();
                            }

                            @test.annotation.Hide
                            public interface HiddenExample {
                                void method();
                                @test.annotation.Hide
                                void notPresentWithAnnotations();
                                void notPresentWithoutAnnotations();
                            }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  public interface Example {
                    method public void aNotAnnotated();
                    method public void cShown();
                  }
                }
                """,
            extraArguments = arrayOf(ARG_WARNING, Issues.UNMATCHED_MERGE_ANNOTATION.name),
        )
    }

    @Test
    fun `Merge inclusion annotations from multiple Java stub files`() {
        check(
            format = FileFormat.V2,
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface Example {
                                void aNotAnnotated();
                                void bHidden();
                                void cShown();
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public interface HiddenExample {
                                void method();
                            }
                        """
                    ),
                ),
            hideAnnotations = arrayOf("test.annotation.Hide"),
            showAnnotations = arrayOf("test.annotation.Show"),
            showUnannotated = true,
            mergeInclusionAnnotations =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface Example {
                                void aNotAnnotated();
                                void bHidden();
                                @test.annotation.Show void cShown();
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public interface Example {
                                void aNotAnnotated();
                                @test.annotation.Hide void bHidden();
                                @test.annotation.Hide void cShown();
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            @test.annotation.Hide
                            public interface HiddenExample {
                                void method();
                            }
                        """
                    ),
                ),
            api =
                """
                    package test.pkg {
                      public interface Example {
                        method public void aNotAnnotated();
                        method public void cShown();
                      }
                    }
                """
        )
    }

    @Test
    fun `Merge @FlaggedApi inclusion annotations from Java stub files`() {
        check(
            format = FileFormat.V2,
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface Example {
                                void aNotAnnotated();
                                void cShown();
                            }
                        """
                    ),
                ),
            hideAnnotations = arrayOf("test.annotation.Hide"),
            showAnnotations = arrayOf("test.annotation.Show"),
            showUnannotated = true,
            mergeInclusionAnnotations =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface Example {
                                void aNotAnnotated();
                                void bHidden();
                                @test.annotation.Hide @test.annotation.Show void cShown();
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public interface Example {
                                void aNotAnnotated();
                                @android.annotation.FlaggedApi("flag")
                                void cShown();
                            }
                        """
                    ),
                ),
            api =
                """
                    package test.pkg {
                      public interface Example {
                        method public void aNotAnnotated();
                        method @FlaggedApi("flag") public void cShown();
                      }
                    }
                """
        )
    }

    @Test
    fun `Merge inclusion annotations from Java stub files using --show-single-annotation`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        "src/test/pkg/Example.annotated.java",
                        """
                    package test.pkg;

                    public interface Example {
                        void aNotAnnotated();
                        void bShown();
                    }
                    """
                    )
                ),
            extraArguments =
                arrayOf(
                    ARG_HIDE_ANNOTATION,
                    "test.annotation.Hide",
                    ARG_SHOW_SINGLE_ANNOTATION,
                    "test.annotation.Show"
                ),
            showUnannotated = true,
            mergeInclusionAnnotations =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @test.annotation.Hide
                            @test.annotation.Show
                            public interface Example {
                                void aNotAnnotated();
                                @test.annotation.Show void bShown();
                            }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  public interface Example {
                    method public void bShown();
                  }
                }
                """
        )
    }

    @Test
    fun `Merge inclusion annotations on api in java namespace`() {
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        "src/java/net/Example.java",
                        """
                    package java.net;

                    public class Example {
                        public void aNotAnnotated() { }
                        public void bShown() { }
                    }
                    """
                    )
                ),
            extraArguments = arrayOf(ARG_SHOW_SINGLE_ANNOTATION, "test.annotation.Show"),
            mergeInclusionAnnotations =
                arrayOf(
                    java(
                        """
                            package java.net;

                            public class Example {
                                void aNotAnnotated();
                                @test.annotation.Show void bShown();
                            }
                        """
                    ),
                ),
            api =
                """
                package java.net {
                  public class Example {
                    method public void bShown();
                  }
                }
                """
        )
    }

    @Test
    fun `Redefining java lang object plus using some internal classes`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package java.util;
                    public class HashMap {
                        static class Node {
                        }
                        static class TreeNode extends LinkedHashMap.LinkedHashMapEntry {
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package java.util;

                    public class LinkedHashMap<K,V>
                        extends HashMap<K,V>
                        implements Map<K,V>
                    {
                        static class LinkedHashMapEntry<K,V> extends HashMap.Node<K,V> {
                        }
                    }

                    """
                    ),
                    java(
                        """
                    package java.lang;

                    public class Object {
                        protected void finalize() throws Throwable { }
                    }
                    """
                    )
                ),
            extraArguments = arrayOf(ARG_SHOW_SINGLE_ANNOTATION, "libcore.api.CorePlatformApi"),
            mergeInclusionAnnotations =
                arrayOf(
                    java(
                        """
                            package java.util;

                            public class LinkedHashMap extends java.util.HashMap {
                            }
                        """
                    ),
                ),
            api = "" // This test is checking that it doesn't crash
        )
    }

    @Test
    fun `Merge nullability into child`() {
        // This is a contrived test that verifies that even if Child no longer directly declares
        // method1, the inherited method1 is still found
        check(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class Child extends Parent {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class Parent {
                        public void method1(String arg) {
                        }
                    }
                    """
                    )
                ),
            mergeJavaStubAnnotations =
                """
                package test.pkg;

                public class Child {
                    public void method1(@Nullable String arg) {
                    }
                }
                """,
            api =
                """
                package test.pkg {
                  public class Child extends test.pkg.Parent {
                    ctor public Child();
                  }
                  public class Parent {
                    ctor public Parent();
                    method public void method1(String);
                  }
                }
                """,
            expectedIssues = "" // should not report that Child.method1 is undefined
        )
    }

    @Test
    fun `Merge Contract and Language annotations from XML files`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.text;

                    public class TextUtils {
                        public static boolean isEmpty(CharSequence str) {
                            return str == null || str.length() == 0;
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.graphics;
                    public class RuntimeShader {
                        public RuntimeShader(@NonNull String sksl) {
                        }
                    }
                    """
                    )
                ),
            mergeXmlAnnotations =
                """<?xml version="1.0" encoding="UTF-8"?>
                <root>
                  <item name="android.text.TextUtils boolean isEmpty(java.lang.CharSequence)">
                    <annotation name="org.jetbrains.annotations.Contract">
                      <val name="value" val="&quot;null-&gt;true&quot;" />
                    </annotation>
                  </item>
                  <item name="android.text.TextUtils boolean isEmpty(java.lang.CharSequence) 0">
                    <annotation name="androidx.annotation.Nullable" />
                  </item>
                  <item name="android.graphics.RuntimeShader RuntimeShader(java.lang.String) 0">
                    <annotation name="org.intellij.lang.annotations.Language">
                      <val name="value" val="&quot;AGSL&quot;" />
                    </annotation>
                  </item>
                  <item name="android.graphics.RuntimeShader RuntimeShader(java.lang.String, boolean) 0">
                    <annotation name="org.intellij.lang.annotations.Language">
                      <val name="value" val="&quot;AGSL&quot;" />
                    </annotation>
                  </item>
                </root>
                """,
            format = FileFormat.V4,
            api =
                """
                // Signature format: 4.0
                package android.graphics {
                  public class RuntimeShader {
                    ctor public RuntimeShader(String);
                  }
                }
                package android.text {
                  public class TextUtils {
                    ctor public TextUtils();
                    method public static boolean isEmpty(CharSequence?);
                  }
                }
                """,
            extractAnnotations =
                mapOf(
                    "android.text" to
                        """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                  <item name="android.text.TextUtils boolean isEmpty(java.lang.CharSequence)">
                    <annotation name="org.jetbrains.annotations.Contract">
                      <val name="value" val="&quot;null-&gt;true&quot;" />
                    </annotation>
                  </item>
                </root>
                """,
                    "android.graphics" to
                        """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                  <item name="android.graphics.RuntimeShader RuntimeShader(java.lang.String) 0">
                    <annotation name="org.intellij.lang.annotations.Language">
                      <val name="value" val="&quot;AGSL&quot;" />
                    </annotation>
                  </item>
                </root>
                """
                )
        )
    }

    @Test
    fun `Merge Contract and Language annotations from signature files`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.text;

                    public class TextUtils {
                        public static boolean isEmpty(CharSequence str) {
                            return str == null || str.length() == 0;
                        }
                    }
                    """
                    ),
                    java(
                        """
                    package android.graphics;
                    public class RuntimeShader {
                        public RuntimeShader(@NonNull String sksl) {
                        }
                    }
                    """
                    )
                ),
            format = FileFormat.V4,
            mergeSignatureAnnotations =
                """
                // Signature format: 4.0
                package android.graphics {
                  public class RuntimeShader {
                    ctor public RuntimeShader(@org.intellij.lang.annotations.Language("AGSL") String);
                  }
                }
                package android.text {
                  public class TextUtils {
                    method @org.jetbrains.annotations.Contract("null->true") public static boolean isEmpty(CharSequence?);
                  }
                }
            """,
            extractAnnotations =
                mapOf(
                    "android.text" to
                        """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                  <item name="android.text.TextUtils boolean isEmpty(java.lang.CharSequence)">
                    <annotation name="org.jetbrains.annotations.Contract">
                      <val name="value" val="&quot;null-&gt;true&quot;" />
                    </annotation>
                  </item>
                </root>
                """,
                    "android.graphics" to
                        """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                  <item name="android.graphics.RuntimeShader RuntimeShader(java.lang.String) 0">
                    <annotation name="org.intellij.lang.annotations.Language">
                      <val name="value" val="&quot;AGSL&quot;" />
                    </annotation>
                  </item>
                </root>
                """
                )
        )
    }
}
