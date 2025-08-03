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

import com.android.tools.metalava.ARG_KOTLIN_STUBS
import com.android.tools.metalava.model.SUPPORT_TYPE_USE_ANNOTATIONS
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

@SuppressWarnings("ALL")
class StubsInterfaceTest : AbstractStubsTest() {
    @Test
    fun `Generate stubs for interface class`() {
        // Interface: makes sure the right modifiers etc are shown (and that "package private"
        // methods
        // in the interface are taken to be public etc)
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public interface Foo {
                        void foo();
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public interface Foo {
                public void foo();
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Check implementing a package private interface`() {
        // If you implement a package private interface, we just remove it and inline the members
        // into
        // the subclass

        // BUG: Note that we need to implement the parent
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class MyClass implements HiddenInterface {
                        @Override public void method() { }
                        @Override public void other() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface OtherInterface {
                        void other();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    interface HiddenInterface extends OtherInterface {
                        void method() { }
                        String CONSTANT = "MyConstant";
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass implements test.pkg.OtherInterface {
                public MyClass() { throw new RuntimeException("Stub!"); }
                public void method() { throw new RuntimeException("Stub!"); }
                public void other() { throw new RuntimeException("Stub!"); }
                public static final java.lang.String CONSTANT = "MyConstant";
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Check generating constants in interface without inline-able initializers`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public interface MyClass {
                        String[] CONSTANT1 = {"MyConstant","MyConstant2"};
                        boolean CONSTANT2 = Boolean.getBoolean(System.getenv("VAR1"));
                        int CONSTANT3 = Integer.parseInt(System.getenv("VAR2"));
                        String CONSTANT4 = null;
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public interface MyClass {
                public static final java.lang.String[] CONSTANT1 = null;
                public static final boolean CONSTANT2 = false;
                public static final int CONSTANT3 = 0; // 0x0
                public static final java.lang.String CONSTANT4 = null;
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Check generating type parameters in interface list`() {
        checkStubs(
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    @SuppressWarnings("NullableProblems")
                    public class GenericsInInterfaces<T> implements Comparable<GenericsInInterfaces> {
                        @Override
                        public int compareTo(GenericsInInterfaces o) {
                            return 0;
                        }

                        void foo(T bar) {
                        }
                    }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public class GenericsInInterfaces<T> implements java.lang.Comparable<test.pkg.GenericsInInterfaces> {
                    ctor public GenericsInInterfaces();
                    method public int compareTo(test.pkg.GenericsInInterfaces);
                  }
                }
                """,
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class GenericsInInterfaces<T> implements java.lang.Comparable<test.pkg.GenericsInInterfaces> {
                public GenericsInInterfaces() { throw new RuntimeException("Stub!"); }
                public int compareTo(test.pkg.GenericsInInterfaces o) { throw new RuntimeException("Stub!"); }
                }
                """
        )
    }

    @Test
    fun `Check generating required stubs from hidden super classes and interfaces`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    public class MyClass extends HiddenSuperClass implements HiddenInterface, PublicInterface2 {
                        public void myMethod() { }
                        @Override public void publicInterfaceMethod2() { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    class HiddenSuperClass extends PublicSuperParent {
                        @Override public void inheritedMethod2() { }
                        @Override public void publicInterfaceMethod() { }
                        @Override public void publicMethod() {}
                        @Override public void publicMethod2() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public abstract class PublicSuperParent {
                        public void inheritedMethod1() {}
                        public void inheritedMethod2() {}
                        public abstract void publicMethod() {}
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    interface HiddenInterface extends PublicInterface {
                        int MY_CONSTANT = 5;
                        void hiddenInterfaceMethod();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface PublicInterface {
                        void publicInterfaceMethod();
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface PublicInterface2 {
                        void publicInterfaceMethod2();
                    }
                    """
                    )
                ),
            warnings = "",
            api =
                """
                    package test.pkg {
                      public class MyClass extends test.pkg.PublicSuperParent implements test.pkg.PublicInterface test.pkg.PublicInterface2 {
                        ctor public MyClass();
                        method public void myMethod();
                        method public void publicInterfaceMethod();
                        method public void publicInterfaceMethod2();
                        method public void publicMethod();
                        method public void publicMethod2();
                        field public static final int MY_CONSTANT = 5; // 0x5
                      }
                      public interface PublicInterface {
                        method public void publicInterfaceMethod();
                      }
                      public interface PublicInterface2 {
                        method public void publicInterfaceMethod2();
                      }
                      public abstract class PublicSuperParent {
                        ctor public PublicSuperParent();
                        method public void inheritedMethod1();
                        method public void inheritedMethod2();
                        method public abstract void publicMethod();
                      }
                    }
                """,
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MyClass extends test.pkg.PublicSuperParent implements test.pkg.PublicInterface, test.pkg.PublicInterface2 {
                public MyClass() { throw new RuntimeException("Stub!"); }
                public void myMethod() { throw new RuntimeException("Stub!"); }
                public void publicInterfaceMethod2() { throw new RuntimeException("Stub!"); }
                public void publicMethod() { throw new RuntimeException("Stub!"); }
                public void publicMethod2() { throw new RuntimeException("Stub!"); }
                public void publicInterfaceMethod() { throw new RuntimeException("Stub!"); }
                public void inheritedMethod2() { throw new RuntimeException("Stub!"); }
                public static final int MY_CONSTANT = 5; // 0x5
                }
                """
        )
    }

    @Test
    fun `Rewriting type parameters in interfaces from hidden super classes and in throws lists`() {
        checkStubs(
            format = FileFormat.V2,
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
                package test.pkg {
                  public class Generics {
                    ctor public Generics();
                  }
                  public class Generics.MyClass<X, Y extends java.lang.Number> extends test.pkg.Generics.PublicParent<X,Y> implements test.pkg.Generics.PublicInterface<X,Y> {
                    ctor public Generics.MyClass();
                    method public java.util.Map<X,java.util.Map<Y,java.lang.String>> createMap(java.util.List<X>) throws java.io.IOException;
                    method protected java.util.List<X> foo();
                  }
                  public static interface Generics.PublicInterface<A, B> {
                    method public java.util.Map<A,java.util.Map<B,java.lang.String>> createMap(java.util.List<A>) throws java.io.IOException;
                  }
                  public abstract class Generics.PublicParent<A, B extends java.lang.Number> {
                    ctor public Generics.PublicParent();
                    method protected abstract java.util.List<A> foo();
                  }
                }
                """,
            source =
                if (SUPPORT_TYPE_USE_ANNOTATIONS) {
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
                } else {
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
                }
        )
    }

    @Test
    fun `Interface extending multiple interfaces`() {
        // Ensure that we handle sorting correctly where we're mixing super classes and implementing
        // interfaces
        // Real-world example: XmlResourceParser
        check(
            checkCompilation = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.content.res;
                    import android.util.AttributeSet;
                    import org.xmlpull.v1.XmlPullParser;

                    @SuppressWarnings("UnnecessaryInterfaceModifier")
                    public interface XmlResourceParser extends XmlPullParser, AttributeSet, AutoCloseable {
                        public void close();
                    }
                    """
                    ),
                    java(
                        """
                    package android.util;
                    public interface AttributeSet {
                    }
                    """
                    ),
                    java(
                        """
                    package java.lang;
                    public interface AutoCloseable {
                    }
                    """
                    ),
                    java(
                        """
                    package org.xmlpull.v1;
                    public interface XmlPullParser {
                    }
                    """
                    )
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                    package android.content.res;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface XmlResourceParser extends org.xmlpull.v1.XmlPullParser, android.util.AttributeSet, java.lang.AutoCloseable {
                    public void close();
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Functional Interfaces`() {
        checkStubs(
            skipEmitPackages = emptyList(),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package com.android.metalava.test;

                    @SuppressWarnings("something") @FunctionalInterface
                    public interface MyInterface {
                        void run();
                    }
                    """
                    )
                ),
            warnings = "",
            source =
                """
                package com.android.metalava.test;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                @java.lang.FunctionalInterface
                public interface MyInterface {
                public void run();
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Extends and implements multiple interfaces in Kotlin Stubs`() {
        check(
            extraArguments = arrayOf(ARG_KOTLIN_STUBS),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg
                    class MainClass: MyParentClass(), MyInterface1, MyInterface2

                    open class MyParentClass
                    interface MyInterface1
                    interface MyInterface2
                """
                    )
                ),
            stubFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        @file:Suppress("ALL")
                        class MainClass : test.pkg.MyParentClass(), test.pkg.MyInterface1, test.pkg.MyInterface2 {
                        open fun MainClass(): test.pkg.MainClass = error("Stub!")
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `Extends and implements multiple interfaces`() {
        check(
            checkCompilation = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class MainClass extends MyParentClass implements MyInterface1, MyInterface2 {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public interface MyInterface1 { }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public interface MyInterface2 { }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    public class MyParentClass { }
                    """
                    )
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class MainClass extends test.pkg.MyParentClass implements test.pkg.MyInterface1, test.pkg.MyInterface2 {
                        public MainClass() { throw new RuntimeException("Stub!"); }
                        }
                    """
                    )
                )
        )
    }
}
