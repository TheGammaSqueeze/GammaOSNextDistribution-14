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

import com.android.tools.metalava.testing.java
import org.junit.Test

@SuppressWarnings("ALL")
class StubsGenericTest : AbstractStubsTest() {
    @Test
    fun `Generate stubs for generics`() {
        // Basic interface with generics; makes sure <T extends Object> is written as just <T>
        // Also include some more complex generics expressions to make sure they're serialized
        // correctly (in particular, using fully qualified names instead of what appears in
        // the source code.)
        check(
            checkCompilation = true,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public interface MyInterface2<T extends Number>
                            extends MyBaseInterface {
                        class TtsSpan<C extends MyInterface<?>> { }
                        abstract class Range<T extends Comparable<? super T>> { }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public interface MyInterface<T extends Object>
                            extends MyBaseInterface {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface MyBaseInterface {
                    }
                    """
                    )
                ),
            expectedIssues = "",
            stubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface MyInterface2<T extends java.lang.Number> extends test.pkg.MyBaseInterface {
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public abstract static class Range<T extends java.lang.Comparable<? super T>> {
                    public Range() { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static class TtsSpan<C extends test.pkg.MyInterface<?>> {
                    public TtsSpan() { throw new RuntimeException("Stub!"); }
                    }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface MyInterface<T> extends test.pkg.MyBaseInterface {
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public interface MyBaseInterface {
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Check correct throws list for generics`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import java.util.function.Supplier;

                    @SuppressWarnings("RedundantThrows")
                    public final class Test<T> {
                        public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
                            return null;
                        }
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public final class Test<T> {
                public Test() { throw new RuntimeException("Stub!"); }
                public <X extends java.lang.Throwable> T orElseThrow(java.util.function.Supplier<? extends X> exceptionSupplier) throws X { throw new RuntimeException("Stub!"); }
                }
                """
        )
    }

    @Test
    fun `Generate stubs for additional generics scenarios`() {
        // Some additional declarations where PSI default type handling diffs from doclava1
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public abstract class Collections {
                        public static <T extends java.lang.Object & java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T> collection) {
                            return null;
                        }
                        public abstract <T extends java.util.Collection<java.lang.String>> T addAllTo(T t);
                        public final class Range<T extends java.lang.Comparable<? super T>> { }
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public abstract class Collections {
                public Collections() { throw new RuntimeException("Stub!"); }
                public static <T extends java.lang.Object & java.lang.Comparable<? super T>> T max(java.util.Collection<? extends T> collection) { throw new RuntimeException("Stub!"); }
                public abstract <T extends java.util.Collection<java.lang.String>> T addAllTo(T t);
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public final class Range<T extends java.lang.Comparable<? super T>> {
                public Range() { throw new RuntimeException("Stub!"); }
                }
                }
                """
        )
    }

    @Test
    fun `Generate stubs for even more generics scenarios`() {
        // Some additional declarations where PSI default type handling diffs from doclava1
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import java.util.Set;

                    @SuppressWarnings("ALL")
                    public class MoreAsserts {
                        public static void assertEquals(String arg1, Set<? extends Object> arg2, Set<? extends Object> arg3) { }
                        public static void assertEquals(Set<? extends Object> arg1, Set<? extends Object> arg2) { }
                    }
                    """
                    )
                ),
            source =
                """
                package test.pkg;
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class MoreAsserts {
                public MoreAsserts() { throw new RuntimeException("Stub!"); }
                public static void assertEquals(java.lang.String arg1, java.util.Set<?> arg2, java.util.Set<?> arg3) { throw new RuntimeException("Stub!"); }
                public static void assertEquals(java.util.Set<?> arg1, java.util.Set<?> arg2) { throw new RuntimeException("Stub!"); }
                }
                """,
            checkTextStubEquivalence = true
        )
    }

    @Test
    fun `Check generating classes with generics`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    public class Generics {
                        public <T> Generics(int surfaceSize, Class<T> klass) {
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
                    public class Generics {
                    public <T> Generics(int surfaceSize, java.lang.Class<T> klass) { throw new RuntimeException("Stub!"); }
                    }
                """
        )
    }

    @Test
    fun `Generics Variable Rewriting`() {
        // When we move methods from hidden superclasses into the subclass since they
        // provide the implementation for a required method, it's possible that the
        // method we copied in is referencing generics with a different variable than
        // in the current class, so we need to handle this

        checkStubs(
            sourceFiles =
                arrayOf(
                    // TODO: Try using prefixes like "A", and "AA" to make sure my generics
                    // variable renaming doesn't do something really unexpected
                    java(
                        """
                    package test.pkg;

                    import java.util.List;
                    import java.util.Map;

                    public class Generics {
                        public class MyClass<X extends Number,Y> extends HiddenParent<X,Y> implements PublicParent<X,Y> {
                        }

                        public class MyClass2<W> extends HiddenParent<Float,W> implements PublicParent<Float, W> {
                        }

                        public class MyClass3 extends HiddenParent<Float,Double> implements PublicParent<Float,Double> {
                        }

                        class HiddenParent<M, N> extends HiddenParent2<M, N>  {
                        }

                        class HiddenParent2<T, TT>  {
                            public Map<T,Map<TT, String>> createMap(List<T> list) {
                                return null;
                            }
                        }

                        public interface PublicParent<A extends Number,B> {
                            Map<A,Map<B, String>> createMap(List<A> list);
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
                    public class Generics {
                    public Generics() { throw new RuntimeException("Stub!"); }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass<X extends java.lang.Number, Y> implements test.pkg.Generics.PublicParent<X,Y> {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    public java.util.Map<X,java.util.Map<Y,java.lang.String>> createMap(java.util.List<X> list) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass2<W> implements test.pkg.Generics.PublicParent<java.lang.Float,W> {
                    public MyClass2() { throw new RuntimeException("Stub!"); }
                    public java.util.Map<java.lang.Float,java.util.Map<W,java.lang.String>> createMap(java.util.List<java.lang.Float> list) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass3 implements test.pkg.Generics.PublicParent<java.lang.Float,java.lang.Double> {
                    public MyClass3() { throw new RuntimeException("Stub!"); }
                    public java.util.Map<java.lang.Float,java.util.Map<java.lang.Double,java.lang.String>> createMap(java.util.List<java.lang.Float> list) { throw new RuntimeException("Stub!"); }
                    }
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public static interface PublicParent<A extends java.lang.Number, B> {
                    public java.util.Map<A,java.util.Map<B,java.lang.String>> createMap(java.util.List<A> list);
                    }
                    }
                    """
        )
    }
}
