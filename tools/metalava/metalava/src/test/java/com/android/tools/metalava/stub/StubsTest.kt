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

import com.android.tools.metalava.ARG_API_CLASS_RESOLUTION
import com.android.tools.metalava.ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS
import com.android.tools.metalava.ARG_KOTLIN_STUBS
import com.android.tools.metalava.deprecatedForSdkSource
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.supportParameterName
import com.android.tools.metalava.systemApiSource
import com.android.tools.metalava.testApiSource
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

@SuppressWarnings("ALL")
class StubsTest : AbstractStubsTest() {
    // TODO: test fields that need initialization
    // TODO: test @DocOnly handling

    @Test
    fun `Generate stubs for fields with initial values`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public class Foo {
                        private int hidden = 1;
                        int hidden2 = 2;
                        /** @hide */
                        int hidden3 = 3;

                        protected int field00; // No value
                        public static final boolean field01 = true;
                        public static final int field02 = 42;
                        public static final long field03 = 42L;
                        public static final short field04 = 5;
                        public static final byte field05 = 5;
                        public static final char field06 = 'c';
                        public static final float field07 = 98.5f;
                        public static final double field08 = 98.5;
                        public static final String field09 = "String with \"escapes\" and \u00a9...";
                        public static final double field10 = Double.NaN;
                        public static final double field11 = Double.POSITIVE_INFINITY;

                        public static final boolean field12;
                        public static final byte field13;
                        public static final char field14;
                        public static final short field15;
                        public static final int field16;
                        public static final long field17;
                        public static final float field18;
                        public static final double field19;

                        public static final String GOOD_IRI_CHAR = "a-zA-Z0-9\u00a0-\ud7ff\uf900-\ufdcf\ufdf0-\uffef";
                        public static final char HEX_INPUT = 61184;
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                public static final java.lang.String GOOD_IRI_CHAR = "a-zA-Z0-9\u00a0-\ud7ff\uf900-\ufdcf\ufdf0-\uffef";
                public static final char HEX_INPUT = 61184; // 0xef00 '\uef00'
                protected int field00;
                public static final boolean field01 = true;
                public static final int field02 = 42; // 0x2a
                public static final long field03 = 42L; // 0x2aL
                public static final short field04 = 5; // 0x5
                public static final byte field05 = 5; // 0x5
                public static final char field06 = 99; // 0x0063 'c'
                public static final float field07 = 98.5f;
                public static final double field08 = 98.5;
                public static final java.lang.String field09 = "String with \"escapes\" and \u00a9...";
                public static final double field10 = (0.0/0.0);
                public static final double field11 = (1.0/0.0);
                public static final boolean field12;
                static { field12 = false; }
                public static final byte field13;
                static { field13 = 0; }
                public static final char field14;
                static { field14 = 0; }
                public static final short field15;
                static { field15 = 0; }
                public static final int field16;
                static { field16 = 0; }
                public static final long field17;
                static { field17 = 0; }
                public static final float field18;
                static { field18 = 0; }
                public static final double field19;
                static { field19 = 0; }
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Generate stubs for various modifier scenarios`() {
        // Include as many modifiers as possible to see which ones are included
        // in the signature files, and the expected sorting order.
        // Note that the signature files treat "deprecated" as a fake modifier.
        // Note also how the "protected" modifier on the interface method gets
        // promoted to public.
        checkStubs(
            warnings = null,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("ALL")
                    public abstract class Foo {
                        /** @deprecated */ @Deprecated private static final long field1 = 5;
                        /** @deprecated */ @Deprecated private static volatile long field2 = 5;
                        /** @deprecated */ @Deprecated public static strictfp final synchronized void method1() { }
                        /** @deprecated */ @Deprecated public static final synchronized native void method2();
                        /** @deprecated */ @Deprecated protected static final class Inner1 { }
                        /** @deprecated */ @Deprecated protected static abstract  class Inner2 { }
                        /** @deprecated */ @Deprecated protected interface Inner3 {
                            protected default void method3() { }
                            static void method4() { }
                        }
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public abstract class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                /** @deprecated */
                @Deprecated
                public static final synchronized void method1() { throw new RuntimeException("Stub!"); }
                /** @deprecated */
                @Deprecated
                public static final synchronized native void method2();
                /** @deprecated */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @Deprecated
                protected static final class Inner1 {
                @Deprecated
                protected Inner1() { throw new RuntimeException("Stub!"); }
                }
                /** @deprecated */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @Deprecated
                protected abstract static class Inner2 {
                @Deprecated
                protected Inner2() { throw new RuntimeException("Stub!"); }
                }
                /** @deprecated */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @Deprecated
                protected static interface Inner3 {
                @Deprecated
                public default void method3() { throw new RuntimeException("Stub!"); }
                @Deprecated
                public static void method4() { throw new RuntimeException("Stub!"); }
                }
                }
                """
        )
    }

    @Test
    fun `Check throws list`() {
        // Make sure we format a throws list
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import java.io.IOException;

                    @SuppressWarnings("RedundantThrows")
                    public abstract class AbstractCursor {
                        @Override protected void finalize1() throws Throwable { }
                        @Override protected void finalize2() throws IOException, IllegalArgumentException {  }
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public abstract class AbstractCursor {
                public AbstractCursor() { throw new RuntimeException("Stub!"); }
                protected void finalize1() throws java.lang.Throwable { throw new RuntimeException("Stub!"); }
                protected void finalize2() throws java.io.IOException, java.lang.IllegalArgumentException { throw new RuntimeException("Stub!"); }
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Test final instance fields`() {
        // Instance fields in a class must be initialized
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class InstanceFieldTest {
                        public static final class WindowLayout {
                            public WindowLayout(int width, int height, int gravity) {
                                this.width = width;
                                this.height = height;
                                this.gravity = gravity;
                            }

                            public final int width;
                            public final int height;
                            public final int gravity;

                        }
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class InstanceFieldTest {
                    public InstanceFieldTest() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static final class WindowLayout {
                    public WindowLayout(int width, int height, int gravity) { throw new RuntimeException("Stub!"); }
                    public final int gravity;
                    { gravity = 0; }
                    public final int height;
                    { height = 0; }
                    public final int width;
                    { width = 0; }
                    }
                    }
                    """
        )
    }

    @Test
    fun `Check overridden method added for complex hierarchy`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                package test.pkg;
                public final class A extends C implements B<String> {
                    @Override public void method2() { }
                }
                """
                    ),
                    java(
                        """
                package test.pkg;
                public interface B<T> {
                    void method1(T arg1);
                }
                """
                    ),
                    java(
                        """
                package test.pkg;
                public abstract class C extends D {
                    public abstract void method2();
                }
                """
                    ),
                    java(
                        """
                package test.pkg;
                public abstract class D implements B<String> {
                    @Override public void method1(String arg1) { }
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
                public final class A extends test.pkg.C implements test.pkg.B<java.lang.String> {
                public A() { throw new RuntimeException("Stub!"); }
                public void method2() { throw new RuntimeException("Stub!"); }
                }
                """
                    ),
                    java(
                        """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public interface B<T> {
                public void method1(T arg1);
                }
                """
                    ),
                    java(
                        """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public abstract class C extends test.pkg.D {
                public C() { throw new RuntimeException("Stub!"); }
                public abstract void method2();
                }
                """
                    ),
                    java(
                        """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public abstract class D implements test.pkg.B<java.lang.String> {
                public D() { throw new RuntimeException("Stub!"); }
                public void method1(java.lang.String arg1) { throw new RuntimeException("Stub!"); }
                }
                """
                    )
                ),
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Preserve file header comments`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    /*
                    My header 1
                     */

                    /*
                    My header 2
                     */

                    // My third comment

                    package test.pkg;

                    public class HeaderComments {
                    }
                    """
                    )
                ),
            source =
                """
                    /*
                    My header 1
                     */
                    /*
                    My header 2
                     */
                    // My third comment
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class HeaderComments {
                    public HeaderComments() { throw new RuntimeException("Stub!"); }
                    }
                    """
        )
    }

    @Test
    fun `Parameter Names in Java`() {
        // Java code which explicitly specifies parameter names: make sure stub uses
        // parameter name
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import androidx.annotation.ParameterName;

                    public class Foo {
                        public void foo(int javaParameter1, @ParameterName("publicParameterName") int javaParameter2) {
                        }
                    }
                    """
                    ),
                    supportParameterName
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                public void foo(int javaParameter1, int publicParameterName) { throw new RuntimeException("Stub!"); }
                }
                 """
        )
    }

    @Test
    fun `DocOnly members should be omitted`() {
        // When marked @doconly don't include in stubs or signature files
        // unless specifically asked for (which we do when generating docs-stubs).
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("JavaDoc")
                    public class Outer {
                        /** @doconly Some docs here */
                        public class MyClass1 {
                            public int myField;
                        }

                        public class MyClass2 {
                            /** @doconly Some docs here */
                            public int myField;

                            /** @doconly Some docs here */
                            public int myMethod() { return 0; }
                        }
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Outer {
                public Outer() { throw new RuntimeException("Stub!"); }
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass2 {
                public MyClass2() { throw new RuntimeException("Stub!"); }
                }
                }
                    """,
            api =
                """
                package test.pkg {
                  public class Outer {
                    ctor public Outer();
                  }
                  public class Outer.MyClass2 {
                    ctor public Outer.MyClass2();
                  }
                }
                """
        )
    }

    @Test
    fun `DocOnly members should be included when requested`() {
        // When marked @doconly don't include in stubs or signature files
        // unless specifically asked for (which we do when generating docs).
        checkStubs(
            docStubs = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("JavaDoc")
                    public class Outer {
                        /** @doconly Some docs here */
                        public class MyClass1 {
                            public int myField;
                        }

                        public class MyClass2 {
                            /** @doconly Some docs here */
                            public int myField;

                            /** @doconly Some docs here */
                            public int myMethod() { return 0; }
                        }
                    }
                    """
                    )
                ),
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Outer {
                    public Outer() { throw new RuntimeException("Stub!"); }
                    /** @doconly Some docs here */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass1 {
                    public MyClass1() { throw new RuntimeException("Stub!"); }
                    public int myField;
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass2 {
                    public MyClass2() { throw new RuntimeException("Stub!"); }
                    /** @doconly Some docs here */
                    public int myMethod() { throw new RuntimeException("Stub!"); }
                    /** @doconly Some docs here */
                    public int myField;
                    }
                    }
                    """
        )
    }

    @Test
    fun `Picking super class throwables`() {
        // Like previous test, but without compatibility mode: ensures that we
        // use super classes of filtered throwables
        checkStubs(
            format = FileFormat.V3,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import java.io.IOException;
                    import java.util.List;
                    import java.util.Map;

                    @SuppressWarnings({"RedundantThrows", "WeakerAccess"})
                    public class Generics {
                        @SuppressWarnings("HiddenSuperclass") // HiddenParent is not public
                        public class MyClass<X, Y extends Number> extends HiddenParent<X, Y> implements PublicInterface<X, Y> {
                        }

                        class HiddenParent<M, N extends Number> extends PublicParent<M, N> {
                            @SuppressWarnings("ReferencesHidden") // MyThrowable is not public
                            public Map<M, Map<N, String>> createMap(List<M> list) throws MyThrowable {
                                return null;
                            }

                            protected List<M> foo() {
                                return null;
                            }

                        }

                        class MyThrowable extends IOException {
                        }

                        public abstract class PublicParent<A, B extends Number> {
                            protected abstract List<A> foo();
                        }

                        public interface PublicInterface<A, B> {
                            Map<A, Map<B, String>> createMap(List<A> list) throws IOException;
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            api =
                """
                // Signature format: 3.0
                package test.pkg {
                  public class Generics {
                    ctor public Generics();
                  }
                  public class Generics.MyClass<X, Y extends java.lang.Number> extends test.pkg.Generics.PublicParent<X,Y> implements test.pkg.Generics.PublicInterface<X,Y> {
                    ctor public Generics.MyClass();
                    method public java.util.Map<X!,java.util.Map<Y!,java.lang.String!>!>! createMap(java.util.List<X!>!) throws java.io.IOException;
                    method protected java.util.List<X!>! foo();
                  }
                  public static interface Generics.PublicInterface<A, B> {
                    method public java.util.Map<A!,java.util.Map<B!,java.lang.String!>!>! createMap(java.util.List<A!>!) throws java.io.IOException;
                  }
                  public abstract class Generics.PublicParent<A, B extends java.lang.Number> {
                    ctor public Generics.PublicParent();
                    method protected abstract java.util.List<A!>! foo();
                  }
                }
            """,
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Generics {
                    public Generics() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass<X, Y extends java.lang.Number> extends test.pkg.Generics.PublicParent<X,Y> implements test.pkg.Generics.PublicInterface<X,Y> {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    protected java.util.List<X> foo() { throw new RuntimeException("Stub!"); }
                    public java.util.Map<X,java.util.Map<Y,java.lang.String>> createMap(java.util.List<X> list) throws java.io.IOException { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface PublicInterface<A, B> {
                    public java.util.Map<A,java.util.Map<B,java.lang.String>> createMap(java.util.List<A> list) throws java.io.IOException;
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class PublicParent<A, B extends java.lang.Number> {
                    public PublicParent() { throw new RuntimeException("Stub!"); }
                    protected abstract java.util.List<A> foo();
                    }
                    }
                    """
        )
    }

    @Test
    fun `Rewriting implements class references`() {
        // Checks some more subtle bugs around generics type variable renaming
        checkStubs(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import java.util.Collection;
                    import java.util.Set;

                    @SuppressWarnings("all")
                    public class ConcurrentHashMap<K, V> {
                        public abstract static class KeySetView<K, V> extends CollectionView<K, V, K>
                                implements Set<K>, java.io.Serializable {
                        }

                        abstract static class CollectionView<K, V, E>
                                implements Collection<E>, java.io.Serializable {
                            public final Object[] toArray() { return null; }

                            public final <T> T[] toArray(T[] a) {
                                return null;
                            }

                            @Override
                            public int size() {
                                return 0;
                            }
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            api =
                """
                    package test.pkg {
                      public class ConcurrentHashMap<K, V> {
                        ctor public ConcurrentHashMap();
                      }
                      public abstract static class ConcurrentHashMap.KeySetView<K, V> implements java.util.Collection<K> java.io.Serializable java.util.Set<K> {
                        ctor public ConcurrentHashMap.KeySetView();
                        method public int size();
                        method public final Object[] toArray();
                        method public final <T> T[] toArray(T[]);
                      }
                    }
                    """,
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class ConcurrentHashMap<K, V> {
                    public ConcurrentHashMap() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class KeySetView<K, V> implements java.util.Collection<K>, java.io.Serializable, java.util.Set<K> {
                    public KeySetView() { throw new RuntimeException("Stub!"); }
                    public int size() { throw new RuntimeException("Stub!"); }
                    public final java.lang.Object[] toArray() { throw new RuntimeException("Stub!"); }
                    public final <T> T[] toArray(T[] a) { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
        )
    }

    @Test
    fun `Arrays in type arguments`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Generics2 {
                        public class FloatArrayEvaluator implements TypeEvaluator<float[]> {
                        }

                        @SuppressWarnings("WeakerAccess")
                        public interface TypeEvaluator<T> {
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Generics2 {
                    public Generics2() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class FloatArrayEvaluator implements test.pkg.Generics2.TypeEvaluator<float[]> {
                    public FloatArrayEvaluator() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface TypeEvaluator<T> {
                    }
                    }
                    """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Overriding protected methods`() {
        // Checks a scenario where the stubs were missing overrides
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class Layouts {
                        public static class View {
                            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                            }
                        }

                        public static abstract class ViewGroup extends View {
                            @Override
                            protected abstract void onLayout(boolean changed,
                                    int l, int t, int r, int b);
                        }

                        public static class Toolbar extends ViewGroup {
                            @Override
                            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                            }
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            api =
                """
                    package test.pkg {
                      public class Layouts {
                        ctor public Layouts();
                      }
                      public static class Layouts.Toolbar extends test.pkg.Layouts.ViewGroup {
                        ctor public Layouts.Toolbar();
                      }
                      public static class Layouts.View {
                        ctor public Layouts.View();
                        method protected void onLayout(boolean, int, int, int, int);
                      }
                      public abstract static class Layouts.ViewGroup extends test.pkg.Layouts.View {
                        ctor public Layouts.ViewGroup();
                        method protected abstract void onLayout(boolean, int, int, int, int);
                      }
                    }
                    """,
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Layouts {
                    public Layouts() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class Toolbar extends test.pkg.Layouts.ViewGroup {
                    public Toolbar() { throw new RuntimeException("Stub!"); }
                    protected void onLayout(boolean changed, int l, int t, int r, int b) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class View {
                    public View() { throw new RuntimeException("Stub!"); }
                    protected void onLayout(boolean changed, int left, int top, int right, int bottom) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class ViewGroup extends test.pkg.Layouts.View {
                    public ViewGroup() { throw new RuntimeException("Stub!"); }
                    protected abstract void onLayout(boolean changed, int l, int t, int r, int b);
                    }
                    }
                    """
        )
    }

    @Test
    fun `Missing overridden method`() {
        // Another special case where overridden methods were missing
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import java.util.Collection;
                    import java.util.Set;

                    @SuppressWarnings("all")
                    public class SpanTest {
                        public interface CharSequence {
                        }
                        public interface Spanned extends CharSequence {
                            public int nextSpanTransition(int start, int limit, Class type);
                        }

                        public interface Spannable extends Spanned {
                        }

                        public class SpannableString extends SpannableStringInternal implements CharSequence, Spannable {
                        }

                        /* package */ abstract class SpannableStringInternal {
                            public int nextSpanTransition(int start, int limit, Class kind) {
                                return 0;
                            }
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class SpanTest {
                    public SpanTest() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface CharSequence {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface Spannable extends test.pkg.SpanTest.Spanned {
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class SpannableString implements test.pkg.SpanTest.CharSequence, test.pkg.SpanTest.Spannable {
                    public SpannableString() { throw new RuntimeException("Stub!"); }
                    public int nextSpanTransition(int start, int limit, java.lang.Class kind) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface Spanned extends test.pkg.SpanTest.CharSequence {
                    public int nextSpanTransition(int start, int limit, java.lang.Class type);
                    }
                    }
                    """
        )
    }

    @Test
    fun `Skip type variables in casts`() {
        // When generating casts in super constructor calls, use raw types
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("all")
                    public class Properties {
                        public abstract class Property<T, V> {
                            public Property(Class<V> type, String name) {
                            }
                            public Property(Class<V> type, String name, String name2) { // force casts in super
                            }
                        }

                        public abstract class IntProperty<T> extends Property<T, Integer> {

                            public IntProperty(String name) {
                                super(Integer.class, name);
                            }
                        }
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Properties {
                    public Properties() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class IntProperty<T> extends test.pkg.Properties.Property<T,java.lang.Integer> {
                    public IntProperty(java.lang.String name) { super((java.lang.Class)null, (java.lang.String)null); throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class Property<T, V> {
                    public Property(java.lang.Class<V> type, java.lang.String name) { throw new RuntimeException("Stub!"); }
                    public Property(java.lang.Class<V> type, java.lang.String name, java.lang.String name2) { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
        )
    }

    @Test
    fun `Generate stubs with --exclude-documentation-from-stubs`() {
        checkStubs(
            extraArguments = arrayOf(ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    /*
                     * This is the copyright header.
                     */

                    package test.pkg;

                    import java.util.List;

                    /** This is the documentation for the class */
                    public class Foo {

                        /** My field doc */
                        protected static final String field = "a\nb\n\"test\"";

                        /**
                         * Method documentation.
                         * @see List
                         */
                        protected static void onCreate(List<String> parameter1) {
                            // This is not in the stub
                            System.out.println(parameter1);
                        }
                    }
                    """
                    )
                ),
            // Excludes javadoc because of ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS:
            source =
                """
                /*
                 * This is the copyright header.
                 */
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                protected static void onCreate(java.util.List<java.lang.String> parameter1) { throw new RuntimeException("Stub!"); }
                protected static final java.lang.String field = "a\nb\n\"test\"";
                }
                """
        )
    }

    @Test
    fun `Generate documentation stubs with --exclude-documentation-from-stubs`() {
        checkStubs(
            extraArguments = arrayOf(ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    /*
                     * This is the copyright header.
                     */

                    package test.pkg;

                    import java.util.List;

                    /** This is the documentation for the class */
                    public class Foo {

                        /** My field doc */
                        protected static final String field = "a\nb\n\"test\"";

                        /**
                         * Method documentation.
                         * @see List
                         */
                        protected static void onCreate(List<String> parameter1) {
                            // This is not in the stub
                            System.out.println(parameter1);
                        }
                    }
                    """
                    )
                ),
            docStubs = true,
            // Includes javadoc despite ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS, because of docStubs:
            source =
                """
                /*
                 * This is the copyright header.
                 */
                package test.pkg;
                import java.util.List;
                /** This is the documentation for the class */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Foo {
                public Foo() { throw new RuntimeException("Stub!"); }
                /**
                 * Method documentation.
                 * @see java.util.List
                 */
                protected static void onCreate(java.util.List<java.lang.String> parameter1) { throw new RuntimeException("Stub!"); }
                /** My field doc */
                protected static final java.lang.String field = "a\nb\n\"test\"";
                }
                """
        )
    }

    @Test
    fun `Regression test for 116777737`() {
        // Regression test for 116777737: Stub generation broken for Bouncycastle
        // """
        //    It appears as though metalava does not handle the case where:
        //    1) class Alpha extends Beta<Orange>.
        //    2) class Beta<T> extends Charlie<T>.
        //    3) class Beta is hidden.
        //
        //    It should result in a stub where Alpha extends Charlie<Orange> but
        //    instead results in a stub where Alpha extends Charlie<T>, so the
        //    type substitution of Orange for T is lost.
        // """
        check(
            expectedIssues =
                "src/test/pkg/Alpha.java:2: warning: Public class test.pkg.Alpha stripped of unavailable superclass test.pkg.Beta [HiddenSuperclass]",
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class Orange {
                        private Orange() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class Alpha extends Beta<Orange> {
                        private Alpha() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    /** @hide */
                    public class Beta<T> extends Charlie<T> {
                        private Beta() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public class Charlie<T> {
                        private Charlie() { }
                    }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public class Alpha extends test.pkg.Charlie<test.pkg.Orange> {
                  }
                  public class Charlie<T> {
                  }
                  public class Orange {
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Orange {
                    Orange() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Alpha extends test.pkg.Charlie<test.pkg.Orange> {
                    Alpha() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Basic Kotlin stubs`() {
        check(
            extraArguments = arrayOf(ARG_KOTLIN_STUBS),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    /* My file header */
                    // Another comment
                    @file:JvmName("Driver")
                    package test.pkg
                    /** My class doc */
                    class Kotlin(
                        val property1: String = "Default Value",
                        arg2: Int
                    ) : Parent() {
                        override fun method() = "Hello World"
                        /** My method doc */
                        fun otherMethod(ok: Boolean, times: Int) {
                        }

                        /** property doc */
                        var property2: String? = null

                        /** @hide */
                        var hiddenProperty: String? = "hidden"

                        private var someField = 42
                        @JvmField
                        var someField2 = 42
                    }

                    /** Parent class doc */
                    open class Parent {
                        open fun method(): String? = null
                        open fun method2(value1: Boolean, value2: Boolean?): String? = null
                        open fun method3(value1: Int?, value2: Int): Int = null
                    }
                    """
                    ),
                    kotlin(
                        """
                    package test.pkg
                    open class ExtendableClass<T>
                """
                    )
                ),
            stubFiles =
                arrayOf(
                    kotlin(
                        """
                        /* My file header */
                        // Another comment
                        package test.pkg
                        /** My class doc */
                        @file:Suppress("ALL")
                        class Kotlin : test.pkg.Parent() {
                        open fun Kotlin(open property1: java.lang.String, open arg2: int): test.pkg.Kotlin = error("Stub!")
                        open fun method(): java.lang.String = error("Stub!")
                        /** My method doc */
                        open fun otherMethod(open ok: boolean, open times: int): void = error("Stub!")
                        }
                    """
                    ),
                    kotlin(
                        """
                        package test.pkg
                        @file:Suppress("ALL")
                        open class ExtendableClass<T> {
                        open fun ExtendableClass(): test.pkg.ExtendableClass<T> = error("Stub!")
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `NaN constants`() {
        check(
            checkCompilation = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class MyClass {
                        public static final float floatNaN = 0.0f / 0.0f;
                        public static final double doubleNaN = 0.0d / 0.0;
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
                        public class MyClass {
                        public MyClass() { throw new RuntimeException("Stub!"); }
                        public static final double doubleNaN = (0.0/0.0);
                        public static final float floatNaN = (0.0f/0.0f);
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `Translate DeprecatedForSdk to Deprecated`() {
        // See b/144111352
        check(
            expectedIssues =
                """
                src/test/pkg/PublicApi.java:30: error: Method test.pkg.PublicApi.method4(): Documentation contains `@deprecated` which implies this API is fully deprecated, not just @DeprecatedForSdk [DeprecationMismatch]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                            """
                    package test.pkg;
                    import android.annotation.DeprecatedForSdk;
                    import android.annotation.DeprecatedForSdk.*;

                    public class PublicApi {
                        private PublicApi() { }
                        // Normal deprecation:
                        /** @deprecated My deprecation reason 1 */
                        @Deprecated
                        public static void method1() { }

                        // Deprecated in the SDK. No comment; make sure annotation comment
                        // shows up in the doc stubs.
                        @DeprecatedForSdk("My deprecation reason 2")
                        public static void method2() { }

                        // Deprecated in the SDK, and has comment: Make sure comments merged
                        // in the doc stubs.
                        /**
                         * My docs here.
                         * @return the value
                         */
                        @DeprecatedForSdk("My deprecation reason 3")
                        public static void method3() { } // warn about missing annotation

                        // Already implicitly deprecated everywhere (because of @deprecated
                        // comment; complain if combined with @DeprecatedForSdk
                        /** @deprecated Something */
                        @DeprecatedForSdk("Something")
                        public static void method4() { }

                        // Test @DeprecatedForSdk with specific exemptions; none of these are
                        // the current public SDK so make sure it's deprecated there.
                        // A different test will check whath appens when generating the
                        // system API or test API.
                        @DeprecatedForSdk(value = "Explanation", allowIn = { SYSTEM_API, TEST_API })
                        public static void method5() { }
                    }
                    """
                        )
                        .indented(),
                    deprecatedForSdkSource
                ),
            api =
                """
                package test.pkg {
                  public class PublicApi {
                    method @Deprecated public static void method1();
                    method @Deprecated public static void method2();
                    method @Deprecated public static void method3();
                    method @Deprecated public static void method4();
                    method @Deprecated public static void method5();
                  }
                }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicApi {
                    PublicApi() { throw new RuntimeException("Stub!"); }
                    /** @deprecated My deprecation reason 1 */
                    @Deprecated
                    public static void method1() { throw new RuntimeException("Stub!"); }
                    /**
                     * @deprecated My deprecation reason 2
                     */
                    @Deprecated
                    public static void method2() { throw new RuntimeException("Stub!"); }
                    /**
                     * My docs here.
                     * @deprecated My deprecation reason 3
                     * @return the value
                     */
                    @Deprecated
                    public static void method3() { throw new RuntimeException("Stub!"); }
                    /** @deprecated Something */
                    @Deprecated
                    public static void method4() { throw new RuntimeException("Stub!"); }
                    /**
                     * @deprecated Explanation
                     */
                    @Deprecated
                    public static void method5() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                ),
            docStubs = true
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Translate DeprecatedForSdk with API Filtering`() {
        // See b/144111352.
        // Remaining: don't include @deprecated in the docs for allowed platforms!
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            sourceFiles =
                arrayOf(
                    java(
                            """
                    package test.pkg;

                    import android.annotation.SystemApi;
                    import android.annotation.TestApi;
                    import android.annotation.DeprecatedForSdk;

                    public class PublicApi2 {
                        private PublicApi2() {
                        }

                        // This method should be deprecated in the SDK but *not* here in
                        // the system API (this test runs with --show-annotations SystemApi)
                        @DeprecatedForSdk(value = "My deprecation reason 1", allowIn = {SystemApi.class, TestApi.class})
                        public static void method1() {
                        }

                        // Same as method 1 (here we're just using a different annotation
                        // initializer form to test we're handling both types): *not* deprecated.

                        /**
                         * My docs.
                         */
                        @DeprecatedForSdk(value = "My deprecation reason 2", allowIn = SystemApi.class)
                        public static void method2() {
                        }

                        // Finally, this method *is* deprecated in the system API and should
                        // show up as such.

                        /**
                         * My docs.
                         */
                        @DeprecatedForSdk(value = "My deprecation reason 3", allowIn = TestApi.class)
                        public static void method3() {
                        }
                    }
                    """
                        )
                        .indented(),
                    // Include some Kotlin files too to make sure we correctly handle
                    // annotation lookup for Kotlin (which uses UAST instead of plain Java PSI
                    // behind the scenes), even if android.util.ArrayMap is really implemented in
                    // Java
                    kotlin(
                            """
                    package android.util
                    import android.annotation.DeprecatedForSdk
                    import android.annotation.SystemApi;
                    import android.annotation.TestApi;

                    @DeprecatedForSdk(value = "Use androidx.collection.ArrayMap")
                    class ArrayMap

                    @DeprecatedForSdk(value = "Use androidx.collection.ArrayMap", allowIn = [SystemApi::class])
                    class SystemArrayMap

                    @DeprecatedForSdk("Use android.Manifest.permission.ACCESS_FINE_LOCATION instead")
                    const val FINE_LOCATION =  "android.permission.ACCESS_FINE_LOCATION"
                    """
                        )
                        .indented(),
                    deprecatedForSdkSource,
                    systemApiSource,
                    testApiSource
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicApi2 {
                    PublicApi2() { throw new RuntimeException("Stub!"); }
                    public static void method1() { throw new RuntimeException("Stub!"); }
                    /**
                     * My docs.
                     */
                    public static void method2() { throw new RuntimeException("Stub!"); }
                    /**
                     * My docs.
                     * @deprecated My deprecation reason 3
                     */
                    @Deprecated
                    public static void method3() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    package android.util;
                    /**
                     * @deprecated Use androidx.collection.ArrayMap
                     */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @Deprecated
                    public final class ArrayMap {
                    @Deprecated
                    public ArrayMap() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    // SystemArrayMap is like ArrayMap, but has allowedIn=SystemApi::class, so
                    // it should not be deprecated here in the system api stubs
                    java(
                        """
                    package android.util;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public final class SystemArrayMap {
                    public SystemArrayMap() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    package android.util;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public final class ArrayMapKt {
                    /**
                     * @deprecated Use android.Manifest.permission.ACCESS_FINE_LOCATION instead
                     */
                    @Deprecated @androidx.annotation.NonNull public static final java.lang.String FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
                    }
                    """
                    )
                ),
            docStubs = true
        )
    }

    @Test
    fun `From-text stubs can be generated from signature files with conflicting class definitions`() {
        check(
            format = FileFormat.V2,
            signatureSources =
                arrayOf(
                    """
            // Signature format: 2.0
            package test.pkg {
              public class SystemClassExtendingPublicClass extends test.pkg.PublicClass {
                ctor public SystemClassExtendingPublicClass();
                method public void foo(int i);
              }
              public class PublicClass {
                ctor public PublicClass();
              }
            }
            """, // current.txt
                    """
            // Signature format: 2.0
            package test.pkg {
              public class SystemClass extends test.pkg.PublicClass {
                ctor public SystemClass();
                method public void bar();
              }
              public class SystemClassExtendingPublicClass extends test.pkg.SystemClass {
              }
            }
            """, // system-current.txt
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class PublicClass {
                    public PublicClass() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class SystemClass extends test.pkg.PublicClass {
                    public SystemClass() { throw new RuntimeException("Stub!"); }
                    public void bar() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class SystemClassExtendingPublicClass extends test.pkg.SystemClass {
                    public SystemClassExtendingPublicClass() { throw new RuntimeException("Stub!"); }
                    public void foo(int i) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                ),
        )
    }

    @Test
    fun `Ensure that when generating stubs from signature files the constructors are setup correctly`() {
        check(
            format = FileFormat.V2,
            signatureSources =
                arrayOf(
                    """
            // Signature format: 2.0
            package test.pkg {
              public abstract class Parent {
                ctor protected Parent(String);
              }
              public static class Child extends test.pkg.Parent {
                ctor protected Child(String);
              }
            }
            """,
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class Parent {
                    protected Parent(java.lang.String arg1) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                    // class test.pkg.Parent does not have a default constructor but has a
                    // constructor that takes a String argument as an input. Therefore, the
                    // constructor in the class test.pkg.Child must call the super constructor with
                    // a String argument to avoid a compiler error.
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class Child extends test.pkg.Parent {
                    protected Child(java.lang.String arg1) { super(null); throw new RuntimeException("Stub!"); }
                    }
                    """
                    ),
                ),
            extraArguments =
                arrayOf(
                    ARG_API_CLASS_RESOLUTION,
                    "api:classpath",
                ),
        )
    }

    @Test
    fun `Compilable stubs are not generated when inheriting class exists in jar passed via classpath`() {
        check(
            format = FileFormat.V2,
            signatureSources =
                arrayOf(
                    """
            // Signature format: 2.0
            package java.text {
              public abstract class Format implements java.lang.Cloneable java.io.Serializable {
                ctor protected Format();
              }
              public static class Format.Field extends java.text.AttributedCharacterIterator.Attribute {
                ctor protected Format.Field(String);
              }
            }
            """,
                ),
            stubFiles =
                arrayOf(
                    // class java.text.AttributedCharacterIterator.Attribute is included in
                    // android.jar, which is passed as classpath in DriverTest. The class does not
                    // have a default constructor but has a constructor that takes a String argument
                    // as an input. Therefore, the constructor in the class java.text.Format.Field
                    // must call the super constructor with a String argument to avoid compile
                    // error.
                    java(
                        """
                    package java.text;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract class Format implements java.lang.Cloneable, java.io.Serializable {
                    protected Format() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class Field extends java.text.AttributedCharacterIterator.Attribute {
                    protected Field(java.lang.String arg1) { super(null); throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
                    ),
                ),
            extraArguments =
                arrayOf(
                    ARG_API_CLASS_RESOLUTION,
                    "api:classpath",
                ),
        )
    }

    @Test
    fun `Type-use annotations are not included in stubs`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
                            public @interface TypeAnnotation {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
                            public @interface MethodAnnotation {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE_USE})
                            public @interface MethodAndTypeAnnotation {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import java.util.List;
                            public class Foo {
                                @MethodAnnotation
                                @MethodAndTypeAnnotation
                                public @TypeAnnotation List<@TypeAnnotation String> foo() {}
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
                            @test.pkg.MethodAndTypeAnnotation
                            @test.pkg.MethodAnnotation
                            public java.util.List<java.lang.String> foo() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            format = FileFormat.V2,
            api =
                """
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method @test.pkg.MethodAndTypeAnnotation @test.pkg.MethodAnnotation public java.util.List<java.lang.String> foo();
                      }
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE_USE}) public @interface MethodAndTypeAnnotation {
                      }
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD) public @interface MethodAnnotation {
                      }
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) public @interface TypeAnnotation {
                      }
                    }
                """
        )
    }
}
